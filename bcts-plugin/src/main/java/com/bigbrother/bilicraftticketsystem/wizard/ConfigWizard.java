package com.bigbrother.bilicraftticketsystem.wizard;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天分步配置向导基类：依次引导玩家在聊天框输入若干配置项，收集完成后写回配置。
 * <p>
 * 由 {@link WizardManager} 管理生命周期，{@link com.bigbrother.bilicraftticketsystem.listeners.WizardListeners}
 * 把玩家聊天转发到 {@link #handleInput(String)}（已切回主线程）。子类通过 {@link #steps()} 定义步骤，
 * {@link #onComplete(Map)} 处理收集结果。
 * <p>
 * 两种模式：
 * <ul>
 *   <li><b>新建模式</b>（{@code editMode=false}）：必填项不可跳过，选填项可跳过（跳过=不写该项）。</li>
 *   <li><b>修改模式</b>（{@code editMode=true}）：所有项均可跳过（跳过=保留原值）；{@link #values}
 *       初始预填为现有配置值，提示时展示当前值。</li>
 * </ul>
 */
public abstract class ConfigWizard {
    @Getter
    protected final Player player;
    /**
     * 是否为修改模式。
     */
    protected final boolean editMode;
    /**
     * 已收集 / 现有的配置值（修改模式下初始为现有值）。
     */
    protected final Map<String, Object> values = new LinkedHashMap<>();

    private List<WizardStep> steps;
    private int index = 0;
    /**
     * 是否有异步步骤正在处理（如图片下载）。处理期间忽略玩家的后续聊天输入，避免重复触发。
     */
    private volatile boolean asyncRunning = false;

    /**
     * @param player   发起向导的玩家
     * @param editMode 是否修改模式
     */
    protected ConfigWizard(Player player, boolean editMode) {
        this.player = player;
        this.editMode = editMode;
    }

    /**
     * 子类定义本向导的所有步骤（按引导顺序）。
     *
     * @return 步骤列表
     */
    protected abstract List<WizardStep> steps();

    /**
     * 全部步骤完成后调用，子类据此写回配置并触发重载。
     *
     * @param collected 收集到的配置值（键为各 {@link WizardStep#getKey()}）
     */
    protected abstract void onComplete(Map<String, Object> collected);

    /**
     * 向导标题 / 简介，开始时发给玩家（如 "新建线路 pr-cw"）。
     *
     * @return 标题文本
     */
    protected abstract Component title();

    /**
     * 取某步骤在修改模式下用于展示的「当前值」文本；新建模式或无值返回 null。
     *
     * @param key 步骤键
     * @return 当前值展示文本，无则 null
     */
    protected String currentValueDisplay(String key) {
        Object v = values.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 启动向导：发送标题与第一步提示。必须在主线程调用。
     */
    public void start() {
        this.steps = steps();
        player.sendMessage(MainConfig.prefix.append(title()));
        if (editMode) {
            player.sendMessage(Component.text("在聊天框依次输入各项配置；随时可点 ", NamedTextColor.GRAY)
                    .append(saveExitButton())
                    .append(Component.text(" 直接保存，或点 ", NamedTextColor.GRAY))
                    .append(exitButton())
                    .append(Component.text(" 放弃修改。", NamedTextColor.GRAY)));
        } else {
            player.sendMessage(Component.text("在聊天框依次输入各项配置；随时可点 ", NamedTextColor.GRAY)
                    .append(exitButton())
                    .append(Component.text(" 放弃编辑。", NamedTextColor.GRAY)));
        }
        sendCurrentPrompt();
    }

    /**
     * 处理玩家一行聊天输入。必须在主线程调用（由 {@link com.bigbrother.bilicraftticketsystem.listeners.WizardListeners}
     * 切回主线程后调用）。
     * <p>
     * 同步步骤直接在主线程解析；{@link WizardStep#isAsync() 异步步骤}（如网络下载图片）则把解析函数
     * 放到独立线程执行，受超时保护，完成 / 超时后切回主线程处理结果，避免阻塞服务器主线程。
     *
     * @param input 玩家输入（已 trim）
     */
    public void handleInput(String input) {
        WizardStep step = steps.get(index);
        if (step.isAsync()) {
            handleAsyncInput(step, input);
            return;
        }
        WizardStep.Result result = step.getParser().apply(input);
        applyResult(result);
    }

    /**
     * 异步步骤处理：先发「处理中」提示，再开线程执行解析函数（带超时），结束后切回主线程推进。
     * 处理期间忽略玩家的后续输入（{@link #asyncRunning} 置位），防止重复触发。
     *
     * @param step  当前异步步骤
     * @param input 玩家输入
     */
    private void handleAsyncInput(WizardStep step, String input) {
        if (asyncRunning) {
            player.sendMessage(MainConfig.prefix
                    .append(Component.text("正在处理上一次输入，请稍候...", NamedTextColor.GRAY)));
            return;
        }
        asyncRunning = true;
        player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(
                MainConfig.message.get("wizard-async-processing", "<gray>正在处理，请稍候..."))));

        long timeout = step.getTimeoutMillis();
        BiliCraftTicketSystem plugin = BiliCraftTicketSystem.plugin;
        // 标记本次异步任务，超时回调与正常完成回调通过它判断谁先到，确保结果只处理一次
        AtomicBoolean settled = new AtomicBoolean(false);

        // 超时兜底：到点仍未完成则放弃本次处理，提示玩家重输
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (settled.compareAndSet(false, true)) {
                asyncRunning = false;
                player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(
                        MainConfig.message.get("wizard-async-timeout", "<red>处理超时，请重新输入。"))));
            }
        }, Math.max(1L, timeout / 50L));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            WizardStep.Result result;
            try {
                result = step.getParser().apply(input);
            } catch (Exception e) {
                result = WizardStep.Result.error("处理时发生错误：" + e.getMessage());
            }
            WizardStep.Result finalResult = result;
            // 切回主线程：判断是否已超时，未超时才取消超时任务并处理结果
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!settled.compareAndSet(false, true)) {
                    return; // 已超时处理过，丢弃迟到结果
                }
                timeoutTask.cancel();
                asyncRunning = false;
                // 玩家可能在处理期间已退出向导
                if (!WizardManager.isActive(player.getUniqueId()) || WizardManager.get(player.getUniqueId()) != this) {
                    return;
                }
                applyResult(finalResult);
            });
        });
    }

    /**
     * 处理一步的解析结果：成功则存值并推进，失败则提示重输。须在主线程调用。
     *
     * @param result 解析结果
     */
    private void applyResult(WizardStep.Result result) {
        WizardStep step = steps.get(index);
        if (!result.isOk()) {
            player.sendMessage(MainConfig.prefix
                    .append(Component.text(result.getError(), NamedTextColor.RED)));
            player.sendMessage(Component.text("请重新输入。", NamedTextColor.GRAY));
            return;
        }
        values.put(step.getKey(), result.getValue());
        advance();
    }

    /**
     * 跳过当前步骤（由 [跳过] 按钮回调触发）。
     */
    public void skipCurrent() {
        if (!WizardManager.isActive(player.getUniqueId()) || steps == null) {
            return;
        }
        WizardStep step = steps.get(index);
        if (!canSkip(step)) {
            player.sendMessage(MainConfig.prefix
                    .append(Component.text("该项为必填，不能跳过。", NamedTextColor.RED)));
            return;
        }
        // 新建模式跳过选填项：移除该键（不写）。修改模式跳过：保留预填的现有值。
        if (!editMode) {
            values.remove(step.getKey());
        }
        advance();
    }

    /**
     * 取消向导（由 [退出]/[放弃] 按钮回调或掉线触发）。
     */
    public void cancel() {
        player.sendMessage(MainConfig.prefix
                .append(Component.text("已放弃本次编辑。", NamedTextColor.YELLOW)));
    }

    /**
     * 保存并退出（仅修改模式可用，由 [保存并退出] 按钮回调触发）：把当前已收集 / 预填的值
     * 直接写回，不必走完剩余步骤。
     */
    public void saveAndExit() {
        if (!WizardManager.isActive(player.getUniqueId()) || steps == null) {
            return;
        }
        if (!editMode) {
            return;
        }
        WizardManager.finish(player.getUniqueId());
        complete();
    }

    private void advance() {
        index++;
        if (index >= steps.size()) {
            WizardManager.finish(player.getUniqueId());
            complete();
            return;
        }
        sendCurrentPrompt();
    }

    /**
     * 调用子类 {@link #onComplete} 写回配置，统一异常处理。
     */
    private void complete() {
        try {
            onComplete(values);
        } catch (Exception e) {
            player.sendMessage(MainConfig.prefix
                    .append(Component.text("保存配置时出错：" + e, NamedTextColor.RED)));
            BiliCraftTicketSystem.plugin.getLogger().warning("向导保存配置失败：" + e);
        }
    }

    private void sendCurrentPrompt() {
        WizardStep step = steps.get(index);
        Component header = Component.text("[%d/%d] ".formatted(index + 1, steps.size()), NamedTextColor.AQUA)
                .append(step.getPrompt());
        player.sendMessage(MainConfig.prefix.append(header));

        if (editMode) {
            String current = currentValueDisplay(step.getKey());
            player.sendMessage(Component.text("当前值：", NamedTextColor.GRAY)
                    .append(Component.text(current == null ? "（空）" : current, NamedTextColor.WHITE)));
        }

        Component actions = Component.text("可操作：", NamedTextColor.GRAY);
        if (canSkip(step)) {
            actions = actions.append(skipButton()).append(Component.text("  ", NamedTextColor.GRAY));
        }
        if (editMode) {
            actions = actions.append(saveExitButton()).append(Component.text("  ", NamedTextColor.GRAY));
        }
        actions = actions.append(exitButton());
        player.sendMessage(actions);
    }

    private boolean canSkip(WizardStep step) {
        return editMode || !step.isRequired();
    }

    private Component skipButton() {
        return Component.text(" [跳过] ", NamedTextColor.GREEN)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.callback(a -> WizardManager.skip(player.getUniqueId())));
    }

    private Component saveExitButton() {
        return Component.text(" [保存并退出] ", NamedTextColor.AQUA)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.callback(a -> WizardManager.saveAndExit(player.getUniqueId())));
    }

    private Component exitButton() {
        String label = editMode ? " [放弃] " : " [退出] ";
        return Component.text(label, NamedTextColor.RED)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.callback(a -> WizardManager.cancel(player.getUniqueId())));
    }
}
