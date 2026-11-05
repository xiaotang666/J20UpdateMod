package com.xiaotang.j20updatemod.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * options.txt 自动勾选（一次性）：新安装时把汉化包追加到 resourcePacks 末尾（不抢覆盖优先级）。
 * 仅首次发布触发；用户之后的取消/挪动由游戏正常持久化，模组不再干预。
 */
public final class OptionsSelector {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    private OptionsSelector() {
    }

    /** 首次发布时调用：立即写一次，退出时兜底再写一次（防游戏退出时用内存设置覆盖文件）。 */
    public static void autoSelectOnce(Path gameDir, String packId, boolean firstPublish) {
        if (!firstPublish) {
            return;
        }
        appendToOptionsTxt(gameDir, packId);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                appendToOptionsTxt(gameDir, packId);
            } catch (Throwable ignored) {
            }
        }, "j20-options-hook"));
    }

    /** 把 packId 追加到 options.txt 的 resourcePacks 数组末尾（文件里已有则不动）。 */
    static void appendToOptionsTxt(Path gameDir, String packId) {
        try {
            Path options = gameDir.resolve("options.txt");
            if (!Files.exists(options)) {
                LOGGER.warn("[j20] options.txt 不存在，跳过自动勾选");
                return;
            }
            String content = Files.readString(options, StandardCharsets.UTF_8);
            if (content.contains(packId)) {
                return;
            }
            int lineStart = content.indexOf("resourcePacks:[");
            if (lineStart < 0) {
                LOGGER.warn("[j20] options.txt 无 resourcePacks 行，跳过自动勾选");
                return;
            }
            int bracket = content.indexOf(']', lineStart);
            if (bracket < 0) {
                return;
            }
            String insert = content.charAt(bracket - 1) == '[' ? "\"" + packId + "\"" : ",\"" + packId + "\"";
            String newContent = content.substring(0, bracket) + insert + content.substring(bracket);
            Files.writeString(options, newContent, StandardCharsets.UTF_8);
            LOGGER.info("[j20] 已自动勾选汉化资源包（追加到已选列表末尾）: {}", packId);
        } catch (Throwable t) {
            LOGGER.warn("[j20] options.txt 自动勾选失败: {}", t.toString());
        }
    }
}
