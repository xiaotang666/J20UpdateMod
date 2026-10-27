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
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * j20UpdateMod（J20模组汉化）主入口。
 * 启动时后台执行：语言检测 → 镜像源选择 → manifest 获取 → 资源包下载/MD5 校验 → VP 模块部署；
 * PackRepository 创建时（AddPackFindersEvent）等待更新完成并把汉化包发布到 resourcepacks 文件夹。
 */
@Mod(J20UpdateMod.MODID)
public class J20UpdateMod {

    public static final String MODID = "j20updatemod";
    public static final String VERSION = "0.1.0";
    public static final String LOADER = "forge";

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    /** 更新流程整体等待上限（秒）：超过后注入流程用缓存兜底，不阻塞启动。 */
    private static final long UPDATE_BUDGET_SECONDS = 120;

    /** 兜底用的构建目标 MC 版本组（正常情况下从 SharedConstants 读取）。 */
    private static final String BUILD_TARGET_MC = "26.2";

    private static final CountDownLatch UPDATE_DONE = new CountDownLatch(1);
    private static volatile UpdateOutcome outcome;
    private static final AtomicBoolean VP_REMINDER_PENDING = new AtomicBoolean(false);
    /** 启动早期同步等待预算（秒）：首次安装限时等下载（A 方案），超时转后台。 */
    private static final long SYNC_BUDGET_SECONDS = 30;
    private static final AtomicBoolean WORKER_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean EARLY_CHECKED = new AtomicBoolean(false);
    private static final AtomicBoolean EARLY_DEPLOY_DONE = new AtomicBoolean(false);
    private static final AtomicBoolean RESTART_REMINDER_PENDING = new AtomicBoolean(false);

    private static final String VP_REMINDER_TEXT =
            "§e[j20] §f已启用硬编码补丁，但未检测到 §bVault Patcher§f。"
                    + "请安装 Vault Patcher 模组并重启游戏，硬编码文本汉化才会生效。";
    private static final String RESTART_REMINDER_TEXT =
            "j20：最新汉化已就位，但本次启动的翻译不会完全生效，请重启一次游戏使其生效。";

    /** 更新流程结果。 */
    public record UpdateOutcome(Path packZip, String packVersion, String langCode, String manifestVersion) {
    }

    public J20UpdateMod(FMLJavaModLoadingContext context) {
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(J20UpdateMod::onPlayerLogin);

        runEarlyCheck();

        LOGGER.info("[j20] j20UpdateMod {} 已加载（模组 ID: {}，目标 MC {} / {}）",
                VERSION, MODID, BUILD_TARGET_MC, LOADER);
    }

    /** 等待更新流程结束（有上限），返回结果（失败时为 null，由注入器回退缓存）。 */
    public static UpdateOutcome awaitUpdate() {
        try {
            UPDATE_DONE.await(UPDATE_BUDGET_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return outcome;
    }

    /** 启动早期检查（preLaunch/模组构造期）：首次安装限时同步等下载（A），否则异步（B）。 */
    public static void runEarlyCheck() {
        startUpdateWorker();
        if (EARLY_CHECKED.getAndSet(true)) {
            return;
        }
        Path gameDir = null;
        try {
            gameDir = GameDirs.resolveGameDir();
        } catch (Throwable ignored) {
        }
        if (gameDir != null && hasLocalPackState(gameDir)) {
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
        LOGGER.info("[j20] 汉化已在启动早期就位（耗时 {}s），本次启动生效",
                (System.currentTimeMillis() - start) / 1000);
    }

    /** 幂等启动更新线程。 */
    public static void startUpdateWorker() {
        if (WORKER_STARTED.getAndSet(true)) {
            return;
        }
        Thread worker = new Thread(J20UpdateMod::runUpdate, "j20update-bootstrap");
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
    private static boolean isVpInitializedThisLaunch() {
        if (!isVpLoaded()) {
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
            gameDir = GameDirs.resolveGameDir();
            Path cacheDir = gameDir.resolve("j20update-cache");

            J20Config config = J20Config.load(gameDir);

            String langCode = LanguageDetector.detect(gameDir);
            boolean preferCn = LanguageDetector.isSimplifiedChinese(langCode);
            LOGGER.info("[j20] 游戏语言: {} → {}", langCode, preferCn ? "国内镜像源优先" : "GitHub Release 优先");

            List<Sources.Source> ordered = MirrorSelector.order(Sources.loadBuiltin().sources, preferCn);

            String mcVersion = detectMcVersion();
            mcGroup = versionGroup(mcVersion);
            LOGGER.info("[j20] Minecraft {}（版本组 {}），加载器 {}", mcVersion, mcGroup, LOADER);

            Manifest manifest = ManifestFetcher.fetch(cacheDir, ordered, mcGroup, LOADER);
            Manifest.PackEntry pack = manifest.findPack(mcGroup, LOADER);
            LOGGER.info("[j20] 目标资源包: {}（{} 字节，md5 {}）", pack.assetName, pack.size, pack.md5);

            PackDownloader.Downloaded downloaded =
                    PackDownloader.ensureResourcePack(cacheDir, pack, manifest.latest.version, ordered);

            Published published = publishToResourcepacks(downloaded.file(), gameDir, mcGroup);
            LOGGER.info("[j20] 资源包已发布到 resourcepacks 文件夹: {}", published.file().getFileName());
            OptionsSelector.autoSelectOnce(gameDir, "file/" + published.file().getFileName(), published.firstPublish());

            boolean vpLoaded = isVpLoaded();
            VPModuleLoader.Result vpResult = VPModuleLoader.deployIfNeeded(
                    config.enableHardcodedPatch, vpLoaded, gameDir, cacheDir,
                    manifest, mcGroup, manifest.latest.version, ordered);
            if (vpResult.vpMissing() && config.showVPInstallReminder) {
                VP_REMINDER_PENDING.set(true);
            }

            outcome = new UpdateOutcome(downloaded.file(), manifest.latest.version, langCode, manifest.latest.version);
            boolean needsRestart = (published.changed() && !EARLY_DEPLOY_DONE.get())
                    || (vpResult.changed() && isVpInitializedThisLaunch());
            if (needsRestart) {
                RESTART_REMINDER_PENDING.set(true);
                LOGGER.info("[j20] 汉化已部署，但本次启动不会完全生效（packChanged={} vpChanged={} vpInitialized={}），进服后提示重启",
                        published.changed(), vpResult.changed(), isVpInitializedThisLaunch());
            }
            LOGGER.info("[j20] 汉化更新流程完成：资源包版本 {}{}", manifest.latest.version,
                    downloaded.fromCache() ? "（缓存）" : "（本次下载）");
        } catch (Throwable t) {
            LOGGER.error("[j20] 汉化更新流程失败，本次启动回退使用已有缓存", t);
            if (gameDir != null && mcGroup != null) {
                Path cached = gameDir.resolve("j20update-cache").resolve("pack-" + mcGroup + "-" + LOADER + ".zip");
                if (Files.exists(cached)) {
                    outcome = new UpdateOutcome(cached, "cache", null, null);
                }
            }
        } finally {
            UPDATE_DONE.countDown();
        }
    }

    /** VP 安装提醒（进服/进档后发一条聊天消息，仅提醒一次）。 */
    private static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (VP_REMINDER_PENDING.getAndSet(false)) {
            try {
                if (event.getPlayer() != null) {
                    event.getPlayer().sendSystemMessage(Component.literal(VP_REMINDER_TEXT));
                }
            } catch (Throwable t) {
                LOGGER.warn("[j20] VP 安装提醒发送失败: {}", t.toString());
            }
        }
        onPlayerLoginRestart(event);
    }

    /** 重启提醒（由 VP 提醒方法尾部调用，仅一次）。 */
    private static void onPlayerLoginRestart(ClientPlayerNetworkEvent.LoggingIn event) {
        if (RESTART_REMINDER_PENDING.getAndSet(false)) {
            try {
                if (event.getPlayer() != null) {
                    event.getPlayer().sendSystemMessage(Component.literal(RESTART_REMINDER_TEXT));
                }
            } catch (Throwable t) {
                LOGGER.warn("[j20] 重启提醒发送失败: {}", t.toString());
            }
        }
    }

    /** 检测 Vault Patcher 是否已安装。 */
    private static boolean isVpLoaded() {
        try {
            return ModList.isLoaded("vaultpatcher");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 当前 Minecraft 版本号（如 26.1.2）。 */
    private static String detectMcVersion() {
        try {
            String name = SharedConstants.getCurrentVersion().name();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Throwable ignored) {
        }
        try {
            String id = SharedConstants.getCurrentVersion().id();
            if (id != null && !id.isEmpty()) {
                return id;
            }
        } catch (Throwable ignored) {
        }
        return BUILD_TARGET_MC;
    }

    /** 版本号 → 版本组（前两段，如 1.21.1 → 1.21；26.3.x → 26.3）。 */
    static String versionGroup(String mcVersion) {
        if (mcVersion == null || mcVersion.isEmpty()) {
            return BUILD_TARGET_MC;
        }
        String[] parts = mcVersion.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return mcVersion;
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
        boolean firstPublish = !Files.exists(target);
        boolean changed = firstPublish || Files.mismatch(cachedZip, target) != -1L;
        if (!changed) {
            return new Published(target, false, false);
        }
        Path tmp = dir.resolve(name + ".j20tmp");
        Files.copy(cachedZip, tmp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Published(target, firstPublish, true);
    }

}
