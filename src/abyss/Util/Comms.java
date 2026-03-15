package abyss.Util;

import battlecode.common.MapLocation;

public final class Comms {
    public static final int TYPE_SYMMETRY = 1;
    public static final int TYPE_TOWER = 2;
    public static final int TYPE_MOPPER_TARGET = 3;

    public int encodeSymmetry(Symmetry symmetry) {
        return (TYPE_SYMMETRY << 28) | ((symmetry.code & 0xF) << 24);
    }

    public int encodeTower(MapLocation location, int status) {
        return (TYPE_TOWER << 28)
                | ((location.x & 0x3F) << 22)
                | ((location.y & 0x3F) << 16)
                | ((status & 0xF) << 12);
    }

    public int encodeMopperTarget(MapLocation location) {
        return (TYPE_MOPPER_TARGET << 28)
                | ((location.x & 0x3F) << 22)
                | ((location.y & 0x3F) << 16);
    }

    public int type(int raw) {
        return (raw >>> 28) & 0xF;
    }

    public Symmetry readSymmetry(int raw) {
        return Symmetry.fromCode((raw >>> 24) & 0xF);
    }

    public TowerInfo readTower(int raw, int round) {
        int x = (raw >>> 22) & 0x3F;
        int y = (raw >>> 16) & 0x3F;
        int status = (raw >>> 12) & 0xF;
        return new TowerInfo(new MapLocation(x, y), status, round);
    }

    public MapLocation readLocation(int raw) {
        int x = (raw >>> 22) & 0x3F;
        int y = (raw >>> 16) & 0x3F;
        return new MapLocation(x, y);
    }
}
