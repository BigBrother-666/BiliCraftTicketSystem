package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.Direction;
import lombok.Getter;

/**
 * bcswitcher 道岔控制牌的一个分支声明：{@code <方向>@<线路id>}。
 * <p>
 * 例如 {@code e@pr-cw} 表示「向东的轨道属于 pr-cw 线路」，{@code l@contact} 表示
 * 「左侧的轨道是联络线」，{@code f@default} 表示「前方是到发线」。
 * <p>
 * 方向沿用 traincarts 的写法：绝对方向 e/s/w/n，或相对牌子的 f/b/l/r，解析交给
 * {@link Direction#parse(String)}。线路 id 为 routes.yml 中的线路 id，或特殊 id
 * contact（联络线）/ default（到发线）。
 */
@Getter
public class BcSwitcherBranch {
    /**
     * 方向原始字符串（如 "e"、"l"、"f"）。
     */
    private final String directionStr;
    /**
     * 解析后的 traincarts 方向，解析失败为 {@link Direction#NONE}。
     */
    private final Direction direction;
    /**
     * 线路 id（或 contact / default）。
     */
    private final String lineId;

    /**
     * @param directionStr 方向字符串
     * @param lineId       线路 id
     */
    public BcSwitcherBranch(String directionStr, String lineId) {
        this.directionStr = directionStr;
        this.direction = Direction.parse(directionStr);
        this.lineId = lineId;
    }

    /**
     * 该分支是否为联络线。
     *
     * @return true 表示联络线
     */
    public boolean isContact() {
        return "contact".equals(lineId);
    }

    /**
     * 该分支是否为到发线。
     *
     * @return true 表示到发线
     */
    public boolean isDefault() {
        return "default".equals(lineId);
    }

    @Override
    public String toString() {
        return directionStr + "@" + lineId;
    }
}
