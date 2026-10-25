package com.xiaotang.j20updatemod.vp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.xiaotang.j20updatemod.download.Manifest;
import com.xiaotang.j20updatemod.download.PackDownloader;
import com.xiaotang.j20updatemod.download.Sources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Vault Patcher 模块加载器（文档 3.2.5）。
 * vp-modules-{mc_version}.zip 不是资源包：包内路径为 vaultpatcher/modules/*.json，
 * 须解压到游戏根目录（使 .minecraft/vaultpatcher/modules/ 就位），
 * 并把模块名写入 config/vaultpatcher_asm/config.json 的 modules 数组。
 */
public final class VPModuleLoader {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String MODULE_PREFIX = "vaultpatcher/modules/";
    private static final String VP_CONFIG_RELPATH = "config/vaultpatcher_asm/config.json";

    /** 部署结果。 */
    public record Result(boolean enabled, boolean deployed, boolean vpMissing, boolean emptyPackage, List<String> moduleNames) {
        public static Result skipped() {
            return new Result(false, false, false, false, List.of());
        }
    }

    private VPModuleLoader() {
    }

    /**
     * 若启用硬编码补丁：下载（或复用缓存的）VP 模块包并部署到游戏根目录。
     *
     * @param enabled  配置项 enableHardcodedPatch
     * @param vpLoaded 游戏中是否已安装 Vault Patcher
     */
    public static Result deployIfNeeded(boolean enabled, boolean vpLoaded, Path gameDir, Path cacheDir,
                                        Manifest manifest, String mcGroup, String version,
                                        List<Sources.Source> ordered) {
        if (!enabled) {
            LOGGER.info("[j20] 硬编码补丁未启用（enableHardcodedPatch=false），跳过 VP 模块部署");
            return Result.skipped();
        }

        Manifest.VpPackEntry vpPack = manifest.findVpPack(mcGroup);
        if (vpPack == null) {
            LOGGER.warn("[j20] manifest 中没有 {} 的 vp_packages 条目，跳过 VP 模块部署", mcGroup);
            return new Result(true, false, !vpLoaded, false, List.of());
        }

        Path zip;
        try {
            zip = PackDownloader.ensureVpModules(cacheDir, vpPack, version, ordered).file();
        } catch (IOException e) {
            LOGGER.warn("[j20] VP 模块包下载失败: {}", e.toString());
            return new Result(true, false, !vpLoaded, false, List.of());
        }

        List<String> modules;
        try {
            modules = extractModules(zip, gameDir);
        } catch (IOException e) {
            LOGGER.warn("[j20] VP 模块包解压失败 {}: {}", zip, e.toString());
            return new Result(true, false, !vpLoaded, false, List.of());
        }

        if (modules.isEmpty()) {
            // 汉化库当前的 VP 包为空包（尚无真实补丁），属正常情况
            LOGGER.info("[j20] VP 模块包 {} 无模块条目（空包），跳过部署", zip.getFileName());
            return new Result(true, false, !vpLoaded, true, List.of());
        }

        LOGGER.info("[j20] 已部署 {} 个 VP 模块到 {}: {}", modules.size(), gameDir.resolve("vaultpatcher/modules"), modules);
        if (vpLoaded) {
            registerModulesInVpConfig(gameDir, modules);
        } else {
            LOGGER.warn("[j20] 未检测到 Vault Patcher（vaultpatcher_asm），模块文件已就位，安装 VP 后生效");
        }
        return new Result(true, true, !vpLoaded, false, modules);
    }

    /** 把包内 vaultpatcher/modules/ 下的文件解压到游戏根目录。返回模块名（不含 .json）。 */
    static List<String> extractModules(Path zip, Path gameDir) throws IOException {
        List<String> modules = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.startsWith(MODULE_PREFIX)) {
                    continue;
                }
                String fileName = name.substring(MODULE_PREFIX.length());
                if (fileName.isEmpty() || fileName.contains("/")) {
                    // 仅接受 vaultpatcher/modules/ 一层的文件，防止路径穿越
                    continue;
                }
                Path dest = gameDir.resolve("vaultpatcher").resolve("modules").resolve(fileName);
                Files.createDirectories(dest.getParent());
                try (InputStream in = zf.getInputStream(entry);
                     OutputStream out = Files.newOutputStream(dest)) {
                    in.transferTo(out);
                }
                if (fileName.endsWith(".json")) {
                    modules.add(fileName.substring(0, fileName.length() - ".json".length()));
                }
            }
        }
        return modules;
    }

    /**
     * 更新 config/vaultpatcher_asm/config.json：把模块名补进 modules 数组（元素不带 .json 后缀）。
     * schema 已实证定稿（汉化库 FORMATS.md，实证自 VP 源码 VaultPatcherConfig.java）：
     * 字段名是 modules（mods 只是 VP 源码内部变量名，不是 JSON 字段）；
     * 配置路径固定 config/vaultpatcher_asm/config.json，不随模块迁移；
     * 已存在的配置只补 modules，不动用户其他设置；新建时写入完整默认结构。
     */
    static void registerModulesInVpConfig(Path gameDir, List<String> modules) {
        Path config = gameDir.resolve(VP_CONFIG_RELPATH);
        JsonObject root;
        if (Files.exists(config)) {
            try {
                root = GSON.fromJson(Files.readString(config, StandardCharsets.UTF_8), JsonObject.class);
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("[j20] VP 配置解析失败，跳过 modules 注册 {}: {}", config, e.toString());
                return;
            }
            if (root == null) {
                root = defaultConfigSkeleton();
            }
        } else {
            root = defaultConfigSkeleton();
        }

        // 定稿字段：modules（元素不带 .json 后缀）
        int added = registerIntoArray(root, "modules", modules);

        try {
            Files.createDirectories(config.getParent());
            Path tmp = config.resolveSibling(config.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.move(tmp, config, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("[j20] VP 配置已更新 {}（新增 {} 个模块注册）", config, added);
        } catch (IOException e) {
            LOGGER.warn("[j20] VP 配置写入失败 {}: {}", config, e.toString());
        }
    }

    /** VP 配置完整默认结构（FORMATS.md 实证样例），仅在配置文件不存在时创建使用。 */
    private static JsonObject defaultConfigSkeleton() {
        JsonObject root = new JsonObject();
        root.add("modules", new JsonArray());
        root.addProperty("default_language", "en_us");
        root.addProperty("class_patch", false);
        root.addProperty("load_all_modules", false);
        JsonObject debug = new JsonObject();
        debug.addProperty("is_enable", false);
        debug.addProperty("output_mode", 0);
        debug.addProperty("output_node_debug", false);
        debug.addProperty("pairs_hide_limit", 7);
        debug.addProperty("export_class", false);
        debug.addProperty("use_cache", true);
        root.add("debug_mode", debug);
        return root;
    }

    /** 把模块名补进指定字段的 JSON 数组（缺省创建），返回新增数量。 */
    private static int registerIntoArray(JsonObject root, String field, List<String> modules) {
        JsonArray array;
        JsonElement existing = root.get(field);
        if (existing != null && existing.isJsonArray()) {
            array = existing.getAsJsonArray();
        } else {
            array = new JsonArray();
            root.add(field, array);
        }

        int added = 0;
        for (String module : modules) {
            boolean present = false;
            for (JsonElement el : array) {
                if (module.equals(el.getAsString())) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                array.add(module);
                added++;
            }
        }
        return added;
    }
}
