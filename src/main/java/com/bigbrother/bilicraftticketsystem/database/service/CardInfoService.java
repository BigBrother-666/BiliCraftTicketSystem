package com.bigbrother.bilicraftticketsystem.database.service;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.dao.CardInfoDao;
import com.bigbrother.bilicraftticketsystem.database.entity.CardInfo;
import org.bukkit.Bukkit;

import java.util.List;

public class CardInfoService {
    private final BiliCraftTicketSystem plugin;
    private final CardInfoDao cardInfoDao;

    public CardInfoService(BiliCraftTicketSystem plugin, CardInfoDao cardInfoDao) {
        this.plugin = plugin;
        this.cardInfoDao = cardInfoDao;
    }

    public void save(CardInfo cardInfo) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> cardInfoDao.insert(cardInfo));
    }

    public void update(CardInfo cardInfo) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> cardInfoDao.updateByCardUuid(cardInfo));
    }

    public CardInfo getByCardUuid(String cardUuid) {
        return cardInfoDao.findByCardUuid(cardUuid);
    }

    public List<CardInfo> getAll() {
        return cardInfoDao.findAll();
    }
}
