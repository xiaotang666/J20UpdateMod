# j20UpdateMod（J20模组汉化）

![AGPL-3.0 license](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)

Minecraft 模组汉化自动更新模组。安装后启动游戏即自动下载并应用最新汉化资源包，无需手动更新。

## 功能特性

- **自动更新**：启动时从 [J20-Mcmodlanguage-Package](https://github.com/xiaotang666/J20-Mcmodlanguage-Package) 获取最新汉化资源包，MD5 校验后自动注入
- **强制置顶**：汉化资源包自动注入到资源包列表最顶部，不被其他资源包覆盖
- **国内加速**：游戏语言为简体中文时默认走国内镜像源，失败自动回退 GitHub；非中文环境优先 GitHub
- **断点续传**：下载中断可续传，校验失败自动重试（每源最多 3 次），全源失败回退本地缓存
- **硬编码补丁（默认开启）**：自动部署 Vault Patcher 模块，覆盖语言文件之外的硬编码文本（可在配置中关闭）

## 下载

按你的 Minecraft 版本与 Mod 加载器选择对应 JAR，在 [Releases](https://github.com/xiaotang666/J20UpdateMod/releases) 页面下载：

| Minecraft 版本 | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| 1.20.x | `j20UpdateMod-1.20.1-forge-0.2.2.jar` | `j20UpdateMod-1.20.1-neoforge-0.2.2.jar` | `j20UpdateMod-1.20.1-fabric-0.2.2.jar` |
| 1.21.x | `j20UpdateMod-1.21.1-forge-0.2.2.jar` | `j20UpdateMod-1.21.1-neoforge-0.2.2.jar` | `j20UpdateMod-1.21.1-fabric-0.2.2.jar` |
| 26.1.x | `j20UpdateMod-26.1.2-forge-0.2.2.jar` | `j20UpdateMod-26.1.2-neoforge-0.2.2.jar` | `j20UpdateMod-26.1.2-fabric-0.2.2.jar` |
| 26.2 | `j20UpdateMod-26.2-forge-0.2.2.jar` | `j20UpdateMod-26.2-neoforge-0.2.2.jar` | `j20UpdateMod-26.2-fabric-0.2.2.jar` |
| 26.3 | `j20UpdateMod-26.3-forge-0.2.2.jar` | `j20UpdateMod-26.3-neoforge-0.2.2.jar` | `j20UpdateMod-26.3-fabric-0.2.2.jar` |

汉化资源包按版本组共享（如 1.20.x 系列共用同一份汉化内容），模组 JAR 按 MC 具体版本与加载器区分。

## 安装

1. 下载上表对应的模组 JAR
2. 放入 `.minecraft/mods/` 文件夹
3. 启动游戏即可，汉化自动下载并生效

## 配置

配置文件位于 `.minecraft/config/j20update.json`：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enableHardcodedPatch` | boolean | `true` | 是否启用硬编码补丁（Vault Patcher 模块自动部署） |
| `showVPInstallReminder` | boolean | `true` | 启用补丁但未安装 Vault Patcher 时是否提示 |

缓存目录：`.minecraft/j20update-cache/`（存放资源包与版本清单缓存，可随时删除，删除后重新下载）。

硬编码补丁默认开启，需另行安装 [Vault Patcher](https://www.mcmod.cn/class/8765.html) 模组才会实际生效；未安装时游戏内会提示安装。

## 常见问题

- **没生效？** 确认 JAR 在 `mods/` 内、游戏语言为简体中文，并查看 `logs/latest.log` 中 `[j20]` 开头的日志
- **下载慢/失败？** 模组会自动切换镜像源并回退缓存，网络恢复后重启游戏即可更新到最新汉化
- **汉化内容有误/缺失？** 请到 [汉化库仓库](https://github.com/xiaotang666/J20-Mcmodlanguage-Package) 反馈

## 协议与致谢

- 本模组遵循 **AGPL-3.0** 协议
- 汉化内容来自 [J20-Mcmodlanguage-Package](https://github.com/xiaotang666/J20-Mcmodlanguage-Package)（基于 [CFPAOrg/Minecraft-Mod-Language-Package](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package) 改编）
- 自动更新思路参考 [I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3)
