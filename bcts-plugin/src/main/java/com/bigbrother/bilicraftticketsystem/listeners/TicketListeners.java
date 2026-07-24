package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.guide.PlatformGuide;
import com.bigbrother.bilicraftticketsystem.route.NodeId;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

/**
 * 车票交互监听：右键手中的车票启动「寻找上车站台」引导（{@link PlatformGuide}）。
 * <p>
 * 车票是地图物品（{@code BCTicketDisplay} 仅负责渲染，不监听右键），右键交互此前空闲，无冲突。
 */
public class TicketListeners implements Listener {

    /**
     * 右键手中车票 -> 引导前往发车站台。
     */
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!MainConfig.guideEnabled) {
            return;
        }
        Action action = event.getAction();
        if (event.getHand() != EquipmentSlot.HAND
                || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
        if (!BCTicket.isBctsTicket(mainHand)) {
            return;
        }

        Player player = event.getPlayer();
        // 阻止右键顺带触发其他交互（放置 / 使用方块等）
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        BCTicket ticket = BCTicket.fromHeldItem(player);
        if (ticket == null) {
            return;
        }
        String nodeId = ticket.getStartPlatformNodeId();
        if (nodeId.isEmpty()) {
            // 旧格式车票无站台信息
            player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(
                            message.get("guide-no-platform", "<red>该车票没有可引导的站台信息"))
                    .decoration(TextDecoration.ITALIC, false)));
            return;
        }
        Location target = NodeId.toLocation(nodeId);
        if (target == null) {
            // 世界未加载 / 节点 id 非法
            player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(
                            message.get("guide-world-unloaded", "<red>无法定位站台，站台位置不可用"))
                    .decoration(TextDecoration.ITALIC, false)));
            return;
        }

        PlatformGuide.start(player, target, ticket.getStartStationNameNbt(), nodeId);
    }

    /**
     * 切换快捷栏槽位 -> 停止引导（tick 逻辑也会校验，此处即时停）。
     */
    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        PlatformGuide.stop(event.getPlayer());
    }

    /**
     * 玩家退出 -> 停止引导，清理全息实体。
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlatformGuide.stop(event.getPlayer());
    }
}
