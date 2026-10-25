package com.xiaotang.j20updatemod.inject;

import com.xiaotang.j20updatemod.J20UpdateMod;
import com.xiaotang.j20updatemod.util.GameDirs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 资源包注入器（文档 3.2.3）：把下载好的汉化资源包注册进资源包列表并强制置顶。
 * PackRepository 创建时经 mod 总线收到 AddPackFindersEvent，
 * 用 Pack.create(required=true, Position.TOP, fixedPosition=true) 保证最高优先级、
 * 自动启用且不可被拖动降级（1.20.x 的 Pack 构建 API，无 PackSelectionConfig）。
 */
public final class PackInjector {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final String PACK_ID = "j20/lang";

    private PackInjector() {
    }

    /** AddPackFindersEvent 处理（mod 总线）。 */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

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
        event.addRepositorySource(consumer -> {
            Pack pack = buildPack(zip, packVersion);
            if (pack != null) {
                consumer.accept(pack);
                LOGGER.info("[j20] 汉化资源包已注入资源包列表顶部: {}（版本 {}）", zip.getFileName(), packVersion);
            }
        });
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

    /** 扫描 j20update-cache 下的 pack-*-neoforge.zip，返回最新修改的一个。 */
    private static Path findCachedPack(Path gameDir) {
        Path cacheDir = gameDir.resolve("j20update-cache");
        if (!Files.isDirectory(cacheDir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(cacheDir)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("pack-") && name.endsWith("-neoforge.zip");
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
