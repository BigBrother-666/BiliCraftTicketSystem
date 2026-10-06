package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraversalTrace} 的取路与渲染测试。
 * <p>
 * 用不依赖 Bukkit 的坐标形式 API 建图（遍历本体需要主线程 + TC 实时寻路，离线测不了），
 * 重点覆盖「失败时能否给出完整路径与真正的停止原因」：
 * <ul>
 *   <li>被 {@code visited} 去重跳过的后继必须按状态 key 缝合，不能把「已遍历过」当成终止；</li>
 *   <li>岔路优先走经停站那一支，跨站直通的正线不展示；</li>
 *   <li>断轨 / 道岔无出向 / 环线闭合各自给出对应说明。</li>
 * </ul>
 */
class TraversalTraceTest {
    /**
     * 把一条路径竖向渲染成多行纯文本（行间用 {@code \n} 连接），便于断言。
     */
    private String plain(TraversalTrace.Path path) {
        return TraversalTrace.renderVertical(path).stream()
                .map(row -> PlainTextComponentSerializer.plainText().serialize(row))
                .collect(Collectors.joining("\n"));
    }

    private TraversalTrace.TraceNode station(TraversalTrace trace, TraversalTrace.TraceNode parent, String key,
                                             String name, int x, String lineId) {
        return trace.arrive(parent, key, TraversalTrace.Kind.STATION, name, "world", x, 64, x, lineId);
    }

    private TraversalTrace.TraceNode switcher(TraversalTrace trace, TraversalTrace.TraceNode parent, String key,
                                              int x, String lineId) {
        return trace.arrive(parent, key, TraversalTrace.Kind.SWITCH, null, "world", x, 64, x, lineId);
    }

    @Test
    void pathRunsFromStartToBrokenRail() {
        TraversalTrace trace = new TraversalTrace();
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode a = station(trace, root, "s1", "车站A", 1, "l1");
        trace.link(a, "s2");
        TraversalTrace.TraceNode sw = switcher(trace, a, "s2", 2, "l1");
        trace.link(sw, "s3");
        TraversalTrace.TraceNode b = station(trace, sw, "s3", "车站B", 3, "l1");
        trace.link(b, "s4");
        trace.arrive(b, "s4", TraversalTrace.Kind.END, null, "world", 4, 64, 4, "l1");

        // 竖向排版：首行路径起点，其余各行以 "↓ " 前缀相连
        assertEquals("""
                起点[l1](world,0,64,0)
                ↓ 车站A-platform(world,1,64,1)
                ↓ switcher(world,2,64,2)
                ↓ 车站B-platform(world,3,64,3)
                ↓ 断轨结束(world,4,64,4)""", plain(trace.pathOfLine("l1")));
    }

    @Test
    void pathStitchesSuccessorExpandedByAnotherBranch() {
        TraversalTrace trace = new TraversalTrace();
        // 主线：起点 -> 道岔
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode sw = switcher(trace, root, "s1", 1, "l1");

        // 另一条分支先展开了状态 "shared"，其到达节点挂在别处（父节点不是上面那个道岔）
        TraversalTrace.TraceNode other = switcher(trace, null, "other", 9, "l1");
        TraversalTrace.TraceNode shared = station(trace, other, "shared", "车站B", 2, "l1");
        trace.link(shared, "s3");
        trace.arrive(shared, "s3", TraversalTrace.Kind.END, null, "world", 3, 64, 3, "l1");

        // 主线道岔的出向正是 "shared"：GraphWalk 会因去重不再入队，但照样 link
        trace.link(sw, "shared");

        // 取路必须跨分支缝合到 shared 并继续走到断轨，而不是停在道岔上报「已遍历过」
        assertEquals("""
                起点[l1](world,0,64,0)
                ↓ switcher(world,1,64,1)
                ↓ 车站B-platform(world,2,64,2)
                ↓ 断轨结束(world,3,64,3)""", plain(trace.pathOfLine("l1")));
    }

    @Test
    void forkPrefersBranchWithStation() {
        TraversalTrace trace = new TraversalTrace();
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode sw = switcher(trace, root, "s1", 1, "l1");

        // 正线分支：跨站直通，只有道岔然后断轨（不含 platform）
        trace.link(sw, "main");
        TraversalTrace.TraceNode mainLine = switcher(trace, sw, "main", 5, "l1");
        trace.link(mainLine, "mainEnd");
        trace.arrive(mainLine, "mainEnd", TraversalTrace.Kind.END, null, "world", 6, 64, 6, "l1");

        // 停靠分支：经停车站
        trace.link(sw, "stop");
        TraversalTrace.TraceNode stop = station(trace, sw, "stop", "车站A", 2, "l1");
        trace.link(stop, "stopEnd");
        trace.arrive(stop, "stopEnd", TraversalTrace.Kind.END, null, "world", 3, 64, 3, "l1");

        // 岔路应走经停站那一支，正线不展示
        assertEquals("""
                起点[l1](world,0,64,0)
                ↓ switcher(world,1,64,1)
                ↓ 车站A-platform(world,2,64,2)
                ↓ 断轨结束(world,3,64,3)""", plain(trace.pathOfLine("l1")));
    }

    @Test
    void pathStopsAtSwitchWithNoOutDirection() {
        TraversalTrace trace = new TraversalTrace();
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode sw = switcher(trace, root, "s1", 1, "l1");
        trace.note(sw, "该道岔没有匹配此到达方向的出向");
        trace.note(sw, "后写的说明应被忽略");

        assertEquals("""
                起点[l1](world,0,64,0)
                ↓ switcher(world,1,64,1)[该道岔没有匹配此到达方向的出向]""", plain(trace.pathOfLine("l1")));
    }

    @Test
    void pathReportsLoopClosure() {
        TraversalTrace trace = new TraversalTrace();
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode a = station(trace, root, "s1", "环线站", 1, "l1");
        // a 的后继状态就是 a 自己（环线闭合回到已经过的节点）
        trace.link(a, "s1");

        assertTrue(plain(trace.pathOfLine("l1")).endsWith("[回到已经过的节点（环线闭合）]"));
    }

    @Test
    void pathReportsUnexpandedSuccessor() {
        TraversalTrace trace = new TraversalTrace();
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode a = station(trace, root, "s1", "车站A", 1, "l1");
        // 登记了后继但该状态从未被展开（遍历提前中止 / 达到段数上限）
        trace.link(a, "neverExpanded");

        assertTrue(plain(trace.pathOfLine("l1")).endsWith("[后续分支尚未展开（遍历提前结束）]"));
    }

    @Test
    void pathOfLineStartsAtIngressNodeWithContextPrefix() {
        TraversalTrace trace = new TraversalTrace();
        // l2 没有登记起点：它从 l1 的道岔分叉进来
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode sw = switcher(trace, root, "s1", 1, "l1");
        trace.link(sw, "s2");
        TraversalTrace.TraceNode ingress = station(trace, sw, "s2", "换线站", 2, "l2");
        trace.link(ingress, "s3");
        trace.arrive(ingress, "s3", TraversalTrace.Kind.END, null, "world", 3, 64, 3, "l2");

        // 链首的父节点（l1 的道岔）作为上下文前缀保留，并标出两侧线路
        assertEquals("""
                switcher(world,1,64,1){l1}
                ↓ 换线站-platform(world,2,64,2){l2}
                ↓ 断轨结束(world,3,64,3)""", plain(trace.pathOfLine("l2")));
    }

    @Test
    void pathDoesNotFollowOtherLineSuccessors() {
        TraversalTrace trace = new TraversalTrace();
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode sw = switcher(trace, root, "s1", 1, "l1");
        // 唯一后继属于另一条线：本线到此为止，不应跨线续行
        trace.link(sw, "s2");
        station(trace, sw, "s2", "别线站", 2, "l2");

        assertEquals("""
                起点[l1](world,0,64,0)
                ↓ switcher(world,1,64,1)[没有继续沿本线的出向]""", plain(trace.pathOfLine("l1")));
    }

    @Test
    void stationLookupIsScopedToLineAndPathToKeepsCrossLineNodes() {
        TraversalTrace trace = new TraversalTrace();
        TraversalTrace.TraceNode root = trace.seedRoot("l1", "world", 0, 64, 0);
        trace.link(root, "s1");
        TraversalTrace.TraceNode sw = switcher(trace, root, "s1", 1, "l1");
        trace.link(sw, "s2");
        station(trace, sw, "s2", "配置外站", 2, "l2");

        assertTrue(trace.findStations("l1", "配置外站").isEmpty());
        List<TraversalTrace.TraceNode> found = trace.findStations("l2", "配置外站");
        assertEquals(1, found.size());
        // 完整发现路径从登记起点开始，跨线部分不省略
        assertEquals("""
                起点[l1](world,0,64,0)
                ↓ switcher(world,1,64,1)
                ↓ 配置外站-platform(world,2,64,2){l2}""", plain(trace.pathTo(found.getFirst())));
    }

    @Test
    void seedSegmentMustBeLinkedOrPathBreaksImmediately() {
        // 回归：起点首段不经过道岔、没有 (节点,入向,出向,lineId) 状态，但 GraphWalk#seed 仍须给它编一个
        // seed key 并 link 到根节点。漏掉这一步时根节点没有后继，取路第一步就报「此处没有记录任何后继分支」。
        TraversalTrace linked = new TraversalTrace();
        TraversalTrace.TraceNode linkedRoot = linked.seedRoot("l1", "world", 0, 64, 0);
        linked.link(linkedRoot, "seed|0|l1");
        station(linked, linkedRoot, "seed|0|l1", "车站A", 1, "l1");
        assertEquals("""
                起点[l1](world,0,64,0)
                ↓ 车站A-platform(world,1,64,1)[此处没有记录任何后继分支]""", plain(linked.pathOfLine("l1")));

        TraversalTrace unlinked = new TraversalTrace();
        TraversalTrace.TraceNode unlinkedRoot = unlinked.seedRoot("l1", "world", 0, 64, 0);
        station(unlinked, unlinkedRoot, "seed|0|l1", "车站A", 1, "l1");
        // 没有 link：路径只有根节点，首段走到的车站接不上
        assertEquals("起点[l1](world,0,64,0)[此处没有记录任何后继分支]", plain(unlinked.pathOfLine("l1")));
    }

    @Test
    void unknownLineYieldsEmptyPath() {
        TraversalTrace trace = new TraversalTrace();
        trace.seedRoot("l1", "world", 0, 64, 0);

        assertTrue(trace.pathOfLine("nope").nodes().isEmpty());
        assertTrue(trace.pathOfLine(null).nodes().isEmpty());
        assertTrue(trace.pathTo(null).nodes().isEmpty());
    }
}
