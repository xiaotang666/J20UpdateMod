# 开发与构建（维护者文档）

## 架构（v1.0.0+：i18n 式单套类）

```
universal/
├── src/com/xiaotang/j20updatemod/        纯 Java 核心（下载/清单/VP/发布/勾选）
│   └── J20Core.java                      主流程，入口 start(gameDir, mcVersion, loader)
├── dispatcher/{forge,neoforge,fabric}/   各加载器入口（仅引用加载器稳定 API + @Mod 注解）
├── meta/                                 mods.toml / neoforge.mods.toml / pack.mcmeta 模板
└── assemble_universal.py                 组装器（javac 直编，无需 gradle）
```

- 全 mod **零 MC 编译期绑定**：版本探测走反射候选链
  （`ModList.get()` → `getLoadedMods()`（26.x 无 get()）→ `FMLLoader.getVersionInfo().mcVersion()` 兜底）
- 三加载器元数据同 jar 共存；Neo 新老均只解析 `neoforge.mods.toml`（反汇编实证，无双注册）
- 进服聊天提醒降级为日志 + `j20update-cache/notice.txt`（单套类无法挂各版本事件系统）

## 环境

| 工具 | 位置 |
| --- | --- |
| JDK（javac `--release 17`） | `C:/Hermes/toolchain/jdk-25` |
| 依赖 jar（编译期） | gradle 缓存：javafmllanguage / NeoForge loader / fmlloader / fabric-loader / log4j-api / gson |

## 构建

```bash
cd universal
python assemble_universal.py --version X.Y.Z
# 产物：universal/dist/j20UpdateMod-X.Y.Z.jar
```

版本号同步：`J20Core.VERSION` + `meta/*.toml`（组装时注入 {VERSION}）+ CHANGELOG + README。

## 测试

`tests/ChainTest.java`（33 断言，真实网络链路）对 `universal/build` 编译产物运行：

```bash
javac -encoding UTF-8 -cp "<universal/build;gson;log4j-api;log4j-core" tests/ChainTest.java -d <tmp>
java -cp "<tmp>;<universal/build>;gson;log4j-api;log4j-core" ChainTest
```

## 发布流程

1. 归档：`发布/` 只留最新 jar；旧 jar 进 `旧版本/`（快照限 5）；源码包归 `旧版本/zip/`（限 5）
2. Git Data API 推源码 + Release（jar + source.zip），回读验证
3. QQ 通知
