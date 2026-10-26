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
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
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
    public static final String LOADER = "neoforge";

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    /** 更新流程整体等待上限（秒）：超过后注入流程用缓存兜底，不阻塞启动。 */
    private static final long UPDATE_BUDGET_SECONDS = 120;

    /** 兜底用的构建目标 MC 版本（正常情况下从 SharedConstants 读取）。 */
    private static final String BUILD_TARGET_MC = "26.3";

    private static final CountDownLatch UPDATE_DONE = new CountDownLatch(1);
    private static volatile UpdateOutcome outcome;
    private static final AtomicBoolean VP_REMINDER_PENDING = new AtomicBoolean(false);

    private static final String VP_REMINDER_TEXT =
            "§e[j20] §f已启用硬编码补丁，但未检测到 §bVault Patcher§f。"
                    + "请安装 Vault Patcher 模组并重启游戏，硬编码文本汉化才会生效。";

    /** 更新流程结果。 */
    public record UpdateOutcome(Path packZip, String packVersion, String langCode, String manifestVersion) {
    }

    public J20UpdateMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(J20UpdateMod::onPlayerLogin);

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
    }

    /** 检测 Vault Patcher 是否已安装。 */
    private static boolean isVpLoaded() {
        try {
            return ModList.get().isLoaded("vaultpatcher");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 当前 Minecraft 版本号（如 26.1.2）。 */
    private static String detectMcVersion() {
        try {
            // 26.x 的 WorldVersion 方法为 name()/id()（1.21.x 的 getName()/VERSION_STRING 已移除）
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

    /** 发布结果：落盘路径 + 是否首次发布（新安装）。 */
    private record Published(Path file, boolean firstPublish) {
    }

    /**
     * 把资源包发布到 resourcepacks/ 文件夹（对齐 i18n 行为：实体文件可见、同名原子覆盖）。
     * 返回落盘路径与是否首次发布；不再注入资源包列表（普通资源包，用户自行排序/开关）。
     */
    private static Published publishToResourcepacks(Path cachedZip, Path gameDir, String mcGroup) throws IOException {
        Path dir = gameDir.resolve("resourcepacks");
        Files.createDirectories(dir);
        String name = "j20-lang-" + mcGroup + "-" + LOADER + ".zip";
        Path target = dir.resolve(name);
        boolean firstPublish = !Files.exists(target);
        Path tmp = dir.resolve(name + ".j20tmp");
        Files.copy(cachedZip, tmp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Published(target, firstPublish);
    }
}
