# 更新日志

本项目遵循语义化迭代版本号。

## 0.0.4 — 2026-10-26

### 修复

- **修复 Vault Patcher 安装检测失效**：检测用的 `vaultpatcher_asm` 实为 VP 的 Java 包名而非 mod ID，导致已安装 VP 仍被提示未安装且模块注册被跳过；检测改用真实 mod ID `vaultpatcher`

## 0.0.3 — 2026-10-26

### 修复

- **修复 Fabric（26.1.2 / 26.2 / 26.3）启动崩溃**：Mixin 包内混放普通类导致 `IllegalClassLoadError`（PackInjector 无法加载，游戏初始化阶段崩溃），mixin 类已移入独立包

### 变更

- jar 文件名后缀加 mod 版本号（如 `j20UpdateMod-26.2-fabric-0.0.3.jar`），方便区分版本

## 0.0.2 — 2026-10-25

### 变更

- **硬编码补丁默认开启**（`enableHardcodedPatch` 默认值 `false` → `true`）：大量模组一半文本走语言文件、一半为硬编码，混合形态下需同时部署 Vault Patcher 补丁才能完整汉化；不需要可在配置中关闭
- 协议声明更新为 **AGPL-3.0**，对应源码随仓库与 `j20UpdateMod-vX.Y.Z-source.zip` 资产提供

## 0.0.1 — 2026-10-05

首个版本（Minecraft 1.21.1 / NeoForge）。

### 新增

- 启动时自动获取汉化库 `manifest.json` 版本清单（简体中文环境国内镜像优先，失败回退 GitHub，全源失败回退本地缓存）
- 自动下载对应版本组/加载器的汉化资源包，MD5 校验 + 失败重试（每源 3 次）+ 断点续传
- 汉化资源包注入资源包列表最顶部（强制启用、位置固定）
- 缓存机制：`.minecraft/j20update-cache/`，命中缓存免下载
- 配置文件 `.minecraft/config/j20update.json`（`enableHardcodedPatch` / `showVPInstallReminder`）
- Vault Patcher 硬编码补丁模块部署（下载 `vp-modules-{版本组}.zip` 解压到 `.minecraft/vaultpatcher/modules/`，空包容错，路径穿越防护；配置按实证定稿 schema 写入 `modules` 数组，新建配置含完整默认结构）
- 镜像源内置清单与汉化库 `sources.example.json` 权威清单同步（ghproxy / GitHub / jsDelivr 仅镜像 manifest）
- VP 未安装时游戏内提示安装（可配置关闭）
- 语言检测：游戏 API → options.txt → 系统区域，三级回退
