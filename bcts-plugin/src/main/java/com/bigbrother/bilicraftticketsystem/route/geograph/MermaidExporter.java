package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 {@link GeoRouteGraph}（geojson 反向构建的路由图）导出为 Mermaid 流程图（.mmd）。
 * <p>
 * 导出两类文件到目标目录：
 * <ul>
 *   <li>{@code graph.mmd}：整张图（所有节点 + 所有边）。</li>
 *   <li>{@code line_<lineId>.mmd}：每条线路一份子图（仅该线路的边及其两端节点）。</li>
 * </ul>
 * 节点文字：{@code 节点类型@(x,y,z)} 后接 {@code -车站名}（车站节点才有）；
 * 边标签：{@code 线路id | 线路名 | 长度m}。
 */
public final class MermaidExporter {

    private MermaidExporter() {
    }

    /**
     * 导出整张图与各线路子图到 {@code outDir}。
     *
     * @param graph  路由图
     * @param outDir 输出目录（不存在则创建）
     * @return 实际写出的文件数
     * @throws IOException 写文件失败
     */
    public static int exportAll(GeoRouteGraph graph, File outDir) throws IOException {
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("无法创建输出目录：" + outDir.getAbsolutePath());
        }

        // 收集全部边（图只暴露 allNodes + links(nodeId)，遍历每个节点的出边即得全部边）
        List<GeoLink> allLinks = new ArrayList<>();
        for (GeoNode node : graph.allNodes()) {
            allLinks.addAll(graph.links(node.getId()));
        }

        int count = 0;

        // 1) 整张图
        File full = new File(outDir, "graph.mmd");
        writeMermaid(full, graph, graph.allNodes(), allLinks);
        count++;

        // 2) 按 lineId 分组的子图（保持出现顺序）
        Map<String, List<GeoLink>> byLine = new LinkedHashMap<>();
        for (GeoLink link : allLinks) {
            byLine.computeIfAbsent(link.getLineId(), k -> new ArrayList<>()).add(link);
        }
        for (Map.Entry<String, List<GeoLink>> entry : byLine.entrySet()) {
            String lineId = entry.getKey();
            List<GeoLink> lineLinks = entry.getValue();
            // 子图节点 = 该线路边涉及的两端节点
            Set<GeoNode> lineNodes = new LinkedHashSet<>();
            for (GeoLink link : lineLinks) {
                GeoNode from = graph.getNode(link.getFromNodeId());
                GeoNode to = graph.getNode(link.getToNodeId());
                if (from != null) {
                    lineNodes.add(from);
                }
                if (to != null) {
                    lineNodes.add(to);
                }
            }
            File lineFile = new File(outDir, "line_" + sanitizeFileName(lineId) + ".mmd");
            writeMermaid(lineFile, graph, lineNodes, lineLinks);
            count++;
        }

        return count;
    }

    /**
     * 写一份 Mermaid flowchart 文件。
     *
     * @param file  目标文件
     * @param graph 路由图（用于补全边端点未在 nodes 集合时的兜底）
     * @param nodes 要声明的节点集合
     * @param links 要画的边集合
     */
    private static void writeMermaid(File file, GeoRouteGraph graph, Iterable<GeoNode> nodes, List<GeoLink> links)
            throws IOException {
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            w.write("graph TD\n");
            // 节点声明
            for (GeoNode node : nodes) {
                w.write("    ");
                w.write(mermaidId(node.getId()));
                w.write("[\"");
                w.write(escape(nodeLabel(graph, node)));
                w.write("\"]\n");
            }
            w.write("\n");
            // 边（记录顺序索引，便于随后用 linkStyle 按线路色染色）
            List<String> linkStyles = new ArrayList<>();
            int idx = 0;
            for (GeoLink link : links) {
                w.write("    ");
                w.write(mermaidId(link.getFromNodeId()));
                w.write(" -->|\"");
                w.write(escape(edgeLabel(link)));
                w.write("\"| ");
                w.write(mermaidId(link.getToNodeId()));
                w.write("\n");
                String color = normalizeHexColor(link.getColor());
                if (color != null) {
                    linkStyles.add("    linkStyle %d stroke:%s,stroke-width:2px;".formatted(idx, color));
                }
                idx++;
            }
            // 边染色：每条边按其线路标志色（GeoLink.color，十六进制）描边
            if (!linkStyles.isEmpty()) {
                w.write("\n");
                for (String style : linkStyles) {
                    w.write(style);
                    w.write("\n");
                }
            }
        }
    }

    /**
     * 节点文字：
     * <ul>
     *   <li>车站：{@code station@(x,y,z)-车站名}</li>
     *   <li>道岔：{@code switch@(x,y,z)} 后换行附该道岔各出向分支（等价于游戏内 bcswitcher 第三、四行
     *       {@code <出向>@<线路id>[;线路id...]}），由该节点全图出边按 departDirection 分组重建。</li>
     * </ul>
     */
    private static String nodeLabel(GeoRouteGraph graph, GeoNode node) {
        String base = "%s@(%d,%d,%d)".formatted(
                node.getType(),
                (int) Math.round(node.getX()),
                (int) Math.round(node.getY()),
                (int) Math.round(node.getZ()));
        if (node.isStation()) {
            return node.getName() != null && !node.getName().isEmpty() ? base + "-" + node.getName() : base;
        }
        // 道岔：附分支信息（bcswitcher 三、四行）
        String branches = switchBranchInfo(graph, node);
        return branches.isEmpty() ? base : base + "<br/>" + branches;
    }

    /**
     * 由道岔节点的全图出边重建 bcswitcher 第三、四行分支信息，按<b>入向（到达面）分组</b>。
     * <p>
     * 同一物理方块上可能有多块进入方向不同的 bcswitcher（如 {@code [+train:l]} 与 {@code [+train:r]}），
     * 它们在图里塌缩为同一节点。为让导出的图能区分「从哪个方向来 → 走哪些出向」，先按出边的
     * {@link GeoLink#getEnterFacesFrom()}（到达面）分组，每组内再按物理出向 {@code departDirection}
     * 汇总线路 id，拼成 {@code <出向>@<线路id>[;线路id...]}。
     * <ul>
     *   <li>节点只有<b>单一到达面</b>（普通道岔）时省略入向前缀，与旧显示一致。</li>
     *   <li>存在<b>多个到达面</b>（同块多块牌合并）时，每个入向单独一行，前缀 {@code [入向X来]}，
     *       X 为到达面的方位（E/W/S/N，无法识别则用原始面 key）。</li>
     *   <li>旧 geojson 无到达面信息的出边归入 {@code ?} 组。</li>
     * </ul>
     *
     * @param graph 路由图
     * @param node  道岔节点
     * @return 分支信息（无出边 / 无出向记录时为空串）
     */
    private static String switchBranchInfo(GeoRouteGraph graph, GeoNode node) {
        // 到达面 -> (物理出向 -> 线路 id 集合)。到达面为空的出边归入 "?"。
        Map<String, Map<String, Set<String>>> byFace = new LinkedHashMap<>();
        for (GeoLink out : graph.links(node.getId())) {
            String dir = out.getDepartDirection() == null || out.getDepartDirection().isEmpty()
                    ? "?" : out.getDepartDirection();
            Set<String> faces = out.getEnterFacesFrom();
            if (faces.isEmpty()) {
                byFace.computeIfAbsent("?", k -> new LinkedHashMap<>())
                        .computeIfAbsent(dir, k -> new LinkedHashSet<>()).add(out.getLineId());
            } else {
                for (String face : faces) {
                    byFace.computeIfAbsent(face, k -> new LinkedHashMap<>())
                            .computeIfAbsent(dir, k -> new LinkedHashSet<>()).add(out.getLineId());
                }
            }
        }
        // 单一到达面：省略入向前缀，保持普通道岔的简洁显示（每个出向一行，与旧显示一致）
        boolean singleFace = byFace.size() <= 1;
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> faceEntry : byFace.entrySet()) {
            String prefix = singleFace ? "" : "[入向: " + faceLabel(faceEntry.getKey()) + "] ";
            for (Map.Entry<String, Set<String>> dirEntry : faceEntry.getValue().entrySet()) {
                lines.add(prefix + dirEntry.getKey() + "@" + String.join(";", dirEntry.getValue()));
            }
        }
        return String.join("<br/>", lines);
    }

    /**
     * 把到达面 key（{@link com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GraphWalk#faceKey}，
     * 形如 {@code "1_0"} / {@code "1_1"}）转成可读方位。
     * <p>
     * faceKey 由方向向量的 {@code (signum(x), signum(z))} 组成，沿用 Minecraft 坐标约定
     * （+X=东、-X=西、+Z=南、-Z=北）。对角轨道时 x、z 可能同时非零，故按分量组合方位：
     * <ul>
     *   <li>正向：{@code 1_0→E}、{@code -1_0→W}、{@code 0_1→S}、{@code 0_-1→N}</li>
     *   <li>对角：{@code 1_1→SE}、{@code 1_-1→NE}、{@code -1_1→SW}、{@code -1_-1→NW}</li>
     * </ul>
     * 格式不符（如 {@code "?"} 或非 {@code x_z} 结构）时原样返回。
     *
     * @param faceKey 到达面 key
     * @return 方位字母（N/S/E/W 及其组合）或原始 key
     */
    private static String faceLabel(String faceKey) {
        String[] parts = faceKey.split("_");
        if (parts.length != 2) {
            return faceKey;
        }
        int x;
        int z;
        try {
            x = Integer.parseInt(parts[0]);
            z = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return faceKey;
        }
        // 南北（Z 轴，-Z=北 +Z=南）在前、东西（X 轴，+X=东 -X=西）在后，与常见方位读法一致
        StringBuilder sb = new StringBuilder();
        if (z < 0) {
            sb.append('N');
        } else if (z > 0) {
            sb.append('S');
        }
        if (x > 0) {
            sb.append('E');
        } else if (x < 0) {
            sb.append('W');
        }
        return sb.isEmpty() ? faceKey : sb.toString();
    }

    /**
     * 边标签：{@code <出向>: 线路id | 线路名 | 长度m}。出向取 {@link GeoLink#getDepartDirection()}
     * （如 {@code l:} / {@code r:}）；无出向（platform 续行段 / 起点首段）时省略前缀。
     * 线路名取 {@link LineConfig}，缺省回退 lineId；长度取 {@link GeoLink#getDistance()}。
     */
    private static String edgeLabel(GeoLink link) {
        String lineId = link.getLineId();
        LineInfo info = LineConfig.get(lineId);
        String lineName = info == null || info.getLineName() == null ? lineId : info.getLineName();
        String body = "%s | %s | %dm".formatted(lineId, lineName, (int) Math.round(link.getDistance()));
        String dir = link.getDepartDirection();
        return dir == null || dir.isEmpty() ? body : dir + ": " + body;
    }

    /**
     * 把 geojson 节点 id（含 {@code .}、{@code -} 等）转成 Mermaid 合法节点标识（只留字母数字与下划线）。
     */
    private static String mermaidId(String nodeId) {
        StringBuilder sb = new StringBuilder("N");
        for (int i = 0; i < nodeId.length(); i++) {
            char c = nodeId.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    /**
     * 转义 Mermaid 标签里的引号（双引号会截断 {@code ["..."]}）。
     */
    private static String escape(String text) {
        return text.replace("\"", "&quot;");
    }

    /**
     * 把任意 color 值归一化为 Mermaid 可用的 {@code #RRGGBB} / {@code #RGB} 十六进制串。
     * <p>
     * 线路色来源（配置 / geojson）可能带前后空白、缺 {@code #}、或夹杂非 ASCII 字符（这些会让
     * Mermaid 的 linkStyle 解析报 {@code UNICODE_TEXT} 错）。这里只挑出其中的十六进制字符，
     * 取前 6 位（或 3 位）拼成标准色；提取不到合法长度时返回 null（调用方跳过染色）。
     *
     * @param raw 原始颜色串
     * @return 归一化的 {@code #RRGGBB} / {@code #RGB}；无法识别返回 null
     */
    private static String normalizeHexColor(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                hex.append(c);
            }
        }
        if (hex.length() >= 6) {
            return "#" + hex.substring(0, 6);
        }
        if (hex.length() == 3) {
            return "#" + hex;
        }
        return null;
    }

    /**
     * 把 lineId 转成安全文件名（去掉路径分隔符等）。
     */
    private static String sanitizeFileName(String lineId) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineId.length(); i++) {
            char c = lineId.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        }
        return sb.isEmpty() ? "unnamed" : sb.toString();
    }
}
