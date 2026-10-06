package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 遍历路径图：记录「从哪个起点出发、沿某条线依次经过哪些节点、最终为什么停下」，供构建失败时回放现场。
 * <p>
 * <b>为什么是图而不是树</b>：{@link GraphWalk} 的展开按 {@code (节点,入向,出向,lineId)} 状态全局去重，
 * 一个状态只被<b>最先碰到它的那条分支</b>展开，后续分支撞到即不再入队。若只按父子关系记树，路径就会断在
 * 「该状态已被别处展开」处——这是去重假象，不是真正的停止原因，续行其实挂在树的别处。故本类按
 * <b>状态 key</b> 组织：每个被展开的状态记下它到达的节点（{@link #byState}），每个节点记下它的全部后继
 * 状态 key（含被去重的）。取路时按 key 解析后继，就能跨分支缝合出完整路径。
 * <p>
 * 节点只存世界名 + 整数方块坐标（不持有 {@link Block}）：校验与渲染跑在异步线程，不应再触碰 Bukkit 对象。
 * <p>
 * 取路入口 {@link #pathOfLine(String)}：从该线登记起点出发，只沿携带该 lineId 的后继向前，<b>岔路优先走
 * 经停站那一支</b>（跨站直通的正线对定位缺站没有帮助），直到断轨 / 真正无出向 / 环线闭合。
 * 另有 {@link #findStations(String, String)} + {@link #pathTo(TraceNode)} 回溯某车站的发现路径。
 */
public class TraversalTrace {
    /**
     * 岔路选向时，为判断某分支「多少跳能到车站」所允许访问的最大节点数。
     * 仅在构建失败的报告阶段执行，取够大的值即可，纯兜底防止超大图上退化。
     */
    private static final int STATION_LOOKAHEAD_BUDGET = 5000;

    /**
     * 路径节点类型。
     */
    public enum Kind {
        /**
         * 登记起点（每个 seed 一个）。
         */
        START,
        /**
         * 车站节点（platform 控制牌）。
         */
        STATION,
        /**
         * 道岔节点（bcswitcher 控制牌）。
         */
        SWITCH,
        /**
         * 轨道结束（断轨 / 死路 / 疑似无控制牌环路）。
         */
        END
    }

    /**
     * 路径图上的一个节点，即「矿车携带某 lineId 到达某位置」这一次到达。
     * <p>
     * 同一物理位置被不同线路 / 不同方向到达会生成不同的 TraceNode（它们的后继本就不同）。
     */
    @Getter
    public static final class TraceNode {
        private final Kind kind;
        /**
         * 车站名（仅 {@link Kind#STATION} 有，其它类型为 null）。
         */
        private final String stationName;
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        /**
         * 到达本节点时矿车携带的线路 id（决定本节点属于哪条线的路径）。
         */
        private final String lineId;
        /**
         * 首次到达本节点的上一节点（根节点为 null）。供 {@link #pathTo} 回溯与路径上下文前缀。
         */
        private final TraceNode firstParent;
        /**
         * 本节点的全部后继状态 key（<b>含被去重跳过的</b>）：取路时按 key 解析到实际节点，
         * 从而跨分支缝合出完整路径。
         */
        private final List<String> outStateKeys = new ArrayList<>();
        /**
         * 终止说明：本节点确实没有任何可展开后继时写明原因（无匹配出向 / 出向被过滤）。
         * 「已被别处展开」不写在这里——那不是终止，取路时会缝合续行。
         */
        private String endNote;

        private TraceNode(Kind kind, String stationName, String world, int x, int y, int z,
                          String lineId, TraceNode firstParent) {
            this.kind = kind;
            this.stationName = stationName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lineId = lineId;
            this.firstParent = firstParent;
        }
    }

    /**
     * 一条取出的路径：节点序列 + 末端说明（路径为什么在此结束）。
     *
     * @param nodes    节点序列（按行进顺序）
     * @param tailNote 末端说明；无特别说明时为 null
     */
    public record Path(List<TraceNode> nodes, String tailNote) {
    }

    /**
     * 各登记起点的根节点（按 seed 顺序）。
     */
    private final List<TraceNode> roots = new ArrayList<>();
    /**
     * 全部节点（按创建顺序），供按线 / 按站名筛选。
     */
    private final List<TraceNode> order = new ArrayList<>();
    /**
     * 状态 key -> 展开该状态后到达的节点。路径缝合的核心索引：某分支撞到已去重的状态时，
     * 经本表即可找到该状态实际到达的节点（哪条分支先展开的无关紧要）。
     */
    private final Map<String, TraceNode> byState = new HashMap<>();

    /**
     * 为一个登记起点建根节点。
     *
     * @param lineId    起点登记的线路 id
     * @param startRail 起点铁轨方块
     * @return 根节点（作为该起点首段的父节点）
     */
    public TraceNode seedRoot(String lineId, Block startRail) {
        return seedRoot(lineId, startRail.getWorld().getName(), startRail.getX(), startRail.getY(), startRail.getZ());
    }

    /**
     * 为一个登记起点建根节点（坐标形式，不依赖 Bukkit，便于单元测试）。
     *
     * @param lineId 起点登记的线路 id
     * @param world  世界名
     * @param x      方块 x
     * @param y      方块 y
     * @param z      方块 z
     * @return 根节点
     */
    public TraceNode seedRoot(String lineId, String world, int x, int y, int z) {
        TraceNode node = new TraceNode(Kind.START, null, world, x, y, z, lineId, null);
        roots.add(node);
        order.add(node);
        return node;
    }

    /**
     * 记录「展开状态 {@code stateKey} 后到达了这个节点」。
     *
     * @param parent      本段出发的节点（起点首段传根节点）
     * @param stateKey    本段对应的展开状态 key（起点首段无状态，传 null）
     * @param kind        节点类型
     * @param stationName 车站名（非 STATION 传 null）
     * @param rail        节点所在铁轨方块
     * @param lineId      到达本节点时携带的线路 id
     * @return 新节点
     */
    public TraceNode arrive(TraceNode parent, String stateKey, Kind kind, String stationName, Block rail,
                            String lineId) {
        return arrive(parent, stateKey, kind, stationName, rail.getWorld().getName(), rail.getX(), rail.getY(),
                rail.getZ(), lineId);
    }

    /**
     * 记录一次到达（坐标形式，不依赖 Bukkit，便于单元测试）。
     *
     * @param parent      本段出发的节点（可为 null）
     * @param stateKey    本段对应的展开状态 key（null 表示不登记到 {@link #byState}）
     * @param kind        节点类型
     * @param stationName 车站名（非 STATION 传 null）
     * @param world       世界名
     * @param x           方块 x
     * @param y           方块 y
     * @param z           方块 z
     * @param lineId      到达本节点时携带的线路 id
     * @return 新节点
     */
    public TraceNode arrive(TraceNode parent, String stateKey, Kind kind, String stationName, String world,
                            int x, int y, int z, String lineId) {
        TraceNode node = new TraceNode(kind, stationName, world, x, y, z, lineId, parent);
        order.add(node);
        if (stateKey != null) {
            byState.putIfAbsent(stateKey, node);
        }
        return node;
    }

    /**
     * 记录「本节点有一个后继状态」。无论该状态是本次新入队还是已被别处展开都要记录——正是后者让路径
     * 能跨分支缝合。
     *
     * @param from     节点（null 忽略）
     * @param stateKey 后继状态 key
     */
    public void link(TraceNode from, String stateKey) {
        if (from != null && stateKey != null && !from.outStateKeys.contains(stateKey)) {
            from.outStateKeys.add(stateKey);
        }
    }

    /**
     * 给一个节点写终止说明（确实没有可展开后继的原因）。重复调用只保留第一条。
     *
     * @param node 节点（null 忽略）
     * @param note 原因
     */
    public void note(TraceNode node, String note) {
        if (node != null && node.endNote == null) {
            node.endNote = note;
        }
    }

    /**
     * 取某条线的遍历路径：从该线登记起点出发，只沿携带该 lineId 的后继向前，一直走到真正的尽头。
     * <p>
     * 取向规则（见 {@link #chooseNext}）：岔路优先走<b>最快到达车站</b>的那一支——跨站直通的正线对定位
     * 缺站没有帮助；同等条件下按控制牌声明顺序。后继状态按 key 解析，故被去重跳过（续行挂在别的分支下）
     * 的地方会自动缝合，不会把去重当成停止原因。
     * <p>
     * 结束于以下情形之一，并在 {@link Path#tailNote()} 说明：断轨（节点本身即 {@code END}）、
     * 道岔无匹配出向 / 出向被过滤（节点自带 {@code endNote}）、回到已经过的节点（环线闭合）、
     * 后继状态尚未展开（遍历提前中止）。
     *
     * @param lineId 线路 id
     * @return 路径；该线没有任何遍历记录时 {@link Path#nodes()} 为空
     */
    public Path pathOfLine(String lineId) {
        TraceNode start = startOfLine(lineId);
        if (start == null) {
            return new Path(Collections.emptyList(), null);
        }
        List<TraceNode> nodes = new ArrayList<>();
        // 链首不是登记起点（本线是从别的线分叉进来的）时，把上一节点作为上下文前缀，
        // 便于看出是从哪条线的哪个道岔接入本线的
        if (start.kind != Kind.START && start.firstParent != null) {
            nodes.add(start.firstParent);
        }
        Set<TraceNode> onPath = Collections.newSetFromMap(new IdentityHashMap<>());
        String tailNote = null;
        TraceNode cur = start;
        while (true) {
            nodes.add(cur);
            onPath.add(cur);
            if (cur.kind == Kind.END) {
                break; // 断轨：节点本身已表达原因
            }
            if (cur.endNote != null) {
                break; // 真正无出向：节点自带说明
            }
            if (cur.outStateKeys.isEmpty()) {
                tailNote = "此处没有记录任何后继分支";
                break;
            }
            List<TraceNode> successors = successorsOf(cur, lineId);
            if (successors.isEmpty()) {
                tailNote = hasUnresolved(cur)
                        ? "后续分支尚未展开（遍历提前结束）"
                        : "没有继续沿本线的出向";
                break;
            }
            TraceNode next = chooseNext(successors, lineId, onPath);
            if (next == null) {
                tailNote = "回到已经过的节点（环线闭合）";
                break;
            }
            cur = next;
        }
        return new Path(nodes, tailNote);
    }

    /**
     * 取某条线路径的起点：优先该线的登记起点（seed 根节点），没有则取最早到达的本线节点
     * （本线是从别的线分叉进来的，如联络线 / 外线汇入）。
     *
     * @param lineId 线路 id
     * @return 路径起点；该线没有任何记录时返回 null
     */
    private TraceNode startOfLine(String lineId) {
        if (lineId == null) {
            return null;
        }
        for (TraceNode root : roots) {
            if (lineId.equals(root.lineId)) {
                return root;
            }
        }
        for (TraceNode node : order) {
            if (lineId.equals(node.lineId)) {
                return node;
            }
        }
        return null;
    }

    /**
     * 解析一个节点沿指定线路的全部后继（按状态 key 解析，故含被去重跳过、续行挂在别处的分支）。
     *
     * @param node   节点
     * @param lineId 线路 id（只取携带该 lineId 的后继）
     * @return 后继节点（按声明顺序）
     */
    private List<TraceNode> successorsOf(TraceNode node, String lineId) {
        List<TraceNode> result = new ArrayList<>();
        for (String key : node.outStateKeys) {
            TraceNode target = byState.get(key);
            if (target != null && lineId.equals(target.lineId)) {
                result.add(target);
            }
        }
        return result;
    }

    /**
     * 该节点是否存在「已登记但尚未展开」的后继状态（遍历被提前中止时会这样）。
     *
     * @param node 节点
     * @return 存在未展开后继返回 true
     */
    private boolean hasUnresolved(TraceNode node) {
        for (String key : node.outStateKeys) {
            if (!byState.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 岔路选向：优先尚未走过的分支（防环），其次<b>最快到达车站</b>的分支（跨站直通的正线对定位缺站
     * 没有帮助），再次按声明顺序。
     *
     * @param successors 候选后继
     * @param lineId     线路 id
     * @param onPath     当前路径上已经过的节点
     * @return 选中的后继；全部候选都已在路径上（环线闭合）时返回 null
     */
    private TraceNode chooseNext(List<TraceNode> successors, String lineId, Set<TraceNode> onPath) {
        TraceNode best = null;
        int bestHops = Integer.MAX_VALUE;
        boolean bestFresh = false;
        for (TraceNode cand : successors) {
            boolean fresh = !onPath.contains(cand);
            int hops = hopsToStation(cand, lineId);
            boolean better = best == null
                    || (fresh && !bestFresh)
                    || (fresh == bestFresh && hops < bestHops);
            if (better) {
                best = cand;
                bestHops = hops;
                bestFresh = fresh;
            }
        }
        return bestFresh ? best : null;
    }

    /**
     * 沿本线前进时，从某节点起最少多少跳能到一个车站（BFS）。用于岔路优先走经停站那一支。
     *
     * @param from   起始节点
     * @param lineId 线路 id（只沿本线后继搜索）
     * @return 跳数；预算内到不了任何车站返回 {@link Integer#MAX_VALUE}
     */
    private int hopsToStation(TraceNode from, String lineId) {
        if (from.kind == Kind.STATION) {
            return 0;
        }
        Set<TraceNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<TraceNode> frontier = new ArrayDeque<>();
        seen.add(from);
        frontier.add(from);
        int hops = 0;
        int budget = STATION_LOOKAHEAD_BUDGET;
        while (!frontier.isEmpty() && budget > 0) {
            hops++;
            int levelSize = frontier.size();
            for (int i = 0; i < levelSize && budget > 0; i++) {
                TraceNode cur = frontier.poll();
                for (TraceNode next : successorsOf(cur, lineId)) {
                    if (!seen.add(next)) {
                        continue;
                    }
                    if (next.kind == Kind.STATION) {
                        return hops;
                    }
                    budget--;
                    frontier.add(next);
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 回溯某节点的发现路径（首次到达它的那条路径，<b>跨线保留</b>）。用于「到达了配置外的车站」：
     * 关键信息正是列车怎么跑过去的，中途换了哪些线不能省略。
     *
     * @param node 目标节点
     * @return 根到该节点的路径，按行进顺序
     */
    public Path pathTo(TraceNode node) {
        if (node == null) {
            return new Path(Collections.emptyList(), null);
        }
        List<TraceNode> reversed = new ArrayList<>();
        for (TraceNode cur = node; cur != null; cur = cur.firstParent) {
            reversed.add(cur);
        }
        Collections.reverse(reversed);
        return new Path(reversed, null);
    }

    /**
     * 找出以给定线路 id 到达过某车站的全部节点。
     *
     * @param lineId      线路 id
     * @param stationName 车站名
     * @return 匹配的车站节点（按到达顺序）
     */
    public List<TraceNode> findStations(String lineId, String stationName) {
        List<TraceNode> result = new ArrayList<>();
        for (TraceNode node : order) {
            if (node.kind == Kind.STATION && Objects.equals(stationName, node.stationName)
                    && Objects.equals(lineId, node.lineId)) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * 把一条路径渲染成<b>竖向</b>带色文本：每个节点一行，首行为路径起点，其后各行以 {@code ↓ } 前缀相连，形如
     * <pre>
     * 起点[pr-s1](world,1,64,2)
     * ↓ 车站A-platform(world,...)
     * ↓ bcswitcher(world,...)
     * ↓ 断轨结束(world,...)
     * </pre>
     * 配色：起点金色、车站青色、道岔灰色、断轨红色、换线标记黄色、终止说明深灰，箭头深灰。
     * 节点携带的线路 id 与上一个节点不同时，在其后追加 {@code {lineId}} 标出换线位置；
     * {@link Path#tailNote()} 追加在末行。
     *
     * @param path 路径
     * @return 每个节点一行的渲染结果（按行进顺序）
     */
    public static List<Component> renderVertical(Path path) {
        List<Component> rows = new ArrayList<>();
        List<TraceNode> chain = path.nodes();
        String prevLineId = null;
        for (int i = 0; i < chain.size(); i++) {
            TraceNode node = chain.get(i);
            Component row = i == 0
                    ? renderNode(node)
                    : Component.text("↓ ", NamedTextColor.DARK_GRAY).append(renderNode(node));
            // 首节点（非起点）与换线处标出当前线路，便于看出在哪个道岔换了线
            if (node.kind != Kind.START && !Objects.equals(prevLineId, node.lineId)) {
                row = row.append(Component.text("{" + node.lineId + "}", NamedTextColor.YELLOW));
            }
            prevLineId = node.lineId;
            if (node.endNote != null) {
                row = row.append(Component.text("[" + node.endNote + "]", NamedTextColor.DARK_GRAY));
            }
            if (i == chain.size() - 1 && path.tailNote() != null) {
                row = row.append(Component.text("[" + path.tailNote() + "]", NamedTextColor.DARK_GRAY));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 渲染单个节点（含坐标）。
     *
     * @param node 节点
     * @return 渲染结果
     */
    private static Component renderNode(TraceNode node) {
        String coords = "(%s,%d,%d,%d)".formatted(node.world, node.x, node.y, node.z);
        return switch (node.kind) {
            case START -> Component.text("起点[" + node.lineId + "]" + coords, NamedTextColor.GOLD);
            case STATION -> Component.text(node.stationName + "-platform" + coords, NamedTextColor.AQUA);
            case SWITCH -> Component.text("bcswitcher" + coords, NamedTextColor.GRAY);
            case END -> Component.text("断轨结束" + coords, NamedTextColor.RED);
        };
    }
}
