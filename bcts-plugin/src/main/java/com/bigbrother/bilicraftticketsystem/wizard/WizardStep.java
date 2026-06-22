package com.bigbrother.bilicraftticketsystem.wizard;

import lombok.Getter;
import net.kyori.adventure.text.Component;

import java.util.function.Function;

/**
 * 配置向导中的一个步骤：引导玩家在聊天框输入一项配置值。
 * <p>
 * 每个步骤含提示语、是否必填、把玩家输入解析/校验为存储值的函数。解析函数返回
 * {@link Result}：成功带解析后的值，失败带错误提示（向导据此让玩家重输）。
 */
@Getter
public class WizardStep {
    /**
     * 解析结果：成功（带值）或失败（带错误提示）。
     */
    @Getter
    public static class Result {
        private final boolean ok;
        private final Object value;
        private final String error;

        private Result(boolean ok, Object value, String error) {
            this.ok = ok;
            this.value = value;
            this.error = error;
        }

        /**
         * @param value 解析后的存储值
         * @return 成功结果
         */
        public static Result ok(Object value) {
            return new Result(true, value, null);
        }

        /**
         * @param error 错误提示（展示给玩家）
         * @return 失败结果
         */
        public static Result error(String error) {
            return new Result(false, null, error);
        }

    }

    /**
     * 该步骤收集的值在 values 映射中的键（通常即配置字段名）。
     */
    private final String key;
    /**
     * 提示语（格式要求等），向导发给玩家。
     */
    private final Component prompt;
    /**
     * 是否必填。必填项在新建模式下不可跳过；选填项及修改模式下可跳过。
     */
    private final boolean required;
    /**
     * 把玩家输入字符串解析/校验为存储值的函数。
     * <p>
     * 同步步骤在主线程调用；{@link #async} 步骤则在异步线程调用（因此函数体内不可触碰非线程安全的
     * Bukkit API），由 {@link ConfigWizard} 负责切回主线程处理 {@link Result}。
     */
    private final Function<String, Result> parser;
    /**
     * 是否为异步步骤：true 时 {@link #parser} 会被放到异步线程执行（用于网络下载等耗时操作），
     * 并受 {@link #timeoutMillis} 超时保护，防止卡住服务器主线程。
     */
    private final boolean async;
    /**
     * 异步步骤的超时时间（毫秒）；非异步步骤忽略此值。超时后向导放弃本次处理并提示玩家重输。
     */
    private final long timeoutMillis;

    /**
     * 构造同步步骤。
     *
     * @param key      存储键
     * @param prompt   提示语
     * @param required 是否必填
     * @param parser   输入解析/校验函数（主线程执行）
     */
    public WizardStep(String key, Component prompt, boolean required, Function<String, Result> parser) {
        this(key, prompt, required, parser, false, 0L);
    }

    /**
     * 构造异步步骤：{@code parser} 在异步线程执行并受超时保护。
     *
     * @param key           存储键
     * @param prompt        提示语
     * @param required      是否必填
     * @param parser        输入解析/校验函数（异步线程执行，禁止调用非线程安全的 Bukkit API）
     * @param timeoutMillis 超时时间（毫秒）
     */
    public WizardStep(String key, Component prompt, boolean required, Function<String, Result> parser, long timeoutMillis) {
        this(key, prompt, required, parser, true, timeoutMillis);
    }

    private WizardStep(String key, Component prompt, boolean required, Function<String, Result> parser,
                       boolean async, long timeoutMillis) {
        this.key = key;
        this.prompt = prompt;
        this.required = required;
        this.parser = parser;
        this.async = async;
        this.timeoutMillis = timeoutMillis;
    }
}
