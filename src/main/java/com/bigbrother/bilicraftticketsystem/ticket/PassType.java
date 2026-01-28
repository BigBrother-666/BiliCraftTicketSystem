package com.bigbrother.bilicraftticketsystem.ticket;

import java.util.Optional;

public enum PassType {
    NONE("none"),
    TICKET("ticket"),
    CARD("card");

    private final String id;

    PassType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static Optional<PassType> fromId(String id) {
        if (id == null) return Optional.empty();
        for (PassType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}