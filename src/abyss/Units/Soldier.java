package abyss.Units;

import abyss.Unit;
import abyss.Util.TowerInfo;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class Soldier extends Unit {
    private static final int EARLY_SCOUT_WINDOW = 100;
    private static final int ROLLING_BUILD_WINDOW = 40;
    private MapLocation blockedRuin = null;
    private int blockedUntilRound = -1;

    public Soldier(RobotController rc) {
        super(rc);
    }

    @Override
    protected UnitState chooseState() {
        if (lowPaint(0.12)) {
            return UnitState.REFILL;
        }
        if (shouldPrioritizeSymmetryScout()) {
            return UnitState.EXPLORE;
        }
        if (preferredBuildRuin() != null && rc.getChips() >= 1000) {
            return UnitState.BUILD;
        }
        if (nearestEnemyTower() != null && shouldAttackEnemyTower()) {
            return UnitState.ATTACK;
        }
        return UnitState.EXPLORE;
    }

    @Override
    protected void playState() throws GameActionException {
        switch (state) {
            case REFILL -> refillFromKnownTower();
            case BUILD -> buildTowerPattern();
            case ATTACK -> attackEnemyTower();
            case EXPLORE -> exploreAndPaint();
            default -> exploreAndPaint();
        }
    }

    private void buildTowerPattern() throws GameActionException {
        MapLocation ruin = preferredBuildRuin();
        if (ruin == null) {
            exploreAndPaint();
            return;
        }

        if (ruinHasEnemyPaint(ruin)) {
            MapLocation dirtyTile = firstEnemyPaintAroundRuin(ruin);
            sendMopperRequestIfPossible(dirtyTile);
            blockedRuin = ruin;
            blockedUntilRound = rc.getRoundNum() + ROLLING_BUILD_WINDOW;
            explorationTarget = getAlternateExploreTarget(ruin);
            exploreAndPaint();
            return;
        }

        UnitType towerType = chooseTowerTypeForRuin(ruin);
        if (towerType == null) {
            exploreAndPaint();
            return;
        }

        if (rc.canCompleteTowerPattern(towerType, ruin)) {
            rc.completeTowerPattern(towerType, ruin);
            rememberTower(ruin, TowerInfo.STATUS_ALLY, rc.getRoundNum());
            if (isPaintTower(towerType)) {
                knownPaintTower = ruin;
            }
            lastReportedTower = ruin;
            return;
        }

        if (rc.getLocation().distanceSquaredTo(ruin) > 8) {
            moveLocalDijkstra(ruin);
            return;
        }
        if (rc.canMarkTowerPattern(towerType, ruin)) {
            rc.markTowerPattern(towerType, ruin);
        }

        MapLocation patternTarget = nearestPatternMismatch(ruin);
        if (patternTarget != null) {
            if (rc.canAttack(patternTarget)) {
                rc.attack(patternTarget, useSecondaryForPattern(patternTarget));
            } else if (rc.isMovementReady()) {
                moveLocalDijkstra(patternTarget);
            }
        } else {
            paintNearbyNeutralTile();
        }

        if (rc.canCompleteTowerPattern(towerType, ruin)) {
            rc.completeTowerPattern(towerType, ruin);
            rememberTower(ruin, TowerInfo.STATUS_ALLY, rc.getRoundNum());
            if (isPaintTower(towerType)) {
                knownPaintTower = ruin;
            }
            lastReportedTower = ruin;
        } else if (rc.isMovementReady() && patternTarget == null) {
            moveGreedy(ruin);
        }
    }

    private void attackEnemyTower() throws GameActionException {
        MapLocation target = nearestEnemyTower();
        if (target == null) {
            exploreAndPaint();
            return;
        }
        if (rc.canAttack(target)) {
            rc.attack(target);
        } else {
            moveLocalDijkstra(target);
        }
        paintNearbyNeutralTile();
    }

    private void exploreAndPaint() throws GameActionException {
        paintNearbyNeutralTile();
        MapLocation localNeutral = nearestNeutralPaintableTile();
        if (localNeutral != null && rc.getLocation().distanceSquaredTo(localNeutral) > 2) {
            moveGreedy(localNeutral);
            return;
        }
        MapLocation attackableEnemyPaint = attackableEnemyPaintTile();
        if (attackableEnemyPaint != null && rc.isActionReady()) {
            rc.attack(attackableEnemyPaint);
            return;
        }
        if (explorationTarget != null) {
            moveLocalDijkstra(explorationTarget);
        }
    }

    private MapLocation preferredBuildRuin() {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        MapLocation current = rc.getLocation();
        for (int i = 0; i < towerInfoCount; i++) {
            TowerInfo info = towerInfos[i];
            if (info.status != TowerInfo.STATUS_RUIN) {
                continue;
            }
            if (blockedRuin != null
                    && info.location.equals(blockedRuin)
                    && rc.getRoundNum() < blockedUntilRound) {
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

    private boolean shouldPrioritizeSymmetryScout() {
        if (!shouldScoutSymmetry() || symmetry != abyss.Util.Symmetry.UNKNOWN) {
            return false;
        }
        if (rc.getRoundNum() > EARLY_SCOUT_WINDOW) {
            return false;
        }
        MapLocation ruin = nearestBuildableRuin();
        return ruin == null || rc.getLocation().distanceSquaredTo(ruin) > 4;
    }

    private MapLocation nearestPatternMismatch(MapLocation ruin) throws GameActionException {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation tile = ruin.translate(dx, dy);
                if (!rc.onTheMap(tile) || !rc.canSenseLocation(tile)) {
                    continue;
                }
                MapInfo info = rc.senseMapInfo(tile);
                if (!info.isPassable() || info.hasRuin()) {
                    continue;
                }
                PaintType desired = desiredPatternPaint(info.getMark());
                PaintType actual = info.getPaint();
                if (actual == desired) {
                    continue;
                }
                int distance = rc.getLocation().distanceSquaredTo(tile);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = tile;
                }
            }
        }
        return best;
    }

    private boolean useSecondaryForPattern(MapLocation tile) throws GameActionException {
        return desiredPatternPaint(rc.senseMapInfo(tile).getMark()) == PaintType.ALLY_SECONDARY;
    }

    private PaintType desiredPatternPaint(PaintType mark) {
        return mark == PaintType.ALLY_PRIMARY ? PaintType.ALLY_PRIMARY : PaintType.ALLY_SECONDARY;
    }

    private boolean shouldAttackEnemyTower() {
        if (rc.getRoundNum() > 1200) {
            return rc.getPaint() >= 35;
        }
        if (rc.getRoundNum() > 500) {
            return rc.getPaint() >= 55;
        }
        return rc.getPaint() >= 80;
    }

    private MapLocation getAlternateExploreTarget(MapLocation avoidedRuin) {
        MapLocation current = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < towerInfoCount; i++) {
            TowerInfo info = towerInfos[i];
            if (info.status != TowerInfo.STATUS_RUIN || info.location.equals(avoidedRuin)) {
                continue;
            }
            int distance = current.distanceSquaredTo(info.location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = info.location;
            }
        }
        return best != null ? best : explorationTarget;
    }
}
