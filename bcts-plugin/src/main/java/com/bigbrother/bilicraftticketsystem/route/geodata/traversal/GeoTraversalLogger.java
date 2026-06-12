package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * 遍历日志记录器：把遍历过程（关键控制牌、车站名核对、错误 + stack trace）写到
 * {@code <插件目录>/logs/<时间戳>.log}（UTF-8，不输出控制台），并可同时把重要消息发给发起玩家。
 * <p>
 * 遍历是离线难以测试的实时寻路过程，详尽的日志是排查游戏内问题的主要手段。
 */
public class GeoTraversalLogger {
    private final BiliCraftTicketSystem plugin;
    private final CommandSender sender;
    private final Logger logger;

    /**
     * @param plugin 插件实例
     * @param sender 发起遍历的玩家 / 控制台（用于同步发送重要消息，可为 null）
     */
    public GeoTraversalLogger(BiliCraftTicketSystem plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
        this.logger = createLogger();
    }

    private Logger createLogger() {
        Logger log = Logger.getLogger("bcts-railgeo-" + System.identityHashCode(this));
        log.setUseParentHandlers(false);
        try {
            File logDir = plugin.getDataFolder().toPath().resolve("logs").toFile();
            if (!logDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                logDir.mkdirs();
            }
            String timeStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filePath = logDir.getAbsolutePath() + File.separator + "railgeo_" + timeStr + ".log";
            FileHandler fileHandler = new FileHandler(filePath, false);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(new SimpleFormatter());
            log.addHandler(fileHandler);
        } catch (Exception e) {
            plugin.getLogger().severe("无法创建遍历日志文件: " + e.getMessage());
        }
        return log;
    }

    /**
     * 记录一条普通信息。
     *
     * @param msg 信息
     */
    public void info(String msg) {
        if (logger != null) {
            logger.info(msg);
        }
    }

    /**
     * 记录一条警告（如车站名与配置顺序不符）。
     *
     * @param msg 警告内容
     */
    public void warn(String msg) {
        if (logger != null) {
            logger.warning(msg);
        }
    }

    /**
     * 记录一条错误（含异常 stack trace）。
     *
     * @param msg 错误描述
     * @param e   异常（可为 null）
     */
    public void error(String msg, Throwable e) {
        if (logger != null) {
            logger.severe(msg + (e == null ? "" : "\n" + stackTrace(e)));
        }
    }

    /**
     * 记录信息并发送给发起者。
     *
     * @param msg   信息
     * @param color 消息颜色
     */
    public void message(String msg, NamedTextColor color) {
        info(msg);
        if (sender != null) {
            sender.sendMessage(Component.text(msg, color));
        }
    }

    private String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * 关闭日志文件句柄。遍历结束必须调用。
     */
    public void close() {
        if (logger == null) {
            return;
        }
        for (Handler handler : logger.getHandlers()) {
            handler.flush();
            handler.close();
            logger.removeHandler(handler);
        }
    }
}
