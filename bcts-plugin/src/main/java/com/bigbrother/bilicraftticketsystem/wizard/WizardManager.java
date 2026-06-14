package com.bigbrother.bilicraftticketsystem.wizard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动配置向导的注册表。
 * <p>
 * 以玩家 UUID 为键，保存其正在进行的 {@link ConfigWizard}。聊天监听器据此把玩家输入转发到
 * 对应向导，掉线 / 完成 / 取消时移除。可点击按钮（[跳过]/[退出]）回调也经由此类分发。
 * <p>
 * 注册表用 {@link ConcurrentHashMap}，因为聊天事件在异步线程读取 {@link #isActive}；
 * 实际的向导推进（{@code skip/cancel/handleInput}）应在主线程进行。
 */
public final class WizardManager {
    private static final Map<UUID, ConfigWizard> active = new ConcurrentHashMap<>();

    private WizardManager() {
    }

    /**
     * 启动一个向导：登记并发送首步提示。须在主线程调用。
     * 若该玩家已有进行中的向导，会先取消旧的。
     *
     * @param wizard 向导实例
     */
    public static void start(ConfigWizard wizard) {
        UUID uuid = wizard.getPlayer().getUniqueId();
        ConfigWizard old = active.remove(uuid);
        if (old != null) {
            old.cancel();
        }
        active.put(uuid, wizard);
        wizard.start();
    }

    /**
     * 该玩家是否有进行中的向导（聊天监听据此判断是否拦截）。
     *
     * @param uuid 玩家 UUID
     * @return true 表示有
     */
    public static boolean isActive(UUID uuid) {
        return active.containsKey(uuid);
    }

    /**
     * 取玩家当前向导。
     *
     * @param uuid 玩家 UUID
     * @return 向导，无则 null
     */
    public static ConfigWizard get(UUID uuid) {
        return active.get(uuid);
    }

    /**
     * 把一行输入交给玩家当前向导处理。须在主线程调用。
     *
     * @param uuid  玩家 UUID
     * @param input 输入（已 trim）
     */
    public static void handleInput(UUID uuid, String input) {
        ConfigWizard wizard = active.get(uuid);
        if (wizard != null) {
            wizard.handleInput(input);
        }
    }

    /**
     * 跳过玩家当前向导的当前步骤。须在主线程调用（[跳过] 按钮回调）。
     *
     * @param uuid 玩家 UUID
     */
    public static void skip(UUID uuid) {
        ConfigWizard wizard = active.get(uuid);
        if (wizard != null) {
            wizard.skipCurrent();
        }
    }

    /**
     * 取消玩家当前向导并移除（[退出] 按钮回调或掉线）。
     *
     * @param uuid 玩家 UUID
     */
    public static void cancel(UUID uuid) {
        ConfigWizard wizard = active.remove(uuid);
        if (wizard != null) {
            wizard.cancel();
        }
    }

    /**
     * 保存并退出玩家当前向导（[保存并退出] 按钮回调，仅修改模式有效）。
     *
     * @param uuid 玩家 UUID
     */
    public static void saveAndExit(UUID uuid) {
        ConfigWizard wizard = active.get(uuid);
        if (wizard != null) {
            wizard.saveAndExit();
        }
    }

    /**
     * 完成并移除玩家当前向导（不发取消提示），由向导内部走完全部步骤时调用。
     *
     * @param uuid 玩家 UUID
     */
    public static void finish(UUID uuid) {
        active.remove(uuid);
    }
}
