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

import net.minecraftforge.fml.common.Loader;

/**
 * 检测诅咒版是否已加载，决定 hbmzz 的运行模式。
 * 本类只使用 Forge API，不引用任何 com.hbm.* 类型。
 */
public final class CursedCompat {

    /** 诅咒版的 modid。 */
    public static final String HBM_MODID = "hbm";

    private CursedCompat() {
    }

    /**
     * 判定当前运行模式。任何异常都安全降级为 STANDALONE。
     *
     * @return {@link CompatMode#COEXIST} 或 {@link CompatMode#STANDALONE}
     */
    public static CompatMode detect() {
        try {
            return Loader.isModLoaded("hbm") ? CompatMode.COEXIST : CompatMode.STANDALONE;
        } catch (Throwable t) {
            return CompatMode.STANDALONE;
        }
    }

    /** 当前运行模式。 */
    public static CompatMode mode() {
        return detect();
    }

    /** 是否处于共存模式。 */
    public static boolean isCoexist() {
        return detect().isCoexist();
    }

    /** 是否处于独立模式。 */
    public static boolean isStandalone() {
        return detect().isStandalone();
    }
}
