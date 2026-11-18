/*
 * HBMZZ Core - independent reactor system.
 * Copyright (C) 2026 HBMZZ contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.hbmzz.reactor;

/**
 * 反应堆状态机：纯函数，只读 ticks 与读数，不接触世界、方块实体或客户端。
 *
 * <p>刻意做成纯逻辑，是为了能在没有游戏环境的情况下离线验证相位迁移。任何
 * 相位只能向前移动；{@link Phase#MELTDOWN} 与 {@link Phase#SPENT} 没有出口，
 * 因此一次事故不会在冷却后被重新倒计时。</p>
 */
public final class ReactorState {

    /** 默认启动斜坡长度（tick）。 */
    public static final int RAMP_TICKS = 100;

    /** 默认停堆冷却长度（tick）。 */
    public static final int COOLDOWN_TICKS = 200;

    /** 未指定等级时使用的过热阈值。 */
    public static final double DEFAULT_HEAT_TRIP = 1200.0D;

    /** 未指定等级时使用的熔毁阈值。 */
    public static final double DEFAULT_CRITICAL_HEAT = 2400.0D;

    /** 反应堆所处相位，ordinal 即迁移方向。 */
    public enum Phase {
        /** 停机：未装料或冷却失效。 */
        OFFLINE,
        /** 启动：功率斜坡上升。 */
        STARTING,
        /** 稳态运行：对外输出。 */
        RUNNING,
        /** 紧急停堆：余热排出中。 */
        SCRAM,
        /** 堆芯熔毁：终态。 */
        MELTDOWN,
        /** 安全停运：终态。 */
        SPENT
    }

    private ReactorState() {
    }

    /**
     * 推进一个 tick。
     *
     * @param phase         当前相位，{@code null} 视为 {@link Phase#OFFLINE}
     * @param fuelValid     燃料与结构是否有效
     * @param coolingOK     冷却是否可用
     * @param heat          当前热量
     * @param heatTrip      过热阈值
     * @param criticalHeat  熔毁阈值
     * @param integrity     堆芯完整度，0.0 表示已毁
     * @param ticksInPhase  已在当前相位停留的 tick 数
     * @return 下一相位
     */
    public static Phase advance(Phase phase, boolean fuelValid, boolean coolingOK,
                                double heat, double heatTrip, double criticalHeat,
                                double integrity, int ticksInPhase) {
        if (phase == null) {
            phase = Phase.OFFLINE;
        }
        if (isTerminal(phase)) {
            return phase;
        }
        if (integrity <= 0.0D || heat >= criticalHeat) {
            return Phase.MELTDOWN;
        }
        int ticks = Math.max(0, ticksInPhase);

        switch (phase) {
            case OFFLINE:
                return (fuelValid && coolingOK && heat < heatTrip)
                        ? Phase.STARTING : Phase.OFFLINE;
            case STARTING:
                if (!fuelValid || !coolingOK) {
                    return Phase.OFFLINE;
                }
                if (heat >= heatTrip) {
                    return Phase.SCRAM;
                }
                return ticks >= RAMP_TICKS ? Phase.RUNNING : Phase.STARTING;
            case RUNNING:
                return (heat >= heatTrip || !coolingOK) ? Phase.SCRAM : Phase.RUNNING;
            case SCRAM:
                return ticks >= COOLDOWN_TICKS ? Phase.SPENT : Phase.SCRAM;
            default:
                return phase;
        }
    }

    /** 使用给定等级的阈值推进。 */
    public static Phase advance(Phase phase, boolean fuelValid, boolean coolingOK,
                                double heat, ReactorTier tier, double integrity,
                                int ticksInPhase) {
        ReactorTier safe = tier == null ? ReactorTier.MARK_I : tier;
        return advance(phase, fuelValid, coolingOK, heat,
                safe.heatTrip(), safe.criticalHeat(), integrity, ticksInPhase);
    }

    /** 使用默认阈值推进，便于无等级场景与离线检查。 */
    public static Phase advance(Phase phase, boolean fuelValid, boolean coolingOK,
                                double heat, double integrity, int ticksInPhase) {
        return advance(phase, fuelValid, coolingOK, heat,
                DEFAULT_HEAT_TRIP, DEFAULT_CRITICAL_HEAT, integrity, ticksInPhase);
    }

    /** 相位变化后 tick 计数是否应归零。 */
    public static boolean shouldResetTicks(Phase before, Phase after) {
        return before != after;
    }

    /** 启动斜坡的加热系数，平方根曲线：开头是抖动，结尾是重击。 */
    public static double rampHeat(int ticksInPhase) {
        int span = Math.max(1, RAMP_TICKS);
        int ticks = Math.max(0, ticksInPhase);
        if (ticks >= span) {
            return 1.0D;
        }
        return Math.sqrt((double) ticks / (double) span);
    }

    /** 输出系数：仅稳态为 1，启动期按斜坡爬升，其余为 0。 */
    public static double outputFactor(Phase phase, int ticksInPhase) {
        if (phase == Phase.RUNNING) {
            return 1.0D;
        }
        if (phase == Phase.STARTING) {
            return rampHeat(ticksInPhase);
        }
        return 0.0D;
    }

    /** 是否正在输出能量。 */
    public static boolean isActive(Phase phase) {
        return phase == Phase.RUNNING;
    }

    /** 是否处于不可回退的终态。 */
    public static boolean isTerminal(Phase phase) {
        return phase == Phase.MELTDOWN || phase == Phase.SPENT;
    }
}
