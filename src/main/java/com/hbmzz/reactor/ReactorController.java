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

import com.hbmzz.reactor.api.ReactorView;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * 反应堆方块实体的抽象基类，也是唯一的状态权威。
 *
 * <p>方块层只把交互转成对这个类的方法调用；网络层只读取它的快照；渲染层只拿到
 * {@link ReactorView}。相位推进委托给 {@link ReactorState} 这个纯函数，TE 自身
 * 只负责计数、持久化与注册。所有 NBT 键都是本模块自有前缀。</p>
 */
public abstract class ReactorController extends TileEntity implements ReactorView {

    private String id = "unbound";
    private ReactorTier tier = ReactorTier.MARK_I;
    private ReactorState.Phase phase = ReactorState.Phase.OFFLINE;
    private int ticksInPhase;
    private double heat;
    private double integrity = 1.0D;
    private boolean fuelValid;
    private boolean coolingOK = true;
    private boolean registered;

    /** 由方块在放置时绑定稳定 id；保存/读取由 NBT 负责。 */
    public final void bindId(String id) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final int getTierId() {
        return tier.id();
    }

    @Override
    public final String getPhase() {
        return phase.name();
    }

    @Override
    public final double getHeat() {
        return heat;
    }

    @Override
    public final double getIntegrity() {
        return integrity;
    }

    @Override
    public final boolean isRunning() {
        return ReactorState.isActive(phase);
    }

    public final ReactorTier getTier() {
        return tier;
    }

    /** 切换等级会重置相位到 OFFLINE，避免用低堆阈值判断高堆读数。 */
    public final void setTier(ReactorTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("tier must not be null");
        }
        this.tier = tier;
        this.phase = ReactorState.Phase.OFFLINE;
        this.ticksInPhase = 0;
    }

    public final void setFuelValid(boolean fuelValid) {
        this.fuelValid = fuelValid;
    }

    public final void setCoolingOK(boolean coolingOK) {
        this.coolingOK = coolingOK;
    }

    public final void setHeat(double heat) {
        this.heat = Math.max(0.0D, heat);
    }

    public final void setIntegrity(double integrity) {
        this.integrity = Math.max(0.0D, Math.min(1.0D, integrity));
    }

    /** 手动紧急停堆；若已在终态则不回退。 */
    public final void scram() {
        if (!ReactorState.isTerminal(phase)) {
            phase = ReactorState.Phase.SCRAM;
            ticksInPhase = 0;
        }
    }

    /** 注册到自有注册表；重复调用幂等。 */
    public final void register() {
        if (!registered) {
            ReactorRegistry.instance().registerController(this);
            registered = true;
        }
    }

    @Override
    public void updateEntity() {
        tick();
    }

    /** 单个 tick 的推进；与具体 MC 版本的 tick 入口解耦。 */
    public final void tick() {
        ticksInPhase++;
        heat = Math.max(0.0D, heat + computeHeatDelta());

        ReactorState.Phase next = ReactorState.advance(
                phase, fuelValid, coolingOK, heat, tier, integrity, ticksInPhase);
        if (ReactorState.shouldResetTicks(phase, next)) {
            ReactorState.Phase previous = phase;
            phase = next;
            ticksInPhase = 0;
            onPhaseChanged(previous, next);
        }
        if (registered) {
            markDirty();
        }
    }

    /** 每个 tick 的净热量变化，由具体实现提供燃料/冷却模型。 */
    protected abstract double computeHeatDelta();

    /** 相位变化回调，供子类接入音效、红石输出等副作用。 */
    protected void onPhaseChanged(ReactorState.Phase previous, ReactorState.Phase current) {
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        id = nbt.getString("hbmzzReactorId");
        ReactorTier loadedTier = ReactorTier.fromId(nbt.getInteger("hbmzzReactorTier"));
        tier = loadedTier == null ? ReactorTier.MARK_I : loadedTier;
        phase = parsePhase(nbt.getString("hbmzzReactorPhase"));
        ticksInPhase = nbt.getInteger("hbmzzReactorTicks");
        heat = nbt.getDouble("hbmzzReactorHeat");
        integrity = nbt.getDouble("hbmzzReactorIntegrity");
        fuelValid = nbt.getBoolean("hbmzzReactorFuel");
        coolingOK = nbt.getBoolean("hbmzzReactorCooling");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("hbmzzReactorId", id);
        nbt.setInteger("hbmzzReactorTier", tier.id());
        nbt.setString("hbmzzReactorPhase", phase.name());
        nbt.setInteger("hbmzzReactorTicks", ticksInPhase);
        nbt.setDouble("hbmzzReactorHeat", heat);
        nbt.setDouble("hbmzzReactorIntegrity", integrity);
        nbt.setBoolean("hbmzzReactorFuel", fuelValid);
        nbt.setBoolean("hbmzzReactorCooling", coolingOK);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (registered) {
            ReactorRegistry.instance().unregisterController(id);
            registered = false;
        }
    }

    private static ReactorState.Phase parsePhase(String name) {
        for (ReactorState.Phase candidate : ReactorState.Phase.values()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return ReactorState.Phase.OFFLINE;
    }
}
