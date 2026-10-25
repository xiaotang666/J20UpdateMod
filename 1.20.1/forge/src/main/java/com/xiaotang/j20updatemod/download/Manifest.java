package com.xiaotang.j20updatemod.download;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 汉化库 manifest.json 数据模型（对接 J20-Mcmodlanguage-Package 的冻结接口）。
 * 冻结字段：schema_version、latest.version（YYYY.MM.DD-NNN）、latest.packages[] 的
 * mc_version/loader/asset_name/release_url/md5/size；vp_packages 为可选字段。
 */
public class Manifest {

    /** 版本号格式：YYYY.MM.DD-NNN。 */
    public static final Pattern VERSION_PATTERN = Pattern.compile("\\d{4}\\.\\d{2}\\.\\d{2}-\\d{3}");

    @SerializedName("schema_version")
    public int schemaVersion;

    @SerializedName("latest")
    public Latest latest;

    public static class Latest {
        @SerializedName("version")
        public String version;

        @SerializedName("build_time")
        public String buildTime;

        @SerializedName("commit")
        public String commit;

        @SerializedName("packages")
        public List<PackEntry> packages;

        @SerializedName("vp_packages")
        public List<VpPackEntry> vpPackages;
    }

    /** 资源包条目（pack-{mc_version}-{loader}.zip）。 */
    public static class PackEntry {
        @SerializedName("mc_version")
        public String mcVersion;

        @SerializedName("loader")
        public String loader;

        @SerializedName("asset_name")
        public String assetName;

        @SerializedName("release_url")
        public String releaseUrl;

        @SerializedName("md5")
        public String md5;

        @SerializedName("size")
        public long size;
    }

    /** VP 模块包条目（vp-modules-{mc_version}.zip，无 loader 区分）。 */
    public static class VpPackEntry {
        @SerializedName("mc_version")
        public String mcVersion;

        @SerializedName("asset_name")
        public String assetName;

        @SerializedName("release_url")
        public String releaseUrl;

        @SerializedName("md5")
        public String md5;

        @SerializedName("size")
        public long size;
    }

    /** 校验清单基本结构是否符合冻结接口；返回 null 表示通过，否则返回原因。 */
    public String validate(String needMcGroup, String needLoader) {
        if (schemaVersion != 1) {
            return "schema_version 不是 1: " + schemaVersion;
        }
        if (latest == null) {
            return "缺少 latest 字段";
        }
        if (latest.version == null || !VERSION_PATTERN.matcher(latest.version).matches()) {
            return "latest.version 格式不符合 YYYY.MM.DD-NNN: " + latest.version;
        }
        if (latest.packages == null || latest.packages.isEmpty()) {
            return "latest.packages 为空";
        }
        if (needMcGroup != null && needLoader != null && findPack(needMcGroup, needLoader) == null) {
            return "packages 中没有 " + needMcGroup + "/" + needLoader + " 对应的资源包";
        }
        return null;
    }

    /** 按 MC 版本组与加载器查找资源包条目。 */
    public PackEntry findPack(String mcGroup, String loader) {
        if (latest == null || latest.packages == null) {
            return null;
        }
        for (PackEntry p : latest.packages) {
            if (mcGroup.equals(p.mcVersion) && loader.equalsIgnoreCase(p.loader)) {
                return p;
            }
        }
        return null;
    }

    /** 按 MC 版本组查找 VP 模块包条目（可为空）。 */
    public VpPackEntry findVpPack(String mcGroup) {
        if (latest == null || latest.vpPackages == null) {
            return null;
        }
        for (VpPackEntry p : latest.vpPackages) {
            if (mcGroup.equals(p.mcVersion)) {
                return p;
            }
        }
        return null;
    }
}
