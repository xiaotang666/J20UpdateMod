# 更新日志

本项目遵循语义化迭代版本号。

## 0.2.1 — 2026-10-27

### 修复

- **模块更新后自动清空 `vaultpatcher/cache/`**：VP 类转换缓存会存旧翻译的转换产物，导致汉化库更新模块后旧文本仍生效；现部署模块时按内容（md5 级）变化触发清空，下次启动按新模块重新转换（内容未变不动缓存）
- 修正日志中 VP 的错误 ID 引用（`vaultpatcher_asm` 是 Java 包名而非 mod ID）
- 修正模组内版本常量（此前误显示 0.1.0）

## 0.2.0 — 2026-10-27

### 新增

- **启动早期限时同步下载（A）**：启动早期（Fabric `preLaunch` / 模组构造期）检查本地状态，首次安装（缓存空）时同步等待汉化下载部署完成（预算 30 秒，日志有进度提示），等待期间当次启动即生效，无需重启
- **明确的重启提示（B）**：同步等待超时转后台继续；部署完成后若检测到本次启动不会完全生效（资源包晚于扫描落盘，或 VP 本次启动已初始化而补丁刚部署），进服时明确提示"重启一次游戏生效"

## 0.1.0 — 2026-10-27

### 新增

- **资源包落盘到 `resourcepacks/` 文件夹**（对齐 i18n 行为）：更新流程把汉化资源包以实体文件 `j20-lang-{版本组}-{加载器}.zip` 发布到游戏的 `resourcepacks/` 文件夹（同名原子覆盖），资源管理器/资源包界面可见
- **新安装时自动勾选一次**：把汉化包追加到 `options.txt` 已选列表**末尾**（不抢覆盖优先级）；你之后的取消/挪动由游戏正常记住，模组不再干预

### 变更

- **移除运行时注入与强制置顶**：汉化包现在就是普通资源包，资源包界面只有一条 `file/j20-lang-*.zip`，启用/顺序完全由你控制

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
