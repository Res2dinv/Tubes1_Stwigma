package abyss.Util;

import battlecode.common.MapLocation;

public final class TowerInfo {
    public static final int STATUS_RUIN = 0;
    public static final int STATUS_ALLY = 1;
    public static final int STATUS_ENEMY = 2;

    public final MapLocation location;
    public int status;
    public int lastSeenRound;

    public TowerInfo(MapLocation location, int status, int lastSeenRound) {
        this.location = location;
        this.status = status;
        this.lastSeenRound = lastSeenRound;
    }
}
