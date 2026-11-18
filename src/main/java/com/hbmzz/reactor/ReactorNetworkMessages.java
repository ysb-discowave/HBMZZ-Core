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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 反应堆网络包、协议版本与注册配对。
 *
 * <p>包只携带快照与事件，不携带逻辑；不可信输入只能改显示，不能推进相位。
 * 缓冲区用自有的 {@link ReactorBuffer} 抽象，不引入 netty 或其他第三方库。
 * 每个包类都必须形如 {@code ReactorPacketXxx} 且在下方静态块中以
 * {@code register(ReactorPacketXxx.class, id)} 配对，否则离线检查会失败。</p>
 */
public final class ReactorNetworkMessages {

    /** 双方握手契约；不兼容变更必须升版本并同步更新检查脚本。 */
    public static final int PROTOCOL_VERSION = 1;

    private ReactorNetworkMessages() {
    }

    /** 所有反应堆包实现的公共契约。 */
    public interface ReactorPacket {
        int id();

        void encode(ReactorBuffer buffer);

        void decode(ReactorBuffer buffer);
    }

    /** 自有字节缓冲抽象，隔离具体网络库。 */
    public interface ReactorBuffer {
        void writeInt(int value);

        int readInt();

        void writeDouble(double value);

        double readDouble();

        void writeString(String value);

        String readString();

        void writeBoolean(boolean value);

        boolean readBoolean();
    }

    /** 全量快照：控制器 id、等级、相位、热量、完整度。 */
    public static final class ReactorPacketSync implements ReactorPacket {
        private String controllerId = "";
        private int tierId;
        private String phase = "OFFLINE";
        private double heat;
        private double integrity = 1.0D;

        public ReactorPacketSync() {
        }

        public ReactorPacketSync(String controllerId, int tierId, String phase,
                                 double heat, double integrity) {
            this.controllerId = controllerId == null ? "" : controllerId;
            this.tierId = tierId;
            this.phase = phase == null ? "OFFLINE" : phase;
            this.heat = heat;
            this.integrity = integrity;
        }

        @Override
        public int id() {
            return 1;
        }

        @Override
        public void encode(ReactorBuffer buffer) {
            buffer.writeString(controllerId);
            buffer.writeInt(tierId);
            buffer.writeString(phase);
            buffer.writeDouble(heat);
            buffer.writeDouble(integrity);
        }

        @Override
        public void decode(ReactorBuffer buffer) {
            controllerId = buffer.readString();
            tierId = buffer.readInt();
            phase = buffer.readString();
            heat = buffer.readDouble();
            integrity = buffer.readDouble();
        }

        public String getControllerId() {
            return controllerId;
        }

        public int getTierId() {
            return tierId;
        }

        public String getPhase() {
            return phase;
        }

        public double getHeat() {
            return heat;
        }

        public double getIntegrity() {
            return integrity;
        }
    }

    /** 增量热量更新。 */
    public static final class ReactorPacketHeat implements ReactorPacket {
        private String controllerId = "";
        private double heat;
        private double output;

        public ReactorPacketHeat() {
        }

        public ReactorPacketHeat(String controllerId, double heat, double output) {
            this.controllerId = controllerId == null ? "" : controllerId;
            this.heat = heat;
            this.output = output;
        }

        @Override
        public int id() {
            return 2;
        }

        @Override
        public void encode(ReactorBuffer buffer) {
            buffer.writeString(controllerId);
            buffer.writeDouble(heat);
            buffer.writeDouble(output);
        }

        @Override
        public void decode(ReactorBuffer buffer) {
            controllerId = buffer.readString();
            heat = buffer.readDouble();
            output = buffer.readDouble();
        }

        public String getControllerId() {
            return controllerId;
        }

        public double getHeat() {
            return heat;
        }

        public double getOutput() {
            return output;
        }
    }

    /** 相位事件：停堆、熔毁等。 */
    public static final class ReactorPacketTrip implements ReactorPacket {
        private String controllerId = "";
        private String phase = "OFFLINE";
        private int reasonCode;

        public ReactorPacketTrip() {
        }

        public ReactorPacketTrip(String controllerId, String phase, int reasonCode) {
            this.controllerId = controllerId == null ? "" : controllerId;
            this.phase = phase == null ? "OFFLINE" : phase;
            this.reasonCode = reasonCode;
        }

        @Override
        public int id() {
            return 3;
        }

        @Override
        public void encode(ReactorBuffer buffer) {
            buffer.writeString(controllerId);
            buffer.writeString(phase);
            buffer.writeInt(reasonCode);
        }

        @Override
        public void decode(ReactorBuffer buffer) {
            controllerId = buffer.readString();
            phase = buffer.readString();
            reasonCode = buffer.readInt();
        }

        public String getControllerId() {
            return controllerId;
        }

        public String getPhase() {
            return phase;
        }

        public int getReasonCode() {
            return reasonCode;
        }
    }

    /** 客户端控制请求：命令码 0=启动 1=停堆 2=复位。 */
    public static final class ReactorPacketControl implements ReactorPacket {
        public static final int COMMAND_START = 0;
        public static final int COMMAND_SCRAM = 1;
        public static final int COMMAND_RESET = 2;

        private String controllerId = "";
        private int command;

        public ReactorPacketControl() {
        }

        public ReactorPacketControl(String controllerId, int command) {
            this.controllerId = controllerId == null ? "" : controllerId;
            this.command = command;
        }

        @Override
        public int id() {
            return 4;
        }

        @Override
        public void encode(ReactorBuffer buffer) {
            buffer.writeString(controllerId);
            buffer.writeInt(command);
        }

        @Override
        public void decode(ReactorBuffer buffer) {
            controllerId = buffer.readString();
            command = buffer.readInt();
        }

        public String getControllerId() {
            return controllerId;
        }

        public int getCommand() {
            return command;
        }
    }

    private static final Map<Integer, Class<? extends ReactorPacket>> BY_ID =
            new LinkedHashMap<Integer, Class<? extends ReactorPacket>>();

    static {
        register(ReactorPacketSync.class, 1);
        register(ReactorPacketHeat.class, 2);
        register(ReactorPacketTrip.class, 3);
        register(ReactorPacketControl.class, 4);
    }

    /** 注册包类型与 id；重复 id 或重复类型都会拒绝。 */
    public static synchronized void register(Class<? extends ReactorPacket> type, int id) {
        if (type == null) {
            throw new IllegalArgumentException("packet type must not be null");
        }
        if (BY_ID.containsKey(Integer.valueOf(id))) {
            throw new IllegalStateException("Duplicate reactor packet id: " + id);
        }
        if (BY_ID.containsValue(type)) {
            throw new IllegalStateException("Packet already registered: " + type.getName());
        }
        BY_ID.put(Integer.valueOf(id), type);
    }

    /** 按 id 构造一个空包，供解码端使用。 */
    public static ReactorPacket create(int id) {
        Class<? extends ReactorPacket> type = BY_ID.get(Integer.valueOf(id));
        if (type == null) {
            throw new IllegalArgumentException("Unknown reactor packet id: " + id);
        }
        try {
            return type.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("Cannot instantiate " + type.getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access " + type.getName(), e);
        }
    }

    /** 查包类型的 id，未注册返回 -1。 */
    public static synchronized int idOf(Class<? extends ReactorPacket> type) {
        for (Map.Entry<Integer, Class<? extends ReactorPacket>> entry : BY_ID.entrySet()) {
            if (entry.getValue().equals(type)) {
                return entry.getKey().intValue();
            }
        }
        return -1;
    }

    /** 只读注册表快照。 */
    public static synchronized Map<Integer, Class<? extends ReactorPacket>> registry() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<Integer, Class<? extends ReactorPacket>>(BY_ID));
    }

    /** 握手：远端协议版本不一致时拒绝解码。 */
    public static void checkProtocol(int remoteVersion) {
        if (remoteVersion != PROTOCOL_VERSION) {
            throw new IllegalStateException("Reactor protocol mismatch: local="
                    + PROTOCOL_VERSION + ", remote=" + remoteVersion);
        }
    }
}
