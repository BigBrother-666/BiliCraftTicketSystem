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
     */
    private final Function<String, Result> parser;

    /**
     * @param key      存储键
     * @param prompt   提示语
     * @param required 是否必填
     * @param parser   输入解析/校验函数
     */
    public WizardStep(String key, Component prompt, boolean required, Function<String, Result> parser) {
        this.key = key;
        this.prompt = prompt;
        this.required = required;
        this.parser = parser;
    }
}
