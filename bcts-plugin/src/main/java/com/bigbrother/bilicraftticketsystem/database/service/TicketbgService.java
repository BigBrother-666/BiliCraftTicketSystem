package com.bigbrother.bilicraftticketsystem.database.service;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.database.dao.TicketbgDao;
import com.bigbrother.bilicraftticketsystem.database.entity.FullTicketbgInfo;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.menu.items.ticketbg.SortField;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class TicketbgService {
    private final BiliCraftTicketSystem plugin;
    private final TicketbgDao ticketbgDao;

    public TicketbgService(BiliCraftTicketSystem plugin, TicketbgDao ticketbgDao) {
        this.plugin = plugin;
        this.ticketbgDao = ticketbgDao;
    }

    public TicketbgInfo getCurrentTicketbgInfo(String uuid) {
        UUID playerUuid = UUID.fromString(uuid);
        if (MenuTicketbg.getTicketbgUsageMapping().containsKey(playerUuid)) {
            return MenuTicketbg.getTicketbgUsageMapping().get(playerUuid);
        }
        return ticketbgDao.findCurrentTicketbg(uuid);
    }

    public List<TicketbgInfo> getAllSharedTickets(SortField sortField) {
        return ticketbgDao.findAllSharedTickets(sortField);
    }

    public List<TicketbgInfo> getAllSelfTickets(String uuid, SortField sortField) {
        return ticketbgDao.findAllSelfTickets(uuid, sortField);
    }

    public void updateUsageTicketbg(@Nullable Integer bgId, String uuid) {
        TicketbgInfo info = getCurrentTicketbgInfo(uuid);
        int updated = ticketbgDao.updateUsageTicketbg(bgId, uuid);
        if (updated == 0) {
            ticketbgDao.insertTicketbgUsageInfo(uuid, bgId);
        }

        if (info != null) {
            refreshUsageCount(info.getId());
        }

        if (bgId != null) {
            refreshUsageCount(bgId);
        }
    }

    public int deleteTicketbgLogical(int bgId) {
        int affected = ticketbgDao.logicalDeleteTicketbg(bgId);
        cleanupIfUnused(bgId);
        return affected;
    }

    public void setShared(int bgId, boolean shared) {
        ticketbgDao.setShared(bgId, shared);
    }

    public int getPlayerTicketbgCount(@Nullable String uuid) {
        return ticketbgDao.countPlayerTicketbg(uuid);
    }

    public void addTicketbgInfo(String playerName, String uuid, String itemName, String filePath, String fontColor) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (uuid != null) {
                ticketbgDao.updatePlayerNameByUuid(uuid, playerName);
            }
            boolean shared = uuid == null;
            ticketbgDao.insertTicketbgInfo(playerName, uuid, itemName, filePath, fontColor, shared);
        });
    }

    private void refreshUsageCount(int bgId) {
        ticketbgDao.updateTicketbgUsageCount(bgId);
        cleanupIfUnused(bgId);
    }

    private void cleanupIfUnused(int bgId) {
        FullTicketbgInfo info = ticketbgDao.findById(bgId);
        if (info != null && info.isDeleted() && info.getUsageCount() <= 0) {
            ticketbgDao.deleteTicketbg(bgId);
            CommonUtils.deleteTicketbg(info.getFilePath());
        }
    }
}
