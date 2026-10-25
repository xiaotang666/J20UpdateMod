package com.xiaotang.j20updatemod.download;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 镜像源选择与下载链接构造（文档 3.2.1/3.2.2）。
 * 简体中文环境优先国内源（cn），非中文环境优先全球源（global）；同类源按 priority 升序；
 * 一类全部失败后自动回退另一类。
 */
public final class MirrorSelector {

    private MirrorSelector() {
    }

    /** 返回按优先级排好的源列表：偏好区域在前，各区域内 priority 升序。 */
    public static List<Sources.Source> order(List<Sources.Source> all, boolean preferCn) {
        List<Sources.Source> preferred = new ArrayList<>();
        List<Sources.Source> fallback = new ArrayList<>();
        for (Sources.Source s : all) {
            if (s.isCn() == preferCn) {
                preferred.add(s);
            } else {
                fallback.add(s);
            }
        }
        Comparator<Sources.Source> byPriority = Comparator.comparingInt((Sources.Source s) -> s.priority)
                .thenComparing(s -> s.id == null ? "" : s.id);
        preferred.sort(byPriority);
        fallback.sort(byPriority);

        List<Sources.Source> result = new ArrayList<>(preferred);
        result.addAll(fallback);
        return result;
    }

    /** 用占位符构造资源包下载链接（{version} / {asset_name} / {release_url}）。 */
    public static String assetUrl(Sources.Source source, String version, String assetName, String releaseUrl) {
        String template = source.assetUrlTemplate;
        if (template == null) {
            return null;
        }
        return template
                .replace("{version}", version == null ? "" : version)
                .replace("{asset_name}", assetName == null ? "" : assetName)
                .replace("{release_url}", releaseUrl == null ? "" : releaseUrl);
    }
}
