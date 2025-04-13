package com.bigbrother.bilicraftticketsystem.commands;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.RailwayRoutesConfig;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import lombok.Getter;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_NAME;
import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_UUID;

public class BCTicketSystemCommand implements CommandExecutor {
    private final BiliCraftTicketSystem plugin;
    // 背景界面玩家点击冷却
    private final HashMap<UUID, Long> uploadCooldowns = new HashMap<>();
    // 处于添加路径状态的玩家，uuid-routeid
    @Getter
    private final HashMap<UUID, String> addRouteMode = new HashMap<>();

    public BCTicketSystemCommand(final @NotNull BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
        this.plugin.getCommand("ticket").setExecutor(this);
        this.plugin.getCommand("ticket").setTabCompleter(new BCTicketSystemTabCompleter());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equals("reload")) {
            subCommandReload(commandSender);
            return true;
        }

        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(Component.text("该指令必须玩家执行", NamedTextColor.RED));
            return false;
        }
        Player player = ((Player) commandSender).getPlayer();
        if (player == null) {
            return false;
        }
        if (args.length == 0) {
            if (!player.hasPermission("bcts.ticket.open")) {
                return false;
            }
            // 打开购票界面
            MenuMain.getMenu(player).open();
        } else {
            switch (args[0]) {
                case "uploadbg" -> subCommandUploadbg(player, args, false);
                case "adminuploadbg" -> subCommandAdminUploadbg(player, args);
                case "deletebg" -> subCommandDeletebg(player, args);
                case "menuitem" -> subCommandMenuitem(player, args);
                case "nbt" -> subCommandNbt(player, args);
                case "font" -> subCommandFont(player);
                case "statistics" -> subCommandStatistics(player, args);
                case "co" -> subCommandCo(player, args);
                case "bg" -> subCommandBg(player);
                case "addroute" -> subCommandAddroute(player, args);
                case "delroute" -> subCommandDelroute(player, args);
            }
        }
        return true;
    }

    private void subCommandDelroute(Player player, String[] args) {
        if (!player.hasPermission("bcts.ticket.delroute")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }

        if (args.length > 1) {
            if (RailwayRoutesConfig.railwayRoutes.contains(args[1])) {
                if (!RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(args[1]), "").equals(player.getUniqueId().toString())) {
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("不能删除其他玩家添加的路线。", NamedTextColor.RED)));
                    return;
                }
                RailwayRoutesConfig.railwayRoutes.remove(args[1]);
                RailwayRoutesConfig.save();
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("成功删除路径 %s".formatted(args[1]), NamedTextColor.GREEN)));
            } else {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("不存在id为 %s 的路径".formatted(args[1]), NamedTextColor.RED)));
            }
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("指令格式错误，正确格式：/ticket delroute <routeid>", NamedTextColor.RED)));
        }
    }

    private void subCommandAddroute(Player player, String[] args) {
        if (!player.hasPermission("bcts.ticket.addroute")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }

        if (addRouteMode.containsKey(player.getUniqueId())) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("当前正在处于添加路线模式。", NamedTextColor.YELLOW)));
            return;
        }

        if (args.length > 1) {
            if (RailwayRoutesConfig.railwayRoutes.contains(args[1])) {
                if (!RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(args[1]), "").equals(player.getUniqueId().toString())) {
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("不能修改其他玩家添加的路线。", NamedTextColor.RED)));
                    return;
                }
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("开始修改路线 %s，所有输入不需要添加 / ".formatted(args[1]), NamedTextColor.GREEN)));
            } else {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("开始添加路线 %s，所有输入不需要添加 / ".formatted(args[1]), NamedTextColor.GREEN)));
            }
            addRouteMode.put(player.getUniqueId(), args[1]);
            RailwayRoutesConfig.railwayRoutes.set("%s.owner".formatted(args[1]), player.getUniqueId().toString());
            player.sendMessage(Component.text("step1: 请输入到站时的显示内容，可用占位符{station}，表示当前车站名：", NamedTextColor.AQUA));
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("指令格式错误，正确格式：/ticket addroute <routeid>", NamedTextColor.RED)));
        }
    }

    private void subCommandBg(Player player) {
        // 直接打开背景设置界面
        MenuTicketbg.getMenu(player).open();
    }

    private void subCommandAdminUploadbg(Player player, String[] args) {
        if (!player.hasPermission("bcts.ticket.adminuploadbg")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }
        subCommandUploadbg(player, args, true);
    }

    private void subCommandDeletebg(Player player, String[] args) {
        if (!player.hasPermission("bcts.ticket.deletebg")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }

        if (args.length > 1) {
            Integer bgId = null;
            try {
                bgId = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
            if (bgId != null && plugin.getTrainDatabaseManager().deleteTicketbgLogical(bgId) > 0) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("车票背景图删除成功", NamedTextColor.GREEN)));
            } else {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("无法删除，背景图id不存在", NamedTextColor.YELLOW)));
            }
        }
    }

    private void subCommandUploadbg(Player player, String[] args, boolean isAdmin) {
        if (!player.hasPermission("bcts.ticket.uploadbg")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }

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

        if (args.length > 2) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String imageUrl = args[1];
                File folder = Utils.getPlayerTicketbgFolder(player);
                if (!folder.exists()) {
                    if (!folder.mkdirs()) {
                        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传车票背景图失败：创建文件夹失败", NamedTextColor.RED)));
                        return;
                    }
                }

                // 检查上传数量是否达到最大
                if (!isAdmin && plugin.getTrainDatabaseManager().getPlayerTicketbgCount(player.getUniqueId().toString()) >= MenuTicketbg.getSelfbgMaxCnt()) {
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("最多上传 %d 个背景图，请先删除不需要的背景图再上传".formatted(MenuTicketbg.getSelfbgMaxCnt()), NamedTextColor.RED)));
                    return;
                }

                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传背景图中...", NamedTextColor.AQUA)));
                try {
                    String filePath = folder + File.separator + System.currentTimeMillis();
                    if (downloadAndSaveImage(imageUrl, filePath, player, args, isAdmin)) {
                        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("车票背景图上传成功！可使用/ticket bg指令管理上传的背景图", NamedTextColor.GREEN)));
                    }
                } catch (Exception e) {
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传车票背景图时发生错误：" + e.getMessage(), NamedTextColor.RED)));
                }
            });
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("指令格式错误，正确格式：/ticket uploadbg <图片链接> <自定义背景图名> [车票字体颜色(可选，格式#RRGGBB)]", NamedTextColor.RED)));
        }
    }

    private boolean downloadAndSaveImage(String imageUrl, String savePath, Player player, String[] args, boolean isAdmin) {
        String fontColor = "#000000";
        if (args.length > 3) {
            try {
                Utils.hexToColor(args[3]);
                fontColor = args[3];
            } catch (IllegalArgumentException e) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("16进制颜色代码不合法！格式：#RRGGBB", NamedTextColor.RED)));
                return false;
            }
        }

        try {
            // 获取图片大小
            long contentLength = getImageSize(imageUrl, player);
            if (contentLength == -1) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("无法获取图片大小！", NamedTextColor.RED)));
                return false;
            }

            // 检查图片大小（<= 500 KB）
            if (contentLength > 500 * 1024) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("图片大小不能超过 500 KB，当前大小：" + (contentLength / 1024) + " KB", NamedTextColor.RED)));
                return false;
            }

            // 接收文件
            byte[] imageBytes = getImageBytes(imageUrl);

            // 转化图片
            try {
                imageBytes = convertTo128x128(imageBytes, player);
                if (imageBytes == null) {
                    return false;
                }
            } catch (Exception e) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("图片格式不支持或图片损坏！错误信息：" + e.getMessage(), NamedTextColor.RED)));
                return false;
            }
            savePath += ".png";

            // 保存图片
            Files.write(Paths.get(savePath), imageBytes);

            // 数据库记录
            String itemName = args[2].trim();
            String dbFilePath = savePath.replace(TrainCarts.plugin.getDataFile("images").toString(), "").substring(1);
            if (isAdmin) {
                plugin.getTrainDatabaseManager().addTicketbgInfo("[管理员]", null, itemName, dbFilePath, fontColor);
            } else {
                plugin.getTrainDatabaseManager().addTicketbgInfo(player.getName(), player.getUniqueId().toString(), itemName, dbFilePath, fontColor);
            }
            return true;

        } catch (IOException e) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("上传或处理图片时发生错误：" + e.getMessage(), NamedTextColor.RED)));
            return false;
        }
    }

    private byte[] convertTo128x128(byte[] imageBytes, Player player) throws IOException {
        // 读取图片文件
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (originalImage == null) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("图片格式不支持或图片损坏！", NamedTextColor.RED)));
            return null;
        }

        // 获取原始图片的宽度和高度
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 检查尺寸
        if (originalWidth < 128 || originalHeight < 128) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("图片长或宽小于128像素！", NamedTextColor.RED)));
            return null;
        }

        // 计算缩放比例，缩放到最短边为128像素
        int targetSize = 128;
        int newWidth;
        int newHeight;

        // 按比例缩放，使长宽等比
        if (originalWidth > originalHeight) {
            newWidth = targetSize;
            newHeight = (int) (originalHeight * (targetSize / (double) originalWidth));
        } else {
            newHeight = targetSize;
            newWidth = (int) (originalWidth * (targetSize / (double) originalHeight));
        }

        // 创建一个缩放后的图片
        Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledBufferedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        // 将缩放后的图片绘制到新的 BufferedImage 中
        Graphics2D g2d = scaledBufferedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        // 创建一个 128x128 的透明背景图片
        BufferedImage finalImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();

        // 填充透明背景
        g.setColor(new Color(0, 0, 0, 0)); // 透明
        g.fillRect(0, 0, targetSize, targetSize);

        // 计算位置，使得缩放后的图像居中
        int xOffset = (targetSize - newWidth) / 2;
        int yOffset = (targetSize - newHeight) / 2;

        // 将缩放后的图片绘制到中心位置
        g.drawImage(scaledBufferedImage, xOffset, yOffset, null);
        g.dispose();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(finalImage, "PNG", byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }

    private long getImageSize(String imageUrl, Player player) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("无法获取图片信息，状态码：" + responseCode, NamedTextColor.RED)));
            return -1;
        }

        return conn.getContentLengthLong();
    }

    private byte[] getImageBytes(String imageUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = conn.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return outputStream.toByteArray();
    }

    private CoreProtectAPI getCoreProtectAPI() {
        Plugin pl = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
        if (!(pl instanceof CoreProtect)) {
            return null;
        }
        CoreProtectAPI CoreProtect = ((CoreProtect) pl).getAPI();
        if (!CoreProtect.isEnabled()) {
            return null;
        }
        return CoreProtect;
    }

    private void subCommandCo(Player player, @NotNull String[] args) {
        if (!player.hasPermission("bcts.ticket.co")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }

        if (args.length >= 3 && args[1].trim().equals("add")) {
            String[] split = args[2].split("-");
            if (split.length != 2) {
                player.sendMessage(Component.text("站台tag格式错误", NamedTextColor.RED));
                return;
            }

            CoreProtectAPI coreProtectAPI = getCoreProtectAPI();
            if (coreProtectAPI == null) {
                player.sendMessage(Component.text("未检测到CoreProtect插件！", NamedTextColor.RED));
                return;
            }

            Block targetBlock = player.getTargetBlockExact(5);
            if (targetBlock != null && (targetBlock.getType().toString().toUpperCase().endsWith("BUTTON") || targetBlock.getType().toString().toUpperCase().endsWith("FENCE_GATE"))) {
                int cnt = 0;
                List<String[]> resultList = coreProtectAPI.blockLookup(targetBlock, (int) (System.currentTimeMillis() / 1000L));
                List<String> dateTime = new ArrayList<>();
                for (String[] s : resultList) {
                    CoreProtectAPI.ParseResult parsed = coreProtectAPI.parseResult(s);
                    if (parsed.getActionId() == 2) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        sdf.setTimeZone(TimeZone.getDefault());
                        cnt += 1;
                        dateTime.add(sdf.format(new Timestamp(parsed.getTimestamp())));
                    }
                }
                plugin.getTrainDatabaseManager().addBcspawnInfo(args[2].trim(), dateTime);
                player.sendMessage(Component.text("成功添加 %d 条数据".formatted(cnt), NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("目标方块不是按钮或石质压力板！", NamedTextColor.RED));
            }
        }
    }

    private void subCommandStatistics(Player player, @NotNull String[] args) {
        if (!player.hasPermission("bcts.ticket.statistics")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("查询中...", NamedTextColor.AQUA));
        if (args.length < 3) {
            player.sendMessage(Component.text("指令格式有误，需要指定天数", NamedTextColor.RED));
            return;
        }
        int range;
        try {
            range = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("指令格式有误，需要指定天数", NamedTextColor.RED));
            return;
        }
        if (args[1].equals("ticket")) {
            player.sendMessage(plugin.getTrainDatabaseManager().getDailyRevenue(range));
        } else if (args[1].equals("bcspawn")) {
            player.sendMessage(plugin.getTrainDatabaseManager().getDailySpawn(range));
        } else {
            player.sendMessage(Component.text("指令格式有误", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("查询完成", NamedTextColor.AQUA));
    }

    private void subCommandFont(Player player) {
        if (!player.hasPermission("bcts.ticket.font")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        player.sendMessage(Component.text(String.join(", ", fontNames), NamedTextColor.GREEN));
    }

    private void subCommandNbt(Player player, @NotNull String[] args) {
        if (!player.hasPermission("bcts.ticket.nbt")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2) {
            // 验证主手物品是车票
            if (TicketStore.isTicketItem(player.getInventory().getItemInMainHand())) {
                CommonItemStack mainHandTicket = CommonItemStack.of(HumanHand.getItemInMainHand(player));
                CommonTagCompound nbt = mainHandTicket.getCustomData();
                String cleandTagString = args[1].trim();

                if (args.length > 2 && !args[2].trim().isEmpty()) {
                    // 用空格拼接参数
                    String updateValue = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                    // 更新nbt
                    if (cleandTagString.equals(KEY_TICKET_OWNER_NAME)) {
                        Player newOwner = Bukkit.getPlayer(updateValue);
                        if (newOwner == null) {
                            player.sendMessage(Component.text("此玩家不存在（不在线）", NamedTextColor.RED));
                            return;
                        } else {
                            mainHandTicket.updateCustomData(tag -> tag.putValue(cleandTagString, updateValue));
                            mainHandTicket.updateCustomData(tag -> tag.putValue(KEY_TICKET_OWNER_UUID, newOwner.getUniqueId()));
                        }
                    } else if (cleandTagString.equals(KEY_TICKET_OWNER_UUID)) {
                        if (args.length > 3 && args[2].trim().length() == 36) {
                            mainHandTicket.updateCustomData(tag -> tag.putValue(KEY_TICKET_OWNER_NAME, args[3]));
                            mainHandTicket.updateCustomData(tag -> tag.putValue(cleandTagString, UUID.fromString(args[2])));
                        } else {
                            player.sendMessage(Component.text("没有指定玩家名或uuid不符合格式", NamedTextColor.RED));
                        }
                    } else {
                        mainHandTicket.updateCustomData(tag -> tag.putValue(cleandTagString, updateValue));
                    }
                    player.sendMessage(Component.text("成功将 %s 的值更新为 %s".formatted(cleandTagString, updateValue), NamedTextColor.GREEN));
                } else {
                    // 输出nbt的值
                    String value = nbt.getValue(cleandTagString, "");
                    if (value != null && !value.isEmpty()) {
                        player.sendMessage(Component.text("%s 的值为 %s".formatted(cleandTagString, value), NamedTextColor.GREEN));
                    } else {
                        if (cleandTagString.equals(KEY_TICKET_OWNER_UUID)) {
                            UUID uuid = nbt.getUUID(cleandTagString);
                            if (uuid != null) {
                                player.sendMessage(Component.text("%s 的值为 %s".formatted(cleandTagString, uuid.toString()), NamedTextColor.GREEN));
                                return;
                            }
                        }
                        player.sendMessage(Component.text("此车票没有 %s".formatted(cleandTagString), NamedTextColor.RED));
                    }
                }

            } else {
                player.sendMessage(Component.text("手持的物品不是车票！", NamedTextColor.RED));
            }

        } else {
            player.sendMessage(Component.text("指令格式有误", NamedTextColor.RED));
        }
    }

    private void subCommandReload(CommandSender commandSender) {
        if (!commandSender.hasPermission("bcts.ticket.reload")) {
            commandSender.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }
        if (commandSender instanceof ConsoleCommandSender) {
            plugin.getComponentLogger().info(Component.text("配置文件重载中...", NamedTextColor.GOLD));
        } else {
            commandSender.sendMessage(Component.text("配置文件重载中...", NamedTextColor.GOLD));
            plugin.getComponentLogger().info(Component.text("配置文件重载中...", NamedTextColor.GOLD));
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, sender -> plugin.loadConfig(commandSender));
    }

    private void subCommandMenuitem(Player player, @NotNull String[] args) {
        if (!player.hasPermission("bcts.ticket.menuitem")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }
        if (args.length > 2 && args[1].equals("add")) {
            Utils.saveItemToFile(args[2], player.getInventory().getItemInMainHand());
            player.sendMessage(Component.text("成功保存物品" + args[2], NamedTextColor.GOLD));
        } else if (args.length > 2 && args[1].equals("get")) {
            ItemStack itemStack = Utils.loadItemFromFile(args[2]);
            if (itemStack.getType() == Material.AIR) {
                player.sendMessage(Component.text("物品不存在", NamedTextColor.RED));
                return;
            }
            player.getInventory().addItem(itemStack);
            player.sendMessage(Component.text("成功获取物品" + args[2], NamedTextColor.GOLD));
        } else {
            player.sendMessage(Component.text("指令格式有误", NamedTextColor.RED));
        }
    }
}
