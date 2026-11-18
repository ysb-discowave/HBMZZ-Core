/*
 * This file is part of HBMZZ-Core.
 *
 * HBMZZ-Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HBMZZ-Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HBMZZ-Core.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hbmzz.compat;

/**
 * hbmzz 与诅咒版共存时的运行模式。
 */
public enum CompatMode {
    /** 未安装诅咒版：使用 hbmzz 自有实现。 */
    STANDALONE(false),

    /** 已安装诅咒版：可通过可选桥接增强。 */
    COEXIST(true);

    private final boolean coexist;

    CompatMode(boolean coexist) {
        this.coexist = coexist;
    }

    /** 是否处于共存模式。 */
    public boolean isCoexist() {
        return coexist;
    }

    /** 是否处于独立模式。 */
    public boolean isStandalone() {
        return !coexist;
    }
}
