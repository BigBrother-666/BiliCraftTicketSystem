package com.bigbrother.bilicraftticketsystem.commands;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.ImageUtils;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.UUID;

public class TicketbgCommand {
    private final BiliCraftTicketSystem plugin;
    // 背景界面玩家点击冷却
    private final HashMap<UUID, Long> uploadCooldowns = new HashMap<>();

    public TicketbgCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("上传车票/交通卡背景图片")
    @Command("ticket uploadbg <url> <bgName> [hexColorString]")
    @Permission("bcts.ticket.uploadbg")
    public void uploadBg(
            Player player,
            @Argument(value = "url", description = "图片地址") String url,
            @Argument(value = "bgName", description = "背景名，支持颜色代码") String bgName,
            @Nullable @Argument(value = "hexColorString", description = "车票地图的字体颜色，格式#RRGGBB，不填默认黑色") String hexColorString
    ) {
        subCommandUploadbg(player, url, bgName, hexColorString == null ? "#000000" : hexColorString, false);
    }

    @CommandDescription("以管理员身份上传共享的车票/交通卡背景图片")
    @Command("ticket adminuploadbg <url> <bgName> [hexColorString]")
    @Permission("bcts.ticket.adminuploadbg")
    public void adminUploadBg(
            Player player,
            @Argument(value = "url", description = "图片地址") String url,
            @Argument(value = "bgName", description = "背景名，支持颜色代码") String bgName,
            @Nullable @Argument(value = "hexColorString", description = "车票地图的字体颜色，格式#RRGGBB，不填默认黑色") String hexColorString
    ) {
        subCommandUploadbg(player, url, bgName, hexColorString == null ? "#000000" : hexColorString, true);
    }

    @CommandDescription("根据ID删除背景图")
    @Command("ticket deletebg <id>")
    @Permission("bcts.ticket.deletebg")
    public void deleteBg(
            CommandSender sender,
            @Argument(value = "id", description = "背景图的id") int id
    ) {
        if (plugin.getTrainDatabaseManager().getTicketbgService().deleteTicketbgLogical(id) > 0) {
            sender.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("车票背景图删除成功", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("无法删除，背景图id不存在", NamedTextColor.YELLOW)));
        }
    }

    private void subCommandUploadbg(Player player, String url, String bgName, String hexColorString, boolean isAdmin) {
        // 指令冷却时间10s
        long lastUsedTime = uploadCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long timeLeft = (lastUsedTime + 1000 * 10) - currentTime;
        if (timeLeft > 0) {
            double secondsLeft = timeLeft / 1000.0;
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传过于频繁，请过 %.1f 秒后再使用此命令！".formatted(secondsLeft), NamedTextColor.RED)));
            return;
        }
        uploadCooldowns.put(player.getUniqueId(), currentTime);

        // 验证16进制颜色合法
        try {
            CommonUtils.hexToColor(hexColorString);
        } catch (IllegalArgumentException e) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("16进制颜色代码不合法！格式：#RRGGBB", NamedTextColor.RED)));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File folder = ImageUtils.getPlayerTicketbgFolder(player);
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传车票背景图失败：创建文件夹失败", NamedTextColor.RED)));
                    return;
                }
            }

            // 检查上传数量是否达到最大
            if (!isAdmin && plugin.getTrainDatabaseManager().getTicketbgService().getPlayerTicketbgCount(player.getUniqueId().toString()) >= MenuTicketbg.getSelfbgMaxCnt()) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("最多上传 %d 个背景图，请先删除不需要的背景图再上传".formatted(MenuTicketbg.getSelfbgMaxCnt()), NamedTextColor.RED)));
                return;
            }

            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传背景图中...", NamedTextColor.AQUA)));
            String filePath = folder + File.separator + System.currentTimeMillis() + ".png";
            try {
                if (downloadAndSaveImage(url, player, filePath)) {
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("车票背景图上传成功！可使用/ticket bg指令管理上传的背景图", NamedTextColor.GREEN)));
                    // 数据库记录
                    String dbFilePath = filePath.replace(TrainCarts.plugin.getDataFile("images").toString(), "").substring(1);
                    if (isAdmin) {
                        plugin.getTrainDatabaseManager().getTicketbgService().addTicketbgInfo("[管理员]", null, bgName, dbFilePath, hexColorString);
                    } else {
                        plugin.getTrainDatabaseManager().getTicketbgService().addTicketbgInfo(player.getName(), player.getUniqueId().toString(), bgName, dbFilePath, hexColorString);
                    }
                }
            } catch (Exception e) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传车票背景图时发生错误：" + e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private boolean downloadAndSaveImage(String imageUrl, Player player, String filePath) {
        try {
            // 获取图片大小
            long contentLength = ImageUtils.getImageSize(imageUrl);
            if (contentLength == -1) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("无法获取图片大小！", NamedTextColor.RED)));
                return false;
            }

            // 检查图片大小（<= 1MB）
            if (contentLength > 1000 * 1024) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("图片大小不能超过1MB，当前大小：" + (contentLength / 1024) + " KB", NamedTextColor.RED)));
                return false;
            }

            // 接收文件
            byte[] imageBytes = ImageUtils.getImageBytes(imageUrl);

            // 转化图片
            try {
                imageBytes = ImageUtils.convertTo128x128(imageBytes);
                if (imageBytes == null) {
                    return false;
                }
            } catch (Exception e) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("图片格式不支持或图片损坏！错误信息：" + e.getMessage(), NamedTextColor.RED)));
                return false;
            }

            // 保存图片
            Files.write(Paths.get(filePath), imageBytes);
            return true;
        } catch (IOException e) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传或处理图片时发生错误：" + e.getMessage(), NamedTextColor.RED)));
            return false;
        }
    }
}
