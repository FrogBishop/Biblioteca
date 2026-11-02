"""Run native Sodium terrain/particle/cloud checks in an isolated copied Fabric world."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import zipfile
from verify_matrix import ROOT, quote
from verify_sodium_matrix import select, fetch


def mixin(name, target, method, static=False):
    return f'''package biblioteca.sodiumscene.mixin;
import biblioteca.sodiumscene.SceneProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="{target}", remap=false)
public abstract class {name} {{
  @Inject(method="{method}",at=@At("HEAD"))
  private {'static ' if static else ''}void observe(CallbackInfo ci) {{ SceneProbe.drawn(); }}
}}
'''


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--version', required=True)
    p.add_argument('--world-template', type=Path, required=True)
    p.add_argument('--assets-dir', type=Path)
    p.add_argument('--asset-index')
    p.add_argument('--label', default='scene')
    p.add_argument('--lwjgl-library-dir', type=Path)
    p.add_argument('--tall', action='store_true', help='Use the normal overworld height to test discovery across a long period')
    p.add_argument('--vanilla-particles', action='store_true', help='Disable Sodium particle rewriting to verify the optional callback fallback')
    p.add_argument('--java', default='java')
    p.add_argument('--javac', default='javac')
    a = p.parse_args()
    node = tuple(map(int, a.version.split('.')))
    short = node >= (1, 18) and not a.tall
    particles = node < (1, 21, 9)
    cfg = json.loads((ROOT / 'build/validation/runtime' / (a.version + '.json')).read_text())
    row = next(r for r in select() if r['minecraft'] == a.version and r['loader'] == 'fabric')
    sodium = fetch(row)
    with zipfile.ZipFile(sodium) as z:
        names = z.namelist()
        prefix = 'net.caffeinemc.mods.sodium' if 'net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.class' in names else 'me.jellysquid.mods.sodium'
        clouds = prefix.replace('.', '/') + '/client/render/immediate/CloudRenderer.class' in names
        gpu = prefix.replace('.', '/') + '/client/gpu/device/batch/MultiDrawBatch.class' in names
        region = prefix.replace('.', '/') + '/client/render/chunk/RegionChunkRenderer.class' in names
        plural_draw = region and b'executeDrawBatches' in z.read(prefix.replace('.', '/') + '/client/render/chunk/RegionChunkRenderer.class')
    out = ROOT / 'build/validation' / a.version / 'sodium-scene' / a.label
    run = out / time.strftime('run-%Y%m%d-%H%M%S')
    (run / 'mods').mkdir(parents=True)
    shutil.copy2(sodium, run / 'mods')
    if a.vanilla_particles:
        (run / 'config').mkdir()
        (run / 'config/sodium-mixins.properties').write_text('mixin.features.render.particle=false\n')
    mod_version = __import__('tomllib').loads((ROOT / 'stonecutter.properties.toml').read_text())['mod']['version']
    artifact = ROOT / 'build/distributions' / f'biblioteca-{mod_version}+{a.version}.jar'
    shutil.copy2(artifact, run / 'mods')
    shutil.copytree(a.world_template, run / 'saves/sodium-world', ignore=shutil.ignore_patterns('session.lock'))
    if short:
        for file in cfg['classpath']:
            if Path(file).is_file() and Path(file).name.startswith('minecraft') and str(file).endswith('.jar'):
                with zipfile.ZipFile(file) as z:
                    if 'data/minecraft/dimension_type/overworld.json' in z.namelist():
                        dimension = json.loads(z.read('data/minecraft/dimension_type/overworld.json'))
                        version = json.loads(z.read('version.json'))['pack_version']
                        break
        else:
            raise RuntimeError('Native dimension schema missing')
        dimension.update(height=16, logical_height=16, min_y=0)
        if 'attributes' in dimension:
            dimension['attributes']['minecraft:visual/cloud_height'] = 8.0
        pack = run / 'saves/sodium-world/datapacks/sodium-scene'
        for kind in ['dimension_type', 'dimension']:
            (pack / 'data/biblioteca' / kind).mkdir(parents=True, exist_ok=True)
        metadata = {'description': 'Disposable Sodium scene'}
        if 'data' in version:
            metadata['pack_format'] = version['data']
        else:
            metadata.update(min_format=[version['data_major'], version['data_minor']], max_format=[version['data_major'], version['data_minor']])
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': metadata}))
        (pack / 'data/biblioteca/dimension_type/sodium_scene.json').write_text(json.dumps(dimension))
        flat = {'biome': 'minecraft:plains', 'lakes': False, 'features': False, 'layers': [{'block': 'minecraft:air', 'height': 1}]}
        flat['structure_overrides' if node >= (1, 19) else 'structures'] = [] if node >= (1, 19) else {'structures': {}}
        (pack / 'data/biblioteca/dimension/sodium_scene.json').write_text(json.dumps({'type': 'biblioteca:sodium_scene', 'generator': {'type': 'minecraft:flat', 'settings': flat}}))
    (run / 'options.txt').write_text('renderDistance:8\nsimulationDistance:8\npauseOnLostFocus:false\nmaxFps:60\nfullscreen:false\nfov:0.5\nrenderClouds:true\nlang:en_us\n')
    sources = run / 'probe-sources'
    sources.mkdir()
    for source in (ROOT / 'tools/validation/sodium').glob('*.java'):
        shutil.copy2(source, sources)
    renderer = prefix + ('.client.gpu.device.batch.GLDrawBatch' if gpu else '.client.render.chunk.RegionChunkRenderer' if region else '.client.render.chunk.DefaultChunkRenderer')
    (sources / 'DrawObserver.java').write_text(mixin('DrawObserver', renderer, 'draw' if gpu else 'executeDrawBatches' if plural_draw else 'executeDrawBatch', not gpu and not region))
    mixins = ['DrawObserver']
    if clouds:
        source = mixin('CloudObserver', prefix + '.client.render.immediate.CloudRenderer', 'render').replace('@At("HEAD")', '@At("RETURN")').replace('SceneProbe.drawn()', 'SceneProbe.cloud(this)')
        (sources / 'CloudObserver.java').write_text(source)
        mixins.append('CloudObserver')
    if node >= (1, 18):
        legacy = node < (1, 20, 2)
        parameters = '@Coerce Object screen, String name, boolean customized, Runnable proceed' if legacy else '@Coerce Object world, boolean customized, Runnable proceed, Runnable cancel'
        match = 'name.equals("sodium-world")' if legacy else 'SceneProbe.invoke(world,"getLevelId").equals("sodium-world")'
        proceed = 'SceneProbe.deferOpen(proceed)' if legacy else 'proceed.run()'
        (sources / 'WorldOpenObserver.java').write_text(f'''package biblioteca.sodiumscene.mixin;
import biblioteca.sodiumscene.SceneProbe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.minecraft.client.gui.screens.worldselection.WorldOpenFlows")
public abstract class WorldOpenObserver {{
 @Inject(method="askForBackup",at=@At("HEAD"),cancellable=true)
 private void proceed({parameters},CallbackInfo ci) {{ if({match}) {{ System.out.println("SCENE_BACKUP disposable-world"); {proceed}; ci.cancel(); }} }}
}}''')
        mixins.append('WorldOpenObserver')
    if particles:
        # Sodium 0.7 can cancel the public render method before the private camera method runs.
        # Observe that outer call so both native and optimized paths forward to the real writer.
        modern = False
        method = 'renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;Lorg/joml/Quaternionf;F)V' if modern else 'render'
        extra = '@Coerce Object rotation, ' if modern else ''
        call = 'writer,camera,rotation,partial' if modern else 'writer,camera,partial'
        (sources / 'ParticleObserver.java').write_text(f'''package biblioteca.sodiumscene.mixin;
import biblioteca.sodiumscene.*;
import babel.biblioteca.client.ClientCompat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(value=SingleQuadParticle.class,priority=500)
public abstract class ParticleObserver {{
 @WrapMethod(method="{method}")
 private void observe(VertexConsumer writer,Camera camera,{extra}float partial,Operation<Void> original) {{
  Particle p=(Particle)(Object)this;
  if(!SceneProbe.tracked(p)) {{ original.call({call}); return; }}
  Object y=SceneProbe.field(p,"y"),yo=SceneProbe.field(p,"yo"),age=SceneProbe.field(p,"age"),life=SceneProbe.field(p,"lifetime");
  ParticleForwarder capture=new ParticleForwarder();
  writer=capture.wrap(writer); original.call({call});
  if(!y.equals(SceneProbe.field(p,"y")) || !yo.equals(SceneProbe.field(p,"yo")) || !age.equals(SceneProbe.field(p,"age")) || !life.equals(SceneProbe.field(p,"lifetime"))) throw new AssertionError("particle simulation changed during render");
  if(capture.count!=4) throw new AssertionError("native particle vertices="+capture.count);
  SceneProbe.particle(p,ClientCompat.position(camera).y,capture.sum/capture.count,((Number)age).intValue(),capture.optimized);
 }}
}}''')
        mixins.append('ParticleObserver')
    cp = list(cfg['classpath'])
    if a.version == '1.20.1' and a.lwjgl_library_dir:
        cp = [str(a.lwjgl_library_dir / Path(f).name.split('-3.3.2')[0] / '3.3.1' / Path(f).name.replace('-3.3.2', '-3.3.1')) if '/org.lwjgl/' in f.replace('\\', '/') else f for f in cp]
    classes = run / 'probe-classes'
    classes.mkdir()
    args = ['-encoding', 'UTF-8', '--release', cfg['java'], '-cp', os.pathsep.join(cp), '-d', str(classes), *map(str, sources.glob('*.java'))]
    (run / 'compile.args').write_text('\n'.join(map(quote, args)))
    with (run / 'compile.log').open('w') as log:
        result = subprocess.run([a.javac, '@' + str(run / 'compile.args')], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError('Probe compilation: ' + str(run / 'compile.log'))
    probe = run / 'probe.jar'
    with zipfile.ZipFile(probe, 'w') as z:
        for f in classes.rglob('*.class'): z.write(f, f.relative_to(classes).as_posix())
        z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'biblioteca_sodium_scene', 'version': '1', 'environment': 'client',
            'entrypoints': {'client': ['biblioteca.sodiumscene.SceneProbe']}, 'mixins': ['scene.mixins.json']}))
        z.writestr('scene.mixins.json', json.dumps({'required': True, 'package': 'biblioteca.sodiumscene.mixin', 'compatibilityLevel': 'JAVA_' + cfg['java'], 'client': mixins, 'injectors': {'defaultRequire': 1}}))
    cp = [f for f in cp if f not in (cfg['classes'], cfg['resources'])] + [str(probe)]
    options = ['-Xmx2G', '-XX:StackShadowPages=32', '--sun-misc-unsafe-memory-access=allow', '--enable-native-access=ALL-UNNAMED',
               '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED', '-Duser.language=en', '-Duser.country=US', '-Dfile.encoding=UTF-8', '-Dbiblioteca.scene.short=' + str(short).lower(),
               '-Dbiblioteca.scene.particles=' + str(particles).lower(), '-Dbiblioteca.scene.sodiumClouds=' + str(clouds).lower(),
               '-Dbiblioteca.scene.optimized=' + str(not a.vanilla_particles).lower(),
               '-Dbiblioteca.scene.open=' + ('legacy' if node < (1, 20) else 'quick')]
    launch = Path(next(v.split('=', 1)[1] for v in cfg['clientJvmArgs'] if v.startswith('-Dfabric.dli.config=')))
    group = ''
    launch_args = []
    for line in launch.read_text().splitlines():
        if not line.startswith('\t'): group = line.strip()
        elif group in ('commonProperties', 'clientProperties') and '=' in line:
            key, value = line.strip().split('=', 1)
            if key == 'log4j.configurationFile': value = Path(value).as_uri()
            options.append('-D' + key + '=' + value)
        elif group in ('commonArgs', 'clientArgs'): launch_args.append(line.strip())
    if a.assets_dir and a.asset_index:
        for key in ['--assetsDir', '--assetIndex']:
            if key in launch_args:
                i = launch_args.index(key); del launch_args[i:i + 2]
        launch_args += ['--assetsDir', str(a.assets_dir.resolve()), '--assetIndex', a.asset_index]
    launch_args += ['--username', 'SodiumScene', '--gameDir', str(run), '--width', '960', '--height', '600', '--version', a.version, '--accessToken', '0']
    if node >= (1, 20): launch_args += ['--quickPlaySingleplayer', 'sodium-world']
    command = options + ['-cp', os.pathsep.join(cp), 'net.fabricmc.loader.impl.launch.knot.KnotClient'] + launch_args
    (run / 'client.args').write_text('\n'.join(map(quote, command)))
    print('SCENE_RUN ' + str(run), flush=True)
    with (run / 'client.log').open('w') as log:
        try: code = subprocess.run([a.java, '@' + str(run / 'client.args')], cwd=run, stdout=log, stderr=subprocess.STDOUT, timeout=360).returncode
        except subprocess.TimeoutExpired: code = -1
    text = (run / 'client.log').read_text(encoding='utf-8', errors='replace')
    images = list((run / 'screenshots').glob('*.png'))
    result = {'minecraft': a.version, 'sodium': row['release']['version_number'], 'passed': code == 0 and 'SCENE_DONE passed=true' in text and len(images) == (9 if particles else 6),
              'vanillaParticles': a.vanilla_particles,
              'exitCode': code, 'artifactSha256': hashlib.sha256((run / 'mods' / artifact.name).read_bytes()).hexdigest(),
              'sodiumSha256': hashlib.sha256((run / 'mods' / sodium.name).read_bytes()).hexdigest(),
              'log': str((run / 'client.log').relative_to(ROOT)), 'screenshots': [str(f.relative_to(ROOT)) for f in images],
              'samples': [line for line in text.splitlines() if 'SCENE_' in line]}
    for report in [run / 'results.json', out / 'results.json']: report.write_text(json.dumps(result, indent=2))
    print(('SCENE_OK ' if result['passed'] else 'SCENE_FAILED ') + a.version, flush=True)
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__': main()
