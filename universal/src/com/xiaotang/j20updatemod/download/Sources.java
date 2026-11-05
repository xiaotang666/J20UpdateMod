package com.xiaotang.j20updatemod.download;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 内置镜像源配置（resources/sources.json，文档 3.3.1）。
 * 字段：id / name / region(cn|global) / priority(越小越优先) / manifest_url / asset_url_template。
 * asset_url_template 支持 {version}、{asset_name}、{release_url} 占位符。
 */
public class Sources {

    private static final Gson GSON = new Gson();

    @SerializedName("sources")
    public List<Source> sources;

    public static class Source {
        @SerializedName("id")
        public String id;

        @SerializedName("name")
        public String name;

        @SerializedName("region")
        public String region;

        @SerializedName("priority")
        public int priority;

        @SerializedName("manifest_url")
        public String manifestUrl;

        @SerializedName("asset_url_template")
        public String assetUrlTemplate;

        public boolean isCn() {
            return "cn".equalsIgnoreCase(region);
        }
    }

    /** 从模组 jar 内置的 sources.json 加载。 */
    public static Sources loadBuiltin() throws IOException {
        try (InputStream in = Sources.class.getResourceAsStream("/sources.json")) {
            if (in == null) {
                throw new IOException("模组内缺少内置 sources.json");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Sources sources = GSON.fromJson(json, Sources.class);
            if (sources == null || sources.sources == null || sources.sources.isEmpty()) {
                throw new IOException("内置 sources.json 为空或格式错误");
            }
            return sources;
        }
    }
}
