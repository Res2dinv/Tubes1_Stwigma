package alternative_bot_1;

import alternative_bot_1.*;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public abstract class Tower extends Robot {
    private static final int SYMMETRY_BROADCAST_PERIOD = 12;
    private static final int MAX_NEARBY_FIELD_UNITS = 4;
    private static final int MAX_MID_FIELD_UNITS = 3;
    private static final int MAX_CLOSE_FIELD_UNITS = 1;
    private int lastSpawnRound = -100;

    protected Tower(RobotController rc) {
        super(rc);
    }

    @Override
    protected void takeTurn() throws GameActionException {
        updateCommonState();
        clearResolvedMopperRequest();
        broadcastSymmetryIfPossible();
        shareKnownInfo();
        upgradeIfRich();
        spawnGreedyUnit();
        attackLowestHealthEnemy();
    }

    private void broadcastSymmetryIfPossible() throws GameActionException {
        if (symmetry != alternative_bot_1.Util.Symmetry.UNKNOWN
                && rc.canBroadcastMessage()
                && rc.getRoundNum() % SYMMETRY_BROADCAST_PERIOD == 0) {
            rc.broadcastMessage(comms.encodeSymmetry(symmetry));
        }
    }

    private void shareKnownInfo() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, team);
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType() || !rc.canSendMessage(ally.location)) {
                continue;
            }
            if (requestedMopperTarget != null && ally.getType() == UnitType.MOPPER) {
                rc.sendMessage(ally.location, comms.encodeMopperTarget(requestedMopperTarget));
                continue;
            }
            if (symmetry != alternative_bot_1.Util.Symmetry.UNKNOWN) {
                rc.sendMessage(ally.location, comms.encodeSymmetry(symmetry));
                continue;
            }
            MapLocation nearestTower = getNearestKnownTower(TowerInfo.STATUS_ALLY);
            if (nearestTower != null) {
                rc.sendMessage(ally.location, comms.encodeTower(nearestTower, TowerInfo.STATUS_ALLY));
            }
        }
    }

    private void spawnGreedyUnit() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }
        RobotInfo[] allies = rc.senseNearbyRobots(-1, team);
        int nearbyFieldUnits = countFieldUnits(allies, 20);
        int midFieldUnits = countFieldUnits(allies, 8);
        int closeFieldUnits = countFieldUnits(allies, 2);
        boolean urgentMopper = requestedMopperTarget != null && countType(allies, UnitType.MOPPER) == 0;

        if (!urgentMopper) {
            if (nearbyFieldUnits >= MAX_NEARBY_FIELD_UNITS
                    || midFieldUnits >= MAX_MID_FIELD_UNITS
                    || closeFieldUnits >= MAX_CLOSE_FIELD_UNITS) {
                return;
            }
            if (rc.getRoundNum() - lastSpawnRound < spawnGapForRound()) {
                return;
            }
        }

        UnitType spawnType = chooseSpawnType();
        if (spawnType == null) {
            return;
        }
        if (!urgentMopper && !hasResourceBufferFor(spawnType)) {
            return;
        }
        MapLocation center = rc.getLocation();
        int openSpawnTiles = 0;
        for (battlecode.common.Direction direction : DIRECTIONS) {
            MapLocation first = center.add(direction);
            if (rc.canBuildRobot(spawnType, first)) {
                openSpawnTiles++;
            }
            MapLocation second = first.add(direction);
            if (rc.canBuildRobot(spawnType, second)) {
                openSpawnTiles++;
            }
        }
        if (!urgentMopper && openSpawnTiles <= 1) {
            return;
        }
        for (battlecode.common.Direction direction : DIRECTIONS) {
            MapLocation first = center.add(direction);
            if (rc.canBuildRobot(spawnType, first)) {
                rc.buildRobot(spawnType, first);
                lastSpawnRound = rc.getRoundNum();
                return;
            }
            MapLocation second = first.add(direction);
            if (rc.canBuildRobot(spawnType, second)) {
                rc.buildRobot(spawnType, second);
                lastSpawnRound = rc.getRoundNum();
                return;
            }
        }
    }

    private UnitType chooseSpawnType() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, team);
        int nearbySoldiers = 0;
        int nearbyMoppers = 0;
        for (RobotInfo ally : allies) {
            if (ally.getType() == UnitType.SOLDIER) {
                nearbySoldiers++;
            } else if (ally.getType() == UnitType.MOPPER) {
                nearbyMoppers++;
            }
        }

        if (requestedMopperTarget != null
                && nearbyMoppers == 0
                && rc.getPaint() >= UnitType.MOPPER.paintCost
                && rc.getChips() >= UnitType.MOPPER.moneyCost) {
            return UnitType.MOPPER;
        }
        if (rc.getRoundNum() < 120
                && rc.getPaint() >= UnitType.SOLDIER.paintCost
                && rc.getChips() >= UnitType.SOLDIER.moneyCost) {
            return UnitType.SOLDIER;
        }
        if (nearbySoldiers < 2
                && rc.getPaint() >= UnitType.SOLDIER.paintCost
                && rc.getChips() >= UnitType.SOLDIER.moneyCost) {
            return UnitType.SOLDIER;
        }
        if (rc.getRoundNum() > 600
                && rc.getRoundNum() % 10 == 0
                && rc.getPaint() >= UnitType.SPLASHER.paintCost
                && rc.getChips() >= UnitType.SPLASHER.moneyCost) {
            return UnitType.SPLASHER;
        }
        if (rc.getRoundNum() > 1000
                && rc.getRoundNum() % 6 == 0
                && rc.getPaint() >= UnitType.SPLASHER.paintCost
                && rc.getChips() >= UnitType.SPLASHER.moneyCost) {
            return UnitType.SPLASHER;
        }
        if (rc.getPaint() >= UnitType.SOLDIER.paintCost && rc.getChips() >= UnitType.SOLDIER.moneyCost) {
            return UnitType.SOLDIER;
        }
        if (nearbyMoppers == 0
                && rc.getPaint() >= UnitType.MOPPER.paintCost
                && rc.getChips() >= UnitType.MOPPER.moneyCost) {
            return UnitType.MOPPER;
        }
        return null;
    }

    private int spawnGapForRound() {
        if (rc.getRoundNum() < 150) {
            return 8;
        }
        if (rc.getRoundNum() < 800) {
            return 12;
        }
        return 18;
    }

    private int countFieldUnits(RobotInfo[] allies, int radiusSquared) {
        int count = 0;
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                continue;
            }
            if (radiusSquared != Integer.MAX_VALUE && ally.location.distanceSquaredTo(rc.getLocation()) > radiusSquared) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean hasResourceBufferFor(UnitType spawnType) {
        int paintReserve = paintReserveForRound();
        int chipReserve = chipReserveForRound();
        return rc.getPaint() - spawnType.paintCost >= paintReserve
                && rc.getChips() - spawnType.moneyCost >= chipReserve;
    }

    private int paintReserveForRound() {
        if (rc.getRoundNum() < 200) {
            return 350;
        }
        if (rc.getRoundNum() < 900) {
            return 275;
        }
        return 220;
    }

    private int chipReserveForRound() {
        if (rc.getRoundNum() < 200) {
            return 600;
        }
        if (rc.getRoundNum() < 900) {
            return 450;
        }
        return 325;
    }

    private int countType(RobotInfo[] allies, UnitType type) {
        int count = 0;
        for (RobotInfo ally : allies) {
            if (ally.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private void attackLowestHealthEnemy() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
        RobotInfo best = null;
        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.location)) {
                continue;
            }
            if (best == null || enemy.health < best.health) {
                best = enemy;
            }
        }
        if (best != null) {
            rc.attack(best.location);
        }
    }

    private void clearResolvedMopperRequest() throws GameActionException {
        if (requestedMopperTarget == null || !rc.canSenseLocation(requestedMopperTarget)) {
            return;
        }
        if (!rc.senseMapInfo(requestedMopperTarget).getPaint().isEnemy()) {
            requestedMopperTarget = null;
        }
    }

    private void upgradeIfRich() throws GameActionException {
        if (!rc.canUpgradeTower(rc.getLocation())) {
            return;
        }
        if (rc.getRoundNum() > 700 && rc.getChips() > 3500) {
            rc.upgradeTower(rc.getLocation());
        } else if (rc.getRoundNum() > 1100 && rc.getChips() > 2800) {
            rc.upgradeTower(rc.getLocation());
        }
    }
}
