# 开发与构建（维护者文档）

## 环境

| 工具 | 版本/位置 |
| --- | --- |
| JDK | 21（`C:/Hermes/toolchain/jdk-21`） |
| Gradle | 8.8（`C:/Hermes/toolchain/gradle-8.8/bin/gradle`，不用 wrapper） |
| NeoForge | 21.1.255（1.21.1） |
| ModDevGradle | 2.0.148 |

构建命令（Windows git-bash）：

```bash
export JAVA_HOME='C:/Hermes/toolchain/jdk-21'
cd 'C:/Hermes/Project/MC Mod/j20UpdateMod/1.21.1/neoforge'   # 每个目标工程独立目录：<MC版本>/<loader>/
'C:/Hermes/toolchain/gradle-8.8/bin/gradle' --no-daemon build     # 构建
'C:/Hermes/toolchain/gradle-8.8/bin/gradle' --no-daemon runClient  # 开发运行
```

多目标布局：`j20UpdateMod/<MC版本>/<loader>/` 各为自包含 gradle 工程（核心纯 Java 代码复制自 1.21.1/neoforge，独立维护）；当前覆盖 1.20.1 / 1.21.1 / 26.1.2 / 26.2 / 26.3 × forge / neoforge / fabric。

产物：`build/libs/j20UpdateMod-1.21.1-neoforge.jar`。版本目录规范：构建后复制到 `1.21.1/发布/`（放最新打包好的 mod），被替换的旧 jar 改带版本号文件名移入 `1.21.1/旧版本/`（如 `j20UpdateMod-1.21.1-neoforge-v0.0.1.jar`）。

## 版本号同步（4 处）

迭代版本号时必须同步修改：

1. `build.gradle` → `version = 'X.Y.Z'`
2. `src/main/resources/META-INF/neoforge.mods.toml` → `version = "X.Y.Z"`
3. `src/main/java/com/xiaotang/j20updatemod/J20UpdateMod.java` → `VERSION` 常量
4. `CHANGELOG.md`（新增条目）

`README.md` 不含版本号。**版本号只在推送 GitHub 前迭代**，本地改动不升版。

## 对接接口（汉化库 J20-Mcmodlanguage-Package）

- **manifest.json**（仓库根目录，CI 自动生成）冻结字段：`schema_version`、`latest.version`（`YYYY.MM.DD-NNN`）、`latest.packages[]` 的 `mc_version`/`loader`/`asset_name`/`release_url`/`md5`/`size`；`latest.vp_packages[]` 为可选字段（无 loader）
- `mc_version` 是**版本组**（版本号前两段：1.20 / 1.21 / 26.1 / 26.2 / 26.3），组内小版本共用资源包；运行时用 `SharedConstants.getCurrentVersion().getName()` 取实际版本再映射
- 资产命名：`pack-{版本组}-{loader}.zip`、`vp-modules-{版本组}.zip`
- VP 包内部路径固定 `vaultpatcher/modules/*.json`，**不是资源包**，须解压到游戏根目录；空包（22 字节）表示暂无补丁，属正常情况
- VP 配置 schema 已实证定稿（FORMATS.md，实证自 VP 源码）：字段为 **`modules`**（元素不带 `.json` 后缀；`mods` 是 VP 源码内部变量名，不是 JSON 字段），路径固定 `config/vaultpatcher_asm/config.json` 不迁移；模组仅在配置不存在时按完整默认结构创建（含 `default_language`/`class_patch`/`load_all_modules`/`debug_mode`），已存在只补 `modules` 不动其他
- 镜像源配置内置于 `src/main/resources/sources.json`，**以汉化库 `sources.example.json` 为权威清单同步**（当前 3 源：ghproxy(cn)、GitHub(global)、jsDelivr——jsDelivr 仅镜像 manifest 不镜像 Release 资产，故无 asset_url_template）；新增镜像先改汉化库权威清单，再同步此处

## 已验证 API 事实（NeoForge 1.21.1，勿凭记忆改）

- `AddPackFindersEvent implements IModBusEvent` → **mod 总线**；`ResourcePackLoader.populatePackRepository` 在 `PackRepository` 创建时 post
- 注入资源包：`Pack.readMetaAndCreate(PackLocationInfo, Pack.ResourcesSupplier, PackType, PackSelectionConfig)`；`ResourcesSupplier` 是双方法接口，用 `BuiltInPackSource.fromName(loc -> new FilePackResources(loc, zipAccess, ""))` 适配；zip 句柄为 `FilePackResources.SharedZipFileAccess(File)`（AutoCloseable，游戏生命周期内不关）
- 置顶强制启用：`new PackSelectionConfig(true, Pack.Position.TOP, true)`
- `LanguageManager.getSelected()` 在 1.21.1 **直接返回 String** 语言代码（旧文档 `.getCode()` 已过时）
- `Minecraft.gameDirectory` / `Minecraft.options.languageCode` 均可直接访问

## 测试

核心链路（清单获取/下载/MD5/缓存/VP 部署）冒烟测试：`tests/ChainTest.java`（24 项断言，直连真实汉化库接口，几秒跑完）。

**汉化库每发一个新 Release 后跑一次**（回归验证模组能正确消费新产物，第一个真实 VP 模块入库后尤其必跑）：

```bash
export JAVA_HOME='C:/Hermes/toolchain/jdk-21'
G='C:/Users/Administrator/.gradle/caches/modules-2/files-2.1'
CP='build/classes/java/main;build/resources/main;'"$G"'/com.google.code.gson/gson/2.10.1/b3add478d4382b78ea20b1671390a858002feb6c/gson-2.10.1.jar;'"$G"'/org.apache.logging.log4j/log4j-api/2.22.1/bea6fede6328fabafd7e68363161a7ea6605abd1/log4j-api-2.22.1.jar;'"$G"'/org.apache.logging.log4j/log4j-core/2.22.1/7183a25510a02ad00cc6a95d3b3d2a7d3c5a8dc4/log4j-core-2.22.1.jar'
'C:/Hermes/toolchain/gradle-8.8/bin/gradle' --no-daemon classes
mkdir -p /tmp/j20test && cp tests/ChainTest.java /tmp/j20test/
cd /tmp/j20test
'C:/Hermes/toolchain/jdk-21/bin/javac.exe' -encoding UTF-8 -proc:none -cp "C:/Hermes/Project/MC Mod/j20UpdateMod/1.21.1/$CP" ChainTest.java
'C:/Hermes/toolchain/jdk-21/bin/java.exe' -Dfile.encoding=UTF-8 -cp ".;C:/Hermes/Project/MC Mod/j20UpdateMod/1.21.1/$CP" ChainTest
```

（测试会往自己所在目录写 j20update-cache/ 与 game*/ 沙盒，放 /tmp 即可；输出 `==== 结果: N 通过, M 失败 ====`，M 非 0 即回归失败。注意 GBK 控制台下中文乱码不影响判断，以 PASS/FAIL 计数为准。）

游戏内验证走 `runClient`，在 `run/options.txt` 预置 `language:zh_cn` 可测国内源分支；日志关键字 `[j20]`。
