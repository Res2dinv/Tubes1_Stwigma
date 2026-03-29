package alternative_bot_1.Units;

import alternative_bot_1.*;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper extends Unit {
    public Mopper(RobotController rc) {
        super(rc);
    }

    @Override
    protected UnitState chooseState() throws GameActionException {
        if (lowPaint(0.25)) {
            return UnitState.REFILL;
        }
        if (requestedMopperTarget != null) {
            return UnitState.MOP;
        }
        if (sensedAttackableEnemyPaint != null) {
            return UnitState.MOP;
        }
        if (sensedNearestEnemyPaint != null) {
            return UnitState.MOP;
        }
        return UnitState.EXPLORE;
    }

    @Override
    protected void playState() throws GameActionException {
        switch (state) {
            case REFILL -> refillFromKnownTower();
            case MOP -> mopEnemyPaint();
            case EXPLORE -> exploreArea();
            default -> exploreArea();
        }
    }

    private void mopEnemyPaint() throws GameActionException {
        MapLocation target = requestedMopperTarget != null ? requestedMopperTarget : preferredMopTarget();
        if (target == null) {
            exploreArea();
            return;
        }
        if (rc.canAttack(target)) {
            rc.attack(target);
            if (requestedMopperTarget != null && rc.getLocation().distanceSquaredTo(target) <= 2) {
                requestedMopperTarget = null;
            }
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
        for (RobotInfo enemy : enemies) {
            if (enemy.getPaintAmount() <= 0) {
                continue;
            }
            if (rc.canAttack(enemy.location)) {
                rc.attack(enemy.location);
                return;
            }
        }
        moveLocalDijkstra(target);
    }

    private void exploreArea() throws GameActionException {
        if (sensedNearestEnemyPaint != null) {
            moveLocalDijkstra(sensedNearestEnemyPaint);
            return;
        }

        if (sensedNearestNeutral != null) {
            moveGreedy(sensedNearestNeutral);
            return;
        }
        if (explorationTarget != null) {
            moveLocalDijkstra(explorationTarget);
        }
    }

    private MapLocation preferredMopTarget() throws GameActionException {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < towerInfoCount; i++) {
            if (towerInfos[i].status != alternative_bot_1.Util.TowerInfo.STATUS_RUIN) {
                continue;
            }
            MapLocation dirtyTile = firstEnemyPaintAroundRuin(towerInfos[i].location);
            if (dirtyTile == null) {
                continue;
            }
            int distance = rc.getLocation().distanceSquaredTo(dirtyTile);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = dirtyTile;
            }
        }

        if (best != null) {
            return best;
        }
        return sensedNearestEnemyPaint;
    }
}
