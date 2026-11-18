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

import java.util.Objects;

import com.hbmzz.reactor.ReactorRegistry;

/**
 * standalone 启动入口。
 *
 * <p>负责装配 reactor 注册门面（单例获取或显式注入），构建
 * {@link HbmzzModules}，并对外暴露启动 / tick / 关闭的生命周期方法。
 *
 * <p>只依赖自有 reactor 体系，不依赖诅咒版。
 */
public final class StandaloneBootstrap {

    private final HbmzzModules modules;

    private StandaloneBootstrap(HbmzzModules modules) {
        this.modules = modules;
    }

    /** 默认启动：由 HbmzzModules 取得 reactor 单例并安装为全局网关。 */
    public static StandaloneBootstrap start() {
        return start(HbmzzModules.createStandalone());
    }

    /** 用现成的 HbmzzModules 启动并初始化全部模块。 */
    public static StandaloneBootstrap start(HbmzzModules modules) {
        Objects.requireNonNull(modules, "modules");
        modules.initializeAll();
        return new StandaloneBootstrap(modules);
    }

    /** 显式注入 reactor 注册门面（覆盖默认单例装配），便于测试与共存模式适配。 */
    public static StandaloneBootstrap withReactorRegistry(ReactorRegistry reactorRegistry) {
        return start(new HbmzzModules(reactorRegistry));
    }

    /** 暴露底层模块门面，供 ModContent / proxy / regcheck 等接入点注册内容。 */
    public HbmzzModules modules() {
        return modules;
    }

    /** 驱动一次 tick。 */
    public void tick() {
        modules.tickAll();
    }

    /** 关闭并释放所有模块。 */
    public void shutdown() {
        modules.disposeAll();
    }
}
