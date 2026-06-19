package com.bigbrother.bilicraftticketsystem.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置文件文字字符串的统一解析器。
 * <p>
 * 所有从配置文件读取的展示文本都应经由本类的 {@link #toComponent(String)} 解析，
 * 它<b>同时支持 MiniMessage 标签与 legacy &amp; 颜色代码</b>：先把 {@code &} 代码
 * （含 {@code &#RRGGBB} 十六进制）翻译成等价的 MiniMessage 标签，保留原有 MiniMessage
 * 标签不动，再交给 MiniMessage 解析一次。这样一个字符串里可以混用两种写法。
 */
public final class PlaceholderParser {
    private PlaceholderParser() {
    }

    private static final Pattern PATTERN =
            Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    /**
     * legacy &amp; 颜色 / 样式代码到 MiniMessage 标签的映射。
     */
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    /**
     * 把含 legacy 代码的字符串翻译成 MiniMessage 写法（已有的 MiniMessage 标签原样保留）。
     * <ul>
     *   <li>{@code &#RRGGBB} → {@code <#RRGGBB>}（十六进制颜色，仅 {@code &} 前缀支持）</li>
     *   <li>{@code &a} … {@code &f} / {@code &0} … {@code &9} → 对应颜色标签</li>
     *   <li>{@code &l &m &n &o &k} → 样式标签，{@code &r} → {@code <reset>}</li>
     *   <li>{@code §}（section sign）前缀等同 {@code &}，覆盖 Bukkit 既有上色文本</li>
     *   <li>不是合法代码的孤立前缀字符原样保留</li>
     * </ul>
     */
    public static String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty() || (text.indexOf('&') < 0 && text.indexOf('§') < 0)) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            // & 与 §（section sign）都作为 legacy 颜色前缀处理
            boolean isPrefix = c == '&' || c == '§';
            if (!isPrefix || i + 1 >= n) {
                sb.append(c);
                i++;
                continue;
            }
            char next = text.charAt(i + 1);
            // &#RRGGBB 十六进制（§ 不支持此写法，仅 & 前缀）
            if (c == '&' && next == '#' && i + 8 <= n && isHex(text, i + 2, i + 8)) {
                sb.append("<#").append(text, i + 2, i + 8).append('>');
                i += 8;
                continue;
            }
            String tag = LEGACY_TAGS.get(Character.toLowerCase(next));
            if (tag != null) {
                sb.append(tag);
                i += 2;
                continue;
            }
            // 非合法代码，原样保留前缀字符
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static boolean isHex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * 配置文字字符串 → Component，同时支持 MiniMessage 与 legacy &amp; 代码。
     *
     * @param text 配置文本（可混用两种写法）
     * @return 解析后的 Component
     */
    public static Component toComponent(String text) {
        if (text == null) {
            return Component.empty();
        }
        return CommonUtils.miniMessage().deserialize(legacyToMiniMessage(text));
    }

    public static String apply(String text, Map<String, ?> values) {
        Matcher matcher = PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.get(key.toLowerCase());

            matcher.appendReplacement(
                    sb,
                    value == null
                            ? CommonUtils.NOT_AVAILABLE_MM
//                            ? matcher.group(0)
                            : Matcher.quoteReplacement(value.toString())
            );
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    public static List<Component> parse(
            List<String> template,
            Map<String, Object> placeholders
    ) {
        List<Component> result = new ArrayList<>();

        for (String line : template) {

            String key = line.trim();

            // 多行 lore 占位符
            if (key.startsWith("{") && key.endsWith("}")) {
                Object value = placeholders.get(
                        key.substring(1, key.length() - 1)
                );

                if (value instanceof List<?> list) {
                    for (Object obj : list) {
                        if (obj instanceof Component c) {
                            result.add(c);
                        }
                    }
                    continue;
                }
            }

            // 普通文本占位符
            String parsed = PlaceholderParser.apply(line, placeholders);
            result.add(toComponent(parsed).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }

        return result;
    }
}
