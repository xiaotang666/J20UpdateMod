package com.xiaotang.j20updatemod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置：.minecraft/config/j20update.json（字段与默认值遵循文档 3.3.2）。
 */
public class J20Config {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "j20update.json";

    /** 是否启用硬编码补丁（Vault Patcher 模块部署）。 */
    @SerializedName("enableHardcodedPatch")
    public boolean enableHardcodedPatch = false;

    /** VP 未安装时是否提示用户安装。 */
    @SerializedName("showVPInstallReminder")
    public boolean showVPInstallReminder = true;

    /** 读取配置，文件不存在时写入默认配置。解析失败返回默认值并保留原文件。 */
    public static J20Config load(Path gameDir) {
        Path file = gameDir.resolve("config").resolve(FILE_NAME);
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                J20Config cfg = GSON.fromJson(json, J20Config.class);
                if (cfg != null) {
                    LOGGER.info("[j20] 已读取配置 {}", file);
                    return cfg;
                }
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("[j20] 配置文件解析失败，使用默认配置: {}", e.toString());
            }
            return new J20Config();
        }

        J20Config cfg = new J20Config();
        cfg.save(gameDir);
        return cfg;
    }

    /** 写回配置文件（config 目录不存在时自动创建）。 */
    public void save(Path gameDir) {
        Path file = gameDir.resolve("config").resolve(FILE_NAME);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
            LOGGER.info("[j20] 已写入默认配置 {}", file);
        } catch (IOException e) {
            LOGGER.warn("[j20] 配置文件写入失败 {}: {}", file, e.toString());
        }
    }
}
