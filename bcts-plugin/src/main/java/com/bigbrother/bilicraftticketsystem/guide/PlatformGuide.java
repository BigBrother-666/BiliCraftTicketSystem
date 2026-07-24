package com.bigbrother.bilicraftticketsystem.guide;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

/**
 * 「寻找上车站台」引导服务。玩家右键手中的车票后，用<b>动作栏方向罗盘</b>
 * （实时显示距离 + 朝向站台的箭头）与<b>目的地粒子光柱 + 头顶全息站名</b>引导其前往发车站台。
 * <p>
 * 每玩家至多一个进行中的引导 session（{@link #sessions}）。引导在以下任一情况结束：到达站台
 * （水平距离 &le; {@code guide.arrive-distance}）、主手不再是同一张车票、切换快捷栏槽位、
 * 玩家退出、跨世界、超时（{@code guide.timeout-seconds}）。
 * <p>
 * 全部为纯新增，不改动购票 / 寻路 / 计费逻辑。
 */
public final class PlatformGuide {
    private PlatformGuide() {
    }

    /**
     * 8 向箭头，下标 0 = 正前（↑），顺时针每 45° 一档。
     */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final Map<UUID, GuideSession> sessions = new ConcurrentHashMap<>();

    /**
     * 启动 / 重启对某玩家的站台引导。若该玩家已有引导，先清理旧的。
     *
     * @param player      被引导的玩家
     * @param target      站台目标位置（方块中心）
     * @param stationName 站台所在车站名（用于提示 / 全息文字）
     * @param nodeId      起点站台节点 id（校验主手仍是同一张车票）
     */
    public static void start(Player player, Location target, String stationName, String nodeId) {
        stop(player);
        GuideSession session = new GuideSession(player.getUniqueId(), target, stationName, nodeId);
        session.spawnHologram();
        session.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, session::tick, 0L, Math.max(1, MainConfig.guideUpdateIntervalTicks));
        sessions.put(player.getUniqueId(), session);
    }

    /**
     * 停止对某玩家的引导（取消定时任务 + 清理全息实体）。无进行中的引导时无操作。
     */
    public static void stop(Player player) {
        if (player != null) {
            stop(player.getUniqueId());
        }
    }

    /**
     * 停止对某玩家 UUID 的引导。
     */
    public static void stop(UUID uuid) {
        GuideSession session = sessions.remove(uuid);
        if (session != null) {
            session.cleanup();
        }
    }

    /**
     * 停止全部引导并清理残留全息实体。插件 onDisable 时调用。
     */
    public static void stopAll() {
        for (GuideSession session : sessions.values()) {
            session.cleanup();
        }
        sessions.clear();
    }

    /**
     * 单个玩家的引导会话。
     */
    private static final class GuideSession {
        private final UUID uuid;
        private final Location target;
        private final String stationName;
        private final String nodeId;
        private final long startMillis = System.currentTimeMillis();
        private BukkitTask task;
        private TextDisplay hologram;

        private GuideSession(UUID uuid, Location target, String stationName, String nodeId) {
            this.uuid = uuid;
            this.target = target;
            this.stationName = stationName;
            this.nodeId = nodeId;
        }

        private void spawnHologram() {
            if (!MainConfig.guideShowHologram || target.getWorld() == null) {
                return;
            }
            Location holoLoc = target.clone().add(0, MainConfig.guideBeaconHeight + 0.5, 0);
            hologram = target.getWorld().spawn(holoLoc, TextDisplay.class, display -> {
                display.text(CommonUtils.mmStr2Component(
                        message.get("guide-hologram", "<gold>上车站台 · %s").formatted(stationName)));
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setPersistent(false);
                display.setShadowed(true);
                // 略微放大，远处更醒目
                display.setTransformation(new Transformation(
                        new Vector3f(), new AxisAngle4f(), new Vector3f(MainConfig.guideHologramTextSize, MainConfig.guideHologramTextSize, MainConfig.guideHologramTextSize), new AxisAngle4f()));
            });
        }

        private void tick() {
            Player player = plugin.getServer().getPlayer(uuid);
            // 玩家离线 / 超时 / 主手不再是同一张车票 -> 结束
            if (player == null || !player.isOnline() || isTimedOut() || !holdsSameTicket(player)) {
                stop(uuid);
                return;
            }
            // 跨世界：无法方向引导，提示后结束
            if (target.getWorld() == null || !player.getWorld().equals(target.getWorld())) {
                player.sendActionBar(CommonUtils.mmStr2Component(
                        message.get("guide-other-world", "<red>上车站台在 %s 世界").formatted(target.getWorld().getName())));
                return;
            }

            Location eye = player.getLocation();
            double dx = target.getX() - eye.getX();
            double dz = target.getZ() - eye.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            // 到达
            if (horizontal <= MainConfig.guideArriveDistance) {
                player.sendActionBar(CommonUtils.mmStr2Component(
                        message.get("guide-arrived", "<green>已到达上车站台 · %s").formatted(stationName)));
                player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(
                                message.get("guide-arrived-msg", "您已到达 %s 的上车站台").formatted(stationName))
                        .decoration(TextDecoration.ITALIC, false)));
                stop(uuid);
                return;
            }

            sendActionBar(player, dx, dz, horizontal);
            spawnBeacon(player);
        }

        /**
         * 动作栏方向罗盘：距离 + 相对玩家视角的 8 向箭头。
         */
        private void sendActionBar(Player player, double dx, double dz, double horizontal) {
            String arrow = arrowFor(player.getLocation().getYaw(), dx, dz);
            Component bar = CommonUtils.mmStr2Component(
                    message.get("guide-actionbar", "<gold>前往 %s <yellow>%s <gray>%dm")
                            .formatted(stationName, arrow, Math.round(horizontal)));
            player.sendActionBar(bar);
        }

        /**
         * 目的地粒子光柱，仅对被引导玩家可见。可配置粒子类型、高度、直径与竖直间距。
         */
        private void spawnBeacon(Player player) {
            if (!MainConfig.guideShowBeacon || target.getWorld() == null) {
                return;
            }
            Particle particle = resolveParticle(MainConfig.guideBeaconParticle);
            // DUST 类粒子支持自定义颜色，其它粒子传 null（用粒子自身外观）
            Particle.DustOptions dust = particle == Particle.DUST
                    ? new Particle.DustOptions(parseBeaconColor(MainConfig.guideBeaconColor), 1.6f) : null;
            double radius = Math.max(0, MainConfig.guideBeaconDiameter) / 2.0;
            double gap = Math.max(0.1, MainConfig.guideBeaconVerticalGap);
            // 直径>0 时在竖线四周布一圈点形成一根有粗细的光柱，否则退化为单条居中竖线
            double[][] offsets = ringOffsets(radius);
            for (double y = 0; y <= MainConfig.guideBeaconHeight; y += gap) {
                for (double[] off : offsets) {
                    Location p = target.clone().add(off[0], y, off[1]);
                    player.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
                }
            }
        }

        private boolean isTimedOut() {
            long limit = MainConfig.guideTimeoutSeconds * 1000L;
            return limit > 0 && System.currentTimeMillis() - startMillis >= limit;
        }

        /**
         * 主手是否仍是绑定引导的那张车票（同一起点站台节点 id）。
         */
        private boolean holdsSameTicket(Player player) {
            BCTicket ticket = BCTicket.fromHeldItem(player);
            return ticket != null && nodeId.equals(ticket.getStartPlatformNodeId());
        }

        private void cleanup() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (hologram != null && !hologram.isDead()) {
                hologram.remove();
            }
            hologram = null;
        }
    }

    /**
     * 由目标相对玩家的方位角、减去玩家视角 yaw，归一化到 8 向箭头下标。
     * <p>
     * Minecraft yaw：0=+Z(南)，顺时针增大；{@code atan2(-dx, dz)} 得目标的世界方位角（同一约定），
     * 两者之差即目标在玩家视野中的相对角，映射到 {@link #ARROWS}（0=正前↑）。
     *
     * @param yaw 玩家视角 yaw（度）
     * @param dx  目标 x - 玩家 x
     * @param dz  目标 z - 玩家 z
     * @return 对应箭头
     */
    static String arrowFor(float yaw, double dx, double dz) {
        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetAngle - yaw;
        // 归一化到 [0,360)
        relative = ((relative % 360) + 360) % 360;
        int index = (int) Math.round(relative / 45.0) % 8;
        return ARROWS[index];
    }

    /**
     * 光柱横截面的粒子水平偏移点集：半径 &le; 0 时仅返回中心点（一条竖线），否则返回中心 + 一圈点，
     * 形成有粗细的光柱。
     *
     * @param radius 光柱半径（block）
     * @return 每个元素为 {@code {dx, dz}} 水平偏移
     */
    private static double[][] ringOffsets(double radius) {
        if (radius <= 0) {
            return new double[][]{{0, 0}};
        }
        int points = 8;
        double[][] offsets = new double[points + 1][2];
        offsets[0] = new double[]{0, 0};
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            offsets[i + 1] = new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
        }
        return offsets;
    }

    /**
     * 解析粒子类型枚举名，非法 / 缺失时回退 {@link Particle#DUST}。
     */
    private static Particle resolveParticle(String name) {
        if (name != null) {
            try {
                return Particle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 回退
            }
        }
        return Particle.DUST;
    }

    /**
     * 解析光柱颜色（{@code #RRGGBB}），非法时回退金色。
     */
    private static Color parseBeaconColor(String hex) {
        try {
            java.awt.Color awt = java.awt.Color.decode(hex);
            return Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue());
        } catch (NumberFormatException | NullPointerException e) {
            return Color.fromRGB(0xFF, 0xAA, 0x00);
        }
    }
}
