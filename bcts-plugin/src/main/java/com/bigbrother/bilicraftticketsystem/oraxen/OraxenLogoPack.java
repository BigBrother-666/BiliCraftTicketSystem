package com.bigbrother.bilicraftticketsystem.oraxen;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.utils.ImageUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent;
import io.th0rgal.oraxen.utils.VirtualFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 把各铁路系统的游戏内 logo 注入 Oraxen 资源包，并作为带模型的物品图标分发给玩家。
 * <p>
 * 监听 {@link OraxenPackGeneratedEvent}：Oraxen 每次生成资源包时（含服务器每日重启自动重载），
 * 遍历所有已上传 mc logo 的铁路系统，向打包产物追加三种虚拟文件——
 * <ul>
 *   <li>纹理：{@code assets/bcts/textures/system_logo/<id>.png}（玩家上传的 logo，已统一为 mc 分辨率）；</li>
 *   <li>模型：{@code assets/bcts/models/system_logo/<id>.json}（{@code item/generated} + 该纹理）；</li>
 *   <li>物品定义：{@code assets/bcts/items/system_logo/<id>.json}（1.21.4+ 的 {@code item_model} 组件目标）。</li>
 * </ul>
 * 系统选择界面的图标物品（PAPER）通过 {@link org.bukkit.inventory.meta.ItemMeta#setItemModel(NamespacedKey)}
 * 指向 {@code bcts:system_logo/<id>}，从而显示对应 logo。
 * <p>
 * 本类只在运行时存在 Oraxen 插件时才注册（见 {@code BiliCraftTicketSystem#initListeners}），
 * 因此引用 Oraxen 类是安全的（类在监听器实例化时才加载）。注入仅发生在 Oraxen 重新生成资源包时，
 * 本插件不主动触发下发——按部署约定，资源包随服务器每日重启由 Oraxen 自动重载并下发。
 */
public class OraxenLogoPack implements Listener {
    /**
     * 注入到资源包的命名空间。
     */
    public static final String NAMESPACE = "bcts";
    /**
     * logo 物品 / 模型 / 纹理在命名空间下的路径前缀。
     */
    private static final String LOGO_PATH = "system_logo/";

    /**
     * 取某铁路系统 logo 物品模型对应的 {@link NamespacedKey}（即 {@code item_model} 组件目标）。
     * <p>
     * systemId 会被规范化为合法的资源路径字符（小写，非法字符替换为下划线），
     * 注入资源包与设置物品模型用同一规范化结果，保证一一对应。
     *
     * @param systemId 铁路系统 id
     * @return logo 物品模型 key，形如 {@code bcts:system_logo/<id>}
     */
    public static NamespacedKey itemModelKey(String systemId) {
        return new NamespacedKey(NAMESPACE, LOGO_PATH + sanitize(systemId));
    }

    /**
     * 把 systemId 规范化为合法的资源路径片段：转小写，非 {@code [a-z0-9_.-]} 字符替换为下划线。
     *
     * @param systemId 原始系统 id
     * @return 规范化后的路径片段
     */
    private static String sanitize(String systemId) {
        return "railway_" + systemId.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    /**
     * 资源包生成时，把所有已上传 mc logo 的铁路系统的纹理 / 模型 / 物品定义追加进打包产物。
     *
     * @param event Oraxen 资源包生成事件（其 {@code output} 列表最终被写入资源包 zip）
     */
    @EventHandler
    public void onPackGenerated(OraxenPackGeneratedEvent event) {
        List<VirtualFile> output = event.getOutput();
        // 收集本次注入的 logo 纹理的 sprite 资源名，稍后登记进纹理图集，使 item/generated 能找到纹理
        List<String> spriteResources = new ArrayList<>();
        int injected = 0;
        for (RailwaySystemInfo system : RailwaySystemConfig.getSystems().values()) {
            String systemId = system.getId();
            File logo = ImageUtils.getSystemImageFileMc(systemId);
            if (!logo.isFile()) {
                continue;
            }
            try {
                byte[] textureBytes = Files.readAllBytes(logo.toPath());
                String id = sanitize(systemId);
                addFile(output, "assets/" + NAMESPACE + "/textures/" + LOGO_PATH, id + ".png", textureBytes);
                addFile(output, "assets/" + NAMESPACE + "/models/" + LOGO_PATH, id + ".json",
                        modelJson(id).getBytes(StandardCharsets.UTF_8));
                addFile(output, "assets/" + NAMESPACE + "/items/" + LOGO_PATH, id + ".json",
                        itemDefinitionJson(id).getBytes(StandardCharsets.UTF_8));
                spriteResources.add(NAMESPACE + ":" + LOGO_PATH + id);
                injected++;
            } catch (Exception e) {
                BiliCraftTicketSystem.plugin.getComponentLogger().warn(Component.text("注入铁路系统 logo 资源失败（" + systemId + "）：" + e.getMessage(), NamedTextColor.YELLOW));
            }
        }
        if (injected > 0) {
            registerSpritesInAtlas(output, spriteResources);
            BiliCraftTicketSystem.plugin.getComponentLogger().info(Component.text("已向 Oraxen 资源包注入 " + injected + " 个铁路系统 logo 物品", NamedTextColor.DARK_AQUA));
        }
    }

    /**
     * 把 logo 纹理登记进方块 / 物品纹理图集 {@code assets/minecraft/atlases/blocks.json}。
     * <p>
     * {@code item/generated} 模型的 {@code layer0} 纹理须经图集（atlas）解析为 sprite。Oraxen 生成的图集
     * 把每个纹理逐个列为 {@code single} 源，但**不含**本插件后注入的 {@code bcts} 命名空间纹理——
     * 缺失会导致客户端找不到 sprite、物品渲染成黑紫方块。因此这里把 Oraxen 已生成的图集读出来、
     * 为每张 logo 追加一条 {@code single} 源后写回；若图集不存在（理论上不会）则新建一个。
     *
     * @param output          打包产物列表（含 Oraxen 已生成的图集文件）
     * @param spriteResources 本次注入的各 logo 纹理资源名（形如 {@code bcts:system_logo/<id>}）
     */
    private void registerSpritesInAtlas(List<VirtualFile> output, List<String> spriteResources) {
        String parentFolder = "assets/minecraft/atlases";
        String name = "blocks.json";
        String path = parentFolder + "/" + name;
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root;
            ArrayNode sources;
            VirtualFile existing = output.stream().filter(vf -> vf.getPath().equals(path)).findFirst().orElse(null);
            if (existing != null) {
                root = (ObjectNode) mapper.readTree(existing.getInputStream());
                JsonNode srcNode = root.get("sources");
                sources = srcNode instanceof ArrayNode arr ? arr : root.putArray("sources");
            } else {
                root = mapper.createObjectNode();
                sources = root.putArray("sources");
            }
            for (String resource : spriteResources) {
                ObjectNode src = mapper.createObjectNode();
                src.put("type", "single");
                src.put("resource", resource);
                src.put("sprite", resource);
                sources.add(src);
            }
            byte[] bytes = mapper.writeValueAsBytes(root);
            addFile(output, parentFolder + "/", name, bytes);
        } catch (Exception e) {
            BiliCraftTicketSystem.plugin.getComponentLogger().warn(Component.text("登记铁路系统 logo 纹理图集失败：" + e.getMessage(), NamedTextColor.YELLOW));
        }
    }

    /**
     * 向打包产物追加一个虚拟文件；若同路径已存在则先移除（保证用最新的 logo 覆盖）。
     *
     * @param output       打包产物列表
     * @param parentFolder 父目录（不含末尾文件名）
     * @param name         文件名
     * @param bytes        文件内容
     */
    private void addFile(List<VirtualFile> output, String parentFolder, String name, byte[] bytes) {
        String path = parentFolder + name;
        output.removeIf(vf -> vf.getPath().equals(path));
        output.add(new VirtualFile(parentFolder, name, new ByteArrayInputStream(bytes)));
    }

    /**
     * 生成 logo 模型 json：{@code item/generated} 父模型 + logo 纹理。
     *
     * @param id 规范化后的系统 id
     * @return 模型 json 文本
     */
    private String modelJson(String id) {
        return "{\n"
                + "  \"parent\": \"minecraft:item/generated\",\n"
                + "  \"textures\": {\n"
                + "    \"layer0\": \"" + NAMESPACE + ":" + LOGO_PATH + id + "\"\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * 生成 1.21.4+ 物品定义 json：{@code item_model} 组件指向上面的模型。
     *
     * @param id 规范化后的系统 id
     * @return 物品定义 json 文本
     */
    private String itemDefinitionJson(String id) {
        return "{\n"
                + "  \"model\": {\n"
                + "    \"type\": \"minecraft:model\",\n"
                + "    \"model\": \"" + NAMESPACE + ":" + LOGO_PATH + id + "\"\n"
                + "  }\n"
                + "}\n";
    }
}
