package com.bigbrother.bilicraftticketsystem.commands.argument;

import org.incendo.cloud.annotations.parser.Parser;
import org.incendo.cloud.context.CommandInput;

public class CommandParsers {
    @Parser(name = "statisticsType", suggestions = "statisticsType")
    public StatisticsType statisticsTypeParser(CommandInput input) {
        StatisticsType type = StatisticsType.fromName(input.readString());
        if (type == null) {
            throw new IllegalArgumentException("统计类型不存在");
        }
        return type;
    }
}
