package com.bigbrother.bilicraftticketsystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Utils {
    public static Component str2Component(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
