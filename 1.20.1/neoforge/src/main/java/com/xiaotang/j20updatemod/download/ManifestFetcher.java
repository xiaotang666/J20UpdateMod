package com.xiaotang.j20updatemod.download;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.xiaotang.j20updatemod.util.HttpUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * manifest.json 获取器（文档 3.2.1）：
 * 按镜像源优先级依次获取，校验冻结接口结构，成功后缓存到 j20update-cache/manifest.json；
 * 所有源均失败时回退缓存清单。
 */
public final class ManifestFetcher {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final Gson GSON = new Gson();
    private static final int FETCH_TIMEOUT_MS = 10_000;

    private ManifestFetcher() {
    }

    /**
     * 获取并校验 manifest。
     *
     * @param cacheDir    缓存目录 .minecraft/j20update-cache
     * @param ordered     按优先级排好的源列表
     * @param needMcGroup 需要的 MC 版本组（如 1.21），用于校验清单包含对应资源包
     * @param needLoader  需要的加载器（如 neoforge）
     * @return 校验通过的 manifest（可能是网络获取的，也可能是缓存的）
     */
    public static Manifest fetch(Path cacheDir, List<Sources.Source> ordered, String needMcGroup, String needLoader)
            throws IOException {
        IOException lastError = null;

        for (Sources.Source source : ordered) {
            if (source.manifestUrl == null || source.manifestUrl.isBlank()) {
                continue; // 仅提供资产加速的源（无清单地址），跳过
            }
            try {
                LOGGER.info("[j20] 尝试获取版本清单: {} ({})", source.name, source.manifestUrl);
                byte[] body = HttpUtil.get(source.manifestUrl, FETCH_TIMEOUT_MS);
                Manifest manifest = parse(body, needMcGroup, needLoader);
                cacheManifest(cacheDir, body);
                LOGGER.info("[j20] 版本清单获取成功（{}），最新版本 {}", source.name, manifest.latest.version);
                return manifest;
            } catch (IOException | JsonSyntaxException e) {
                lastError = e instanceof IOException io ? io : new IOException("清单解析失败: " + e, e);
                LOGGER.warn("[j20] 源 {} 获取清单失败: {}", source.name, e.toString());
            }
        }

        Manifest cached = readCachedManifest(cacheDir, needMcGroup, needLoader);
        if (cached != null) {
            LOGGER.warn("[j20] 所有源均失败，使用缓存清单版本 {}（最后错误: {}）",
                    cached.latest.version, lastError == null ? "无" : lastError.toString());
            return cached;
        }
        throw new IOException("版本清单获取失败且无可用缓存: " + (lastError == null ? "" : lastError.getMessage()), lastError);
    }

    private static Manifest parse(byte[] body, String needMcGroup, String needLoader) throws IOException {
        Manifest manifest;
        try {
            manifest = GSON.fromJson(new String(body, StandardCharsets.UTF_8), Manifest.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("清单 JSON 解析失败: " + e, e);
        }
        if (manifest == null) {
            throw new IOException("清单内容为空");
        }
        String problem = manifest.validate(needMcGroup, needLoader);
        if (problem != null) {
            throw new IOException("清单校验失败: " + problem);
        }
        return manifest;
    }

    private static void cacheManifest(Path cacheDir, byte[] body) {
        try {
            Files.createDirectories(cacheDir);
            Files.write(cacheDir.resolve("manifest.json"), body);
        } catch (IOException e) {
            LOGGER.warn("[j20] 清单缓存写入失败: {}", e.toString());
        }
    }

    private static Manifest readCachedManifest(Path cacheDir, String needMcGroup, String needLoader) {
        Path file = cacheDir.resolve("manifest.json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] body = Files.readAllBytes(file);
            return parse(body, needMcGroup, needLoader);
        } catch (IOException e) {
            LOGGER.warn("[j20] 缓存清单不可用: {}", e.toString());
            return null;
        }
    }
}
