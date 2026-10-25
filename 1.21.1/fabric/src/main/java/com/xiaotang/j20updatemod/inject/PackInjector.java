package com.xiaotang.j20updatemod.inject;

import com.xiaotang.j20updatemod.J20UpdateMod;
import com.xiaotang.j20updatemod.util.GameDirs;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 资源包注入器（文档 3.2.3）：把下载好的汉化资源包注册进资源包列表并强制置顶。
 * Fabric 无 AddPackFindersEvent，由 ResourcePackManagerMixin 在客户端资源包管理器
 * 构造完成时回调 {@link #onManagerCreated(Set)} 追加 ResourcePackProvider（≈ RepositorySource），
 * 用 ResourcePackPosition(required=true, TOP, fixedPosition=true) 保证最高优先级且不可被拖动降级。
 */
public final class PackInjector {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final String PACK_ID = "j20/lang";

    private PackInjector() {
    }

    /**
     * ResourcePackManager 构造完成回调（mixin 触发，仅客户端资源包管理器）。
     * 等待更新流程结束后把汉化包源追加进 providers。
     *
     * @param providers 构造器 providers 字段的可变副本，由本方法决定是否追加
     * @return 是否已追加汉化包源
     */
    public static boolean onManagerCreated(Set<ResourcePackProvider> providers) {
        J20UpdateMod.UpdateOutcome outcome = J20UpdateMod.awaitUpdate();
        Path packZip = outcome != null ? outcome.packZip() : null;
        String version = outcome != null ? outcome.packVersion() : null;

        if (packZip == null || !Files.exists(packZip)) {
            // 更新流程失败时回退：扫描缓存目录里已有的汉化包，保证离线也能显示旧版汉化
            packZip = findCachedPack(GameDirs.resolveGameDir());
            version = "cache";
            if (packZip == null) {
                LOGGER.warn("[j20] 无可用汉化资源包（首次使用且下载失败），本次启动不注入");
                return false;
            }
        }

        final Path zip = packZip;
        final String packVersion = version;
        providers.add(consumer -> {
            ResourcePackProfile profile = buildPack(zip, packVersion);
            if (profile != null) {
                consumer.accept(profile);
                LOGGER.info("[j20] 汉化资源包已注入资源包列表顶部: {}（版本 {}）", zip.getFileName(), packVersion);
            }
        });
        return true;
    }

    /** 从缓存 zip 构建资源包对象（置顶、强制启用、位置固定）。 */
    static ResourcePackProfile buildPack(Path zip, String version) {
        try {
            // ZipBackedFactory 是 vanilla 的 zip 资源包工厂（≈ NeoForge 的 BuiltInPackSource.fromName + FilePackResources）
            ResourcePackProfile.PackFactory factory = new ZipResourcePack.ZipBackedFactory(zip);
            ResourcePackProfile profile = ResourcePackProfile.create(
                    new ResourcePackInfo(PACK_ID,
                            Text.literal("J20模组汉化" + (version == null ? "" : " " + version)),
                            ResourcePackSource.NONE,
                            Optional.empty()),
                    factory,
                    ResourceType.CLIENT_RESOURCES,
                    new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, true));

            if (profile == null) {
                LOGGER.error("[j20] 汉化资源包读取失败（pack.mcmeta 无效）: {}", zip);
            } else {
                LOGGER.info("[j20] 汉化资源包构建成功: {} 兼容性={}", zip.getFileName(), profile.getCompatibility());
            }
            return profile;
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
