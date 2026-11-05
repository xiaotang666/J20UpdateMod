#!/usr/bin/env python3
"""j20UpdateMod 通用 jar 组装器（C 架构 / i18n 式单套类）：
单 jar = 三加载器元数据 + 纯 Java 核心 + 3 个加载器 dispatcher（无内嵌变体）。

用法: python assemble_universal.py --version 1.0.0
产物: universal/dist/j20UpdateMod-{version}.jar
"""
import os, shutil, subprocess, sys, zipfile, glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNI = os.path.join(ROOT, 'universal')
DIST = os.path.join(UNI, 'dist')
BUILD = os.path.join(UNI, 'build')
TOOLCHAIN = r'C:/Hermes/toolchain'
GC = r'C:/Users/Administrator/.gradle/caches/modules-2/files-2.1'


def find_jar(pattern):
    hits = [h for h in sorted(glob.glob(f'{GC}/**/{pattern}', recursive=True)) if 'sources' not in h]
    if not hits:
        raise SystemExit(f'缺依赖 jar: {pattern}')
    return hits[-1]


def compiler_cp():
    return [
        find_jar('javafmllanguage-26.3-*.jar'),  # Forge @Mod 注解
        find_jar('loader-12.*.jar'),              # Neo @Mod 注解 + neoforgespi
        find_jar('fmlloader-26.3-*.jar'),         # FMLPaths
        find_jar('fabric-loader-0.20.1.jar'),
        find_jar('log4j-api-*.jar'),
        find_jar('gson-2.10.1.jar'),
    ]


def meta_forge(v):
    content = open(os.path.join(UNI, 'meta', 'mods.toml'), encoding='utf-8').read()
    return 'META-INF/mods.toml', content.replace('{VERSION}', v)


def meta_neoforge(v):
    content = open(os.path.join(UNI, 'meta', 'neoforge.mods.toml'), encoding='utf-8').read()
    return 'META-INF/neoforge.mods.toml', content.replace('{VERSION}', v)


def meta_fabric(v):
    return 'fabric.mod.json', (
        '{\n'
        '  "schemaVersion": 1,\n'
        '  "id": "j20updatemod",\n'
        '  "version": "%s",\n'
        '  "name": "j20UpdateMod",\n'
        '  "description": "J20模组汉化自动更新（通用构建：1.20.1–26.3+，运行时自动识别游戏版本与加载器）",\n'
        '  "environment": "client",\n'
        '  "entrypoints": {\n'
        '    "preLaunch": ["com.xiaotang.j20updatemod.universal.FabricPreLaunch"],\n'
        '    "client": ["com.xiaotang.j20updatemod.universal.FabricDispatcher"]\n'
        '  },\n'
        '  "depends": {\n'
        '    "fabricloader": ">=0.15.0",\n'
        '    "minecraft": ">=1.20.1"\n'
        '  },\n'
        '  "license": "AGPL-3.0"\n'
        '}\n' % v)


def meta_pack(v):
    content = open(os.path.join(UNI, 'meta', 'pack.mcmeta'), encoding='utf-8').read()
    return 'pack.mcmeta', content


def main():
    version = '1.0.0'
    if '--version' in sys.argv:
        version = sys.argv[sys.argv.index('--version') + 1]

    shutil.rmtree(BUILD, ignore_errors=True)
    os.makedirs(BUILD, exist_ok=True)
    os.makedirs(DIST, exist_ok=True)

    javac = os.path.join(TOOLCHAIN, 'jdk-25', 'bin', 'javac.exe')
    srcs = sorted(glob.glob(os.path.join(UNI, 'src', '**', '*.java'), recursive=True))
    srcs += sorted(glob.glob(os.path.join(UNI, 'dispatcher', '**', '*.java'), recursive=True))
    r = subprocess.run([javac, '-encoding', 'UTF-8', '-proc:none', '--release', '17',
                        '-cp', ';'.join(compiler_cp()), '-d', BUILD] + srcs, capture_output=True)
    if r.returncode != 0:
        print(r.stderr.decode('gbk', errors='replace'))
        raise SystemExit('universal 编译失败')

    for res in glob.glob(os.path.join(UNI, 'src', '*.json')):
        shutil.copy(res, BUILD)

    out = os.path.join(DIST, f'j20UpdateMod-{version}.jar')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zf:
        for f in glob.glob(os.path.join(BUILD, '**', '*.class'), recursive=True):
            zf.write(f, os.path.relpath(f, BUILD).replace('\\', '/'))
        for res in glob.glob(os.path.join(BUILD, '*.json')):
            zf.write(res, os.path.basename(res))
        for gen in (meta_forge, meta_neoforge, meta_fabric, meta_pack):
            path, content = gen(version)
            zf.writestr(path, content)
    print(f'OK {os.path.basename(out)}  ({os.path.getsize(out)} B)')


if __name__ == '__main__':
    main()
