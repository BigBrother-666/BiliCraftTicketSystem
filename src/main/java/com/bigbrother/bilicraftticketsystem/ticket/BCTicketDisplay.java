package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import org.bukkit.inventory.ItemStack;

import java.awt.*;
import java.io.File;
import java.io.IOException;
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
    }

    @Override
    public void onMapItemChanged() {
        this.renderTicket();
    }

    public void renderBackground() {
        Ticket ticket = TicketStore.getTicketFromItem(this.getMapItem());
        MapTexture bg;
        if (ticket == null) {
            bg = Ticket.getDefaultBackgroundImage();
        } else {
            bg = ticket.loadBackgroundImage();
        }
        this.getLayer().draw(bg, 0, 0);
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
}

