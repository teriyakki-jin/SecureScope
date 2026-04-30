package com.securescope.common;

public final class RedisKeyPrefix {
    private RedisKeyPrefix() {}

    public static final String BRUTE_FORCE  = "bf:";
    public static final String PORT_SCAN    = "ps:";
    public static final String IP_EVENT_COUNT = "ip:count:";
}
