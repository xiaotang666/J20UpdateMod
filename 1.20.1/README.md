# j20UpdateMod 1.20.1（J20模组汉化）

自动更新并注入 J20 模组汉化资源包的 Minecraft 1.20.1 客户端模组。启动时自动获取最新汉化资源包，MD5 校验通过后注入资源包列表顶部并强制启用；离线时自动回退到本地缓存，保证任何时候都有汉化可用。

- 作者：小坣
- 模组版本：0.0.1
- 协议：AGPL-3.0
- 适用：Minecraft **1.20.1**（客户端）

## 下载

按你使用的模组加载器选择其中一个文件（同一时间只需安装一个）：

| 文件 | 加载器 | 要求 |
| --- | --- | --- |
| `j20UpdateMod-1.20.1-forge-0.1.0.jar` | Minecraft Forge | Forge 47.x |
| `j20UpdateMod-1.20.1-neoforge-0.1.0.jar` | NeoForge | NeoForge 1.20.1（47.1.x） |
| `j20UpdateMod-1.20.1-fabric-0.1.0.jar` | Fabric | Fabric Loader 0.14 及以上 |

## 安装

1. 安装好对应加载器的 Minecraft 1.20.1 客户端。
2. 把下载的 jar 放进游戏目录的 `mods` 文件夹。
3. 启动游戏即可，无需其他操作：模组会在启动时自动下载/校验汉化资源包并注入资源包列表顶部（自动启用、不可拖动降级）。

首次使用需要联网（获取汉化包）；之后断网也会自动使用上次的汉化包。

## 功能说明

- **自动更新汉化**：按当前游戏语言选择下载源（简体中文优先国内加速镜像，否则优先 GitHub Release），下载最新汉化资源包并做 MD5 校验，损坏会自动重新下载。
- **自动注入**：汉化包强制置顶、自动启用，优先级高于其他资源包。
- **离线兜底**：下载失败或断网时自动使用本地缓存的旧版汉化包。
- **硬编码补丁（默认开启）**：自动部署 Vault Patcher 模块对硬编码文本汉化（需要额外安装 Vault Patcher 模组，可在配置中关闭）。

## 配置

首次启动后会生成配置文件 `config/j20update.json`：

```json
{
  "enableHardcodedPatch": true,
  "showVPInstallReminder": true
}
```

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `enableHardcodedPatch` | `true` | 是否启用硬编码补丁（部署 Vault Patcher 模块）。启用后若未安装 Vault Patcher 模组，进入游戏时会提示安装 |
| `showVPInstallReminder` | `true` | 未检测到 Vault Patcher 时是否提示安装 |

修改后重启游戏生效。

## 本地缓存

游戏目录下的 `j20update-cache` 文件夹存放已下载的汉化资源包与清单缓存，可随时删除（删除后会重新下载）。

## 常见问题

- **进游戏没有汉化？** 确认安装的是与加载器匹配的 jar；查看游戏日志中以 `[j20]` 开头的记录，能看到下载/注入过程与失败原因。
- **提示安装 Vault Patcher？** 硬编码补丁默认开启，未安装 Vault Patcher 时就会出现；不想看到提示可把 `showVPInstallReminder` 设为 `false`。
- **资源包列表里的“J20模组汉化”拖不动？** 这是设计行为：汉化包强制置顶以保证汉化优先级。

## 致谢与协议

- 汉化资源来自 [xiaotang666/J20-Mcmodlanguage-Package](https://github.com/xiaotang666/J20-Mcmodlanguage-Package)，版权归汉化贡献者，采用 AGPL-3.0。
- 下载加速渠道：ghproxy.net、jsDelivr。
- 硬编码补丁依赖 [Vault Patcher](https://github.com/VP-Language/VaultPatcher)（另行安装，遵循其自身协议）。
- 本模组（j20UpdateMod）采用 AGPL-3.0 协议，转载请注明出处。
