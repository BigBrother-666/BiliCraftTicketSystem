package com.bigbrother.bilicraftticketsystem.addon.geodata.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.util.Vector;

@Getter
@RequiredArgsConstructor
public class GeoManualLine {
    private final String platformTag;
    private final String lineType;
    private final Location startLocation;
    private final Vector startDirection;
    private final Location endLocation;
}