import com.xiaotang.j20updatemod.download.Manifest;
import com.xiaotang.j20updatemod.download.ManifestFetcher;
import com.xiaotang.j20updatemod.download.MirrorSelector;
import com.xiaotang.j20updatemod.download.PackDownloader;
import com.xiaotang.j20updatemod.download.Sources;
import com.xiaotang.j20updatemod.util.MD5Util;
import com.xiaotang.j20updatemod.vp.VPModuleLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** j20UpdateMod 核心链路冒烟测试（真实网络 + 真实汉化库接口）。 */
public class ChainTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name + (detail.isEmpty() ? "" : "  <- " + detail));
        } else {
            failed++;
            System.out.println("[FAIL] " + name + "  <- " + detail);
        }
    }

    static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.delete(f);
                } catch (IOException ignored) {
                }
            });
        }
    }

    public static void main(String[] args) throws Exception {
        Path base = Path.of("C:/Users/Administrator/AppData/Local/hermes/cache/scratch/j20test");
        Path cacheDir = base.resolve("j20update-cache");
        Path gameDir = base.resolve("game");
        // 清场：固定目录会被上一次运行的缓存/部署残留污染（空包容错场景曾因此误报）
        deleteRecursively(cacheDir);
        deleteRecursively(gameDir);
        deleteRecursively(base.resolve("game2"));
        Files.createDirectories(cacheDir);
        Files.createDirectories(gameDir);

        // ---- 1. 内置 sources.json + 镜像源排序 ----
        Sources sources = Sources.loadBuiltin();
        List<Sources.Source> cnFirst = MirrorSelector.order(sources.sources, true);
        List<Sources.Source> globalFirst = MirrorSelector.order(sources.sources, false);
        check("sources 内置加载", sources.sources.size() >= 2, sources.sources.size() + " 个源");
        check("zh_cn 时国内源优先", cnFirst.get(0).isCn(), cnFirst.get(0).id);
        check("非中文时 GitHub 优先", !globalFirst.get(0).isCn(), globalFirst.get(0).id);

        String url = MirrorSelector.assetUrl(cnFirst.get(0), "2026.10.03-004", "pack-1.21-neoforge.zip",
                "https://github.com/x/y/releases/download/v/pack-1.21-neoforge.zip");
        check("asset_url_template 占位符展开",
                url.contains("pack-1.21-neoforge.zip") && url.startsWith("https://ghproxy.net/https://github.com/"), url);

        // ---- 2. manifest 获取 + 冻结接口校验 ----
        Manifest manifest = ManifestFetcher.fetch(cacheDir, cnFirst, "1.21", "neoforge");
        check("manifest 获取成功", manifest != null && manifest.latest != null, "版本 " + manifest.latest.version);
        check("版本号格式 YYYY.MM.DD-NNN", Manifest.VERSION_PATTERN.matcher(manifest.latest.version).matches(),
                manifest.latest.version);
        check("清单缓存写入", Files.exists(cacheDir.resolve("manifest.json")), cacheDir.resolve("manifest.json").toString());

        Manifest.PackEntry pack = manifest.findPack("1.21", "neoforge");
        check("按版本组/加载器定位资源包", pack != null && "pack-1.21-neoforge.zip".equals(pack.assetName),
                pack == null ? "未找到" : pack.assetName + " md5=" + pack.md5);
        Manifest.VpPackEntry vpPack = manifest.findVpPack("1.21");
        check("vp_packages 为可选字段且可解析", vpPack != null, vpPack == null ? "null" : vpPack.assetName);
        check("校验器拒绝未知版本组", manifest.validate("9.9", "neoforge") != null, manifest.validate("9.9", "neoforge"));

        // ---- 3. 资源包下载 + MD5 校验 ----
        PackDownloader.Downloaded dl = PackDownloader.ensureResourcePack(cacheDir, pack, manifest.latest.version, cnFirst);
        Path packZip = dl.file();
        String md5 = MD5Util.fileMd5Hex(packZip);
        check("资源包下载并 MD5 校验通过", MD5Util.matches(pack.md5, md5), "实际 md5=" + md5 + " 大小=" + Files.size(packZip));
        check("缓存文件名 = asset_name", packZip.getFileName().toString().equals(pack.assetName), packZip.toString());

        // ---- 4. 二次调用命中缓存（不再下载） ----
        long t0 = System.currentTimeMillis();
        PackDownloader.Downloaded dl2 = PackDownloader.ensureResourcePack(cacheDir, pack, manifest.latest.version, cnFirst);
        long elapsed = System.currentTimeMillis() - t0;
        check("二次调用直接命中缓存", dl2.file().equals(packZip) && MD5Util.matches(pack.md5, MD5Util.fileMd5Hex(dl2.file())),
                "耗时 " + elapsed + "ms（无下载则应远小于下载耗时）");

        // ---- 5. 缓存损坏 → 检测不匹配 → 自动重新下载 ----
        Files.writeString(packZip, "corrupted-garbage", StandardCharsets.UTF_8);
        PackDownloader.Downloaded dl3 = PackDownloader.ensureResourcePack(cacheDir, pack, manifest.latest.version, cnFirst);
        String md5After = MD5Util.fileMd5Hex(dl3.file());
        check("损坏缓存被检测并重新下载", MD5Util.matches(pack.md5, md5After), "修复后 md5=" + md5After);
        check("无 .part 残留", !Files.exists(cacheDir.resolve(pack.assetName + ".part")), "");

        // ---- 6. VP：真实产物部署（汉化库现含真实模块） ----
        VPModuleLoader.Result vp1 = VPModuleLoader.deployIfNeeded(true, true, gameDir, cacheDir,
                manifest, "1.21", manifest.latest.version, cnFirst);
        check("VP 真实包部署（汉化库当前产物）", vp1.enabled() && vp1.deployed() && !vp1.moduleNames().isEmpty(),
                vp1.toString());
        VPModuleLoader.Result vpOff = VPModuleLoader.deployIfNeeded(false, true, gameDir, cacheDir,
                manifest, "1.21", manifest.latest.version, cnFirst);
        check("enableHardcodedPatch=false 完全跳过", !vpOff.enabled() && !vpOff.deployed(), vpOff.toString());

        // ---- 6b. VP：构造空包（无模块条目）→ 识别为空包且不报错 ----
        Path emptyZip = cacheDir.resolve("vp-empty-test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            zos.putNextEntry(new ZipEntry("vaultpatcher/modules/"));
            zos.closeEntry();
        }
        Manifest emptyManifest = new Manifest();
        emptyManifest.schemaVersion = 1;
        emptyManifest.latest = new Manifest.Latest();
        emptyManifest.latest.version = manifest.latest.version;
        Manifest.VpPackEntry emptyVp = new Manifest.VpPackEntry();
        emptyVp.mcVersion = "1.21";
        emptyVp.assetName = "vp-empty-test.zip";
        emptyVp.releaseUrl = "https://example.invalid/vp-empty-test.zip";
        emptyVp.md5 = MD5Util.fileMd5Hex(emptyZip);
        emptyManifest.latest.vpPackages = List.of(emptyVp);
        emptyManifest.latest.packages = manifest.latest.packages;
        VPModuleLoader.Result vpEmpty = VPModuleLoader.deployIfNeeded(true, true, gameDir, cacheDir,
                emptyManifest, "1.21", emptyManifest.latest.version, cnFirst);
        check("VP 空包容错（enabled=true）", vpEmpty.enabled() && vpEmpty.emptyPackage() && !vpEmpty.deployed(),
                vpEmpty.toString());

        // ---- 7. VP：构造真实模块包 → 解压到游戏根目录 + 更新 VP 配置 ----
        Path gameDir2 = base.resolve("game2");
        Files.createDirectories(gameDir2);
        Path fakeZip = cacheDir.resolve("vp-modules-1.21.zip");  // 覆盖空包缓存
        Files.writeString(fakeZip, "", StandardCharsets.UTF_8);  // 先清掉
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(fakeZip))) {
            zos.putNextEntry(new ZipEntry("vaultpatcher/modules/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("vaultpatcher/modules/testmod.json"));
            zos.write("[{\"name\":\"测试补丁\"}]".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("evil/../escape.txt"));  // 路径穿越防护
            zos.write("bad".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("readme.txt"));  // 非模块路径应被忽略
            zos.write("skip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        String fakeMd5 = MD5Util.fileMd5Hex(fakeZip);
        Manifest fakeManifest = new Manifest();
        fakeManifest.schemaVersion = 1;
        fakeManifest.latest = new Manifest.Latest();
        fakeManifest.latest.version = manifest.latest.version;
        Manifest.VpPackEntry fakeVp = new Manifest.VpPackEntry();
        fakeVp.mcVersion = "1.21";
        fakeVp.assetName = "vp-modules-1.21.zip";
        fakeVp.releaseUrl = "https://example.invalid/vp-modules-1.21.zip";
        fakeVp.md5 = fakeMd5;
        fakeManifest.latest.vpPackages = List.of(fakeVp);
        fakeManifest.latest.packages = manifest.latest.packages;

        VPModuleLoader.Result vp2 = VPModuleLoader.deployIfNeeded(true, true, gameDir2, cacheDir,
                fakeManifest, "1.21", fakeManifest.latest.version, cnFirst);
        check("VP 模块解压部署", vp2.deployed() && vp2.moduleNames().contains("testmod"), vp2.moduleNames().toString());
        check("VP 首次部署有变更（changed=true）", vp2.changed(), vp2.toString());
        VPModuleLoader.Result vp2b = VPModuleLoader.deployIfNeeded(true, true, gameDir2, cacheDir,
                fakeManifest, "1.21", fakeManifest.latest.version, cnFirst);
        check("VP 重复部署幂等（changed=false）", !vp2b.changed(), vp2b.toString());
        check("模块文件就位于 .minecraft/vaultpatcher/modules/",
                Files.exists(gameDir2.resolve("vaultpatcher/modules/testmod.json")), "");
        check("路径穿越条目被拦截", !Files.exists(gameDir2.resolve("escape.txt"))
                && !Files.exists(gameDir2.resolve("evil")), "");
        check("非模块路径不解压", !Files.exists(gameDir2.resolve("readme.txt")), "");
        Path vpConfig = gameDir2.resolve("config/vaultpatcher_asm/config.json");
        String cfgJson = Files.exists(vpConfig) ? Files.readString(vpConfig, StandardCharsets.UTF_8) : "";
        check("VP config.json 按定稿 schema 注册（仅 modules 字段，无冗余 mods）",
                cfgJson.contains("\"modules\"") && cfgJson.contains("\"testmod\"")
                        && !cfgJson.contains("\"mods\"")
                        && cfgJson.contains("\"load_all_modules\"") && cfgJson.contains("\"default_language\""),
                cfgJson.replace('\n', ' '));

        VPModuleLoader.Result vp3 = VPModuleLoader.deployIfNeeded(true, false, gameDir2, cacheDir,
                fakeManifest, "1.21", fakeManifest.latest.version, cnFirst);
        check("VP 未安装 → vpMissing=true（触发提醒）", vp3.vpMissing(), vp3.toString());

        // ---- 8. 清单回退：全部源不可用时用缓存 ----
        Sources.Source bogus = new Sources.Source();
        bogus.id = "bogus";
        bogus.name = "bogus";
        bogus.region = "cn";
        bogus.priority = 1;
        bogus.manifestUrl = "https://127.0.0.1:1/manifest.json";
        bogus.assetUrlTemplate = "https://127.0.0.1:1/{asset_name}";
        Manifest fallback = ManifestFetcher.fetch(cacheDir, List.of(bogus), "1.21", "neoforge");
        check("全源失败时回退缓存清单", fallback != null && fallback.latest.version.equals(manifest.latest.version),
                "回退版本 " + (fallback == null ? "null" : fallback.latest.version));

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过, " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
