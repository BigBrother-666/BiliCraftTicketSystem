package com.bigbrother.bilicraftticketsystem.menu.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 购票搜索结果的「距离 / 票价混合排序」纯逻辑。
 * <p>
 * 输入一组候选（各含总距离与总票价），按需求：
 * <ol>
 *   <li>取「距离最近」的前 N 条与「票价最低」的前 M 条，两个子集<b>合并去重</b>作为最终展示集；</li>
 *   <li>把展示集内的距离、票价<b>各自归一化到 [0,1]</b>（按集合内 min/max 线性缩放），</li>
 *   <li>{@code weight = wDistance * 归一化距离 + wPrice * 归一化票价}，<b>weight 越小越靠前</b>。</li>
 * </ol>
 * 只依赖入参数值，不涉及 Bukkit / 图 / 车票，便于单测。
 */
public final class TicketRanker {
    private TicketRanker() {
    }

    /**
     * 一个候选的排序输入：其在原始列表中的下标 + 总距离 + 总票价。
     *
     * @param index    原始下标（排序后据此回取原对象）
     * @param distance 总距离（km）
     * @param price    总票价（银币）
     */
    public record Candidate(int index, double distance, double price) {
    }

    /**
     * 对候选做「距离前 N ∪ 票价前 M → 归一化加权排序」，返回排序后的原始下标序列。
     *
     * @param candidates    候选列表
     * @param maxByDistance 显示「距离最近」的条数（{@code <=0} 不限制）
     * @param maxByPrice    显示「票价最低」的条数（{@code <=0} 不限制）
     * @param wDistance     归一化距离权重
     * @param wPrice        归一化票价权重
     * @return 排序后的原始下标列表（已去重）
     */
    public static List<Integer> rank(List<Candidate> candidates, int maxByDistance, int maxByPrice,
                                     double wDistance, double wPrice) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }

        // 距离升序取前 N
        List<Candidate> byDistance = new ArrayList<>(candidates);
        byDistance.sort(Comparator.comparingDouble(Candidate::distance));
        // 票价升序取前 M
        List<Candidate> byPrice = new ArrayList<>(candidates);
        byPrice.sort(Comparator.comparingDouble(Candidate::price));

        // 合并去重（按原始下标），保持「先距离后票价」的加入顺序
        Set<Integer> selected = new LinkedHashSet<>();
        addTop(selected, byDistance, maxByDistance);
        addTop(selected, byPrice, maxByPrice);

        return sortByWeight(new ArrayList<>(selected), candidates, wDistance, wPrice);
    }

    /**
     * 在 {@link #rank} 的基础上加「直达票兜底」：若排序结果里<b>没有任何直达车票</b>（全是联程票），
     * 且 {@code minDirect > 0}，则把最优的 {@code minDirect} 条直达车票（按同一权重公式排序）补到结果<b>最前</b>。
     * <p>
     * 直达 / 联程通过下标区分：{@code index < directCount} 为直达票，其余为联程票（与调用方拼装候选时的下标布局一致）。
     *
     * @param candidates  候选列表（直达在前、联程在后）
     * @param directCount 直达候选的数量（下标 {@code [0, directCount)} 为直达票）
     * @param maxByDistance 距离前 N
     * @param maxByPrice    票价前 M
     * @param wDistance     归一化距离权重
     * @param wPrice        归一化票价权重
     * @param minDirect     结果无直达时至少补充的直达票条数（{@code <=0} 不兜底）
     * @return 排序后的原始下标列表（已去重）
     */
    public static List<Integer> rankWithMinDirect(List<Candidate> candidates, int directCount,
                                                  int maxByDistance, int maxByPrice,
                                                  double wDistance, double wPrice, int minDirect) {
        List<Integer> order = rank(candidates, maxByDistance, maxByPrice, wDistance, wPrice);
        if (minDirect <= 0 || directCount <= 0) {
            return order;
        }
        // 结果里已有直达票则无需兜底
        for (int idx : order) {
            if (idx < directCount) {
                return order;
            }
        }
        // 取最优的 minDirect 条直达票（按同一权重排序），补到最前
        List<Integer> directIndices = new ArrayList<>();
        for (int i = 0; i < directCount; i++) {
            directIndices.add(i);
        }
        List<Integer> bestDirect = sortByWeight(directIndices, candidates, wDistance, wPrice);
        int take = Math.min(minDirect, bestDirect.size());
        List<Integer> result = new ArrayList<>(bestDirect.subList(0, take));
        result.addAll(order);
        return result;
    }

    /**
     * 把给定下标集合按归一化加权公式排序（范围取自该集合内 min/max，span 为 0 时归一化取 0 防除零）。
     */
    private static List<Integer> sortByWeight(List<Integer> indices, List<Candidate> candidates,
                                              double wDistance, double wPrice) {
        double minDist = Double.POSITIVE_INFINITY, maxDist = Double.NEGATIVE_INFINITY;
        double minPrice = Double.POSITIVE_INFINITY, maxPrice = Double.NEGATIVE_INFINITY;
        for (int idx : indices) {
            Candidate c = candidates.get(idx);
            minDist = Math.min(minDist, c.distance());
            maxDist = Math.max(maxDist, c.distance());
            minPrice = Math.min(minPrice, c.price());
            maxPrice = Math.max(maxPrice, c.price());
        }
        final double fMinDist = minDist, fDistSpan = maxDist - minDist;
        final double fMinPrice = minPrice, fPriceSpan = maxPrice - minPrice;
        List<Integer> result = new ArrayList<>(indices);
        result.sort(Comparator.comparingDouble(idx -> {
            Candidate c = candidates.get(idx);
            double normDist = fDistSpan > 0 ? (c.distance() - fMinDist) / fDistSpan : 0.0;
            double normPrice = fPriceSpan > 0 ? (c.price() - fMinPrice) / fPriceSpan : 0.0;
            return wDistance * normDist + wPrice * normPrice;
        }));
        return result;
    }

    /**
     * 把已排序列表的前 {@code limit} 条的原始下标加入 {@code target}（{@code limit<=0} 表示全部）。
     */
    private static void addTop(Set<Integer> target, List<Candidate> sorted, int limit) {
        int count = limit > 0 ? Math.min(limit, sorted.size()) : sorted.size();
        for (int i = 0; i < count; i++) {
            target.add(sorted.get(i).index());
        }
    }
}
