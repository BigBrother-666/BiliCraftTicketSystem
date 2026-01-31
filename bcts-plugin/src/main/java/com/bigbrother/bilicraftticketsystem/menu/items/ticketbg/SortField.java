package com.bigbrother.bilicraftticketsystem.menu.items.ticketbg;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SortField {
    UPLOAD_TIME("upload_time"),
    USAGE_COUNT("usage_count");

    private final String field;
}
