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

/**
 * A read-only view of one reactor controller.
 *
 * <p>This is the only type the rest of HBMZZ is allowed to see from the reactor
 * module. It deliberately exposes primitives only: no implementation class, no
 * tile entity and no state-machine type crosses this boundary. A consumer that
 * only holds a {@code ReactorView} cannot mutate a reactor, and does not need a
 * compile-time dependency on {@code com.hbmzz.reactor}.</p>
 *
 * <p>Implementations should return a stable snapshot for the duration of one
 * call; consecutive calls may differ as the simulation advances.</p>
 */
public interface ReactorView {

    /** Stable registration id of the controller, never {@code null}. */
    String getId();

    /** Numeric tier id; see {@code ReactorTier} in the implementation layer. */
    int getTierId();

    /** Name of the current state-machine phase, e.g. {@code RUNNING}. */
    String getPhase();

    /** Current core heat, in arbitrary but consistent units. */
    double getHeat();

    /** Core integrity in the range 0.0 (destroyed) to 1.0 (intact). */
    double getIntegrity();

    /** True only while the reactor is actively producing output. */
    boolean isRunning();
}
