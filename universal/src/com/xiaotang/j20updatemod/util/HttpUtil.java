package com.xiaotang.j20updatemod.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * HTTP 工具：基于 JDK HttpClient，支持超时、跟随重定向与 Range 断点续传。
 * 所有网络请求均不得抛出未捕获异常打断游戏启动——调用方负责兜底。
 */
public final class HttpUtil {

    /** 断点续传下载的结果。 */
    public record DownloadResult(long totalBytes, boolean resumed) {
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private HttpUtil() {
    }

    /** GET 请求完整内容。非 2xx 抛 IOException。 */
    public static byte[] get(String url, int timeoutMs) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 1000)))
                .header("User-Agent", "j20UpdateMod")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> resp = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + resp.statusCode() + " for " + url);
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while requesting " + url, e);
        }
    }

    /**
     * 下载到文件，支持断点续传。
     *
     * @param url        下载地址
     * @param dest       目标文件（已存在的部分字节会被作为续传起点）
     * @param timeoutMs  单请求超时
     * @return 下载完成后的文件总字节数
     */
    public static DownloadResult download(String url, Path dest, int timeoutMs) throws IOException {
        Files.createDirectories(dest.getParent());
        long existing = Files.exists(dest) ? Files.size(dest) : 0L;
        boolean resumed = existing > 0;

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 1000)))
                .header("User-Agent", "j20UpdateMod")
                .GET();
        if (resumed) {
            builder.header("Range", "bytes=" + existing + "-");
        }

        try {
            HttpResponse<InputStream> resp = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            if (status == 416) {
                // 服务器认为文件已完整（Range 越界），直接按现状返回
                return new DownloadResult(existing, resumed);
            }
            if (status / 100 != 2) {
                throw new IOException("HTTP " + status + " for " + url);
            }

            boolean append = resumed && status == 206;
            if (!append && resumed) {
                // 服务器不支持 Range，重新完整下载
                existing = 0L;
                resumed = false;
            }

            try (InputStream in = resp.body();
                 OutputStream out = Files.newOutputStream(dest,
                         append ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING})) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            return new DownloadResult(Files.size(dest), resumed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading " + url, e);
        }
    }
}
