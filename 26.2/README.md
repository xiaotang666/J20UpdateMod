# j20UpdateMod 26.2（NeoForge / Forge / Fabric）

本文件夹为 **Minecraft 26.2** 的构建产物（覆盖 26.2.x 系列），三个 Mod 加载器各一个 JAR，在 `发布/` 目录内：

| 加载器 | 文件 |
| --- | --- |
| NeoForge | `j20UpdateMod-26.2-neoforge.jar` |
| Forge | `j20UpdateMod-26.2-forge.jar` |
| Fabric | `j20UpdateMod-26.2-fabric.jar` |

本构建覆盖 **26.2 版本组的全部小版本**（26.2.x），无需按小版本另行下载。

## 安装

1. 按你使用的 Mod 加载器下载对应 JAR
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

## 协议与致谢

- 本模组遵循 **AGPL-3.0** 协议，禁止商业用途
- 汉化内容来自 [J20-Mcmodlanguage-Package](https://github.com/xiaotang666/J20-Mcmodlanguage-Package)（基于 [CFPAOrg/Minecraft-Mod-Language-Package](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package) 改编）
- 自动更新思路参考 [I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3)

常见问题与更多说明见[项目根 README](../README.md)。
