package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.map.*;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.UUID;


public class BCTicketDisplay extends MapDisplay {
    private static MapFont<Character> mapFont;

    public static void loadFont() {
        mapFont = null;
        ComponentLogger logger = BiliCraftTicketSystem.plugin.getComponentLogger();

        String fontName = MainConfig.font.get("name", "");
        String fontFileName = MainConfig.font.get("font-file", "");
        Float fontSize = MainConfig.font.get("size", 8f);
        int fontStyle = MainConfig.font.get("bold", false) ? Font.BOLD : Font.PLAIN;

        if (!fontName.isEmpty()) {
            mapFont = MapFont.fromJavaFont(fontName, fontStyle, fontSize.intValue());
            logger.info(Component.text("成功加载字体: " + fontName, NamedTextColor.GOLD));
        } else if (!fontFileName.isEmpty()) {
            File fontFile = new File(BiliCraftTicketSystem.plugin.getDataFolder(), fontFileName);
            if (fontFile.exists() && fontFile.isFile()) {
                try {
                    // 加载字体
                    Font customFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
                    customFont = customFont.deriveFont(fontStyle, fontSize);

                    // 注册字体到 GraphicsEnvironment
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    ge.registerFont(customFont);

                    mapFont = MapFont.fromJavaFont(customFont);
                    logger.info(Component.text("成功加载字体: " + customFont.getFontName(), NamedTextColor.GOLD));
                } catch (IOException | FontFormatException e) {
                    logger.warn(Component.text("加载字体失败: " + e.getMessage(), NamedTextColor.RED));
                }
            }
        }
        if (mapFont == null) {
            logger.info(Component.text("使用默认字体", NamedTextColor.GOLD));
            mapFont = MapFont.MINECRAFT;
        }
    }

    @Override
    public void onAttached() {
        this.setSessionMode(MapSessionMode.VIEWING);
        this.setGlobal(false);
        this.setUpdateWithoutViewers(false);

        renderBackground();
        renderTicket();
    }

    @Override
    public void onMapItemChanged() {
        this.renderTicket();
    }

    public void renderBackground() {
        CommonTagCompound ticketNbt = this.getCommonMapItem().getCustomData();
        UUID bgUuid = ticketNbt.getValue(BCTicket.KEY_TICKET_OWNER_UUID, this.getOwners().get(0).getUniqueId());
        TicketbgInfo ticketbgInfo = BiliCraftTicketSystem.plugin.getTrainDatabaseManager().getTicketbgService().getCurrentTicketbgInfo(bgUuid.toString());
        MapTexture bg;

        if (ticketbgInfo != null) {
            bg = loadBackgroundImage(ticketbgInfo.getFilePath());
        } else {
            bg = loadBackgroundImage(ticketNbt.getValue(BCTicket.KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH, ""));
        }
        this.getLayer().draw(bg, 0, 0);
    }

    public void renderTicket() {
        if (this.getOwners().isEmpty()) {
            // ???
            return;
        }
        Player owner = this.getOwners().get(0);
        this.getLayer(1).clear();
        BCTransitPass bcTransitPass = BCTransitPassFactory.fromItemStack(this.getMapItem());
        CommonTagCompound customData = this.getCommonMapItem().getCustomData();
        UUID bgUuid = customData.getValue(BCTicket.KEY_TICKET_OWNER_UUID, owner.getUniqueId());

        // 获取字体颜色
        TicketbgInfo ticketbgInfo = BiliCraftTicketSystem.plugin.getTrainDatabaseManager().getTicketbgService().getCurrentTicketbgInfo(bgUuid.toString());
        Color c;
        if (ticketbgInfo != null) {
            try {
                c = CommonUtils.hexToColor(ticketbgInfo.getFontColor());
            } catch (IllegalArgumentException e) {
                c = Color.black;
            }
        } else {
            c = Color.black;
        }


        if (bcTransitPass == null) {
            this.getLayer(1).draw(mapFont, 5, 40, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_INVALID.get());
        } else {
            String displayName = bcTransitPass.getTransitPassName();
            this.getLayer(1).draw(mapFont, 5, 35, MapColorPalette.getColor(c), displayName);
            if (bcTransitPass instanceof BCTicket ticket) {
                if (ticket.isTicketExpired()) {
                    this.getLayer(1).draw(MapFont.MINECRAFT, 5, 57, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_EXPIRED.get());
                } else {
                    int maxUses = customData.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0);
                    int numUses = (maxUses == 1) ? 0 : customData.getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0);
                    if (maxUses < 0) {
                        maxUses = -1; // Just in case, so it works properly with Localization
                    }
                    String text = Localization.TICKET_MAP_USES.get(Integer.toString(maxUses), Integer.toString(numUses));
                    this.getLayer(1).draw(MapFont.MINECRAFT, 5, 57, MapColorPalette.getColor(c), text);
                }

                String ownerName = customData.getValue(BCTicket.KEY_TICKET_OWNER_NAME, "Unknown Owner");
                ownerName = StringUtil.stripChatStyle(ownerName);
                if (ticket.isTicketOwner(owner)) {
                    this.getLayer(1).draw(MapFont.MINECRAFT, 5, 74, MapColorPalette.getColor(c), ownerName);
                } else {
                    this.getLayer(1).draw(MapFont.MINECRAFT, 5, 74, MapColorPalette.COLOR_RED, ownerName);
                }
            } else if (bcTransitPass instanceof BCCard card) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 5, 57, MapColorPalette.getColor(c), "balance: %.2f".formatted(card.getCardInfo().getBalance()));
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

