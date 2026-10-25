package com.xiaotang.j20updatemod;

import com.xiaotang.j20updatemod.config.J20Config;
import com.xiaotang.j20updatemod.download.Manifest;
import com.xiaotang.j20updatemod.download.ManifestFetcher;
import com.xiaotang.j20updatemod.download.MirrorSelector;
import com.xiaotang.j20updatemod.download.PackDownloader;
import com.xiaotang.j20updatemod.download.Sources;
import com.xiaotang.j20updatemod.inject.PackInjector;
import com.xiaotang.j20updatemod.lang.LanguageDetector;
import com.xiaotang.j20updatemod.util.GameDirs;
import com.xiaotang.j20updatemod.vp.VPModuleLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * j20UpdateMod（J20模组汉化）主入口。
 * 启动时后台执行：语言检测 → 镜像源选择 → manifest 获取 → 资源包下载/MD5 校验 → VP 模块部署；
 * 客户端资源包管理器创建时（ResourcePackManagerMixin）等待更新完成并把汉化包注入资源包列表顶部。
 */
public class J20UpdateMod implements ClientModInitializer {

    public static final String MODID = "j20updatemod";
    public static final String VERSION = "0.0.1";
    public static final String LOADER = "fabric";

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    /** 更新流程整体等待上限（秒）：超过后注入流程用缓存兜底，不阻塞启动。 */
    private static final long UPDATE_BUDGET_SECONDS = 120;

    /** 兜底用的构建目标 MC 版本（正常情况下从 SharedConstants 读取）。 */
    private static final String BUILD_TARGET_MC = "1.21";

    private static final CountDownLatch UPDATE_DONE = new CountDownLatch(1);
    private static volatile UpdateOutcome outcome;
    private static final AtomicBoolean VP_REMINDER_PENDING = new AtomicBoolean(false);

    private static final String VP_REMINDER_TEXT =
            "§e[j20] §f已启用硬编码补丁，但未检测到 §bVault Patcher§f。"
                    + "请安装 Vault Patcher 模组并重启游戏，硬编码文本汉化才会生效。";

    /** 更新流程结果。 */
    public record UpdateOutcome(Path packZip, String packVersion, String langCode, String manifestVersion) {
    }

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onPlayerLogin(client));

        Thread worker = new Thread(J20UpdateMod::runUpdate, "j20update-bootstrap");
        worker.setDaemon(true);
        worker.start();

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

            boolean vpLoaded = isVpLoaded();
            VPModuleLoader.Result vpResult = VPModuleLoader.deployIfNeeded(
                    config.enableHardcodedPatch, vpLoaded, gameDir, cacheDir,
                    manifest, mcGroup, manifest.latest.version, ordered);
            if (vpResult.vpMissing() && config.showVPInstallReminder) {
                VP_REMINDER_PENDING.set(true);
            }

            outcome = new UpdateOutcome(downloaded.file(), manifest.latest.version, langCode, manifest.latest.version);
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
    private static void onPlayerLogin(MinecraftClient client) {
        if (!VP_REMINDER_PENDING.getAndSet(false)) {
            return;
        }
        try {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal(VP_REMINDER_TEXT), false);
            } else {
                // 进档时机拿不到玩家实体时恢复待发状态，下次进档/进服再提醒
                VP_REMINDER_PENDING.set(true);
            }
        } catch (Throwable t) {
            LOGGER.warn("[j20] VP 安装提醒发送失败: {}", t.toString());
        }
    }

    /** 检测 Vault Patcher 是否已安装。 */
    private static boolean isVpLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded("vaultpatcher_asm");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 当前 Minecraft 版本号（如 1.21.1）。 */
    private static String detectMcVersion() {
        try {
            String name = SharedConstants.getGameVersion().getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (SharedConstants.VERSION_NAME != null && !SharedConstants.VERSION_NAME.isEmpty()) {
                return SharedConstants.VERSION_NAME;
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
}
