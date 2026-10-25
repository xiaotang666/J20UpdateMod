package com.xiaotang.j20updatemod.lang;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 语言检测器（文档 3.2.4）：决定下载源优先级。
 * 优先用游戏 API 读取当前语言；不可用时依次回退 options.txt 与 JVM 默认区域。
 * 注意：1.21.1 的 LanguageManager.getSelected() 直接返回语言代码字符串（旧文档写法 .getCode() 已过时）。
 */
public final class LanguageDetector {

    private LanguageDetector() {
    }

    /** 返回语言代码（如 zh_cn），保证非 null。 */
    public static String detect(Path gameDir) {
        // 1) 游戏 API（若 Minecraft 尚未初始化则跳过）
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                try {
                    Object languageManager = mc.getLanguageManager();
                    if (languageManager != null) {
                        // 反射取值：getSelected() 的返回类型随 MC 版本变化
                        // （1.21.x 直接返回 String；1.20.x 返回 LanguageInfo 需 getCode()；其他版本未知）
                        Object selected = languageManager.getClass().getMethod("getSelected").invoke(languageManager);
                        String code = extractCode(selected);
                        if (code != null) {
                            return normalize(code);
                        }
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (mc.options != null && mc.options.languageCode != null && !mc.options.languageCode.isEmpty()) {
                        return normalize(mc.options.languageCode);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) options.txt
        String fromOptions = readOptionsTxt(gameDir);
        if (fromOptions != null) {
            return fromOptions;
        }

        // 3) JVM 默认区域（zh_CN → zh_cn）
        return normalize(Locale.getDefault().toString());
    }

    /** 从 getSelected() 的返回值提取语言代码：String 直接用；对象尝试 getCode()/code()。 */
    private static String extractCode(Object selected) {
        if (selected instanceof String s) {
            return s.isEmpty() ? null : s;
        }
        if (selected == null) {
            return null;
        }
        for (String method : new String[]{"getCode", "code"}) {
            try {
                Object value = selected.getClass().getMethod(method).invoke(selected);
                if (value instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 是否为简体中文。 */
    public static boolean isSimplifiedChinese(String code) {
        return "zh_cn".equalsIgnoreCase(normalize(code));
    }

    private static String normalize(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String readOptionsTxt(Path gameDir) {
        try {
            Path options = gameDir.resolve("options.txt");
            if (!Files.exists(options)) {
                return null;
            }
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (line.startsWith("language:")) {
                    String code = line.substring("language:".length()).trim();
                    if (!code.isEmpty()) {
                        return normalize(code);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
