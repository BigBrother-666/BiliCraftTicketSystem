package com.bigbrother.bilicraftticketsystem.commands.argument;

import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.ItemsConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
import com.bigbrother.bilicraftticketsystem.ticket.BCCardInfo;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.checkerframework.checker.units.qual.C;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommandSuggestions {
    @Suggestions("lineId")
    public List<String> lineIdSuggestions(CommandContext<Player> context, CommandInput input) {
        // 该玩家所在的所有铁路系统下的线路 id
        return memberLineIds(context);
    }

    /**
     * {@code railgeo walkAll --ignore} 的补全：<b>所有</b>线路 id（不限执行者所在铁路系统），
     * 但执行者所属系统的线路 id 排在前面，便于快速选到自己的线。
     *
     * @param context 命令上下文
     * @param input   命令输入
     * @return 全部线路 id（自己系统的在前）
     */
    @Suggestions("allLineId")
    public List<String> allLineIdSuggestions(CommandContext<Player> context, CommandInput input) {
        return allLineIdsOwnSystemFirst(context);
    }

    /**
     * {@code railgeo walk <lineIds>} 的补全：参数是 {@code @Greedy} 字符串（空格分隔多条线），
     * cloud 会拿<b>整段剩余输入</b>（如 {@code "L1 L2 L"}）与候选做前缀过滤，故裸 id 只能匹配第一个 token。
     * 这里返回<b>完整多 token 串</b>：把已输入完成的前缀（如 {@code "L1 L2 "}）拼到每个候选前面
     * （如 {@code "L1 L2 L3"}），并排除已经选过的线 id，使每个位置都能正常补全。
     *
     * 候选为<b>所有</b>线路 id（不限执行者所在铁路系统、不校验归属），执行者自己系统的线路排在前面。
     *
     * @param context 命令上下文
     * @param input   命令输入
     * @return 带已输入前缀的完整候选串
     */
    @Suggestions("lineIds")
    public List<String> lineIdsSuggestions(CommandContext<Player> context, CommandInput input) {
        String remaining = input.remainingInput();
        String typing = input.lastRemainingToken();
        // 已输入完成的前缀（保留其中的空格），正在输入的最后一个 token 之前的部分
        String committed = remaining.substring(0, remaining.length() - typing.length());
        // 前缀里已选过的线 id，后续不再重复建议
        Set<String> chosen = new LinkedHashSet<>();
        for (String token : committed.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                chosen.add(token);
            }
        }
        List<String> result = new ArrayList<>();
        for (String lineId : allLineIdsOwnSystemFirst(context)) {
            if (!chosen.contains(lineId)) {
                result.add(committed + lineId);
            }
        }
        return result;
    }

    /**
     * 发起者所在所有铁路系统下的线路 id。
     *
     * @param context 命令上下文
     * @return 线路 id 列表
     */
    private List<String> memberLineIds(CommandContext<Player> context) {
        List<String> result = new ArrayList<>();
        for (String systemId : RailwaySystemConfig.getSystemsOfMember(context.sender().getUniqueId())) {
            result.addAll(LineConfig.getLineIdsOfSystem(systemId));
        }
        return result;
    }

    /**
     * 所有线路 id（不限执行者所在铁路系统），但执行者所属系统的线路 id 排在前面、其余线路随后，
     * 各自内部保持配置顺序、整体去重。
     *
     * @param context 命令上下文
     * @return 全部线路 id（自己系统的在前）
     */
    private List<String> allLineIdsOwnSystemFirst(CommandContext<Player> context) {
        Set<String> own = new LinkedHashSet<>(memberLineIds(context));
        List<String> result = new ArrayList<>(own);
        for (String lineId : LineConfig.getLines().keySet()) {
            if (!own.contains(lineId)) {
                result.add(lineId);
            }
        }
        return result;
    }

    @Suggestions("systemId")
    public List<String> systemIdSuggestions(CommandContext<Player> context, CommandInput input) {
        // 该玩家所在的所有铁路系统 id
        return RailwaySystemConfig.getSystemsOfMember(context.sender().getUniqueId());
    }

    @Suggestions("switchTraceState")
    public List<String> switchTraceStateSuggestions(CommandContext<C> context, CommandInput input) {
        return List.of("on", "off");
    }

    @Suggestions("menuItemId")
    public List<String> menuItemIdSuggestions(CommandContext<C> context, CommandInput input) {
        return ItemsConfig.itemsConfig.getKeys().stream().toList();
    }

    @Suggestions("nbtKey")
    public List<String> nbtKeySuggestions(CommandContext<C> context, CommandInput input) {
        return List.of(
                BCTransitPass.KEY_TRANSIT_PASS_PLUGIN,
                BCTransitPass.KEY_TRANSIT_PASS_TYPE,
                BCTransitPass.KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH,
                BCTicket.KEY_TICKET_NAME,
                BCTicket.KEY_TICKET_CREATION_TIME,
                BCTicket.KEY_TICKET_EXPIRATION_TIME,
                BCTicket.KEY_TICKET_NUMBER_OF_USES,
                BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES,
                BCTicket.KEY_TICKET_OWNER_UUID,
                BCTicket.KEY_TICKET_OWNER_NAME,
                BCTicket.KEY_TICKET_MAX_SPEED,
                BCTicket.KEY_TICKET_ORIGIN_PRICE,
                BCTicket.KEY_TICKET_START_STATION,
                BCTicket.KEY_TICKET_END_STATION,
                BCTicket.KEY_TICKET_DISTANCE,
                BCCard.KEY_CARD_UUID,
                BCCard.KEY_CARD_INIT_FLAG
        );
    }

    @Suggestions("nbtValue")
    public List<String> nbtValueSuggestions(CommandContext<C> context, CommandInput input) {
        String i = input.input().trim();
        String[] split = i.split(" ");
        if (split.length >= 3) {
            String key = split[2];
            if (key.trim().equals(BCTicket.KEY_TICKET_OWNER_NAME)) {
                String current = input.readString().toLowerCase();
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(current)).toList();
            }
        }
        return List.of();
    }

    @Suggestions("statisticsType")
    public List<String> statisticsTypeSuggestions(CommandContext<C> context, CommandInput input) {
        return StatisticsType.getNameList();
    }

    @Suggestions("cardUUID")
    public List<String> cardUUIDSuggestions(CommandContext<Player> context, CommandInput input) {
        return BCCardInfo.cache.keySet().stream().toList();
    }
}
