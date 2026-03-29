package alternative_bots_2;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;

public class RobotPlayer {

    static RobotController rc;
    static Random rng;
    static Team myTeam;
    static Team enemyTeam;

    static MapLocation homeBase = null;
    static Direction myExploreDir = null;
    static Direction wallFollowDir = null;
    static boolean followingWall = false;
    static MapLocation knownEnemyTower = null;
    static boolean isEmergencyPaint = false;
    static int towersBuilt = 0;
    static MapLocation mapCenter = null;
    static int lastReportRound = -10;
    static MapLocation exploreTarget = null;
    static int exploreStuckCount = 0;
    static MapLocation lastPosition = null;

    public static void run(RobotController rc) throws Exception {
        RobotPlayer.rc = rc;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        rng = new Random(rc.getID());
        myExploreDir = Direction.allDirections()[rc.getID() % 8];
        wallFollowDir = myExploreDir;
        mapCenter = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);

        if (!rc.getType().isTowerType()) {
            RobotInfo[] near = rc.senseNearbyRobots(2, myTeam);
            for (RobotInfo r : near) {
                if (r.type.isTowerType()) {
                    homeBase = r.location;
                    break;
                }
            }
        }

        while (true) {
            try {
                readTeamSignals();
                UnitType myType = rc.getType();

                if (myType.isTowerType()) {
                    runTower();
                } else {
                    if (handlePaintEmergency()) {
                        continue;
                    }
                    if (myType == UnitType.SOLDIER)
                        runSoldier();
                    else if (myType == UnitType.SPLASHER)
                        runSplasher();
                    else if (myType == UnitType.MOPPER)
                        runMopper();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void runTower() throws Exception {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, myTeam);
        for (RobotInfo ally : allies) {
            if (!ally.type.isTowerType() && ally.getPaintAmount() < ally.type.paintCapacity * 0.8) {
                int give = Math.min(50, Math.min(rc.getPaint() - 20, ally.type.paintCapacity - ally.getPaintAmount()));
                if (give > 0 && rc.canTransferPaint(ally.location, give)) {
                    rc.transferPaint(ally.location, give);
                }
            }
        }

        if (rc.isActionReady() && rc.getChips() > 1000) {
            UnitType toBuild = selectUnitToSpawn();
            Direction toCenter = rc.getLocation().directionTo(mapCenter);
            if (toCenter == Direction.CENTER) {
                toCenter = Direction.NORTH;
            }
            Direction[] spawnDirs = { toCenter, toCenter.rotateLeft(), toCenter.rotateRight(),
                    toCenter.rotateLeft().rotateLeft(), toCenter.rotateRight().rotateRight(),
                    toCenter.opposite().rotateLeft(), toCenter.opposite().rotateRight(), toCenter.opposite() };
            for (Direction dir : spawnDirs) {
                MapLocation loc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(toBuild, loc)) {
                    rc.buildRobot(toBuild, loc);
                    break;
                }
            }
        }

        if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, enemyTeam);
            if (enemies.length > 0 && rc.canAttack(enemies[0].getLocation())) {
                rc.attack(enemies[0].getLocation());
            }
        }

        if (rc.getChips() > 2500 && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        }
    }

    static UnitType selectUnitToSpawn() {
        int round = rc.getRoundNum();
        if (round < 500)
            return UnitType.SOLDIER;
        if (round < 1000) {
            return rng.nextDouble() < 0.55 ? UnitType.SOLDIER : UnitType.MOPPER;
        }
        double roll = rng.nextDouble();
        if (roll < 0.55)
            return UnitType.SOLDIER;
        if (roll < 1.0)
            return UnitType.MOPPER;
        return UnitType.SPLASHER;
    }

    static void runSoldier() throws Exception {
        RobotInfo enemyTower = findClosestEnemyTower();
        if (enemyTower != null) {
            knownEnemyTower = enemyTower.location;
            reportEnemyTower(enemyTower.location);
            attackAndChaseTarget(enemyTower.location);
            paintCurrentTile();
            return;
        }

        if (knownEnemyTower != null) {
            if (rc.canSenseLocation(knownEnemyTower)) {
                RobotInfo there = rc.senseRobotAtLocation(knownEnemyTower);
                if (there == null || there.team != enemyTeam || !there.type.isTowerType()) {
                    knownEnemyTower = null;
                } else {
                    attackAndChaseTarget(knownEnemyTower);
                    paintCurrentTile();
                    return;
                }
            } else if (rc.getLocation().distanceSquaredTo(knownEnemyTower) > 2) {
                moveGreedyToward(knownEnemyTower);
                paintCurrentTile();
                return;
            } else {
                knownEnemyTower = null;
            }
        }

        MapLocation ruin = findClosestUnbuiltRuin();
        if (ruin != null) {
            RobotInfo[] nearRuin = rc.senseNearbyRobots(ruin, 8, myTeam);
            int soldiersNearRuin = 0;
            for (RobotInfo r : nearRuin) {
                if (r.type == UnitType.SOLDIER) soldiersNearRuin++;
            }
            if (soldiersNearRuin <= 2) {
                buildForwardBase(ruin);
                return;
            }
        }

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, enemyTeam);
        if (nearbyEnemies.length > 0) {
            attackAndChaseTarget(nearbyEnemies[0].location);
            paintCurrentTile();
            return;
        }

        if (rc.isActionReady() && rc.getChips() >= 200) {
            completeNearbyResourcePattern();
        }

        exploreFrontier();
        paintCurrentTile();
    }

    static void buildForwardBase(MapLocation ruin) throws Exception {
        int selector = (towersBuilt + rc.getID()) % 3;
        UnitType towerType;
        if (selector == 0)
            towerType = UnitType.LEVEL_ONE_PAINT_TOWER;
        else if (selector == 1)
            towerType = UnitType.LEVEL_ONE_MONEY_TOWER;
        else
            towerType = UnitType.LEVEL_ONE_DEFENSE_TOWER;

        boolean isAllyMarked = false;
        boolean isEnemyMarked = false;
        boolean canSenseAny = false;
        for (Direction d : Direction.allDirections()) {
            if (d == Direction.CENTER)
                continue;
            MapLocation scanLoc = ruin.add(d);
            if (rc.canSenseLocation(scanLoc)) {
                canSenseAny = true;
                PaintType mark = rc.senseMapInfo(scanLoc).getMark();
                if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) {
                    isAllyMarked = true;
                    break;
                } else if (mark != PaintType.EMPTY) {
                    isEnemyMarked = true;
                }
            }
        }

        if (!canSenseAny) {
            moveGreedyToward(ruin);
            return;
        }

        if (isEnemyMarked && !isAllyMarked) {
            return;
        }

        if (!isAllyMarked) {
            if (rc.canMarkTowerPattern(towerType, ruin)) {
                rc.markTowerPattern(towerType, ruin);
            } else {
                moveGreedyToward(ruin);
            }
            return;
        }

        MapLocation targetToPaint = null;
        boolean useSecondary = false;
        int tilesNeedingPaint = 0;
        int minDist = Integer.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = ruin.translate(dx, dy);
                if (rc.canSenseLocation(loc)) {
                    MapInfo info = rc.senseMapInfo(loc);
                    PaintType needed = info.getMark();
                    if (needed == PaintType.ALLY_PRIMARY || needed == PaintType.ALLY_SECONDARY) {
                        if (info.getPaint() != needed) {
                            tilesNeedingPaint++;
                            int dist = rc.getLocation().distanceSquaredTo(loc);
                            if (dist < minDist) {
                                minDist = dist;
                                targetToPaint = loc;
                                useSecondary = (needed == PaintType.ALLY_SECONDARY);
                            }
                        }
                    }
                }
            }
        }

        if (tilesNeedingPaint == 0) {
            int distRuin = rc.getLocation().distanceSquaredTo(ruin);
            if (distRuin > 0 && distRuin <= 2) {
                if (rc.getChips() >= 1000 && rc.canCompleteTowerPattern(towerType, ruin)) {
                    rc.completeTowerPattern(towerType, ruin);
                    towersBuilt++;
                } else {
                    paintCurrentTile();
                }
            } else if (distRuin == 0) {
                for (Direction d : Direction.allDirections()) {
                    if (d != Direction.CENTER && rc.canMove(d)) {
                        rc.move(d);
                        break;
                    }
                }
            } else {
                moveGreedyToward(ruin);
            }
            return;
        }

        if (targetToPaint != null) {
            if (rc.canAttack(targetToPaint) && rc.isActionReady()) {
                rc.attack(targetToPaint, useSecondary);
            } else if (!rc.canAttack(targetToPaint)) {
                moveGreedyToward(targetToPaint);
            }
        }
    }

    static void runMopper() throws Exception {
        if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, enemyTeam);
            if (enemies.length > 0) {
                Direction toEnemy = rc.getLocation().directionTo(enemies[0].getLocation());
                if (rc.canMopSwing(toEnemy)) {
                    rc.mopSwing(toEnemy);
                } else if (rc.canAttack(enemies[0].getLocation())) {
                    rc.attack(enemies[0].getLocation());
                }
            }
        }

        if (rc.isActionReady()) {
            MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
            MapLocation bestTarget = null;
            int bestScore = 0;
            for (MapInfo m : nearby) {
                if (!m.getPaint().isEnemy() || !rc.canAttack(m.getMapLocation()))
                    continue;
                int score = 1;
                for (Direction d : Direction.cardinalDirections()) {
                    MapLocation adj = m.getMapLocation().add(d);
                    if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) {
                        score += 3;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = m.getMapLocation();
                }
            }
            if (bestTarget != null)
                rc.attack(bestTarget);
        }

        if (rc.isMovementReady()) {
            MapLocation enemyPaint = findNearestEnemyPaint();
            if (enemyPaint != null) {
                moveGreedyToward(enemyPaint);
            } else {
                exploreFrontier();
            }
        }

        RobotInfo[] teammates = rc.senseNearbyRobots(2, myTeam);
        for (RobotInfo t : teammates) {
            if (!t.type.isTowerType() && t.getPaintAmount() < 30) {
                int give = Math.min(15, rc.getPaint() - 20);
                if (give > 0 && rc.canTransferPaint(t.location, give)) {
                    rc.transferPaint(t.location, give);
                    break;
                }
            }
        }
    }

    static void runSplasher() throws Exception {
        RobotInfo enemyTower = findClosestEnemyTower();
        if (enemyTower != null) {
            knownEnemyTower = enemyTower.location;
            reportEnemyTower(enemyTower.location);
            if (rc.isActionReady()) {
                MapLocation tgt = selectBestSplashPosition();
                if (tgt != null && rc.canAttack(tgt))
                    rc.attack(tgt);
            }
            if (rc.isMovementReady())
                moveGreedyToward(enemyTower.location);
            return;
        }

        if (knownEnemyTower != null && rc.canSenseLocation(knownEnemyTower)) {
            RobotInfo there = rc.senseRobotAtLocation(knownEnemyTower);
            if (there == null || there.team != enemyTeam || !there.type.isTowerType()) {
                knownEnemyTower = null;
            }
        }
        if (knownEnemyTower != null) {
            if (rc.isActionReady()) {
                MapLocation tgt = selectBestSplashPosition();
                if (tgt != null && rc.canAttack(tgt))
                    rc.attack(tgt);
            }
            if (rc.isMovementReady())
                moveGreedyToward(knownEnemyTower);
            return;
        }

        if (rc.isActionReady()) {
            MapLocation tgt = selectBestSplashPosition();
            if (tgt != null && rc.canAttack(tgt))
                rc.attack(tgt);
        }

        if (rc.isMovementReady()) {
            MapLocation ep = findNearestEnemyPaint();
            if (ep != null) {
                moveGreedyToward(ep);
            } else {
                exploreFrontier();
            }
        }
    }

    static void paintCurrentTile() throws Exception {
        if (!rc.isActionReady())
            return;
        MapLocation myLoc = rc.getLocation();
        if (rc.canSenseLocation(myLoc)) {
            MapInfo here = rc.senseMapInfo(myLoc);
            if (!here.getPaint().isAlly() && !here.isWall() && !here.hasRuin() && rc.canAttack(myLoc)) {
                rc.attack(myLoc);
                return;
            }
        }
        for (Direction d : Direction.cardinalDirections()) {
            MapLocation adj = myLoc.add(d);
            if (rc.canSenseLocation(adj)) {
                MapInfo info = rc.senseMapInfo(adj);
                if (!info.getPaint().isAlly() && !info.isWall() && !info.hasRuin() && rc.canAttack(adj)) {
                    rc.attack(adj);
                    return;
                }
            }
        }
    }

    static void completeNearbyResourcePattern() throws Exception {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (MapInfo m : nearby) {
            if (Clock.getBytecodesLeft() < 3000)
                break;
            MapLocation loc = m.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                return;
            }
        }
    }

    static void exploreFrontier() throws Exception {
        if (!rc.isMovementReady())
            return;

        MapLocation myLoc = rc.getLocation();

        if (lastPosition != null && myLoc.equals(lastPosition)) {
            exploreStuckCount++;
        } else {
            exploreStuckCount = 0;
        }
        lastPosition = myLoc;

        if (exploreStuckCount >= 3) {
            exploreTarget = null;
            exploreStuckCount = 0;
            myExploreDir = Direction.allDirections()[rng.nextInt(8)];
        }

        MapLocation bestNearby = null;
        int bestScore = -999;

        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (MapInfo m : nearby) {
            if (Clock.getBytecodesLeft() < 2500)
                break;
            PaintType p = m.getPaint();
            if (p.isAlly() || m.isWall() || m.hasRuin())
                continue;

            MapLocation loc = m.getMapLocation();
            int dist = myLoc.distanceSquaredTo(loc);

            int score = (p.isEnemy() ? 15 : 8);
            if (homeBase != null) {
                int homeDist = loc.distanceSquaredTo(homeBase);
                score += homeDist / 10;
            }
            score -= dist / 2;

            if (score > bestScore) {
                bestScore = score;
                bestNearby = loc;
            }
        }

        if (bestNearby != null) {
            exploreTarget = bestNearby;
            moveGreedyToward(bestNearby);
            return;
        }

        if (exploreTarget == null || myLoc.distanceSquaredTo(exploreTarget) <= 4) {
            int mapW = rc.getMapWidth();
            int mapH = rc.getMapHeight();
            int tx = myLoc.x + myExploreDir.dx * (mapW / 3);
            int ty = myLoc.y + myExploreDir.dy * (mapH / 3);
            tx = Math.max(1, Math.min(mapW - 2, tx));
            ty = Math.max(1, Math.min(mapH - 2, ty));
            exploreTarget = new MapLocation(tx, ty);
        }

        moveGreedyToward(exploreTarget);
    }

    static MapLocation findNearestEnemyPaint() throws Exception {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;
        for (MapInfo m : nearby) {
            if (m.getPaint().isEnemy()) {
                int d = rc.getLocation().distanceSquaredTo(m.getMapLocation());
                if (d < minDist) {
                    minDist = d;
                    best = m.getMapLocation();
                }
            }
        }
        return best;
    }

    static void readTeamSignals() throws Exception {
        Message[] messages = rc.readMessages(rc.getRoundNum() - 1);
        for (Message m : messages) {
            int bytes = m.getBytes();
            if ((bytes >> 16) == 1) {
                int x = (bytes >> 8) & 0xFF;
                int y = bytes & 0xFF;
                knownEnemyTower = new MapLocation(x, y);
            }
        }
    }

    static void reportEnemyTower(MapLocation loc) throws Exception {
        if (rc.getRoundNum() - lastReportRound < 10) {
            return;
        }
        lastReportRound = rc.getRoundNum();

        int msg = (1 << 16) | (loc.x << 8) | loc.y;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, myTeam);
        int sent = 0;
        for (RobotInfo ally : allies) {
            if (sent >= 2) break;
            if (rc.canSendMessage(ally.location, msg)) {
                rc.sendMessage(ally.location, msg);
                sent++;
            }
        }
    }

    static MapLocation selectBestSplashPosition() throws Exception {
        MapLocation best = null;
        int maxScore = 0;
        int evaluated = 0;

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, enemyTeam);
        for (RobotInfo enemy : nearbyEnemies) {
            if (evaluated >= 6 || Clock.getBytecodesLeft() < 3000)
                break;
            evaluated++;
            MapLocation loc = enemy.location;
            if (!rc.canAttack(loc))
                continue;
            int score = 10;
            for (MapLocation area : rc.getAllLocationsWithinRadiusSquared(loc, 4)) {
                if (Clock.getBytecodesLeft() < 1500)
                    break;
                if (rc.canSenseLocation(area)) {
                    PaintType pt = rc.senseMapInfo(area).getPaint();
                    if (pt.isEnemy())
                        score += 5;
                    else if (pt == PaintType.EMPTY)
                        score += 2;
                }
            }
            if (score > maxScore) {
                maxScore = score;
                best = loc;
            }
        }
        if (best != null)
            return best;

        MapInfo[] tiles = rc.senseNearbyMapInfos();
        for (MapInfo t : tiles) {
            if (evaluated >= 12 || Clock.getBytecodesLeft() < 3000)
                break;
            if (!t.getPaint().isEnemy())
                continue;
            evaluated++;
            MapLocation loc = t.getMapLocation();
            if (!rc.canAttack(loc))
                continue;
            int score = 0;
            for (MapLocation area : rc.getAllLocationsWithinRadiusSquared(loc, 4)) {
                if (Clock.getBytecodesLeft() < 1500)
                    break;
                if (rc.canSenseLocation(area)) {
                    PaintType pt = rc.senseMapInfo(area).getPaint();
                    if (pt.isEnemy())
                        score += 5;
                    else if (pt == PaintType.EMPTY)
                        score += 2;
                }
            }
            if (score > maxScore) {
                maxScore = score;
                best = loc;
            }
        }
        return best;
    }

    static void attackAndChaseTarget(MapLocation target) throws Exception {
        if (rc.canAttack(target) && rc.isActionReady())
            rc.attack(target);
        moveGreedyToward(target);
    }

    static void moveGreedyToward(MapLocation target) throws Exception {
        if (!rc.isMovementReady())
            return;
        Direction d = rc.getLocation().directionTo(target);
        if (d == Direction.CENTER)
            return;
        Direction[] dirs = { d, d.rotateLeft(), d.rotateRight(),
                d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight() };
        Direction bestDir = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                MapLocation next = rc.getLocation().add(dir);
                int penalty = 1;
                if (rc.canSenseLocation(next)) {
                    PaintType pt = rc.senseMapInfo(next).getPaint();
                    if (pt.isAlly()) {
                        penalty = 0;
                    } else if (pt.isEnemy()) {
                        penalty = 3; 
                    }
                }
                if (penalty < bestPenalty) {
                    bestPenalty = penalty;
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null) {
            rc.move(bestDir);
            followingWall = false;
        } else {
            bugNavigationMove();
        }
    }

    static void bugNavigationMove() throws Exception {
        if (!rc.isMovementReady())
            return;

        if (!followingWall) {
            if (rc.canMove(myExploreDir)) {
                rc.move(myExploreDir);
                return;
            }
            Direction[] sideDirs = { myExploreDir.rotateLeft(), myExploreDir.rotateRight(),
                    myExploreDir.rotateLeft().rotateLeft(), myExploreDir.rotateRight().rotateRight() };
            for (Direction sd : sideDirs) {
                if (rc.canMove(sd)) {
                    rc.move(sd);
                    return;
                }
            }
            followingWall = true;
            wallFollowDir = myExploreDir;
        }

        if (followingWall) {
            Direction tryDir = wallFollowDir.rotateRight().rotateRight();
            for (int i = 0; i < 8; i++) {
                if (rc.canMove(tryDir)) {
                    rc.move(tryDir);
                    wallFollowDir = tryDir;
                    if (tryDir == myExploreDir)
                        followingWall = false;
                    return;
                }
                tryDir = tryDir.rotateLeft();
            }
            myExploreDir = Direction.allDirections()[rng.nextInt(8)];
            followingWall = false;
        }
    }

    static boolean handlePaintEmergency() throws Exception {
        int capacity = rc.getType().paintCapacity;
        if (rc.getPaint() < capacity * 0.08) {
            isEmergencyPaint = true;
        } else if (rc.getPaint() > capacity * 0.6) {
            isEmergencyPaint = false;
        }

        if (isEmergencyPaint) {
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, myTeam);
            MapLocation closestTower = null;
            int minDist = Integer.MAX_VALUE;
            for (RobotInfo t : nearbyAllies) {
                if (t.type.isTowerType()) {
                    int dist = rc.getLocation().distanceSquaredTo(t.location);
                    if (dist < minDist) {
                        minDist = dist;
                        closestTower = t.location;
                    }
                    if (homeBase == null)
                        homeBase = t.location;
                }
            }

            if (rc.isMovementReady()) {
                if (closestTower != null) {
                    moveGreedyToward(closestTower);
                } else if (homeBase != null) {
                    moveGreedyToward(homeBase);
                } else {
                    Direction rd = Direction.allDirections()[rng.nextInt(8)];
                    if (rc.canMove(rd))
                        rc.move(rd);
                }
            }
            return true;
        }
        return false;
    }

    static MapLocation findClosestUnbuiltRuin() throws Exception {
        MapInfo[] tiles = rc.senseNearbyMapInfos();
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;
        for (MapInfo m : tiles) {
            if (m.hasRuin()) {
                MapLocation loc = m.getMapLocation();
                if (rc.canSenseRobotAtLocation(loc)) {
                    RobotInfo r = rc.senseRobotAtLocation(loc);
                    if (r != null && r.type.isTowerType())
                        continue;
                }
                int d = rc.getLocation().distanceSquaredTo(loc);
                if (d < minDist) {
                    minDist = d;
                    best = loc;
                }
            }
        }
        return best;
    }

    static RobotInfo findClosestEnemyTower() throws Exception {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
        RobotInfo closest = null;
        int minDist = Integer.MAX_VALUE;
        for (RobotInfo r : enemies) {
            if (r.type.isTowerType()) {
                int d = rc.getLocation().distanceSquaredTo(r.location);
                if (d < minDist) {
                    minDist = d;
                    closest = r;
                }
            }
        }
        return closest;
    }
}
