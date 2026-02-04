package com.bigbrother.bilicraftticketsystem.ticket;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderParser {
    private static final Pattern PATTERN =
            Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    public static String apply(String text, Map<String, ?> values) {
        Matcher matcher = PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.get(key);

            matcher.appendReplacement(
                    sb,
                    value == null
                            ? PlayerOption.NOT_AVALIABLE_MM
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
            result.add(CommonUtils.mmStr2Component(parsed).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }

        return result;
    }
}