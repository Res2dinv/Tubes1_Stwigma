package abyss;

import abyss.Util.TowerInfo;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public abstract class Tower extends Robot {
    private static final int SYMMETRY_BROADCAST_PERIOD = 12;

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
        if (symmetry != abyss.Util.Symmetry.UNKNOWN
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
            if (symmetry != abyss.Util.Symmetry.UNKNOWN) {
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
        UnitType spawnType = chooseSpawnType();
        if (spawnType == null) {
            return;
        }
        MapLocation center = rc.getLocation();
        for (battlecode.common.Direction direction : DIRECTIONS) {
            MapLocation first = center.add(direction);
            if (rc.canBuildRobot(spawnType, first)) {
                rc.buildRobot(spawnType, first);
                return;
            }
            MapLocation second = first.add(direction);
            if (rc.canBuildRobot(spawnType, second)) {
                rc.buildRobot(spawnType, second);
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
