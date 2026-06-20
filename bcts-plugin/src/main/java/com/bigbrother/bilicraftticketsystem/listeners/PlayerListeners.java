package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class PlayerListeners implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Menu.clearPlayerCache(uuid);
        CardListeners.inputModePlayers.remove(uuid);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TicketbgInfo info = plugin.getTrainDatabaseManager().getTicketbgService().getCurrentTicketbgInfo(player.getUniqueId().toString());
        MenuTicketbg.getTicketbgUsageMapping().put(player.getUniqueId(), info);

        payCreatorIncome(player);
    }

    /**
     * 玩家上线时，把其创建的各铁路系统「收入 - 已取出」发放给本人，并把已取出对齐到收入，
     * 然后发送可在 config.yml 配置的到账提示。结算与写回由
     * {@link RailwaySystemConfig#collectIncome(String)} 原子完成（实时写回，防止资金流失）。
     *
     * @param player 上线玩家
     */
    private void payCreatorIncome(Player player) {
        if (plugin.getEcon() == null) {
            return;
        }
        List<String> systemIds = RailwaySystemConfig.getSystemsCreatedBy(player.getUniqueId());
        for (String systemId : systemIds) {
            double payout = RailwaySystemConfig.collectIncome(systemId);
            if (payout <= 10e-8) {
                continue;
            }
            plugin.getEcon().depositPlayer(player, payout);
            RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
            String systemName = system == null ? systemId : system.getName();
            String template = MainConfig.message.get("system-income-collected",
                    "<green>铁路系统 <gold>%s</gold> 收入已到账：<gold>%.2f</gold> 银币");
            player.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(template.formatted(systemName, payout))));
        }
    }
}
