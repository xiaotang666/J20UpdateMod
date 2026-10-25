package com.xiaotang.j20updatemod.download;

import com.xiaotang.j20updatemod.util.HttpUtil;
import com.xiaotang.j20updatemod.util.MD5Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 资源包下载器（文档 3.2.2）：
 * 按镜像源优先级下载到 j20update-cache/，支持断点续传；
 * 下载完成后 MD5 校验（与 manifest 比对），校验失败删除重试，每源最多 3 次；全部失败抛异常。
 */
public final class PackDownloader {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final int DOWNLOAD_TIMEOUT_MS = 30_000;
    private static final int MAX_RETRIES = 3;

    /** 下载结果：缓存文件路径、版本号、是否命中已缓存。 */
    public record Downloaded(Path file, String version, boolean fromCache) {
    }

    private PackDownloader() {
    }

    /** 确保资源包 zip 在缓存中且 MD5 与 manifest 一致；不一致/缺失则下载。 */
    public static Downloaded ensureResourcePack(Path cacheDir, Manifest.PackEntry pack, String version,
                                                List<Sources.Source> ordered) throws IOException {
        Path file = downloadVerified(cacheDir, pack.assetName, pack.releaseUrl, pack.md5, version, ordered);
        return new Downloaded(file, version, false);
    }

    /** 确保 VP 模块包 zip 在缓存中且 MD5 与 manifest 一致。 */
    public static Downloaded ensureVpModules(Path cacheDir, Manifest.VpPackEntry pack, String version,
                                             List<Sources.Source> ordered) throws IOException {
        Path file = downloadVerified(cacheDir, pack.assetName, pack.releaseUrl, pack.md5, version, ordered);
        return new Downloaded(file, version, false);
    }

    private static Path downloadVerified(Path cacheDir, String assetName, String releaseUrl, String expectedMd5,
                                         String version, List<Sources.Source> ordered) throws IOException {
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve(assetName);

        // 已缓存且校验通过 → 直接复用（同时是断点续传的基线）
        if (Files.exists(target)) {
            String existingMd5 = MD5Util.fileMd5Hex(target);
            if (MD5Util.matches(expectedMd5, existingMd5)) {
                LOGGER.info("[j20] {} 命中缓存（md5={}）", assetName, existingMd5);
                return target;
            }
            LOGGER.warn("[j20] {} 缓存 MD5 不匹配（期望 {}，实际 {}），重新下载",
                    assetName, expectedMd5, existingMd5);
            Files.deleteIfExists(target);
        }

        Path part = cacheDir.resolve(assetName + ".part");
        IOException lastError = null;

        for (Sources.Source source : ordered) {
            String url = MirrorSelector.assetUrl(source, version, assetName, releaseUrl);
            if (url == null || url.isEmpty()) {
                continue;
            }
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    LOGGER.info("[j20] 下载 {} 第 {}/{} 次尝试（{}）: {}", assetName, attempt, MAX_RETRIES, source.name, url);
                    HttpUtil.DownloadResult result = HttpUtil.download(url, part, DOWNLOAD_TIMEOUT_MS);
                    LOGGER.info("[j20] 下载完成 {} 共 {} 字节（续传={}）", assetName, result.totalBytes(), result.resumed());

                    String actualMd5 = MD5Util.fileMd5Hex(part);
                    if (MD5Util.matches(expectedMd5, actualMd5)) {
                        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        LOGGER.info("[j20] {} MD5 校验通过（{}），已缓存到 {}", assetName, actualMd5, target);
                        return target;
                    }
                    // 校验失败：删除已下载文件，重新下载（不续传，避免脏数据）
                    LOGGER.warn("[j20] {} MD5 校验失败（期望 {}，实际 {}），删除重试", assetName, expectedMd5, actualMd5);
                    Files.deleteIfExists(part);
                } catch (IOException e) {
                    lastError = e;
                    LOGGER.warn("[j20] {} 下载失败（{} 第 {} 次）: {}", assetName, source.name, attempt, e.toString());
                }
            }
            LOGGER.warn("[j20] 源 {} 连续 {} 次失败，切换下一个源", source.name, MAX_RETRIES);
        }

        Files.deleteIfExists(part);
        throw new IOException("资源包 " + assetName + " 下载失败（所有源均不可用）"
                + (lastError == null ? "" : ": " + lastError.getMessage()), lastError);
    }
}
