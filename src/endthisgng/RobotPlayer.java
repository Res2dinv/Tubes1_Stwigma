package endthisgng;

import battlecode.common.*;

public class RobotPlayer {

    //map movement main dir
    static final Direction[] DIRECTIONS = {
        Direction.NORTH, 
        Direction.NORTHEAST, 
        Direction.EAST, 
        Direction.SOUTHEAST,
        Direction.SOUTH, 
        Direction.SOUTHWEST, 
        Direction.WEST, 
        Direction.NORTHWEST
    };

    static final int REFILL_THRESHOLD = 60;
    static MapLocation paintTowerLoc = null;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                //switch buat each robot/tower
                switch (rc.getType()) {
                    case SOLDIER:   runSoldier(rc);   break;
                    case SPLASHER:  runSplasher(rc);  break;
                    case MOPPER:      break; //no mapper gng
                    default:        runTower(rc);     break;
                }
            } catch (GameActionException e) {
                // ignore errors
            }
            Clock.yield();
        }
    }

    // =============== Tower ================
    static void runTower(RobotController rc) throws GameActionException {
        // shoot first (kalau ada musuh)
        attackWeakest(rc);
        if (!rc.isActionReady()) return;

        // count splashers (jaga ratio)
        RobotInfo[] nearby = rc.senseNearbyRobots(-1, rc.getTeam());
        int splashers = 0, total = 0;
        for (RobotInfo r : nearby) {
            if (r.getType().health > 0) {
                if (r.getType() == UnitType.SPLASHER) splashers++;
                if (r.getType() == UnitType.SOLDIER || r.getType() == UnitType.MOPPER || r.getType() == UnitType.SPLASHER) total++;
            }
        }

        // try to keep splashers at 60% 
        UnitType toSpawn = (total == 0 || (double) splashers / total < 0.6) ? UnitType.SPLASHER : UnitType.SOLDIER;

        // spawn in the first open spot
        for (Direction dir : DIRECTIONS) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                rc.buildRobot(toSpawn, spawnLoc);
                break;
            }
        }
    }

    // =============== Soldier ================
    static void runSoldier(RobotController rc) throws GameActionException {
        findTower(rc);

        // go to tower if out of paint
        if (rc.getPaint() < REFILL_THRESHOLD) { runRefill(rc); return; }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation ruinLoc = null;

        // find a ruin that (seng rong eneng tower)
        for (MapInfo info : nearbyTiles) {
            if (info.hasRuin()) {
                MapLocation loc = info.getMapLocation();
                if (rc.canSenseLocation(loc)) {
                    RobotInfo r = rc.senseRobotAtLocation(loc);
                    if (r != null && isTower(r.getType())) continue;
                }
                ruinLoc = loc;
                break;
            }
        }

        // build tower
        if (ruinLoc != null) {
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
                return;
            }

            // check the 5x5 area and paint whatever is missing
            boolean[][] pattern = rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
            MapLocation bestPaintTarget = null;

            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    MapLocation target = ruinLoc.translate(x - 2, y - 2);
                    if (rc.canSenseLocation(target)) {
                        MapInfo info = rc.senseMapInfo(target);
                        if (info.isWall() || info.hasRuin()) continue;
                        PaintType goal = pattern[x][y] ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                        if (info.getPaint() != goal) {
                            if (rc.canAttack(target) && rc.isActionReady()) {
                                rc.attack(target, pattern[x][y]);
                                return;
                            }
                            if (bestPaintTarget == null) bestPaintTarget = target;
                        }
                    }
                }
            }

            if (bestPaintTarget != null) {
                moveZigzag(rc, bestPaintTarget);
                return;
            }
        }

        // paint buat expand
        MapLocation target = findExpandTarget(rc, nearbyTiles);
        if (target != null) {
            if (rc.canAttack(target) && rc.isActionReady()) {
                rc.attack(target, false);
            }
            if (rc.isMovementReady()) moveZigzag(rc, target);
        } else {
            exploreRandom(rc);
        }
    }

    // tentuin ekspand ke where
    static MapLocation findExpandTarget(RobotController rc, MapInfo[] nearbyTiles) {
        MapLocation best = null;
        int bestScore = 0;

        for (MapInfo tile : nearbyTiles) {
            //sanity check
            if (tile.isWall() || tile.hasRuin()) continue;

            int score = 0;
            PaintType p = tile.getPaint();

            //why? cant paint enemy
            if (p.isEnemy()) score = -10;
            else if (p == PaintType.EMPTY) score = 3;

            if (score > bestScore) {
                bestScore = score;
                best = tile.getMapLocation();
            }
        }
        return best;
    }

    // =============== Splasher ================
    static void runSplasher(RobotController rc) throws GameActionException {
        findTower(rc);

        if (rc.getPaint() < REFILL_THRESHOLD) { runRefill(rc); return; }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation me = rc.getLocation();

        // find if moving helps get a better splash shot
        int currentScore = whereSplash(rc, me, nearbyTiles);
        int bestScore = currentScore;
        MapLocation bestMove = null;

        if (rc.isMovementReady()) {
            for (Direction dir : DIRECTIONS) {
                MapLocation next = me.add(dir);
                if (rc.canMove(dir)) {
                    MapInfo destinationInfo = rc.senseMapInfo(next);
                    PaintType p = destinationInfo.getPaint();

                    // stay on allied paint to avoid the drain penalty
                    if (p == PaintType.ALLY_PRIMARY || p == PaintType.ALLY_SECONDARY) {
                        int score = whereSplash(rc, next, nearbyTiles);
                        if (score > bestScore) {
                            bestScore = score;
                            bestMove = next;
                        }
                    }
                }
            }
        }

        if (bestMove != null) {
            rc.move(me.directionTo(bestMove));
            me = rc.getLocation();
            currentScore = whereSplash(rc, me, nearbyTiles);
        }

        // if there's a good target, splash it
        if (currentScore > 0 && rc.isActionReady()) {
            MapLocation bestSplashCenter = getBestSplashTarget(rc, me, nearbyTiles);
            if (bestSplashCenter != null && rc.canAttack(bestSplashCenter)) {
                rc.attack(bestSplashCenter, false);
                return;
            }
        }

        exploreRandom(rc);
    }

    // check how many enemy/empty tiles we hit from here
    static int whereSplash(RobotController rc, MapLocation splasherPos, MapInfo[] nearbyTiles) {
        int bestScore = 0;
        for (MapInfo tile : nearbyTiles) {
            MapLocation tilePos = tile.getMapLocation();
            if (splasherPos.distanceSquaredTo(tilePos) > 4) continue;
            if (tile.isWall() || tile.hasRuin()) continue;

            int score = 0;
            for (MapInfo inner : nearbyTiles) {
                if (tilePos.distanceSquaredTo(inner.getMapLocation()) <= 2) {
                    if (inner.isWall() || inner.hasRuin()) continue;
                    PaintType p = inner.getPaint();
                    if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) score += 3;
                    else if (p == PaintType.EMPTY) score += 1;
                }
            }
            if (score > bestScore) bestScore = score;
        }
        return bestScore;
    }

    // find the actual coordinate for the best splash
    static MapLocation getBestSplashTarget(RobotController rc, MapLocation splasherPos, MapInfo[] nearbyTiles) {
        MapLocation bestTarget = null;
        int bestScore = 0;

        for (MapInfo tile : nearbyTiles) {
            MapLocation tilePos = tile.getMapLocation();
            if (splasherPos.distanceSquaredTo(tilePos) > 4) continue;
            if (tile.isWall() || tile.hasRuin()) continue;

            int score = 0;
            for (MapInfo inner : nearbyTiles) {
                if (tilePos.distanceSquaredTo(inner.getMapLocation()) <= 2) {
                    if (inner.isWall() || inner.hasRuin()) continue;
                    PaintType p = inner.getPaint();
                    if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) score += 3;
                    else if (p == PaintType.EMPTY) score += 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestTarget = tilePos;
            }
        }
        return bestTarget;
    }

    // =============== Helpers ================

    // move with a little wiggle so we don't get stuck on corners
    static void moveZigzag(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction toward = rc.getLocation().directionTo(target);
        Direction[] attempts = {toward, toward.rotateLeft(), toward.rotateRight(),
                                toward.rotateLeft().rotateLeft(), toward.rotateRight().rotateRight()};
        for (Direction d : attempts) { if (rc.canMove(d)) { rc.move(d); return; } }
    }

    // walk to the nearest tower and grab paint
    static void runRefill(RobotController rc) throws GameActionException {
        if (paintTowerLoc != null) {
            if (rc.getLocation().distanceSquaredTo(paintTowerLoc) <= 2) {
                if (rc.isActionReady() && rc.canTransferPaint(paintTowerLoc, -100)) {
                    rc.transferPaint(paintTowerLoc, -100);
                }
            }
            moveZigzag(rc, paintTowerLoc);
        } else {
            findTower(rc);
            exploreRandom(rc);
        }
    }

    // check sekitar buat tower (but di simpen)
    static void findTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) {
                paintTowerLoc = ally.getLocation();
                return;
            }
        }
    }

    static boolean isPaintTower(UnitType t) {
        return t == UnitType.LEVEL_ONE_PAINT_TOWER || t == UnitType.LEVEL_TWO_PAINT_TOWER || t == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    static boolean isTower(UnitType t) {
        return t.name().contains("TOWER");
    }

    //hit robot paling low hp
    static void attackWeakest(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo weakest = null;
        int minHP = 10000;
        for (RobotInfo e : enemies) {
            if (rc.canAttack(e.getLocation()) && e.health < minHP) {
                minHP = e.health;
                weakest = e;
            }
        }
        if (weakest != null) rc.attack(weakest.getLocation(), false);
    }

    // just wander around (random movement)
    static int exploreDir = (int)(Math.random() * 8);
    static void exploreRandom(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction d = DIRECTIONS[exploreDir % 8];
        if (rc.canMove(d)) rc.move(d);
        else exploreDir = (exploreDir + 1) % 8;
    }
}