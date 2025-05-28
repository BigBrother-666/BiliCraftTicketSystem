package com.bigbrother.bilicraftticketsystem.database.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
public class BcspawnInfo {
    private String spawnStation;
    private String spawnDirection;
    private String spawnRailway;
    private String tag;
    private Integer coordX;
    private Integer coordY;
    private Integer coordZ;
    private String world;

    @Nullable
    public Location getLocation() {
        World world1 = Bukkit.getWorld(world);
        if (world1 != null) {
            return new Location(world1, coordX, coordY, coordZ);
        } else {
            return null;
        }
    }

    public int getFixedY() {
        return coordY + 3;
    }

    @Nullable
    public Location getFixedLocation() {
        World world1 = Bukkit.getWorld(world);
        if (world1 != null) {
            return new Location(world1, coordX, this.getFixedY(), coordZ);
        } else {
            return null;
        }
    }
}
