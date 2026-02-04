package com.bigbrother.bilicraftticketsystem.commands.argument;

import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask.PRGeoWalkingPoint;
import org.checkerframework.checker.units.qual.C;
import org.incendo.cloud.annotations.parser.Parser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

public class CommandParsers {
    @Parser(name = "lineType", suggestions = "lineType")
    public PRGeoWalkingPoint.LineType lineTypeParser(CommandInput input) {
        return PRGeoWalkingPoint.LineType.valueOf(input.readString());
    }

    @Parser(name = "platformTag", suggestions = "platformTag")
    public MermaidGraph.Node platformTagParser(CommandInput input) {
        MermaidGraph.Node node = TrainRoutes.graph.getNodeFromPtag(input.readString());
        if (node == null) {
            throw new IllegalArgumentException("站台tag不存在");
        }
        return node;
    }

    @Parser(name = "statisticsType", suggestions = "statisticsType")
    public StatisticsType statisticsTypeParser(CommandInput input) {
        StatisticsType type = StatisticsType.fromName(input.readString());
        if (type == null) {
            throw new IllegalArgumentException("统计类型不存在");
        }
        return type;
    }
}
