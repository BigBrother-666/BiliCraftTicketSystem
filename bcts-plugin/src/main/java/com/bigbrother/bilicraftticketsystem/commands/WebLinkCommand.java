package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.web.WebLink;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * 网页对接相关指令（见 docs/PLUGIN_ADDENDUM.md §七、§七·补）：
 * <ul>
 *   <li>玩家：{@code /ticket weblogin bind|unbind|status} 绑定 / 解绑网页登录资格。</li>
 *   <li>管理：{@code /ticketadmin weblink status|sync|reconnect} 查看连接 / 强制同步 / 重连。</li>
 * </ul>
 */
public class WebLinkCommand {
    private final BiliCraftTicketSystem plugin;

    public WebLinkCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("绑定网页登录资格（需服务器白名单玩家）")
    @Command("ticket weblogin bind")
    @Permission("bcts.ticket.weblogin")
    public void bind(Player player) {
        if (!MapConfig.isEnabled()) {
            player.sendMessage(Component.text("本服未启用web功能", NamedTextColor.RED));
            return;
        }
        String uuid = player.getUniqueId().toString();
        plugin.getGeoDatabaseManager().upsertBoundPlayer(uuid, player.getName());
        WebLink web = plugin.getWebLink();
        if (web != null) {
            web.getBindPublisher().bind(uuid, player.getName());
        }
        player.sendMessage(Component.text("已绑定网页登录，现在可以用微软账号登录线路图网站了", NamedTextColor.GREEN));
    }

    @CommandDescription("解绑网页登录资格")
    @Command("ticket weblogin unbind")
    @Permission("bcts.ticket.weblogin")
    public void unbind(Player player) {
        String uuid = player.getUniqueId().toString();
        plugin.getGeoDatabaseManager().deleteBoundPlayer(uuid);
        WebLink web = plugin.getWebLink();
        if (web != null) {
            web.getBindPublisher().unbind(uuid);
        }
        player.sendMessage(Component.text("已解绑网页登录", NamedTextColor.GREEN));
    }

    @CommandDescription("查看自己的网页登录绑定状态")
    @Command("ticket weblogin status")
    @Permission("bcts.ticket.weblogin")
    public void status(Player player) {
        boolean bound = plugin.getGeoDatabaseManager().isBoundPlayer(player.getUniqueId().toString());
        player.sendMessage(Component.text("网页登录绑定状态：" + (bound ? "已绑定" : "未绑定"),
                bound ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    @CommandDescription("查看与线路图后端的连接状态")
    @Command("ticketadmin weblink status")
    @Permission("bcts.ticket.reload")
    public void weblinkStatus(CommandSender sender) {
        if (!MapConfig.isEnabled()) {
            sender.sendMessage(Component.text("网页对接未启用（config_map.yml: web-link.enabled=false）", NamedTextColor.GRAY));
            return;
        }
        WebLink web = plugin.getWebLink();
        boolean connected = web != null && web.getClient().isConnected();
        sender.sendMessage(Component.text("后端连接：" + (connected ? "已连接" : "未连接"),
                connected ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (web != null) {
            long last = web.getClient().getLastSnapshotTime();
            sender.sendMessage(Component.text("最近一次快照推送：" + (last == 0 ? "无" : new java.util.Date(last).toString()),
                    NamedTextColor.AQUA));
        }
    }

    @CommandDescription("手动向后端推送指定快照")
    @Command("ticketadmin weblink sync <what>")
    @Permission("bcts.ticket.reload")
    public void weblinkSync(
            CommandSender sender,
            @Argument(value = "what", description = "geo|systems|lines|all") String what
    ) {
        WebLink web = plugin.getWebLink();
        if (web == null || !web.getClient().isConnected()) {
            sender.sendMessage(Component.text("后端未连接，无法同步", NamedTextColor.RED));
            return;
        }
        switch (what) {
            case "geo" -> web.getSnapshotPublisher().publishGeo();
            case "systems" -> web.getSnapshotPublisher().publishSystems();
            case "lines" -> web.getSnapshotPublisher().publishLines();
            default -> web.getSnapshotPublisher().publishAll();
        }
        sender.sendMessage(Component.text("已推送快照：" + what, NamedTextColor.GREEN));
    }

    @CommandDescription("重连线路图后端")
    @Command("ticketadmin weblink reconnect")
    @Permission("bcts.ticket.reload")
    public void weblinkReconnect(CommandSender sender) {
        WebLink web = plugin.getWebLink();
        if (web == null) {
            sender.sendMessage(Component.text("网页对接未启用", NamedTextColor.RED));
            return;
        }
        web.getClient().connect();
        sender.sendMessage(Component.text("已发起重连", NamedTextColor.GREEN));
    }
}
