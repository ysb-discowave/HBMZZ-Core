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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

import com.hbmzz.reactor.ReactorController;
import com.hbmzz.reactor.ReactorRegistry;

/**
 * 模块加载门面。
 *
 * <p>持有模块注册表与 reactor 注册门面（仅接口类型），提供注册、查询与生命周期入口。
 * 本类只通过 {@code com.hbmzz.reactor.ReactorRegistry} / {@code ReactorController} 接口
 * 使用反应堆能力，不 {@code new} 任何具体实现，也不引用诅咒版类。
 */
public final class HbmzzModules implements ModuleRegistry {

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final ReactorRegistry reactorRegistry;

    public HbmzzModules(ReactorRegistry reactorRegistry) {
        this.reactorRegistry = Objects.requireNonNull(reactorRegistry, "reactorRegistry");
    }

    /**
     * standalone 默认构建方式：通过 {@link ServiceLoader} 发现 reactor 实现（可插拔），
     * 实现方在 {@code META-INF/services/com.hbmzz.reactor.ReactorRegistry} 注册即可。
     */
    public static HbmzzModules createStandalone() {
        return new HbmzzModules(discoverReactorRegistry());
    }

    private static ReactorRegistry discoverReactorRegistry() {
        ServiceLoader<ReactorRegistry> loader = ServiceLoader.load(ReactorRegistry.class);
        for (ReactorRegistry candidate : loader) {
            return candidate;
        }
        throw new IllegalStateException(
                "No ReactorRegistry implementation found; standalone 需要一个 reactor 实现"
                        + "（通过 META-INF/services 注册，或显式注入）。");
    }

    /** 暴露 reactor 注册门面（仅接口），供模块按 id 注册/查询控制器。 */
    public ReactorRegistry reactorRegistry() {
        return reactorRegistry;
    }

    /** 便捷入口：把一个反应堆控制器注册到 reactor 门面。 */
    public void registerReactor(String id, ReactorController controller) {
        reactorRegistry.register(id, controller);
    }

    @Override
    public void register(Module module) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(module.id(), "module.id()");
        modules.put(module.id(), module);
    }

    @Override
    public Module get(String id) {
        return modules.get(id);
    }

    @Override
    public boolean has(String id) {
        return modules.containsKey(id);
    }

    @Override
    public Collection<Module> modules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /** 初始化所有已注册模块，并把本门面作为上下文传入。 */
    public void initializeAll() {
        for (Module module : modules.values()) {
            module.initialize(this);
        }
    }

    /** 驱动所有模块的每 tick 逻辑。 */
    public void tickAll() {
        for (Module module : modules.values()) {
            module.tick();
        }
    }

    /** 释放所有模块。 */
    public void disposeAll() {
        for (Module module : modules.values()) {
            module.dispose();
        }
    }
}
