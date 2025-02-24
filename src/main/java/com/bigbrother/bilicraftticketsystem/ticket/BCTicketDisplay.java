package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BCTicketDisplay extends MapDisplay {
    private static MapFont<Character> mapFont;

    public static void loadFont() {
        Logger logger = BiliCraftTicketSystem.plugin.getLogger();
        if (MainConfig.ticketFont != null && !MainConfig.ticketFont.isEmpty()) {
            mapFont = MapFont.fromJavaFont(MainConfig.ticketFont, MainConfig.ticketFontBold ? Font.BOLD : Font.PLAIN, 9);
        } else {
            File directory = BiliCraftTicketSystem.plugin.getDataFolder();
            if (directory.exists() && directory.isDirectory()) {
                // 获取文件夹中的所有 .ttf 文件
                File[] fontFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".ttf") || name.toLowerCase().endsWith(".ttc"));

                if (fontFiles != null && fontFiles.length > 0) {
                    try {
                        // 加载字体
                        Font customFont = Font.createFont(Font.TRUETYPE_FONT, fontFiles[0]);
                        customFont = customFont.deriveFont(9f); // 设置字体大小
                        customFont.deriveFont(MainConfig.ticketFontBold ? Font.BOLD : Font.PLAIN);

                        // 注册字体到 GraphicsEnvironment
                        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                        ge.registerFont(customFont);

                        mapFont = MapFont.fromJavaFont(customFont);
                        logger.log(Level.INFO, "成功加载字体: " + customFont.getFontName());
                    } catch (IOException | FontFormatException e) {
                        logger.log(Level.WARNING, "加载字体失败: " + e.getMessage());
                        logger.log(Level.WARNING, "使用默认字体");
                        mapFont = MapFont.MINECRAFT;
                    }
                } else {
                    logger.log(Level.WARNING, "使用默认字体");
                    mapFont = MapFont.MINECRAFT;
                }
            } else {
                logger.log(Level.WARNING, "使用默认字体");
                mapFont = MapFont.MINECRAFT;
            }
        }
    }

    @Override
    public void onAttached() {
        this.setSessionMode(MapSessionMode.VIEWING);
        this.setGlobal(false);

        renderBackground();
        renderTicket();
        updateTicketTag();
    }

    @Override
    public void onMapItemChanged() {
        this.renderTicket();
    }

    public void renderBackground() {
        CommonTagCompound ticketNbt = this.getCommonMapItem().getCustomData();
        MapTexture bg = loadBackgroundImage(ticketNbt.getValue(BCTicket.KEY_TICKET_BACKGROUND_IMAGE_PATH, ""));
        this.getLayer().draw(bg, 0, 0);
    }

    private void updateTicketTag() {
        CommonItemStack ticket = this.getCommonMapItem();
        CommonTagCompound ticketNbt = ticket.getCustomData();
        if (!ticketNbt.containsKey(BCTicket.KEY_TICKET_VERSION)) {
            return;
        }
        int version = ticketNbt.getValue(BCTicket.KEY_TICKET_VERSION, Integer.class, null);
        // 版本一样，不需要更新
        if (version == MainConfig.expressTicketVersion) {
            return;
        }
        List<String> tags = List.of(ticketNbt.getValue(BCTicket.KEY_TICKET_TAGS, "").split(","));
        double distance = ticketNbt.getValue(BCTicket.KEY_TICKET_DISTANCE, 0.0);
        String startStation = ticketNbt.getValue(BCTicket.KEY_TICKET_START_STATION, String.class, "");
        String endStation = ticketNbt.getValue(BCTicket.KEY_TICKET_END_STATION, String.class, "");
        String startPlatformTag = ticketNbt.getValue(BCTicket.KEY_TICKET_START_PLATFORM_TAG, String.class, "");
        List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(startStation, endStation);
        if (!pathInfoList.isEmpty()) {
            for (TrainRoutes.PathInfo pathInfo : pathInfoList) {
                // 距离相同且新车票包含所有旧车票的tag 则认为是同一路线的车票
                if (Math.abs(pathInfo.getDistance() - distance) < 1e-3 && pathInfo.getTags().containsAll(tags)) {
                    if (!startStation.equals(endStation) && !startPlatformTag.equals(pathInfo.getStartPlatformTag())) {
                        // 线路延长，xxx方向改变
                        ticket.updateCustomData(tag -> tag.putValue(BCTicket.KEY_TICKET_START_PLATFORM_TAG, pathInfo.getStartPlatformTag()));
                        ticket.toBukkit().lore(BCTicket.getBaseTicketInfoLore(pathInfo, ticketNbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0)));
                        startPlatformTag = pathInfo.getStartPlatformTag();
                    }

                    if (pathInfo.getTags().size() != tags.size() && startPlatformTag.equals(pathInfo.getStartPlatformTag())) {
                        // 新增车站
                        ticket.updateCustomData(tag -> tag.putValue(BCTicket.KEY_TICKET_TAGS, String.join(",", pathInfo.getTags())));
                        ticket.toBukkit().lore(BCTicket.getBaseTicketInfoLore(pathInfo, ticketNbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0)));
                    }
                    break;
                }
            }
        }
        ticket.updateCustomData(tag -> tag.putValue(BCTicket.KEY_TICKET_VERSION, MainConfig.expressTicketVersion));
    }

    private void renderTicket() {
        this.getLayer(1).clear();

        ItemStack ticketItem = this.getMapItem();
        CommonTagCompound customData = this.getCommonMapItem().getCustomData();
        String displayName = customData.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME, MainConfig.expressTicketName);
        if (ticketItem == null) {
            this.getLayer(1).draw(MapFont.MINECRAFT, 5, 40, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_INVALID.get());
        } else {
            this.getLayer(1).draw(mapFont, 5, 35, MapColorPalette.COLOR_BLACK, displayName);
            if (TicketStore.isTicketExpired(ticketItem)) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 5, 57, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_EXPIRED.get());
            } else {
                int maxUses = customData.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0);
                int numUses = (maxUses == 1) ? 0 : TicketStore.getNumberOfUses(ticketItem);
                if (maxUses < 0) {
                    maxUses = -1; // Just in case, so it works properly with Localization
                }
                String text = Localization.TICKET_MAP_USES.get(Integer.toString(maxUses), Integer.toString(numUses));
                this.getLayer(1).draw(MapFont.MINECRAFT, 5, 57, MapColorPalette.COLOR_BLACK, text);
            }

            String ownerName = customData.getValue("ticketOwnerName", "Unknown Owner");
            ownerName = StringUtil.stripChatStyle(ownerName);
            if (TicketStore.isTicketOwner(this.getOwners().get(0), ticketItem)) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 5, 74, MapColorPalette.COLOR_BLACK, ownerName);
            } else {
                this.getLayer(1).draw(MapFont.MINECRAFT, 5, 74, MapColorPalette.COLOR_RED, ownerName);
            }
        }
    }

    public MapTexture loadBackgroundImage(String backgroundImagePath) {
        if (!backgroundImagePath.isEmpty()) {
            int index = backgroundImagePath.indexOf(58);
            MapTexture bg;
            if (index != -1) {
                String pluginName = backgroundImagePath.substring(0, index);
                Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
                if (plugin instanceof JavaPlugin) {
                    try {
                        bg = MapTexture.loadPluginResource((JavaPlugin) plugin, backgroundImagePath.substring(index + 1));
                        if (bg.getWidth() >= 128 && bg.getHeight() >= 128) {
                            return bg;
                        }
                    } catch (RuntimeException ignored) {
                    }

                    return Ticket.getDefaultBackgroundImage();
                }
            }

            File imagesDir = TrainCarts.plugin.getDataFile("images");
            File imageFile = new File(backgroundImagePath);
            if (!imageFile.isAbsolute()) {
                imageFile = new File(imagesDir, backgroundImagePath);
            }

            if (!TCConfig.allowExternalTicketImagePaths) {
                boolean validLocation;
                try {
                    File a = imageFile.getAbsoluteFile().getCanonicalFile();
                    File b = imagesDir.getAbsoluteFile().getCanonicalFile();
                    validLocation = a.toPath().startsWith(b.toPath());
                } catch (IOException var9) {
                    validLocation = false;
                }

                if (!validLocation) {
                    return Ticket.getDefaultBackgroundImage();
                }
            }

            try {
                bg = MapTexture.fromImageFile(imageFile.getAbsolutePath());
                if (bg.getWidth() >= 128 && bg.getHeight() >= 128) {
                    return bg;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return Ticket.getDefaultBackgroundImage();
    }
}

