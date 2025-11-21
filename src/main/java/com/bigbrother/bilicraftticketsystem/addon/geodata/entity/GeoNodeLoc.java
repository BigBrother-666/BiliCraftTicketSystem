package com.bigbrother.bilicraftticketsystem.addon.geodata.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.util.Vector;

@Getter
@RequiredArgsConstructor
public class GeoNodeLoc {
    private final String platformTag;
    private final Location startLocation;
    private final Vector startDirection;
}
