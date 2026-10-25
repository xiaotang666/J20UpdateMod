# 更新日志

本项目遵循语义化迭代版本号。

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
