package com.bigbrother.bilicraftticketsystem.menu.search;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.JourneyPlan;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.ticket.ThroughTicket;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 车票搜索：按起终点寻找直达票与联程票，并用 {@link TicketRanker} 做距离 / 票价混合排序。
 * <p>
 * 购票界面的搜索按钮与 {@code /ticket buy} 指令共用，保证两者候选与排序一致。
 * 寻路较重，应在异步线程调用。
 */
public final class TicketSearcher {

    private TicketSearcher() {
    }

    /**
     * 搜索结果：直达票、联程票，以及排序后的下标序列。
     * <p>
     * {@code order} 中下标 {@code < direct.size()} 指向 {@code direct}，其余减去 {@code direct.size()} 指向 {@code through}。
     *
     * @param direct  直达票候选
     * @param through 联程票候选
     * @param order   排序后的下标（越靠前越推荐）
     */
    public record Result(List<BCTicket> direct, List<ThroughTicket> through, List<Integer> order) {

        /**
         * 是否没有任何可购买的车票。
         *
         * @return 无结果返回 true
         */
        public boolean isEmpty() {
            return order.isEmpty();
        }
    }

    /**
     * 按玩家选项（起终点 / 速度 / 次数）搜索并排序车票，受 {@link MainConfig#searchComputeTimeoutSeconds} 耗时上限约束。
     *
     * @param option 玩家购票选项（起点站、终点站、速度、次数）
     * @param player 购票玩家（车票持有者）
     * @return 搜索结果
     */
    public static Result search(PlayerOption option, Player player) {
        String start = option.getStartStationString();
        String end = option.getEndStationString();

        // 单次搜索的计算耗时上限：超时后 findByStation/findTransferJourneys 会停止枚举，
        // 返回目前已找到的部分结果（不算错误），避免极端无解/复杂查询长时间占用异步线程。
        long deadlineNanos = MainConfig.searchComputeTimeoutSeconds > 0
                ? System.nanoTime() + MainConfig.searchComputeTimeoutSeconds * 1_000_000_000L
                : 0L;

        // 直达候选池：只需覆盖「距离前 N ∪ 票价前 M」，取两者之和作上限即可满足混合排序，
        // 避免不限条数(k=16)在大图上的重 KSP。任一维度不限(<=0)时退回不限条数。
        int directPool;
        if (MainConfig.maxDistanceResults <= 0 || MainConfig.maxPriceResults <= 0) {
            directPool = 0;
        } else {
            directPool = MainConfig.maxDistanceResults + MainConfig.maxPriceResults;
        }
        List<BCTicket> directTickets = new ArrayList<>();
        for (GeoRoutePath path : GeoRouteEngine.findByStation(start, end, directPool, deadlineNanos)) {
            directTickets.add(new BCTicket(option, path, player));
        }
        // 换乘（联程）候选：限方案数 + 限候选换乘站数，防止大线组合爆炸
        List<ThroughTicket> throughTickets = new ArrayList<>();
        for (JourneyPlan plan : GeoRouteEngine.findTransferJourneys(start, end,
                MainConfig.maxTransferResults, MainConfig.transferMinImprovement, deadlineNanos)) {
            throughTickets.add(new ThroughTicket(option, plan, player));
        }

        // 汇总为排序候选：直达票在前、联程票在后（下标空间连续）
        List<TicketRanker.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < directTickets.size(); i++) {
            BCTicket t = directTickets.get(i);
            candidates.add(new TicketRanker.Candidate(i, t.getPathInfo().getDistance(), t.getPrice()));
        }
        for (int i = 0; i < throughTickets.size(); i++) {
            ThroughTicket t = throughTickets.get(i);
            candidates.add(new TicketRanker.Candidate(directTickets.size() + i, t.getTotalDistance(), t.getTotalPrice()));
        }

        List<Integer> order = TicketRanker.rankWithMinDirect(candidates, directTickets.size(),
                MainConfig.maxDistanceResults, MainConfig.maxPriceResults,
                MainConfig.searchWeightDistance, MainConfig.searchWeightPrice,
                MainConfig.minDirectResults);
        return new Result(directTickets, throughTickets, order);
    }
}
