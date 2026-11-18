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

import com.hbmzz.reactor.ReactorController;
import com.hbmzz.reactor.ReactorRegistry;

/**
 * 模块加载门面。
 *
 * <p>持有模块注册表与 reactor 注册门面，提供注册、查询与生命周期入口。
 * reactor 侧是 {@code ReactorRegistry}（final class + 单例）与 {@code ReactorController}
 * （abstract class）的组合，读取边界在 {@code com.hbmzz.reactor.api.ReactorGateway}。
 * 本类通过单例获取注册表、通过公开方法注册/查询控制器，不 {@code new} reactor 实现，
 * 也不引用诅咒版类，依赖方向单向指向自有 reactor 体系。
 */
public final class HbmzzModules implements ModuleRegistry {

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final ReactorRegistry reactorRegistry;

    public HbmzzModules(ReactorRegistry reactorRegistry) {
        this.reactorRegistry = Objects.requireNonNull(reactorRegistry, "reactorRegistry");
    }

    /**
     * standalone 默认构建方式：取得自有 reactor 注册表单例并安装为全局网关。
     *
     * <p>{@link ReactorRegistry#instance()} 返回 final class 的单例，不是服务可发现的接口；
     * {@link ReactorRegistry#install()} 内部只是把本单例绑定到
     * {@code ReactorGateway}，重复调用幂等，因此这里每次构建都安全调用一次即可。
     */
    public static HbmzzModules createStandalone() {
        ReactorRegistry registry = ReactorRegistry.instance();
        registry.install();
        return new HbmzzModules(registry);
    }

    /** 暴露 reactor 注册门面，供模块按 id 注册/查询控制器。 */
    public ReactorRegistry reactorRegistry() {
        return reactorRegistry;
    }

    /** 便捷入口：把一个反应堆控制器注册到 reactor 门面（控制器自带 id，内部自注册）。 */
    public void registerReactor(ReactorController controller) {
        reactorRegistry.registerController(controller);
    }

    /** 按 id 查询已注册的反应堆控制器；不存在返回 {@code null}。 */
    public ReactorController reactor(String id) {
        return reactorRegistry.controller(id);
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
