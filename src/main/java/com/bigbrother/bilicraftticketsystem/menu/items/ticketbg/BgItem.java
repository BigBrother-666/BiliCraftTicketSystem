package com.bigbrother.bilicraftticketsystem.menu.items.ticketbg;

import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.HashMap;
import java.util.UUID;

@Getter
@Setter
public abstract class BgItem extends AbstractItem {
    private TicketbgInfo ticketbgInfo;
    private final static HashMap<UUID, Long> uploadCooldowns = new HashMap<>();

    /**
     * 检查按钮冷却时间
     * @param player 点击按钮的玩家
     * @return true 可以使用按钮
     */
    public boolean isCooldown(Player player) {
        // 冷却 1.5s
        long lastUsedTime = uploadCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long timeLeft = (lastUsedTime + (long) (1000 * 1.5)) - currentTime;
        if (timeLeft > 0) {
            double secondsLeft = timeLeft / 1000.0;
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[帕拉伦国有铁路车票系统] <red>点击过于频繁，请过 %.1f 秒后再试！".formatted(secondsLeft)));
            return true;
        }
        uploadCooldowns.put(player.getUniqueId(), currentTime);
        return false;
    }

    public boolean isUseCurrTicketbg(Player player) {
        TicketbgInfo ticketbgInfo = MenuTicketbg.getTicketbgUsageMapping().get(player.getUniqueId());
        if (ticketbgInfo == null) {
            return false;
        }
        return this.ticketbgInfo.getId() == ticketbgInfo.getId();
    }
}
