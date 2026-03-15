package abyss.Util;

import battlecode.common.MapLocation;

public enum Symmetry {
    UNKNOWN(0),
    ROTATIONAL(1),
    HORIZONTAL(2),
    VERTICAL(3);

    public static final Symmetry[] CHECK_ORDER = {
            ROTATIONAL,
            HORIZONTAL,
            VERTICAL
    };

    public final int code;

    Symmetry(int code) {
        this.code = code;
    }

    public static Symmetry fromCode(int code) {
        for (Symmetry symmetry : values()) {
            if (symmetry.code == code) {
                return symmetry;
            }
        }
        return UNKNOWN;
    }

    public MapLocation mirror(MapLocation location, int width, int height) {
        return switch (this) {
            case ROTATIONAL -> new MapLocation(width - 1 - location.x, height - 1 - location.y);
            case HORIZONTAL -> new MapLocation(location.x, height - 1 - location.y);
            case VERTICAL -> new MapLocation(width - 1 - location.x, location.y);
            case UNKNOWN -> location;
        };
    }
}
