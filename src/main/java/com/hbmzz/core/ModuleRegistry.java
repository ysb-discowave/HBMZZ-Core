/*
 * HBMZZ-Core - Independent HBMZZ core
 * Copyright (C) 2026 HBMZZ contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hbmzz.core;

import java.util.Collection;

/**
 * 模块注册接口。
 *
 * <p>各功能模块实现 {@link Module} 契约，并通过 {@link #register(Module)} 注册进来。
 * {@link HbmzzModules} 作为模块加载门面实现本接口，对外提供实现。
 *
 * <p>本接口位于 {@code com.hbmzz.core}，不依赖诅咒版任何类型。
 */
public interface ModuleRegistry {

    /** 注册一个模块；同一 id 重复注册时实现方可选择覆盖或拒绝。 */
    void register(Module module);

    /** 按 id 查询已注册模块；不存在返回 {@code null}。 */
    Module get(String id);

    /** 是否已注册指定 id 的模块。 */
    boolean has(String id);

    /** 当前已注册模块的只读快照。 */
    Collection<Module> modules();

    /**
     * 一个可注册模块的最小生命周期契约。
     *
     * <p>模块通过 {@link #initialize(HbmzzModules)} 收到的上下文拿到 reactor 注册门面，
     * 因此模块本身也只面向接口编程，不接触具体反应堆实现类。
     */
    interface Module {

        /** 模块唯一标识。 */
        String id();

        /** 初始化；可在此通过 context 注册 {@code com.hbmzz.reactor.ReactorController}。 */
        default void initialize(HbmzzModules context) {
        }

        /** 每 tick 调用一次。 */
        default void tick() {
        }

        /** 卸载/关闭时调用，释放资源。 */
        default void dispose() {
        }
    }
}
