package com.bigbrother.bilicraftticketsystem.ticket;

import com.bigbrother.bilicraftticketsystem.database.entity.CardInfo;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

@Getter
public class BCCardInfo extends PlayerOption {
    public static final Map<String, BCCardInfo> cache = new HashMap<>();

    private final String cardUuid;
    private Double balance;

    private boolean changed;

    public BCCardInfo(String cardUuid) {
        super();
        this.cardUuid = cardUuid;
        this.speed = 4.0;
        this.balance = 0.0;
        save();

        cache.put(this.cardUuid, this);
        this.changed = false;
    }

    public BCCardInfo(CardInfo info) {
        super(info.getMmStartStation(), info.getMmEndStation());
        this.cardUuid = info.getCardUuid();
        this.speed = info.getMaxSpeed();
        this.balance = info.getBalance();

        cache.put(this.cardUuid, this);
        this.changed = false;
    }

    public void setStartStation(Component startStation) {
        super.setStartStation(startStation);
        changed = true;
    }

    public void setEndStation(Component endStation) {
        super.setEndStation(endStation);
        changed = true;
    }

    public static @Nullable BCCardInfo load(String cardUuid) {
        if (cache.containsKey(cardUuid)) {
            return cache.get(cardUuid);
        }
        CardInfo cardInfo = plugin.getTrainDatabaseManager().getCardInfoService().getByCardUuid(cardUuid);
        if (cardInfo != null) {
            return new BCCardInfo(cardInfo);
        }
        return null;
    }

    public void addBalance(Double toAdd) {
        this.balance += toAdd;
        // 金钱相关操作，立即更新
        update();
    }

    private void update() {
        plugin.getTrainDatabaseManager().getCardInfoService().update(new CardInfo(
                this.cardUuid,
                this.getStartStationString(),
                this.getMmStartStationName(),
                this.getEndStationString(),
                this.getMmEndStationName(),
                this.speed,
                this.balance
        ));
        changed = false;
    }

    /**
     * 保存新卡
     */
    private void save() {
        plugin.getTrainDatabaseManager().getCardInfoService().save(new CardInfo(
                this.cardUuid,
                this.getStartStationString(),
                this.getMmStartStationName(),
                this.getEndStationString(),
                this.getMmEndStationName(),
                this.speed,
                this.balance
        ));
        changed = false;
    }

    public static void clearCache() {
        cache.clear();
    }

    public static void saveAll() {
        cache.values().forEach(bcCardInfo -> {
            if (bcCardInfo.isChanged()) {
                bcCardInfo.update();
            }
        });
    }

    public static void reloadAllCache() {
        saveAll();
        clearCache();
        plugin.getTrainDatabaseManager().getCardInfoService().getAll().forEach(cardInfo -> cache.put(cardInfo.getCardUuid(), new BCCardInfo(cardInfo)));
    }
}
