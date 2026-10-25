package com.xiaotang.j20updatemod.inject;

import com.xiaotang.j20updatemod.J20UpdateMod;
import com.xiaotang.j20updatemod.util.GameDirs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * 资源包注入器（文档 3.2.3）：把下载好的汉化资源包注册进资源包列表并强制置顶。
 * Fabric 没有 AddPackFindersEvent，改由 PackRepositoryMixin 在 PackRepository 构造期
 * 把本类创建的 RepositorySource 追加进 sources（与 NeoForge 的 addRepositorySource 语义一致）；
 * 用 PackSelectionConfig(required=true, TOP, fixedPosition=true) 保证最高优先级且不可被拖动降级
 * （26.x javap 验证：PackRepository.rebuildSelected 对 required 包无条件强制入选，
 * 并经 Pack.Position.insert 按 defaultPosition 插位）。
 */
public final class PackInjector {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final String PACK_ID = "j20/lang";

    private PackInjector() {
    }

    /**
     * 创建注入用的 RepositorySource（由 PackRepositoryMixin 在客户端资源包仓库构造期挂入）。
     * loadPacks 在每次仓库发现阶段被调用；首次调用会阻塞等待更新流程完成（有上限），与 NeoForge 版行为一致。
     */
    public static RepositorySource createSource() {
        return consumer -> {
            J20UpdateMod.UpdateOutcome outcome = J20UpdateMod.awaitUpdate();
            Path packZip = outcome != null ? outcome.packZip() : null;
            String version = outcome != null ? outcome.packVersion() : null;

            if (packZip == null || !Files.exists(packZip)) {
                // 更新流程失败时回退：扫描缓存目录里已有的汉化包，保证离线也能显示旧版汉化
                packZip = findCachedPack(GameDirs.resolveGameDir());
                version = "cache";
                if (packZip == null) {
                    LOGGER.warn("[j20] 无可用汉化资源包（首次使用且下载失败），本次启动不注入");
                    return;
                }
            }

            final Path zip = packZip;
            final String packVersion = version;
            Pack pack = buildPack(zip, packVersion);
            if (pack != null) {
                consumer.accept(pack);
                LOGGER.info("[j20] 汉化资源包已注入资源包列表顶部: {}（版本 {}）", zip.getFileName(), packVersion);
            }
        };
    }

    /** 从缓存 zip 构建资源包对象（置顶、强制启用、位置固定）。 */
    static Pack buildPack(Path zipPath, String version) {
        try {
            ZipFile zip = new ZipFile(zipPath.toFile());
            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo location) {
                    return new ZipPackResources(location, zip);
                }

                @Override
                public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                    return new ZipPackResources(location, zip);
                }
            };

            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(PACK_ID,
                            Component.literal("J20模组汉化" + (version == null ? "" : " " + version)),
                            PackSource.DEFAULT,
                            Optional.empty()),
                    supplier,
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, true));

            if (pack == null) {
                zip.close();
                LOGGER.error("[j20] 汉化资源包读取失败（pack.mcmeta 无效）: {}", zipPath);
            } else {
                LOGGER.info("[j20] 汉化资源包构建成功: {} 兼容性={}", zipPath.getFileName(), pack.getCompatibility());
            }
            return pack;
        } catch (Exception e) {
            LOGGER.error("[j20] 汉化资源包注入异常: {}", zipPath, e);
            return null;
        }
    }

    /** 扫描 j20update-cache 下的 pack-*-fabric.zip，返回最新修改的一个。 */
    private static Path findCachedPack(Path gameDir) {
        Path cacheDir = gameDir.resolve("j20update-cache");
        if (!Files.isDirectory(cacheDir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(cacheDir)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("pack-") && name.endsWith("-fabric.zip");
                    })
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
        } catch (IOException e) {
            LOGGER.warn("[j20] 缓存目录扫描失败: {}", e.toString());
            return null;
        }
    }
}
