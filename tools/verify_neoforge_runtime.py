"""Exercise native creation/video screens, joined-world sync and camera crossings on NeoForge."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import zipfile
from verify_neoforge import ROOT, compile_java, quote
from verify_neoforge_legacy import loader_aliases, game_command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--variant', choices=['full', 'norender'], default='full')
    parser.add_argument('--world-template', type=Path, required=True)
    parser.add_argument('--label', default='runtime')
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--sodium', action='store_true', help='Use the selected official NeoForge Sodium release')
    args = parser.parse_args()
    runtime = json.loads((ROOT / f'platforms/neoforge-{args.version}/build/validation/runtime.json').read_text(encoding='utf-8'))
    artifact = Path(runtime['artifacts'][args.variant])
    loaded = Path(runtime.get('validationArtifacts', {}).get(args.variant, str(artifact)))
    if not (args.world_template / 'level.dat').is_file():
        raise SystemExit('A disposable saved world template is required')
    run = ROOT / 'build/neoforge-backport/runtime' / args.version / args.variant / args.label / time.strftime('run-%Y%m%d-%H%M%S')
    (run / 'mods').mkdir(parents=True)
    (run / 'production').mkdir()
    shutil.copy2(artifact, run / 'production' / artifact.name)
    frozen = run / 'mods' / loaded.name
    shutil.copy2(loaded, frozen)
    sodium_record = None
    if args.sodium:
        from verify_sodium_matrix import select, fetch
        row = next(row for row in select() if row['minecraft'] == args.version and row['loader'] == 'neoforge')
        sodium = fetch(row)
        if sodium is None:
            raise SystemExit('No official Sodium release for this target')
        shutil.copy2(sodium, run / 'mods' / sodium.name)
        sodium_record = {'version': row['release']['version_number'], 'sha256': hashlib.sha256(sodium.read_bytes()).hexdigest()}
    shutil.copytree(args.world_template, run / 'saves/runtime-world', ignore=shutil.ignore_patterns('session.lock'))
    (run / 'options.txt').write_text('renderDistance:8\nsimulationDistance:8\npauseOnLostFocus:false\nmaxFps:60\nfullscreen:false\nfov:0.5\nlang:en_us\n', encoding='utf-8')
    (run / 'config').mkdir()
    (run / 'config/fml.toml').write_text('earlyWindowControl=false\n', encoding='utf-8')
    if args.sodium and tuple(map(int, args.version.split('.'))) >= (1, 21, 8):
        # NeoForge enables this wrapper only in development; Sodium expects the production
        # OpenGL texture type. Match NeoForgeClientConfig's normal-install default in this run.
        (run / 'config/neoforge-client.toml').write_text('enableB3DValidationLayer=false\n', encoding='utf-8')
    source_dir = run / 'probe-source'; source_dir.mkdir()
    source = ROOT / 'tools/neoforge-runtime/RuntimeProbe.java'
    (source_dir / source.name).write_text(loader_aliases(source.read_text(encoding='utf-8'), runtime), encoding='utf-8')
    observers = {
        'ServerObserver': '''package biblioteca.runtimeprobe.mixin;
import biblioteca.runtimeprobe.RuntimeProbe;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftServer.class) public abstract class ServerObserver {
 @Inject(method="tickServer",at=@At("TAIL")) private void tick(CallbackInfo ci) { RuntimeProbe.server((MinecraftServer)(Object)this); }
}''',
        'ClientObserver': '''package biblioteca.runtimeprobe.mixin;
import biblioteca.runtimeprobe.RuntimeProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class) public abstract class ClientObserver {
 @Inject(method="tick",at=@At("TAIL")) private void tick(CallbackInfo ci) { RuntimeProbe.tick((Minecraft)(Object)this); }
}''',
        'CameraObserver': '''package biblioteca.runtimeprobe.mixin;
import biblioteca.runtimeprobe.RuntimeProbe;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class) public abstract class CameraObserver {
 @Inject(method="setup",at=@At("TAIL")) private void setup(BlockGetter level,Entity entity,boolean detached,boolean mirror,float partial,CallbackInfo ci) {
  RuntimeProbe.camera((Camera)(Object)this,entity,partial);
 }
}'''
    }
    legacy_open = tuple(map(int, args.version.split('.'))) < (1, 20, 2)
    parameters = '@Coerce Object screen,String name,boolean customized,Runnable proceed' if legacy_open else '@Coerce Object access,boolean customized,Runnable proceed,Runnable cancel'
    condition = '"runtime-world".equals(name)' if legacy_open else 'RuntimeProbe.isWorld(access)'
    observers['WorldOpenObserver'] = f'''package biblioteca.runtimeprobe.mixin;
import biblioteca.runtimeprobe.RuntimeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.minecraft.client.gui.screens.worldselection.WorldOpenFlows")
public abstract class WorldOpenObserver {{
 @Inject(method="askForBackup",at=@At("HEAD"),cancellable=true)
 private void disposable({parameters},CallbackInfo ci) {{
  if ({condition}) {{ RuntimeProbe.deferOpen(proceed); ci.cancel(); }}
 }}
}}'''
    for name, text in observers.items():
        (source_dir / (name + '.java')).write_text(text, encoding='utf-8')
    classes = run / 'probe-classes'
    compile_java(args.javac, runtime.get('clientClasspath', runtime['classpath']), classes, list(source_dir.glob('*.java')), run / 'compile.log', runtime['java'])
    probe = run / 'runtime-probe.jar'
    with zipfile.ZipFile(probe, 'w') as jar:
        for file in classes.rglob('*.class'):
            jar.write(file, file.relative_to(classes).as_posix())
        header = 'modLoader="javafml"\nloaderVersion="[1,)"\n' if runtime.get('launcher') == 'modlauncher' else ''
        jar.writestr(runtime.get('metadata', 'META-INF/neoforge.mods.toml'), header + 'license="GPLv3"\n[[mods]]\nmodId="biblioteca_runtime_probe"\nversion="1"\ndisplayName="Biblioteca native runtime validation"\n[[mixins]]\nconfig="biblioteca.runtime-probe.mixins.json"\n')
        jar.writestr('biblioteca.runtime-probe.mixins.json', json.dumps({'required': True, 'package': 'biblioteca.runtimeprobe.mixin',
                'compatibilityLevel': 'JAVA_' + str(runtime['java']), 'mixins': ['ServerObserver'], 'client': ['ClientObserver', 'CameraObserver', 'WorldOpenObserver'], 'injectors': {'defaultRequire': 1}}))
        if args.version == '1.20.1':
            jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nMixinConfigs: biblioteca.runtime-probe.mixins.json\n\n')
            jar.writestr('pack.mcmeta', json.dumps({'pack': {'description': 'Disposable runtime probe resources', 'pack_format': 15}}))
    folders = os.pathsep.join(['biblioteca%%' + str(frozen), 'biblioteca_runtime_probe%%' + str(probe)])
    options = ['-Xmx3G', '--enable-native-access=ALL-UNNAMED', '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
               '-Dfile.encoding=UTF-8', '-Dbiblioteca.runtime.norender=' + str(args.variant == 'norender').lower()]
    command, environment = game_command(runtime, 'client', run, folders, options,
            ['--username', 'RuntimeCheck', '--accessToken', '0', '--width', '1280', '--height', '720'])
    argfile = run / 'client.args'; argfile.write_text('\n'.join(map(quote, command)) + '\n', encoding='utf-8')
    print('NEO_RUNTIME_RUN', run, flush=True)
    with (run / 'client.log').open('w', encoding='utf-8') as log:
        try:
            code = subprocess.run([args.java, '@' + str(argfile)], cwd=run, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=360).returncode
        except subprocess.TimeoutExpired:
            code = -1
    text = (run / 'client.log').read_text(encoding='utf-8', errors='replace')
    screenshots = sorted((run / 'screenshots').glob('*.png'))
    result = {'passed': code == 0 and 'NEO_RUNTIME_OK ' in text and 'NEO_RUNTIME_FAILED' not in text and len(screenshots) == 6,
              'minecraft': args.version, 'variant': args.variant, 'exitCode': code,
              'sodium': sodium_record,
              'productionGpuSettings': args.sodium and tuple(map(int, args.version.split('.'))) >= (1, 21, 8),
              'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(), 'loadedSha256': hashlib.sha256(frozen.read_bytes()).hexdigest(),
              'screenshots': [str(p.relative_to(ROOT)) for p in screenshots], 'log': str((run / 'client.log').relative_to(ROOT)),
              'samples': [line for line in text.splitlines() if 'NEO_RUNTIME_' in line]}
    (run / 'result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('NEO_RUNTIME_RESULT', result['passed'], flush=True)
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
