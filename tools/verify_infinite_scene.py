"""Run the Infinity snapshot's native client in an isolated world with optional no-render UI checks."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import tomllib
import zipfile

from verify_neoforge import ROOT, compile_java, quote


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world-template', type=Path, required=True)
    parser.add_argument('--assets-dir', type=Path, default=ROOT / 'build/infinite-port/assets')
    parser.add_argument('--asset-index', default='20w14infinite')
    parser.add_argument('--variant', choices=['full', 'norender'], default='full')
    parser.add_argument('--label', default='scene')
    parser.add_argument('--scenario', choices=['scene', 'catalog'], default='scene')
    parser.add_argument('--language', default='en_us')
    parser.add_argument('--width', type=int, default=1280)
    parser.add_argument('--height', type=int, default=720)
    args = parser.parse_args()
    cfg = json.loads((ROOT / 'build/validation/runtime/20w14infinite.json').read_text(encoding='utf-8'))
    version = tomllib.loads((ROOT / 'stonecutter.properties.toml').read_text(encoding='utf-8'))['mod']['version']
    catalog = args.scenario == 'catalog'
    probe_class = 'InfiniteCatalogProbe' if catalog else 'InfiniteSceneProbe'
    marker = 'INFINITE_CATALOG' if catalog else 'INFINITE_SCENE'
    suffix = '-norender' if args.variant == 'norender' else ''
    artifact = ROOT / 'build/distributions' / f'biblioteca-{version}+20w14infinite{suffix}.jar'
    run = ROOT / 'build/validation/20w14infinite' / ('catalog-ui' if catalog else 'scene') / args.label / time.strftime('run-%Y%m%d-%H%M%S')
    (run / 'mods').mkdir(parents=True)
    shutil.copy2(artifact, run / 'mods' / artifact.name)
    shutil.copytree(args.world_template, run / 'saves/infinite-world', ignore=shutil.ignore_patterns('session.lock'))
    lifecycle = next((ROOT / '.gradle/fabric-api/0.5.7+build.2-20w14infinite').glob('fabric-events-lifecycle-v0-*.jar'))
    shutil.copy2(lifecycle, run / 'mods' / lifecycle.name)
    (run / 'options.txt').write_text('renderDistance:16\npauseOnLostFocus:false\nmaxFps:60\nfullscreen:false\nfov:0.5\nrenderClouds:true\nlang:' + args.language + '\n', encoding='utf-8')
    sources = run / 'probe-sources'
    sources.mkdir()
    shutil.copy2(ROOT / 'tools/validation/infinite' / (probe_class + '.java'), sources)
    observers = {
        'ClientTickObserver': '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteSceneProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class ClientTickObserver {
 @Inject(method="tick",at=@At("TAIL"))
 private void observe(CallbackInfo ci) { InfiniteSceneProbe.tick((Minecraft)(Object)this); }
}''',
        'ParticleObserver': '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteSceneProbe;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(value=SingleQuadParticle.class,priority=500)
public abstract class ParticleObserver {
 @WrapMethod(method="render")
 private void observe(VertexConsumer buffer,Camera camera,float partial,Operation<Void> original) {
  InfiniteSceneProbe.renderParticle((SingleQuadParticle)(Object)this,buffer,camera,partial,original);
 }
}''',
        'EntityObserver': '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteSceneProbe;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=EntityRenderDispatcher.class,priority=500)
public abstract class EntityObserver {
 @Inject(method="render",at=@At("HEAD"))
 private void observe(Entity entity,double x,double y,double z,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci) {
  InfiniteSceneProbe.entity(entity,y);
 }
}''',
        'CloudObserver': '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteSceneProbe;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=LevelRenderer.class,priority=500)
public abstract class CloudObserver {
 @org.spongepowered.asm.mixin.injection.Inject(method="renderClouds",at=@At("HEAD"))
 private void begin(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { InfiniteSceneProbe.beginCloudFrame(); }
 @WrapOperation(method="renderClouds",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexBuffer;draw(Lcom/mojang/math/Matrix4f;I)V"))
 private void observe(VertexBuffer buffer,Matrix4f pose,int mode,Operation<Void> original) {
  original.call(buffer,pose,mode); InfiniteSceneProbe.cloud(buffer);
 }
}''',
    }
    if args.variant == 'full':
        observers['TerrainObserver'] = '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteSceneProbe;
import babel.biblioteca.client.FixedTerrainBackend;
import babel.biblioteca.client.PeriodicTerrainPlan;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=FixedTerrainBackend.class,remap=false)
public abstract class TerrainObserver {
 @WrapOperation(method="draw",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexBuffer;draw(Lcom/mojang/math/Matrix4f;I)V"))
 private void observe(VertexBuffer buffer,Matrix4f pose,int mode,Operation<Void> original,@Local PeriodicTerrainPlan.Copy<VertexBuffer> copy) {
  original.call(buffer,pose,mode); InfiniteSceneProbe.terrain(copy.y()-copy.source().y());
 }
}'''
    if catalog:
        observers = {
            'ClientTickObserver': observers['ClientTickObserver'].replace('InfiniteSceneProbe', probe_class),
            'CatalogTextObserver': '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteCatalogProbe;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Font.class)
public abstract class CatalogTextObserver {
 @Inject(method="drawShadow(Ljava/lang/String;FFI)I",at=@At("HEAD"))
 private void observe(String text,float x,float y,int color,CallbackInfoReturnable<Integer> ci) { InfiniteCatalogProbe.text(text); }
}''',
            'CatalogHoverObserver': '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteCatalogProbe;
import babel.biblioteca.client.InfiniteDimensionConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=InfiniteDimensionConfigScreen.class,remap=false)
public abstract class CatalogHoverObserver {
 @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,ordinal=0)
 private int x(int value) { return InfiniteCatalogProbe.mouseX(value); }
 @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,ordinal=1)
 private int y(int value) { return InfiniteCatalogProbe.mouseY(value); }
}''',
            'CatalogTooltipObserver': '''package biblioteca.infiniteprobe.mixin;
import biblioteca.infiniteprobe.InfiniteCatalogProbe;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public abstract class CatalogTooltipObserver {
 @Inject(method="renderTooltip(Ljava/util/List;II)V",at=@At("TAIL"))
 private void observe(java.util.List<String> lines,int x,int y,CallbackInfo ci) { InfiniteCatalogProbe.tooltip(lines); }
}'''
        }
    for name, text in observers.items():
        (sources / (name + '.java')).write_text(text, encoding='utf-8')
    classes = run / 'probe-classes'
    compile_java('javac', cfg['classpath'] + [str(lifecycle)], classes, list(sources.glob('*.java')), run / 'compile.log', release=17)
    (classes / 'fabric.mod.json').write_text(json.dumps({
        'schemaVersion': 1, 'id': 'biblioteca_infinite_scene_probe', 'version': '1.0.0', 'environment': 'client',
        'entrypoints': {'client': ['biblioteca.infiniteprobe.' + probe_class]}, 'mixins': ['infinite-scene.mixins.json']
    }), encoding='utf-8')
    (classes / 'infinite-scene.mixins.json').write_text(json.dumps({
        'required': True, 'package': 'biblioteca.infiniteprobe.mixin', 'compatibilityLevel': 'JAVA_17',
        'client': list(observers), 'injectors': {'defaultRequire': 1}
    }), encoding='utf-8')
    probe = run / 'probe.jar'
    with zipfile.ZipFile(probe, 'w') as jar:
        for file in classes.rglob('*'):
            if file.is_file():
                jar.write(file, file.relative_to(classes).as_posix())
    cp = [p for p in cfg['classpath'] if p not in (cfg['classes'], cfg['resources'])] + [str(probe)]
    opts = ['-Xmx3G', '-Xss4M', '--enable-native-access=ALL-UNNAMED',
            '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED', '-Dfile.encoding=UTF-8',
            '-Dbiblioteca.infinite.norender=' + str(args.variant == 'norender').lower()]
    # Development launch properties include the extracted native-library directory.
    launch = next(v.split('=', 1)[1] for v in cfg['clientJvmArgs'] if v.startswith('-Dfabric.dli.config='))
    group = ''
    for line in Path(launch).read_text(encoding='utf-8').splitlines():
        if not line.startswith('\t'):
            group = line.strip()
        elif group in ('commonProperties', 'clientProperties') and '=' in line:
            key, value = line.strip().split('=', 1)
            if key == 'log4j.configurationFile':
                value = Path(value).resolve().as_uri()
            opts.append('-D' + key + '=' + value)
    game_args = ['--username', 'BibliotecaProbe', '--version', '20w14infinite', '--gameDir', str(run),
                 '--assetsDir', str(args.assets_dir.resolve()), '--assetIndex', args.asset_index,
                 '--uuid', '00000000000000000000000000000001', '--accessToken', '0', '--userType', 'mojang',
                 '--width', str(args.width), '--height', str(args.height)]
    arguments = opts + ['--class-path', os.pathsep.join(cp), 'net.fabricmc.loader.impl.launch.knot.KnotClient'] + game_args
    argfile = run / 'client.args'
    argfile.write_text('\n'.join(map(quote, arguments)), encoding='utf-8')
    print(marker + '_RUN', run, flush=True)
    with (run / 'client.log').open('w', encoding='utf-8') as log:
        try:
            code = subprocess.run(['java', '@' + str(argfile)], cwd=run, stdout=log, stderr=subprocess.STDOUT, timeout=360).returncode
        except subprocess.TimeoutExpired:
            code = -1
    text = (run / 'client.log').read_text(encoding='utf-8', errors='replace')
    images = sorted((run / 'screenshots').glob('*.png'))
    result = {'passed': code == 0 and marker + '_OK' in text and marker + '_FAILED' not in text and len(images) == (6 if catalog else 8),
              'variant': args.variant, 'scenario': args.scenario, 'language': args.language, 'exitCode': code, 'artifactSha256': hashlib.sha256((run / 'mods' / artifact.name).read_bytes()).hexdigest(),
              'screenshots': [str(p.relative_to(ROOT)) for p in images],
              'samples': [line for line in text.splitlines() if marker + '_' in line]}
    (run / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(marker + '_RESULT', result['passed'], flush=True)
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
