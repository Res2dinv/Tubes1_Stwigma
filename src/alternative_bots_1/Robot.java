package alternative_bot_1;

import alternative_bot_1.Util.Comms;
import alternative_bot_1.Util.GreedyMove;
import alternative_bot_1.Util.Symmetry;
import alternative_bot_1.Util.TowerInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;

public abstract class Robot {
    protected static final byte FEATURE_UNKNOWN = 0;
    protected static final byte FEATURE_PASSABLE = 1;
    protected static final byte FEATURE_WALL = 2;
    protected static final byte FEATURE_RUIN = 3;
    protected static final byte FEATURE_ALLY_TOWER = 4;
    protected static final byte FEATURE_ENEMY_TOWER = 5;

    protected static final Direction[] DIRECTIONS = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };

    protected final RobotController rc;
    protected final Team team;
    protected final Team opponent;
    protected final int mapWidth;
    protected final int mapHeight;
    protected final MapLocation spawnLocation;
    protected final int spawnRound;
    protected final Comms comms;

    protected Symmetry symmetry = Symmetry.UNKNOWN;
    protected final TowerInfo[] towerInfos = new TowerInfo[64];
    protected int towerInfoCount = 0;
    protected MapLocation knownPaintTower = null;
    protected MapLocation requestedMopperTarget = null;
    protected final byte[][] observedFeatures;
    protected boolean possibleRotational = true;
    protected boolean possibleHorizontal = true;
    protected boolean possibleVertical = true;
    protected boolean symmetryShared = false;

    protected Robot(RobotController rc) {
        this.rc = rc;
        this.team = rc.getTeam();
        this.opponent = team.opponent();
        this.mapWidth = rc.getMapWidth();
        this.mapHeight = rc.getMapHeight();
        this.spawnLocation = rc.getLocation();
        this.spawnRound = rc.getRoundNum();
        this.comms = new Comms();
        this.observedFeatures = new byte[mapWidth][mapHeight];
    }

    public final void run() {
        while (true) {
            try {
                takeTurn();
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    protected abstract void takeTurn() throws GameActionException;

    protected void updateCommonState() throws GameActionException {
        readMessages();
        observeNearbyMapFeatures();
        observeNearbyRuinsAndTowers();
        observeSymmetryClues();
    }

    protected void readMessages() throws GameActionException {
        int currentRound = rc.getRoundNum();
        int oldestRound = Math.max(1, currentRound - 4);
        for (int round = oldestRound; round < currentRound; round++) {
            for (battlecode.common.Message message : rc.readMessages(round)) {
                int raw = message.getBytes();
                int type = comms.type(raw);
                if (type == Comms.TYPE_SYMMETRY) {
                    Symmetry received = comms.readSymmetry(raw);
                    if (received != Symmetry.UNKNOWN) {
                        symmetry = received;
                        setSymmetryCandidates(received);
                    }
                } else if (type == Comms.TYPE_TOWER) {
                    TowerInfo info = comms.readTower(raw, round);
                    rememberTower(info.location, info.status, info.lastSeenRound);
                } else if (type == Comms.TYPE_MOPPER_TARGET) {
                    requestedMopperTarget = comms.readLocation(raw);
                }
            }
        }
    }

    protected void observeNearbyMapFeatures() throws GameActionException {
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (MapInfo info : infos) {
            MapLocation location = info.getMapLocation();
            if (info.isWall()) {
                observeFeature(location, FEATURE_WALL);
            } else if (info.hasRuin()) {
                observeFeature(location, FEATURE_RUIN);
            } else {
                observeFeature(location, FEATURE_PASSABLE);
            }
        }
    }

    protected void observeNearbyRuinsAndTowers() throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : ruins) {
            RobotInfo robot = rc.canSenseRobotAtLocation(ruin) ? rc.senseRobotAtLocation(ruin) : null;
            int status = TowerInfo.STATUS_RUIN;
            byte feature = FEATURE_RUIN;
            if (robot != null && robot.getType().isTowerType()) {
                status = robot.getTeam() == team ? TowerInfo.STATUS_ALLY : TowerInfo.STATUS_ENEMY;
                feature = robot.getTeam() == team ? FEATURE_ALLY_TOWER : FEATURE_ENEMY_TOWER;
            }
            observeFeature(ruin, feature);
            rememberTower(ruin, status, rc.getRoundNum());
        }

        RobotInfo[] robots = rc.senseNearbyRobots(-1);
        for (RobotInfo robot : robots) {
            if (!robot.getType().isTowerType()) {
                continue;
            }
            int status = robot.getTeam() == team ? TowerInfo.STATUS_ALLY : TowerInfo.STATUS_ENEMY;
            observeFeature(robot.getLocation(), status == TowerInfo.STATUS_ALLY ? FEATURE_ALLY_TOWER : FEATURE_ENEMY_TOWER);
            rememberTower(robot.getLocation(), status, rc.getRoundNum());
            if (status == TowerInfo.STATUS_ALLY && isPaintTower(robot.getType())) {
                knownPaintTower = robot.getLocation();
            }
        }
    }

    protected void observeSymmetryClues() {
        if (symmetry != Symmetry.UNKNOWN || !shouldScoutSymmetry()) {
            return;
        }
        possibleRotational = isSymmetryStillPossible(Symmetry.ROTATIONAL);
        possibleHorizontal = isSymmetryStillPossible(Symmetry.HORIZONTAL);
        possibleVertical = isSymmetryStillPossible(Symmetry.VERTICAL);

        int possibleCount = getPossibleSymmetryCount();
        if (possibleCount == 1) {
            if (possibleRotational) {
                symmetry = Symmetry.ROTATIONAL;
            } else if (possibleHorizontal) {
                symmetry = Symmetry.HORIZONTAL;
            } else if (possibleVertical) {
                symmetry = Symmetry.VERTICAL;
            }
        }
    }

    private boolean isSymmetryStillPossible(Symmetry test) {
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                byte feature = observedFeatures[x][y];
                if (feature == FEATURE_UNKNOWN) {
                    continue;
                }
                MapLocation mirrored = test.mirror(new MapLocation(x, y), mapWidth, mapHeight);
                byte mirrorFeature = observedFeatures[mirrored.x][mirrored.y];
                if (mirrorFeature == FEATURE_UNKNOWN) {
                    continue;
                }
                if (mirrorFeature != feature) {
                    return false;
                }
            }
        }
        return true;
    }

    protected boolean shouldScoutSymmetry() {
        return spawnRound < 100;
    }

    protected int getPossibleSymmetryCount() {
        int count = 0;
        if (possibleRotational) {
            count++;
        }
        if (possibleHorizontal) {
            count++;
        }
        if (possibleVertical) {
            count++;
        }
        return count;
    }

    protected void setSymmetryCandidates(Symmetry confirmed) {
        possibleRotational = confirmed == Symmetry.ROTATIONAL;
        possibleHorizontal = confirmed == Symmetry.HORIZONTAL;
        possibleVertical = confirmed == Symmetry.VERTICAL;
    }

    protected void observeFeature(MapLocation location, byte feature) {
        byte current = observedFeatures[location.x][location.y];
        if (current == feature) {
            return;
        }
        if (featurePriority(current) > featurePriority(feature)) {
            return;
        }
        observedFeatures[location.x][location.y] = feature;
    }

    protected boolean isObserved(MapLocation location) {
        return observedFeatures[location.x][location.y] != FEATURE_UNKNOWN;
    }

    protected byte featureAt(MapLocation location) {
        return observedFeatures[location.x][location.y];
    }

    private int featurePriority(byte feature) {
        return switch (feature) {
            case FEATURE_UNKNOWN -> 0;
            case FEATURE_PASSABLE -> 1;
            case FEATURE_WALL, FEATURE_RUIN -> 2;
            case FEATURE_ALLY_TOWER, FEATURE_ENEMY_TOWER -> 3;
            default -> 0;
        };
    }

    protected TowerInfo findTowerInfo(MapLocation location) {
        for (int i = 0; i < towerInfoCount; i++) {
            if (towerInfos[i].location.equals(location)) {
                return towerInfos[i];
            }
        }
        return null;
    }

    protected void rememberTower(MapLocation location, int status, int round) {
        TowerInfo existing = findTowerInfo(location);
        if (existing != null) {
            existing.status = status;
            existing.lastSeenRound = round;
            return;
        }
        if (towerInfoCount >= towerInfos.length) {
            return;
        }
        towerInfos[towerInfoCount++] = new TowerInfo(location, status, round);
    }

    protected MapLocation getNearestKnownTower(int status) {
        MapLocation current = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < towerInfoCount; i++) {
            TowerInfo info = towerInfos[i];
            if (info.status != status) {
                continue;
            }
            int distance = current.distanceSquaredTo(info.location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = info.location;
            }
        }
        return best;
    }

    protected MapLocation getNearestPredictedRuin() {
        if (symmetry == Symmetry.UNKNOWN) {
            return null;
        }
        MapLocation current = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < towerInfoCount; i++) {
            TowerInfo info = towerInfos[i];
            if (info.status != TowerInfo.STATUS_RUIN && info.status != TowerInfo.STATUS_ALLY) {
                continue;
            }
            MapLocation mirrored = symmetry.mirror(info.location, mapWidth, mapHeight);
            if (findTowerInfo(mirrored) != null) {
                continue;
            }
            int distance = current.distanceSquaredTo(mirrored);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mirrored;
            }
        }
        return best;
    }

    protected boolean lowPaint(double ratio) {
        return rc.getPaint() <= rc.getType().paintCapacity * ratio;
    }

    protected void sendSymmetryIfPossible() throws GameActionException {
        if (symmetry == Symmetry.UNKNOWN || symmetryShared) {
            return;
        }
        RobotInfo[] allies = rc.senseNearbyRobots(-1, team);
        int payload = comms.encodeSymmetry(symmetry);
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) {
                continue;
            }
            if (rc.canSendMessage(ally.location, payload)) {
                rc.sendMessage(ally.location, payload);
                symmetryShared = true;
                break;
            }
        }
        // System.out.println("kirim pesan yey" + payload);
    }

    protected void sendMopperRequestIfPossible(MapLocation target) throws GameActionException {
        if (target == null) {
            return;
        }
        requestedMopperTarget = target;
        int payload = comms.encodeMopperTarget(target);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, team);
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) {
                continue;
            }
            if (rc.canSendMessage(ally.location, payload)) {
                rc.sendMessage(ally.location, payload);
                return;
            }
        }
    }

    protected boolean moveGreedy(MapLocation target) throws GameActionException {
        Direction move = GreedyMove.bestDirection(rc, target);
        if (move != Direction.CENTER && rc.canMove(move)) {
            rc.move(move);
            return true;
        }
        return false;
    }

    protected boolean moveLocalDijkstra(MapLocation target) throws GameActionException {
        Direction move = GreedyMove.localDijkstraDirection(rc, target);
        if (move != Direction.CENTER && rc.canMove(move)) {
            rc.move(move);
            return true;
        }
        return false;
    }

    protected boolean isPaintTower(UnitType type) {
        return type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER;
    }
}
