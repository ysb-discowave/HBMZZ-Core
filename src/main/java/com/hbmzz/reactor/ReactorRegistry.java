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

import com.hbmzz.reactor.api.ReactorGateway;
import com.hbmzz.reactor.api.ReactorView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反应堆自有注册表：等级与控制器都在这里注册，并作为网关对外提供只读快照。
 *
 * <p>不借用任何外部注册系统；standalone 模式下它是唯一权威。实现
 * {@link ReactorGateway} 使主模块只需依赖 api 层即可查询，注册表本身对 api
 * 的依赖是单向的。所有公开方法同步，注册顺序保持插入顺序以保证可预测遍历。</p>
 */
public final class ReactorRegistry implements ReactorGateway {

    private static final ReactorRegistry INSTANCE = new ReactorRegistry();

    private final Map<Integer, ReactorTier> tiers = new LinkedHashMap<Integer, ReactorTier>();
    private final Map<String, ReactorController> controllers =
            new LinkedHashMap<String, ReactorController>();
    private volatile boolean frozen;

    private ReactorRegistry() {
        for (ReactorTier tier : ReactorTier.values()) {
            tiers.put(Integer.valueOf(tier.id()), tier);
        }
    }

    public static ReactorRegistry instance() {
        return INSTANCE;
    }

    /** 把本注册表安装为全局网关；重复调用幂等。 */
    public void install() {
        ReactorGateway.bind(this);
    }

    /** 注册等级，id 冲突则拒绝。 */
    public synchronized void registerTier(ReactorTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("tier must not be null");
        }
        ensureMutable();
        Integer key = Integer.valueOf(tier.id());
        if (tiers.containsKey(key)) {
            throw new IllegalStateException("Duplicate reactor tier id: " + tier.id());
        }
        tiers.put(key, tier);
    }

    public synchronized ReactorTier tier(int id) {
        return tiers.get(Integer.valueOf(id));
    }

    public synchronized Collection<ReactorTier> tiers() {
        return Collections.unmodifiableCollection(
                new ArrayList<ReactorTier>(tiers.values()));
    }

    /** 注册控制器，id 冲突则拒绝。 */
    public synchronized void registerController(ReactorController controller) {
        if (controller == null || controller.getId() == null) {
            throw new IllegalArgumentException("controller and its id must not be null");
        }
        ensureMutable();
        String key = controller.getId();
        if (controllers.containsKey(key)) {
            throw new IllegalStateException("Duplicate reactor controller id: " + key);
        }
        controllers.put(key, controller);
    }

    public synchronized void unregisterController(String id) {
        controllers.remove(id);
    }

    public synchronized ReactorController controller(String id) {
        return controllers.get(id);
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    /** 冻结后不再接受新注册，防止运行期意外改动。 */
    public synchronized void freeze() {
        frozen = true;
    }

    /** 仅供离线检查与测试使用，清空除内置等级外的所有条目。 */
    public synchronized void clear() {
        controllers.clear();
        frozen = false;
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("ReactorRegistry is frozen");
        }
    }

    @Override
    public synchronized ReactorView get(String id) {
        return controllers.get(id);
    }

    @Override
    public synchronized List<ReactorView> snapshot() {
        List<ReactorView> out = new ArrayList<ReactorView>(controllers.size());
        out.addAll(controllers.values());
        return Collections.unmodifiableList(out);
    }
}
