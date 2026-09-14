package io.slice.stream.apiserver.streamer.domain.model;

public enum GrassLevel {
    LEVEL_0(0),
    LEVEL_1(1),
    LEVEL_2(2),
    LEVEL_3(3),
    LEVEL_4(4);

    private final int level;

    GrassLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static GrassLevel fromDurationSeconds(long durationSeconds) {
        if (durationSeconds <= 0) {
            return LEVEL_0;
        }
        if (durationSeconds < 7200) {
            return LEVEL_1;
        }
        if (durationSeconds < 14400) {
            return LEVEL_2;
        }
        if (durationSeconds < 25200) {
            return LEVEL_3;
        }
        return LEVEL_4;
    }
}
