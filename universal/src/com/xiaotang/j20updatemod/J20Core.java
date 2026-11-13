package com.xiaotang.j20updatemod;

import com.xiaotang.j20updatemod.config.J20Config;
import com.xiaotang.j20updatemod.download.Manifest;
import com.xiaotang.j20updatemod.download.ManifestFetcher;
import com.xiaotang.j20updatemod.download.MirrorSelector;
import com.xiaotang.j20updatemod.download.PackDownloader;
import com.xiaotang.j20updatemod.download.Sources;
import com.xiaotang.j20updatemod.lang.LanguageDetector;
import com.xiaotang.j20updatemod.util.GameDirs;
import com.xiaotang.j20updatemod.util.OptionsSelector;
import com.xiaotang.j20updatemod.vp.VPModuleLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * j20UpdateMod 核心（纯 Java：无 MC / 加载器编译期绑定，i18n 式单套类架构）。
 * 启动时后台执行：语言检测 → 镜像源选择 → manifest 获取 → 资源包下载/MD5 校验 → VP 模块部署；
 * 完成后发布到 resourcepacks/ 并自动勾选。入口由 universal dispatcher 调用 {@link #start}。
 *
 * <p>提醒三层输出：日志 + 游戏目录 j20update-cache/notice.txt（覆盖写）
 * + 进服聊天栏提醒（{@link com.xiaotang.j20updatemod.util.ChatNotifier} 纯反射按形状探测，
 * 等玩家进服后显示，环境不支持时静默降级）。</p>
 */
public final class J20Core {

    public static final String MODID = "j20updatemod";
    public static final String VERSION = "1.0.4";

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    /** 更新流程整体等待上限（秒）：超过后注入流程用缓存兜底，不阻塞启动。 */
    private static final long UPDATE_BUDGET_SECONDS = 120;

    /** 启动早期同步等待预算（秒）：首次安装限时等下载（A 方案），超时转后台。 */
    private static final long SYNC_BUDGET_SECONDS = 30;

    /** 版本组（manifest mc_version 键：1.20 / 1.21 / 26.1 / 26.2 / 26.3）按版本序；未收录版本回退最新组（best-effort）。 */
    private static final String[] GROUPS = {"1.20", "1.21", "26.1", "26.2", "26.3"};

    private static final CountDownLatch UPDATE_DONE = new CountDownLatch(1);
    private static volatile UpdateOutcome outcome;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean WORKER_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean EARLY_CHECKED = new AtomicBoolean(false);
    private static final AtomicBoolean EARLY_DEPLOY_DONE = new AtomicBoolean(false);

    private static volatile Path GAME_DIR;
    private static volatile String MC_VERSION = "";
    private static volatile String LOADER = "unknown";

    private static final String VP_NOTICE_TEXT =
            "已启用硬编码补丁，但未检测到 Vault Patcher。"
                    + "请安装 Vault Patcher 模组并重启游戏，硬编码文本汉化才会生效。";
    private static final String RESTART_NOTICE_TEXT =
            "最新汉化已就位，但本次启动的翻译不会完全生效，请重启一次游戏使其生效。";

    /** 更新流程结果。 */
    public record UpdateOutcome(Path packZip, String packVersion, String langCode, String manifestVersion) {
    }

    private J20Core() {
    }

    /**
     * 统一入口（幂等）：universal dispatcher 在各加载器回调时调用。
     *
     * @param gameDir    游戏目录（dispatcher 从加载器 API 取得）
     * @param mcVersion  运行时 MC 版本（dispatcher 反射探测；可空，内部回退探测链）
     * @param loader     加载器名：forge / neoforge / fabric
     */
    public static void start(Path gameDir, String mcVersion, String loader) {
        if (STARTED.getAndSet(true)) {
            return;
        }
        GAME_DIR = gameDir == null ? GameDirs.resolveGameDir() : gameDir.toAbsolutePath().normalize();
        MC_VERSION = (mcVersion == null || mcVersion.isEmpty()) ? detectMcVersion() : mcVersion;
        LOADER = loader;
        LOGGER.info("[j20] j20UpdateMod {} 启动：Minecraft {} / {} / {}",
                VERSION, MC_VERSION, LOADER, GAME_DIR);
        runEarlyCheck();
    }

    /** 等待更新流程结束（有上限），返回结果（失败时为 null）。 */
    public static UpdateOutcome awaitUpdate() {
        try {
            UPDATE_DONE.await(UPDATE_BUDGET_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return outcome;
    }

    /** 启动早期检查：首次安装限时同步等下载（A），否则异步（B）。 */
    public static void runEarlyCheck() {
        startUpdateWorker();
        if (EARLY_CHECKED.getAndSet(true)) {
            return;
        }
        Path gameDir = GAME_DIR != null ? GAME_DIR : GameDirs.resolveGameDir();
        if (hasLocalPackState(gameDir)) {
            LOGGER.info("[j20] 本地已有汉化资源包，后台检查更新（不阻塞启动）");
            return;
        }
        LOGGER.info("[j20] 首次安装：同步等待汉化下载部署（最长 {} 秒，日志有进度）", SYNC_BUDGET_SECONDS);
        long start = System.currentTimeMillis();
        long deadline = start + SYNC_BUDGET_SECONDS * 1000L;
        try {
            while (!UPDATE_DONE.await(5, TimeUnit.SECONDS)) {
                if (System.currentTimeMillis() >= deadline) {
                    LOGGER.warn("[j20] 同步等待超时（{}s），转后台继续下载，完成后提示重启生效", SYNC_BUDGET_SECONDS);
                    return;
                }
                LOGGER.info("[j20] 正在准备汉化…已等待 {}s / {}s（首次启动需下载，请稍候）",
                        (System.currentTimeMillis() - start) / 1000, SYNC_BUDGET_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        EARLY_DEPLOY_DONE.set(true);
        LOGGER.info("[j20] 汉化资源包已在启动早期就位（耗时 {}s）",
                (System.currentTimeMillis() - start) / 1000);
    }

    /** 启动后台更新线程（仅一次）。 */
    public static void startUpdateWorker() {
        if (WORKER_STARTED.getAndSet(true)) {
            return;
        }
        Thread worker = new Thread(J20Core::runUpdate, "j20update-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    /** 本地是否已有汉化资源包与缓存（决定启动是否需要同步等待）。 */
    private static boolean hasLocalPackState(Path gameDir) {
        try (java.util.stream.Stream<Path> a = Files.list(gameDir.resolve("resourcepacks"));
             java.util.stream.Stream<Path> b = Files.list(gameDir.resolve("j20update-cache"))) {
            boolean pack = a.anyMatch(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("j20-lang-") && n.endsWith(".zip");
            });
            boolean cache = b.anyMatch(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("pack-") && n.endsWith(".zip");
            });
            return pack && cache;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 本次启动 VP 是否已初始化（VaultPatcher.mcPath 在其 init 入口赋值；不确定时按已初始化处理）。 */
    private static boolean isVpInitializedThisLaunch(Path gameDir) {
        if (!isVpLoaded(gameDir)) {
            return false;
        }
        try {
            Class<?> c = Class.forName("me.fengming.vaultpatcher_asm.VaultPatcher");
            java.lang.reflect.Field f = c.getField("mcPath");
            return f.get(null) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 更新流程主体（后台线程）。 */
    private static void runUpdate() {
        Path gameDir = null;
        String mcGroup = null;
        try {
            gameDir = GAME_DIR != null ? GAME_DIR : GameDirs.resolveGameDir();
            Path cacheDir = gameDir.resolve("j20update-cache");

            J20Config config = J20Config.load(gameDir);

            String langCode = LanguageDetector.detect(gameDir);
            boolean preferCn = LanguageDetector.isSimplifiedChinese(langCode);
            LOGGER.info("[j20] 游戏语言: {} → {}", langCode, preferCn ? "国内镜像源优先" : "GitHub Release 优先");

            List<Sources.Source> ordered = MirrorSelector.order(Sources.loadBuiltin().sources, preferCn);

            mcGroup = versionGroup(MC_VERSION);
            LOGGER.info("[j20] Minecraft {}（版本组 {}），加载器 {}", MC_VERSION, mcGroup, LOADER);

            Manifest manifest = ManifestFetcher.fetch(cacheDir, ordered, mcGroup, LOADER);
            Manifest.PackEntry pack = manifest.findPack(mcGroup, LOADER);
            LOGGER.info("[j20] 目标资源包: {}（{} 字节，md5 {}）", pack.assetName, pack.size, pack.md5);

            PackDownloader.Downloaded downloaded =
                    PackDownloader.ensureResourcePack(cacheDir, pack, manifest.latest.version, ordered);

            Published published;
            try {
                published = publishToResourcepacks(downloaded.file(), gameDir, mcGroup);
                LOGGER.info("[j20] 资源包已发布到 resourcepacks 文件夹: {}", published.file().getFileName());
                OptionsSelector.autoSelectOnce(gameDir, "file/" + published.file().getFileName(), published.firstPublish());
            } catch (IOException e) {
                LOGGER.error("[j20] 汉化包替换失败（新包已缓存，下次启动自动重试）: {}", e.toString());
                writeNotice(gameDir, "汉化资源包替换失败（文件被占用）：新包已缓存，下次启动游戏自动替换生效。");
                published = new Published(gameDir.resolve("resourcepacks").resolve("j20-lang-" + mcGroup + "-" + LOADER + ".zip"), false, false);
            }

            boolean vpLoaded = isVpLoaded(gameDir);
            VPModuleLoader.Result vpResult = VPModuleLoader.deployIfNeeded(
                    config.enableHardcodedPatch, vpLoaded, gameDir, cacheDir,
                    manifest, mcGroup, manifest.latest.version, ordered);

            if (vpResult.vpMissing() && config.showVPInstallReminder) {
                // 误报防护：VP 加载晚于本流程时稍候复检，确认确实不在才提示
                if (!vpLoaded) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    vpLoaded = isVpLoaded(gameDir);
                }
                if (!vpLoaded) {
                    writeNotice(gameDir, VP_NOTICE_TEXT);
                }
            }

            outcome = new UpdateOutcome(downloaded.file(), manifest.latest.version, langCode, manifest.latest.version);
            // options.txt 勾选在 Forge 上必然重启后生效（Options 快照先于模组加载）；
            // 仅 fabric preLaunch 早期就位时本启动真生效
            boolean sameLaunchEffective = EARLY_DEPLOY_DONE.get() && "fabric".equals(LOADER);
            boolean needsRestart = (published.changed() && !sameLaunchEffective)
                    || (vpResult.changed() && isVpInitializedThisLaunch(gameDir));
            if (needsRestart) {
                writeNotice(gameDir, RESTART_NOTICE_TEXT);
                LOGGER.info("[j20] 汉化已部署，但本次启动不会完全生效（packChanged={} vpChanged={} vpInitialized={}），已提示重启",
                        published.changed(), vpResult.changed(), isVpInitializedThisLaunch(gameDir));
            }
            LOGGER.info("[j20] 汉化更新流程完成：资源包版本 {}{}", manifest.latest.version,
                    downloaded.fromCache() ? "（缓存）" : "（本次下载）");
        } catch (Throwable t) {
            LOGGER.error("[j20] 汉化更新流程失败，本次启动回退使用已有缓存", t);
            if (gameDir != null && mcGroup != null) {
                Path cached = findCachedPack(gameDir.resolve("j20update-cache"), mcGroup);
                if (cached != null) {
                    outcome = new UpdateOutcome(cached, "cache", null, null);
                }
            }
        } finally {
            UPDATE_DONE.countDown();
        }
    }

    /** 需要用户注意时：日志 + 游戏目录提示文件（单套类架构下替代进服聊天提醒）。 */
    private static final java.util.List<String> NOTICES = new java.util.ArrayList<>();

    /** 写提醒：日志 + 进服聊天 + notice.txt（累计写，多个提示共存，不互相覆盖）。 */
    private static void writeNotice(Path gameDir, String text) {
        LOGGER.warn("[j20] {}", text);
        com.xiaotang.j20updatemod.util.ChatNotifier.notifyInGameAsync(text);
        try {
            synchronized (NOTICES) {
                if (!NOTICES.contains(text)) {
                    NOTICES.add(text);
                }
                Path notice = gameDir.resolve("j20update-cache").resolve("notice.txt");
                Files.createDirectories(notice.getParent());
                Files.writeString(notice, String.join(System.lineSeparator(), NOTICES) + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 回退用：在缓存目录找本组任意 pack-*.zip（资产名可能 fo 共用，不含本 loader 名）。 */
    private static Path findCachedPack(Path cacheDir, String mcGroup) {
        try (java.util.stream.Stream<Path> s = Files.list(cacheDir)) {
            return s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("pack-" + mcGroup + "-") && n.endsWith(".zip");
            }).findFirst().orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 检测 Vault Patcher 是否已安装（零时机依赖，任一信号命中即真）：
     * 1) mods 目录文件名扫描（不依赖加载器状态）；2) 加载器 API（仅真短路，不因 false 提前返回）；
     * 3) VP 类存在性（initialize=false：只查存在、不跑静态初始化——早期时序下 <clinit> 抛错曾导致误报）。
     */
    public static boolean isVpLoaded(Path gameDir) {
        try (java.util.stream.Stream<Path> s = Files.list(gameDir.resolve("mods"))) {
            if (s.anyMatch(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("vaultpatcher"))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        for (String ml : new String[]{"net.neoforged.fml.ModList", "net.minecraftforge.fml.ModList"}) {
            try {
                Class<?> c = Class.forName(ml);
                Object list = null;
                try {
                    list = c.getMethod("get").invoke(null);
                } catch (NoSuchMethodException ignored) {
                }
                if (list == null) {
                    List<?> mods = (List<?>) c.getMethod("getLoadedMods").invoke(null);
                    for (Object m : mods) {
                        if ("vaultpatcher".equals(m.getClass().getMethod("getModId").invoke(m))) {
                            return true;
                        }
                    }
                } else {
                    try {
                        Object loaded = c.getMethod("isLoaded", String.class).invoke(list, "vaultpatcher");
                        if (Boolean.TRUE.equals(loaded)) {
                            return true;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                    Object opt = c.getMethod("getModContainerById", String.class).invoke(list, "vaultpatcher");
                    Object cont = opt.getClass().getMethod("orElse", Object.class).invoke(opt, (Object) null);
                    if (cont != null) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Class.forName("me.fengming.vaultpatcher_asm.VaultPatcher", false, J20Core.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 当前 Minecraft 版本：start() 传入为主；此处为反射探测回退链（公开供 dispatcher 用）。 */
    public static String detectMcVersion() {
        for (String ml : new String[]{"net.neoforged.fml.ModList", "net.minecraftforge.fml.ModList"}) {
            String v = versionFromModList(ml);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        for (String fl : new String[]{"net.neoforged.fml.loading.FMLLoader", "net.minecraftforge.fml.loading.FMLLoader"}) {
            try {
                Object vi = Class.forName(fl).getMethod("getVersionInfo").invoke(null);
                Object v = vi.getClass().getMethod("mcVersion").invoke(vi);
                if (v != null && !v.toString().isEmpty()) {
                    return v.toString();
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private static String versionFromModList(String modListClass) {
        try {
            Class<?> ml = Class.forName(modListClass);
            Object list = null;
            try {
                list = ml.getMethod("get").invoke(null);
            } catch (NoSuchMethodException ignored) {
                // 26.x 移除了静态 get()
            }
            if (list != null) {
                Object opt = ml.getMethod("getModContainerById", String.class).invoke(list, "minecraft");
                Object c = opt.getClass().getMethod("orElse", Object.class).invoke(opt, (Object) null);
                return c == null ? null : versionFromContainer(c);
            }
            Object mods = ml.getMethod("getLoadedMods").invoke(null);
            for (Object c : (List<?>) mods) {
                if ("minecraft".equals(c.getClass().getMethod("getModId").invoke(c))) {
                    return versionFromContainer(c);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String versionFromContainer(Object container) throws Exception {
        Object info = container.getClass().getMethod("getModInfo").invoke(container);
        Object ver = info.getClass().getMethod("getVersion").invoke(info);
        return ver == null ? null : ver.toString();
    }

    /**
     * 版本号 → 版本组（manifest mc_version 键）：前两段精确匹配（1.21.4→1.21、26.1.2→26.1）→ 回退最新组。
     * 未来未收录版本 best-effort 回退，纯 Java 核心照常工作。
     */
    public static String versionGroup(String mcVersion) {
        if (mcVersion != null && !mcVersion.isEmpty()) {
            String[] parts = mcVersion.split("\\.");
            String key = parts.length >= 2 ? parts[0] + "." + parts[1] : mcVersion;
            for (String known : GROUPS) {
                if (known.equals(key)) {
                    return known;
                }
            }
            LOGGER.warn("[j20] 未收录的 MC 版本 {}，回退版本组 {}（best-effort，建议等待适配更新）",
                    mcVersion, GROUPS[GROUPS.length - 1]);
        }
        return GROUPS[GROUPS.length - 1];
    }

    /** 发布结果：落盘路径 + 是否首次发布（新安装）+ 内容是否变化。 */
    private record Published(Path file, boolean firstPublish, boolean changed) {
    }

    /**
     * 把资源包发布到 resourcepacks/ 文件夹（对齐 i18n 行为：实体文件可见、同名原子覆盖）。
     * 内容未变则跳过写盘；返回落盘路径、是否首次发布、内容是否变化。
     */
    private static Published publishToResourcepacks(Path cachedZip, Path gameDir, String mcGroup) throws IOException {
        Path dir = gameDir.resolve("resourcepacks");
        Files.createDirectories(dir);
        String name = "j20-lang-" + mcGroup + "-" + LOADER + ".zip";
        Path target = dir.resolve(name);

        // 清理其他命名时代/其他组的旧 j20-lang-* 残留（含失败留下的 .j20tmp）
        try (java.util.stream.Stream<Path> s = Files.list(dir)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("j20-lang-") && !n.equals(name);
            }).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                    LOGGER.info("[j20] 清理旧汉化包: {}", p.getFileName());
                } catch (IOException e) {
                    LOGGER.warn("[j20] 旧汉化包清理失败 {}: {}", p.getFileName(), e.toString());
                }
            });
        } catch (IOException ignored) {
        }

        boolean firstPublish = !Files.exists(target);
        boolean changed = firstPublish || Files.mismatch(cachedZip, target) != -1L;
        if (!changed) {
            return new Published(target, false, false);
        }
        Path tmp = dir.resolve(name + ".j20tmp");
        IOException lastErr = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                Files.copy(cachedZip, tmp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException | java.nio.file.AccessDeniedException fallback) {
                    // 游戏正占用旧包（zipfs 打开）时 ATOMIC_MOVE 被系统拒绝；
                    // 普通 REPLACE_EXISTING 不需独占，实测仍可成功
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                LOGGER.info("[j20] 已替换汉化包: {}（第 {} 次尝试）", name, attempt);
                return new Published(target, firstPublish, true);
            } catch (IOException e) {
                lastErr = e;
                LOGGER.warn("[j20] 替换 {} 第 {}/5 次失败（多为文件被占用）: {}", name, attempt, e.toString());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw lastErr == null ? new IOException("替换 " + name + " 失败") : lastErr;
    }
}
