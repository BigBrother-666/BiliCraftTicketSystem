package com.bigbrother.bilicraftticketsystem.menu.items.common;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.HashMap;
import java.util.UUID;

public abstract class CoolDownItem extends AbstractItem {
    @Setter
    private double cooldownTime = 1;

    private final static HashMap<UUID, Long> cooldowns = new HashMap<>();

    /**
     * 检查按钮冷却时间
     * @param player 点击按钮的玩家
     * @return true 可以使用按钮
     */
    public boolean isCooldown(Player player) {
        // 冷却 1.0s
        long lastUsedTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long timeLeft = (lastUsedTime + (long) (1000 * cooldownTime)) - currentTime;
        if (timeLeft > 0) {
            double secondsLeft = timeLeft / 1000.0;
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("点击过于频繁，请过 %.1f 秒后再试！".formatted(secondsLeft), NamedTextColor.RED)));
            return true;
        }
        cooldowns.put(player.getUniqueId(), currentTime);
        return false;
    }
}
