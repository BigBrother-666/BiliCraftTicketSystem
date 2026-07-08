package com.bigbrother.bilicraftticketsystem.ticket;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.JourneyPlan;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

/**
 * 联程票：把一次「换乘行程方案」（{@link JourneyPlan}，当前为两段）打包为菜单里的一张展示卡片。
 * <p>
 * 底层<b>不是</b>新的车票模型：每段仍是一张独立的直达 {@link BCTicket}（到换乘站停车、下车换乘再上另一段），
 * 本类只负责①合并两段 lore + 分隔行 + 底部总价用于展示，②购买时<b>一次性扣总价</b>、成功后逐段交付实体票。
 */
@Getter
public class ThroughTicket {
    /**
     * 联程票展示名。
     */
    private String throughTicketName;

    private final Player owner;
    private final List<BCTicket> legs;
    private final ItemStack itemStack;

    /**
     * @param option 玩家选项（次数 / 速度，两段一致）
     * @param plan   换乘行程方案
     * @param owner  购票玩家
     */
    public ThroughTicket(PlayerOption option, JourneyPlan plan, Player owner) {
        this.owner = owner;
        this.legs = new ArrayList<>();
        for (GeoRoutePath leg : plan.getLegs()) {
            legs.add(new BCTicket(option, leg, owner));
        }
        this.throughTicketName = buildThroughTicketName(option);
        this.itemStack = createDisplayItem();
    }

    private String buildThroughTicketName(PlayerOption option) {
        StringBuilder name = new StringBuilder("联程票(");
        for (int i = 0; i < this.legs.size(); i++) {
            GeoRoutePath leg = this.legs.get(i).getPathInfo();
            name.append(leg.getStartStationName()).append("→").append(leg.getEndStationName());
            if (i < this.legs.size() - 1) {
                name.append(" & ");
            }
        }
        name.append(")");
        name.append(option.getUses() > 1 ? " %d次票".formatted(option.getUses()) : " 单次票");
        return name.toString();
    }

    /**
     * 全程总价（各段 {@link BCTicket#getPrice()} 之和，各段已含次数倍数与折扣）。
     *
     * @return 总价
     */
    public double getTotalPrice() {
        double total = 0.0;
        for (BCTicket leg : legs) {
            total += leg.getPrice();
        }
        return total;
    }

    /**
     * 全程总距离（km）。
     *
     * @return 总距离
     */
    public double getTotalDistance() {
        double total = 0.0;
        for (BCTicket leg : legs) {
            total += leg.getPathInfo().getDistance();
        }
        return total;
    }

    /**
     * 构建展示物品：以第一段车票物品为底（保持车票外观），数量 = 段数，名「联程票」，lore 为合并 lore。
     */
    private ItemStack createDisplayItem() {
        ItemStack display = legs.getFirst().getItemStack().clone();
        display.setAmount(legs.size());
        applyDisplayMeta(display);
        return display;
    }

    /**
     * 玩家在搜索后修改速度 / 次数时刷新联程票展示（各段按新选项重算价格与 lore，再重建合并 lore）。
     *
     * @param option 新的玩家选项
     */
    public void refreshMeta(PlayerOption option) {
        for (BCTicket leg : legs) {
            leg.refreshTicketMeta(option);
        }
        throughTicketName = buildThroughTicketName(option);
        applyDisplayMeta(itemStack);
    }

    /**
     * 把「联程票名 + 合并 lore」写入给定物品。
     */
    private void applyDisplayMeta(ItemStack item) {
        List<Component> lore = buildMergedLore();
        item.editMeta(meta -> {
            meta.displayName(Component.text(throughTicketName, NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            meta.lore(lore);
        });
    }

    /**
     * 合并 lore：每段前加分隔行（含行程序号与该段起终点站名），段内为该段<b>不含价格</b>的 lore，
     * 末尾按 {@link MainConfig#ticketPriceLore} 格式显示<b>全程总价</b>（各段各系统距离合并计算收费详情）。
     */
    private List<Component> buildMergedLore() {
        List<Component> lore = new ArrayList<>();
        int total = legs.size();
        for (int i = 0; i < total; i++) {
            BCTicket leg = legs.get(i);
            GeoRoutePath path = leg.getPathInfo();
            Map<String, Object> sepPlaceholder = new HashMap<>();
            sepPlaceholder.put("index", i + 1);
            sepPlaceholder.put("total", total);
            sepPlaceholder.put("leg_start_station", path.getStartStationName());
            sepPlaceholder.put("leg_end_station", path.getEndStationName());
            lore.addAll(PlaceholderParser.parse(List.of(MainConfig.throughTicketSeparator), sepPlaceholder));
            // 该段不含价格的 lore
            lore.addAll(leg.buildLore(false));
        }
        // 底部：全程总价（合并各段各系统距离生成收费详情，价格用各段含折扣总价之和）
        Map<String, Double> mergedDistances = new LinkedHashMap<>();
        for (BCTicket leg : legs) {
            for (Map.Entry<String, Double> entry : leg.segmentDistancesBySystem().entrySet()) {
                mergedDistances.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        Map<String, Object> pricePlaceholder = new HashMap<>();
        pricePlaceholder.put("distance_info_lore", BCTransitPass.priceInfoLoreOf(mergedDistances));
        pricePlaceholder.put("price", "%.2f".formatted(getTotalPrice()));
        pricePlaceholder.put("distance", "%.2f".formatted(getTotalDistance()));
        pricePlaceholder.put("speed", "%.2f".formatted(legs.getFirst().getSpeedKph()));
        lore.addAll(PlaceholderParser.parse(MainConfig.ticketPriceLore, pricePlaceholder));
        return lore;
    }

    /**
     * 购买联程票：先算全程总价，<b>一次性 Vault 扣总额</b>；成功后逐段交付独立实体票并按段所属系统建账
     * （各段传入其自身应计金额，各段之和 == 总价）；余额不足则一张不发、给提示。
     * <p>
     * 须在主线程调用（涉及 Vault / 背包 / 数据库）。
     */
    public void purchase() {
        double totalPrice = getTotalPrice();
        EconomyResponse r = plugin.getEcon().withdrawPlayer(owner, totalPrice);
        if (!r.transactionSuccess()) {
            owner.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-buy-failure", "车票购买失败：%s").formatted(r.errorMessage))
                            .decoration(TextDecoration.ITALIC, false)
            ));
            return;
        }
        // 扣款成功：逐段交付，各段按其自身价格建账（之和 == 已扣总价）
        for (BCTicket leg : legs) {
            leg.deliverAfterPayment(leg.getPrice());
        }
        owner.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(message.get("ticket-buy-success", "您成功花费 %.2f 购买了 %s").formatted(r.amount, throughTicketName))
                        .decoration(TextDecoration.ITALIC, false)
        ));
        org.bukkit.Bukkit.getConsoleSender().sendMessage(MainConfig.prefix.append(
                Component.text("玩家 %s 成功花费 %.2f 购买了%s".formatted(owner.getName(), r.amount, throughTicketName), NamedTextColor.GREEN)));
    }

    /**
     * 创造模式（免费）直接交付各段实体票，不扣款、不建账。
     */
    public void give() {
        for (BCTicket leg : legs) {
            leg.give();
        }
    }
}
