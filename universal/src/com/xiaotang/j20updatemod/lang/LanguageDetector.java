package com.xiaotang.j20updatemod.lang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语言检测器（纯 Java）：决定下载源优先级。
 * 读取 options.txt 的 lang: 行（游戏持久化的权威语言设置），回退 JVM 默认区域。
 */
public final class LanguageDetector {

    private static final Pattern LANG_LINE = Pattern.compile("^lang:(.+)$");

    private LanguageDetector() {
    }

    /** 返回语言代码（如 zh_cn），保证非 null。 */
    public static String detect(Path gameDir) {
        try {
            for (String line : Files.readAllLines(gameDir.resolve("options.txt"), StandardCharsets.UTF_8)) {
                Matcher m = LANG_LINE.matcher(line.trim());
                if (m.matches()) {
                    return normalize(m.group(1));
                }
            }
        } catch (IOException ignored) {
        }
        return normalize(Locale.getDefault().toString());
    }

    /** 是否简体中文。 */
    public static boolean isSimplifiedChinese(String langCode) {
        return langCode != null && (langCode.startsWith("zh_cn") || langCode.startsWith("zh-hans"));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
