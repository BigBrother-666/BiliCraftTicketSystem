package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

/**
 * 配置向导（editRoute / editSystem）的聊天与掉线监听。
 * <p>
 * 玩家进入向导后，其聊天被拦截并逐行转发给向导推进；掉线则放弃编辑、停止监听。
 * {@link AsyncPlayerChatEvent} 在异步线程触发，因此实际推进（发提示、写配置、构造可点击组件）
 * 切回主线程执行。
 */
public class WizardListeners implements Listener {

    /**
     * 拦截处于向导中的玩家聊天，逐行交给其向导处理。
     *
     * @param event 聊天事件
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!WizardManager.isActive(uuid)) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage().trim();
        // AsyncPlayerChatEvent 在异步线程；向导推进需在主线程
        Bukkit.getScheduler().runTask(plugin, () -> WizardManager.handleInput(uuid, input));
    }

    /**
     * 玩家掉线则放弃其正在进行的向导。
     *
     * @param event 退出事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        WizardManager.cancel(event.getPlayer().getUniqueId());
    }
}
