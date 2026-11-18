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

package com.hbmzz.reactor.api;

import java.util.Collections;
import java.util.List;

/**
 * Service-locator boundary between HBMZZ and the reactor core.
 *
 * <p>The main module talks to this interface and to {@link ReactorView} only.
 * The implementation layer installs itself with {@link #bind(ReactorGateway)};
 * main-module code never imports {@code com.hbmzz.reactor.*}, so the dependency
 * is one-way and the core may be replaced or omitted without recompiling the
 * consumer.</p>
 */
public interface ReactorGateway {

    /** Look up one controller by id, or {@code null} when unknown. */
    ReactorView get(String id);

    /** Immutable snapshot of every known controller. */
    List<ReactorView> snapshot();

    /** Safe no-op gateway used before the core is installed. */
    ReactorGateway EMPTY = new ReactorGateway() {
        @Override
        public ReactorView get(String id) {
            return null;
        }

        @Override
        public List<ReactorView> snapshot() {
            return Collections.emptyList();
        }
    };

    /** Install a gateway. Passing {@code null} restores {@link #EMPTY}. */
    static void bind(ReactorGateway gateway) {
        Holder.current = gateway == null ? EMPTY : gateway;
    }

    /** Currently installed gateway, never {@code null}. */
    static ReactorGateway get() {
        return Holder.current;
    }

    /** Holder idiom: keeps the mutable field out of the interface contract. */
    final class Holder {
        private static volatile ReactorGateway current = EMPTY;

        private Holder() {
        }
    }
}
