"""Helpers for the real ModLauncher/FML verification path used before Minecraft 1.21.9."""
from pathlib import Path
import os
import shlex
import zipfile


def loader_aliases(text, runtime):
    if runtime['minecraft'] != '1.20.1':
        return text
    return (text.replace('net.neoforged.neoforge.common.NeoForge', 'net.minecraftforge.common.MinecraftForge')
            .replace('NeoForge.EVENT_BUS', 'MinecraftForge.EVENT_BUS')
            .replace('net.neoforged.neoforge.', 'net.minecraftforge.')
            .replace('net.neoforged.fml.', 'net.minecraftforge.fml.')
            .replace('net.neoforged.api.distmarker.', 'net.minecraftforge.api.distmarker.')
            .replace('super(null, null, null, null);', 'super(null, null, null);'))


def prepare_launcher(root, runtime, reports, javac, compile_java):
    sources = reports / 'launcher-source'
    sources.mkdir(parents=True, exist_ok=True)
    text = (root / 'tools/neoforge-validation/launcher/LegacyLaunchTarget.java').read_text(encoding='utf-8')
    prefix = 'net/minecraftforge' if runtime['minecraft'] == '1.20.1' else 'net/neoforged'
    available = set()
    for path in runtime['classpath']:
        if path.endswith('.jar') and ('loader-' in path or 'fmlloader-' in path):
            with zipfile.ZipFile(path) as archive:
                available.update(archive.namelist())
    base = next(name for name in ['JUnitUserDevLaunchTarget', 'JUnitDevLaunchTarget', 'NeoForgeUserdevLaunchHandler', 'ForgeUserdevLaunchHandler']
                if prefix + '/fml/loading/targets/' + name + '.class' in available)
    text = loader_aliases(text.replace('NeoForgeUserdevLaunchHandler', base), runtime)
    source = sources / 'LegacyLaunchTarget.java'
    source.write_text(text, encoding='utf-8')
    classes = reports / 'launcher-classes'
    compile_java(javac, runtime['classpath'], classes, [source], reports / 'launcher-compile.log', runtime['java'])
    jar = reports / 'validation-launcher.jar'
    with zipfile.ZipFile(jar, 'w') as output:
        for file in classes.rglob('*.class'):
            output.write(file, file.relative_to(classes).as_posix())
        output.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nAutomatic-Module-Name: biblioteca.validation.launcher\nFMLModType: LIBRARY\n\n')
        output.writestr('META-INF/services/cpw.mods.modlauncher.api.ILaunchHandlerService',
                        'biblioteca.validation.launcher.LegacyLaunchTarget\n')
    return jar


def prepare_checks(root, runtime, reports):
    generated = reports / 'checks-source'
    generated.mkdir(parents=True, exist_ok=True)
    sources = []
    folder = root / 'tools/neoforge-validation'
    if runtime['network'] in ('channel', 'payload'):
        folder /= 'legacy'
    for path in sorted(folder.glob('*.java')):
        text = loader_aliases(path.read_text(encoding='utf-8'), runtime)
        # These helpers are invoked only by the 26.3 branch. The older class has no LoadResult API.
        text = text.replace('registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), Map.of())).apply();',
                            'throw new AssertionError("26.3-only tag reload helper");')
        dest = generated / path.name
        dest.write_text(text, encoding='utf-8')
        sources.append(dest)
    source = root / 'tools/neoforge-validation/launcher/LegacyBootstrap.java'
    dest = generated / source.name
    dest.write_text(loader_aliases(source.read_text(encoding='utf-8'), runtime), encoding='utf-8')
    sources.append(dest)
    return sources


def prepare_transform_bootstrap(root, runtime, reports, javac, compile_java):
    sources = reports / 'bootstrap-source'
    sources.mkdir(parents=True, exist_ok=True)
    source = sources / 'LegacyBootstrap.java'
    source.write_text(loader_aliases((root / 'tools/neoforge-validation/launcher/LegacyBootstrap.java').read_text(encoding='utf-8'), runtime), encoding='utf-8')
    marker = sources / 'TransformBootstrapMod.java'
    marker.write_text(loader_aliases('''package biblioteca.validation.bootstrap;
@net.neoforged.fml.common.Mod("biblioteca_validation_bootstrap")
public final class TransformBootstrapMod {}
''', runtime), encoding='utf-8')
    classes = reports / 'bootstrap-classes'
    compile_java(javac, runtime.get('clientClasspath', runtime['classpath']), classes, [source, marker], reports / 'bootstrap-compile.log', runtime['java'])
    jar = reports / 'bootstrap-probe.jar'
    with zipfile.ZipFile(jar, 'w') as output:
        for file in classes.rglob('*.class'):
            output.write(file, file.relative_to(classes).as_posix())
        output.writestr(runtime.get('metadata', 'META-INF/neoforge.mods.toml'),
                        'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="GPLv3"\n[[mods]]\n'
                        'modId="biblioteca_validation_bootstrap"\nversion="1"\ndisplayName="Biblioteca validation bootstrap"\n')
    return jar


def command(runtime, config_path, launcher, cp, jvm):
    project_build = Path(runtime['classes']).parents[2]
    side = runtime.get('side', config_path.parent.name)
    game_dir = Path(runtime.get('gameDir', str(config_path.parent / 'run')))
    if 'launchArguments' in runtime:
        launch = runtime['launchArguments'][side]
        vm, program, main = list(launch['jvm']), list(launch['program']), launch['main']
        classpath = list(runtime['clientClasspath'] if side == 'client' else runtime['classpath'])
    else:
        vm_file = project_build / f'moddev/{side}RunVmArgs.txt'
        program_file = project_build / f'moddev/{side}RunProgramArgs.txt'
        vm = shlex.split(vm_file.read_text(encoding='utf-8'), comments=True)
        program = shlex.split(program_file.read_text(encoding='utf-8'), comments=True)
        main = program.pop(0)
        source_classpath = project_build / f'moddev/{side}LegacyClasspath.txt'
        classpath = source_classpath.read_text(encoding='utf-8').splitlines()
    vm = [('-Dlog4j2.configurationFile=' + Path(arg.split('=', 1)[1]).resolve().as_uri())
          if arg.startswith('-Dlog4j2.configurationFile=') else arg for arg in vm]
    assert main == 'cpw.mods.bootstraplauncher.BootstrapLauncher', main
    program[program.index('--launchTarget') + 1] = 'bibliotecavalidation'
    if '--gameDir' in program:
        program[program.index('--gameDir') + 1] = str(game_dir)
    else:
        program += ['--gameDir', str(game_dir)]
    classpath = [p for p in classpath if p not in (runtime['classes'], runtime['resources'])]
    classpath.append(str(launcher))
    copied_classpath = config_path.parent / 'legacy-classpath.txt'
    copied_classpath.write_text('\n'.join(classpath) + '\n', encoding='utf-8')
    (game_dir / 'config').mkdir(parents=True, exist_ok=True)
    (game_dir / 'config/fml.toml').write_text('earlyWindowControl=false\n', encoding='utf-8')
    vm += ['-DlegacyClassPath.file=' + str(copied_classpath), '-Dbiblioteca.validation.config=' + str(config_path),
           '-Dbiblioteca.validation.client=' + str(side == 'client').lower()]
    return vm + jvm + ['--class-path', os.pathsep.join(cp + [str(launcher)]), main] + program


def game_command(runtime, side, run_dir, folders, jvm, game):
    """Launch the actual game target, using Gradle's native VM/module-path configuration."""
    run_dir = Path(run_dir).resolve()
    cp = runtime.get('clientClasspath', runtime['classpath']) if side == 'client' else runtime['classpath']
    cp = [p for p in cp if p not in (runtime['classes'], runtime['resources'])]
    environment = dict(os.environ, MOD_CLASSES=folders)
    if runtime.get('launcher') != 'modlauncher':
        main = 'net.neoforged.fml.startup.' + ('Client' if side == 'client' else 'Server')
        return jvm + ['-Dfml.modFolders=' + folders, '--class-path', os.pathsep.join(cp), main] + game, environment
    project_build = Path(runtime['classes']).parents[2]
    if 'launchArguments' in runtime:
        launch = runtime['launchArguments'][side]
        vm, program, main = list(launch['jvm']), list(launch['program']), launch['main']
        legacy_cp = list(cp)
    else:
        vm = shlex.split((project_build / f'moddev/{side}RunVmArgs.txt').read_text(encoding='utf-8'), comments=True)
        program = shlex.split((project_build / f'moddev/{side}RunProgramArgs.txt').read_text(encoding='utf-8'), comments=True)
        main = program.pop(0)
        legacy_cp = (project_build / f'moddev/{side}LegacyClasspath.txt').read_text(encoding='utf-8').splitlines()
    legacy_cp = [p for p in legacy_cp if p not in (runtime['classes'], runtime['resources'])]
    classpath_file = run_dir / (side + '-legacy-classpath.txt')
    classpath_file.write_text('\n'.join(legacy_cp) + '\n', encoding='utf-8')
    vm = [('-Dlog4j2.configurationFile=' + Path(arg.split('=', 1)[1]).resolve().as_uri())
          if arg.startswith('-Dlog4j2.configurationFile=') else arg for arg in vm]
    replacements = (['--gameDir', str(run_dir)] if side == 'client' or '--gameDir' in program else []) + list(map(str, game))
    i = 0
    while i < len(replacements):
        key = replacements[i]
        if key == '--nogui':
            if key not in program:
                program.append(key)
            i += 1
        else:
            value = replacements[i + 1]
            if key in program:
                program[program.index(key) + 1] = value
            else:
                program += [key, value]
            i += 2
    vm += ['-DlegacyClassPath.file=' + str(classpath_file), '-Dfml.modFolders=' + folders]
    return vm + jvm + ['--class-path', os.pathsep.join(cp), main] + program, environment
