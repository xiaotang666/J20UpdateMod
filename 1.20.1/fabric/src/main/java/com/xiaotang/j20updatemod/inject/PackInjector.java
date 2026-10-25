package com.xiaotang.j20updatemod.inject;

import com.xiaotang.j20updatemod.J20UpdateMod;
import com.xiaotang.j20updatemod.util.GameDirs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 资源包注入器（文档 3.2.3）：把下载好的汉化资源包注册进资源包列表并强制置顶。
 * Fabric 无 AddPackFindersEvent，等效方案：PackRepositoryMixin 在
 * PackRepository.discoverAvailable() 返回处调用 {@link #mergePack} 并入汉化包；
 * 1.20.x 的 PackRepository.rebuildSelected 会把 required=true 的包按其默认位置自动插回选中列表，
 * 因此 Pack.create(required=true, Position.TOP, fixedPosition=true) 即可保证强制启用 + 置顶。
 */
public final class PackInjector {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final String PACK_ID = "j20/lang";

    private PackInjector() {
    }

    /** discoverAvailable 钩子（mixin 调用）：把汉化包并入可用资源包表。 */
    public static Map<String, Pack> mergePack(Map<String, Pack> discovered) {
        try {
            J20UpdateMod.UpdateOutcome outcome = J20UpdateMod.awaitUpdate();
            Path packZip = outcome != null ? outcome.packZip() : null;
            String version = outcome != null ? outcome.packVersion() : null;

            if (packZip == null || !Files.exists(packZip)) {
                // 更新流程失败时回退：扫描缓存目录里已有的汉化包，保证离线也能显示旧版汉化
                packZip = findCachedPack(GameDirs.resolveGameDir());
                version = "cache";
                if (packZip == null) {
                    LOGGER.warn("[j20] 无可用汉化资源包（首次使用且下载失败），本次启动不注入");
                    return discovered;
                }
            }

            final Path zip = packZip;
            final String packVersion = version;
            Pack pack = buildPack(zip, packVersion);
            if (pack == null) {
                return discovered;
            }

            Map<String, Pack> merged = new TreeMap<>(discovered);
            merged.put(pack.getId(), pack);
            LOGGER.info("[j20] 汉化资源包已注入资源包列表顶部: {}（版本 {}）", zip.getFileName(), packVersion);
            return merged;
        } catch (Throwable t) {
            LOGGER.error("[j20] 汉化资源包注入异常", t);
            return discovered;
        }
    }

    /** 从缓存 zip 构建资源包对象（置顶、强制启用、位置固定）。 */
    static Pack buildPack(Path zip, String version) {
        try {
            Pack.ResourcesSupplier supplier = name -> new FilePackResources(name, zip.toFile(), false);

            Pack.Info info = Pack.readPackInfo(PACK_ID, supplier);
            if (info == null) {
                LOGGER.error("[j20] 汉化资源包读取失败（pack.mcmeta 无效）: {}", zip);
                return null;
            }

            Pack pack = Pack.create(
                    PACK_ID,
                    Component.literal("J20模组汉化" + (version == null ? "" : " " + version)),
                    true,
                    supplier,
                    info,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    true,
                    PackSource.DEFAULT);

            if (pack == null) {
                LOGGER.error("[j20] 汉化资源包构建失败: {}", zip);
            } else {
                LOGGER.info("[j20] 汉化资源包构建成功: {} 兼容性={}", zip.getFileName(), pack.getCompatibility());
            }
            return pack;
        } catch (Exception e) {
            LOGGER.error("[j20] 汉化资源包注入异常: {}", zip, e);
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
