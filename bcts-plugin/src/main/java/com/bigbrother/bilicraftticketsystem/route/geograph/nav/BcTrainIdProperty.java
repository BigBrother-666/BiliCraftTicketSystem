package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

import java.util.Optional;
import java.util.UUID;

/**
 * Web 侧使用的稳定列车唯一 id。
 */
public class BcTrainIdProperty implements ITrainProperty<String> {
    public static final BcTrainIdProperty INSTANCE = new BcTrainIdProperty();
    private static final String KEY = "bcTrainId";

    @Override
    public String getDefault() {
        return "";
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        if (config.contains(KEY)) {
            return Optional.ofNullable(config.get(KEY, String.class));
        }
        return Optional.empty();
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        if (value.isPresent()) {
            config.set(KEY, value.get());
        } else {
            config.remove(KEY);
        }
    }

    public static String read(MinecartGroup group) {
        if (group == null) {
            return "";
        }
        String id = group.getProperties().get(INSTANCE);
        return id == null ? "" : id;
    }

    public static String ensure(MinecartGroup group) {
        String id = read(group);
        if (!id.isEmpty()) {
            return id;
        }
        id = UUID.randomUUID().toString();
        write(group, id);
        return id;
    }

    public static void write(MinecartGroup group, String trainId) {
        if (group == null) {
            return;
        }
        group.getProperties().set(INSTANCE, trainId == null ? "" : trainId);
    }
}
