package io.slice.stream.engine.chat.infrastructure.chzzk.websocket;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CmdType {
    PING(0),
    PONG(10000),
    CONNECT_ACK(100),
    CONNECTED(10100),
    REQUEST_RECENT_CHAT(5101),
    RECENT_CHAT(15101),
    CHAT(93101),
    DONATION(93102),
    UNKNOWN(-1);

    private final int value;

    CmdType(int value) {
        this.value = value;
    }

    private static final Map<Integer, CmdType> map = Arrays.stream(values())
        .collect(Collectors.toMap(CmdType::getValue, Function.identity()));

    public static CmdType fromInt(int value) {
        return map.getOrDefault(value, UNKNOWN);
    }

    public int getValue() {
        return value;
    }
}
