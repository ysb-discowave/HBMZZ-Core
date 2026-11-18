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

import java.lang.reflect.Method;

/**
 * 与诅咒版交互的反射门面。
 *
 * <p>本类不 import、不引用任何 {@code com.hbm.*} 类型；诅咒版类名仅以字符串常量出现。
 * 方法签名只暴露 boolean / Object / String 等 JDK 类型。所有失败路径（hbm 未加载、
 * 类缺失、方法缺失、调用异常）都返回安全默认值（false / null），不向调用方抛异常，
 * 从而保证 standalone 模式下行为与未安装诅咒版时一致。</p>
 */
public final class CursedBridge {

    /** 诅咒版中用于探测桥接可用性的类；仅字符串，不产生编译期依赖。 */
    private static final String HBM_PROBE_CLASS = "com.hbm.lib.Refstrings";

    private CursedBridge() {
    }

    /** 是否处于共存模式且反射桥接可用。 */
    public static boolean isBridgeAvailable() {
        if (!CursedCompat.isCoexist()) {
            return false;
        }
        return findClass(HBM_PROBE_CLASS) != null;
    }

    /**
     * 反射调用诅咒版的无参静态方法并取布尔结果。
     * 任何失败均返回 false。
     *
     * @param className  诅咒版类的全限定名（字符串）
     * @param methodName 无参静态方法名
     */
    public static boolean invokeBoolean(String className, String methodName) {
        if (!CursedCompat.isCoexist()) {
            return false;
        }
        try {
            Method method = findPublicMethod(className, methodName);
            if (method == null) {
                return false;
            }
            Object result = method.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 反射调用诅咒版的无参静态方法并取占位对象，仅以 {@link Object} 暴露。
     * 任何失败均返回 null，调用方须判空后再使用。
     *
     * @param className  诅咒版类的全限定名（字符串）
     * @param methodName 无参静态方法名
     */
    public static Object invokeObject(String className, String methodName) {
        if (!CursedCompat.isCoexist()) {
            return null;
        }
        try {
            Method method = findPublicMethod(className, methodName);
            if (method == null) {
                return null;
            }
            return method.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 供调用方查询探测类名，不泄漏诅咒版类型。 */
    public static String probeClassName() {
        return HBM_PROBE_CLASS;
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className, false, CursedBridge.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findPublicMethod(String className, String methodName) throws Exception {
        Class<?> clazz = Class.forName(className, false, CursedBridge.class.getClassLoader());
        return clazz.getMethod(methodName);
    }
}
