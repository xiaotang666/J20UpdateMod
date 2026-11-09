# j20UpdateMod（J20模组汉化自动更新）

自动下载并应用「J20 模组汉化资源包」的 Minecraft 客户端模组：启动时自动获取最新汉化资源包（MD5 校验），
发布到 `resourcepacks/` 并自动勾选；可选部署 Vault Patcher 硬编码补丁。

## 下载

- [Releases](https://github.com/xiaotang666/J20UpdateMod/releases) 下载 **`j20UpdateMod-1.0.2.jar`**

## 支持范围

| 项目 | 支持 |
| --- | --- |
| Minecraft | 1.20.1 ~ 26.3+（未来版本自动适配） |
| Mod 加载器 | MinecraftForge / NeoForge / Fabric |
| Java | 17+ |

**一个 jar 通吃全部版本与加载器**：运行时自动识别游戏版本与加载器，不需要按版本挑文件。

## 使用

1. 下载 `j20UpdateMod-{版本}.jar` 放入 `mods` 文件夹（Fabric 无需 fabric-api）
2. 启动游戏：模组自动下载最新汉化资源包到 `resourcepacks/j20-lang-{版本组}-{加载器}.zip`，
   并自动勾选（追加到已选列表末尾、不抢优先级，可手动调整或取消）
3. **重启一次游戏后汉化生效**（资源包勾选在下次启动加载）；提示写入 `j20update-cache/notice.txt`
4. 可选：安装 [Vault Patcher](https://github.com/3093FengMing/VaultPatcher) 并在配置中开启硬编码补丁，
   覆盖语言文件无法处理的硬编码文本

## 配置

首次启动在 `config/j20update.json` 生成带说明的配置（硬编码补丁开关、VP 安装提醒开关）。

## 汉化资源包

由 [J20-Mcmodlanguage-Package](https://github.com/xiaotang666/J20-Mcmodlanguage-Package) 构建发布，
翻译源文件、术语库与构建流程见汉化库仓库。

## 致谢

- [CFPAOrg / I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3) —— 自动汉化更新模组的架构参考
- [Vault Patcher](https://github.com/3093FengMing/VaultPatcher) —— 硬编码文本汉化

## 协议

[AGPL-3.0](LICENSE)。源码随 Release 以 `j20UpdateMod-{版本}-source.zip` 附件分发。
