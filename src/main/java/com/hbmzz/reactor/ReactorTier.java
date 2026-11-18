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
 * Reactor规格（等级）与其固定参数。
 *
 * <p>数值全部是本模块自有定义，不对应任何诅咒版常量。等级是纯数据 enum：
 * 状态机只通过这里给出的阈值判断过热与输出，不关心等级从哪来。</p>
 */
public enum ReactorTier {

    /** 实验堆：小、慢、宽容，用于教学与早期游戏阶段。 */
    MARK_I(0, "Mk I", 64, 20, 800.0D, 8000, 100),

    /** 标准堆：主流配置。 */
    MARK_II(1, "Mk II", 256, 15, 1200.0D, 32000, 400),

    /** 增殖堆：大、快、阈值高，需要主动冷却。 */
    MARK_III(2, "Mk III", 1024, 10, 1600.0D, 128000, 1600),

    /** 实验性超大堆：仅供压力测试与后期玩法。 */
    EXPERIMENTAL(3, "Experimental", 4096, 5, 2000.0D, 512000, 6400);

    private final int id;
    private final String displayName;
    private final int maxColumns;
    private final int cycleTicks;
    private final double heatTrip;
    private final int steamCapacity;
    private final int outputPerCycle;

    ReactorTier(int id, String displayName, int maxColumns, int cycleTicks,
                double heatTrip, int steamCapacity, int outputPerCycle) {
        this.id = id;
        this.displayName = displayName;
        this.maxColumns = maxColumns;
        this.cycleTicks = cycleTicks;
        this.heatTrip = heatTrip;
        this.steamCapacity = steamCapacity;
        this.outputPerCycle = outputPerCycle;
    }

    public int id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** 允许的最大逻辑列数，超过则无法形成有效堆芯。 */
    public int maxColumns() {
        return maxColumns;
    }

    /** 一个输出周期的 tick 数。 */
    public int cycleTicks() {
        return cycleTicks;
    }

    /** 进入 SCRAM 的过热阈值。 */
    public double heatTrip() {
        return heatTrip;
    }

    /** 直接进入 MELTDOWN 的临界热量。 */
    public double criticalHeat() {
        return heatTrip * 2.0D;
    }

    public int steamCapacity() {
        return steamCapacity;
    }

    public int outputPerCycle() {
        return outputPerCycle;
    }

    /** 按 id 查等级，未知返回 {@code null}。 */
    public static ReactorTier fromId(int id) {
        for (ReactorTier tier : values()) {
            if (tier.id == id) {
                return tier;
            }
        }
        return null;
    }

    /** 按 id 查等级，未知抛出异常，用于必须存在的场景。 */
    public static ReactorTier require(int id) {
        ReactorTier tier = fromId(id);
        if (tier == null) {
            throw new IllegalArgumentException("Unknown reactor tier id: " + id);
        }
        return tier;
    }
}
