package abyss;

import abyss.Util.Symmetry;
import abyss.Util.TowerInfo;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public abstract class Unit extends Robot {
    protected enum UnitState {
        EXPLORE,
        REFILL,
        BUILD,
        MOP,
        ATTACK
    }

    protected UnitState state = UnitState.EXPLORE;
    protected MapLocation explorationTarget = null;
    protected MapLocation lastReportedTower = null;

    protected MapInfo[] nearbyMapInfos = new MapInfo[0];
    protected RobotInfo[] nearbyAllies = new RobotInfo[0];
    protected RobotInfo[] nearbyEnemies = new RobotInfo[0];
    protected MapInfo currentTileInfo = null;
    protected MapLocation sensedNearestNeutral = null;
    protected MapLocation sensedNearestEnemyPaint = null;
    protected MapLocation sensedAttackableEnemyPaint = null;
    protected MapLocation sensedNearestSafeRuin = null;

    protected Unit(RobotController rc) {
        super(rc);
    }

    @Override
    protected void takeTurn() throws GameActionException {
        updateCommonState();
        senseTurnContext();
        markNearbySafeRuinPattern();
        chooseExplorationTarget();
        state = chooseState();
        playState();
        sendSymmetryIfPossible();
    }

    protected abstract UnitState chooseState() throws GameActionException;

    protected abstract void playState() throws GameActionException;

    protected void senseTurnContext() throws GameActionException {
        nearbyMapInfos = rc.senseNearbyMapInfos();
        nearbyAllies = rc.senseNearbyRobots(-1, team);
        nearbyEnemies = rc.senseNearbyRobots(-1, opponent);
        currentTileInfo = rc.senseMapInfo(rc.getLocation());

        sensedNearestNeutral = null;
        sensedNearestEnemyPaint = null;
        sensedAttackableEnemyPaint = null;
        sensedNearestSafeRuin = null;

        MapLocation current = rc.getLocation();
        int neutralDistance = Integer.MAX_VALUE;
        int enemyDistance = Integer.MAX_VALUE;
        int attackableDistance = Integer.MAX_VALUE;
        int ruinDistance = Integer.MAX_VALUE;

        for (MapInfo info : nearbyMapInfos) {
            MapLocation location = info.getMapLocation();
            if (info.hasRuin() && !ruinHasEnemyPaint(location)) {
                int distance = current.distanceSquaredTo(location);
                if (distance < ruinDistance) {
                    ruinDistance = distance;
                    sensedNearestSafeRuin = location;
                }
            }
            if (!info.isPassable() || info.hasRuin()) {
                continue;
            }
            if (info.getPaint() == PaintType.EMPTY) {
                int distance = current.distanceSquaredTo(location);
                if (distance < neutralDistance) {
                    neutralDistance = distance;
                    sensedNearestNeutral = location;
                }
            } else if (info.getPaint().isEnemy()) {
                int distance = current.distanceSquaredTo(location);
                if (distance < enemyDistance) {
                    enemyDistance = distance;
                    sensedNearestEnemyPaint = location;
                }
                if (rc.canAttack(location) && distance < attackableDistance) {
                    attackableDistance = distance;
                    sensedAttackableEnemyPaint = location;
                }
            }
        }
    }

    protected void chooseExplorationTarget() {
        if (shouldScoutSymmetry() && symmetry == Symmetry.UNKNOWN) {
            MapLocation symmetryTarget = chooseSymmetryScoutTarget();
            if (symmetryTarget != null) {
                explorationTarget = symmetryTarget;
                return;
            }
        }

        if (explorationTarget != null && rc.getLocation().distanceSquaredTo(explorationTarget) <= 2) {
            explorationTarget = null;
        }
        if (explorationTarget != null && localAreaFullyAllied()) {
            explorationTarget = extendExplorationTarget(explorationTarget);
        }
        if (explorationTarget != null) {
            return;
        }

        MapLocation observedRuin = getNearestKnownTower(TowerInfo.STATUS_RUIN);
        if (observedRuin != null) {
            explorationTarget = observedRuin;
            return;
        }

        MapLocation predictedRuin = getNearestPredictedRuin();
        if (predictedRuin != null) {
            explorationTarget = predictedRuin;
            return;
        }

        if (rc.getRoundNum() > 500) {
            MapLocation enemyTower = nearestEnemyTower();
            if (enemyTower != null) {
                explorationTarget = enemyTower;
                return;
            }
        }

        MapLocation current = rc.getLocation();
        int halfW = mapWidth / 2;
        int halfH = mapHeight / 2;
        int targetX = current.x < halfW ? halfW / 2 : halfW + (mapWidth - halfW) / 2;
        int targetY = current.y < halfH ? halfH / 2 : halfH + (mapHeight - halfH) / 2;
        explorationTarget = new MapLocation(targetX, targetY);
    }

    protected MapLocation chooseSymmetryScoutTarget() {
        MapLocation checkpoint = nearestUnseenCheckpoint();
        MapLocation verification = nearestVerificationTarget();
        if (checkpoint == null) {
            return verification;
        }
        if (verification == null) {
            return checkpoint;
        }
        int checkpointCost = rc.getLocation().distanceSquaredTo(checkpoint);
        int verificationCost = rc.getLocation().distanceSquaredTo(verification);
        return checkpointCost <= verificationCost ? checkpoint : verification;
    }

    protected MapLocation nearestUnseenCheckpoint() {
        MapLocation[] checkpoints = getQuadrantCheckpoints();
        MapLocation current = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MapLocation checkpoint : checkpoints) {
            if (checkpoint == null || isObserved(checkpoint) || rc.canSenseLocation(checkpoint)) {
                continue;
            }
            int distance = current.distanceSquaredTo(checkpoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = checkpoint;
            }
        }
        return best;
    }

    protected MapLocation nearestVerificationTarget() {
        if (getPossibleSymmetryCount() == 0) {
            return null;
        }
        MapLocation current = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                MapLocation source = new MapLocation(x, y);
                byte feature = featureAt(source);
                if (feature != FEATURE_WALL && feature != FEATURE_RUIN && feature != FEATURE_ALLY_TOWER && feature != FEATURE_ENEMY_TOWER) {
                    continue;
                }
                best = chooseBestVerificationFromCandidates(current, source, best, bestDistance);
                if (best != null) {
                    bestDistance = current.distanceSquaredTo(best);
                }
            }
        }
        return best;
    }

    private MapLocation chooseBestVerificationFromCandidates(MapLocation current, MapLocation source, MapLocation best, int bestDistance) {
        if (possibleRotational) {
            best = pickBetterVerification(current, best, bestDistance, Symmetry.ROTATIONAL.mirror(source, mapWidth, mapHeight));
            if (best != null) {
                bestDistance = current.distanceSquaredTo(best);
            }
        }
        if (possibleHorizontal) {
            best = pickBetterVerification(current, best, bestDistance, Symmetry.HORIZONTAL.mirror(source, mapWidth, mapHeight));
            if (best != null) {
                bestDistance = current.distanceSquaredTo(best);
            }
        }
        if (possibleVertical) {
            best = pickBetterVerification(current, best, bestDistance, Symmetry.VERTICAL.mirror(source, mapWidth, mapHeight));
        }
        return best;
    }

    private MapLocation pickBetterVerification(MapLocation current, MapLocation best, int bestDistance, MapLocation candidate) {
        if (candidate == null || isObserved(candidate) || rc.canSenseLocation(candidate)) {
            return best;
        }
        int distance = current.distanceSquaredTo(candidate);
        if (best == null || distance < bestDistance) {
            return candidate;
        }
        return best;
    }

    protected MapLocation[] getQuadrantCheckpoints() {
        int minX = spawnLocation.x * 2 < mapWidth ? 0 : mapWidth / 2;
        int maxX = spawnLocation.x * 2 < mapWidth ? Math.max(0, mapWidth / 2 - 1) : mapWidth - 1;
        int minY = spawnLocation.y * 2 < mapHeight ? 0 : mapHeight / 2;
        int maxY = spawnLocation.y * 2 < mapHeight ? Math.max(0, mapHeight / 2 - 1) : mapHeight - 1;

        int midX = (minX + maxX) / 2;
        int midY = (minY + maxY) / 2;
        int innerX = spawnLocation.x * 2 < mapWidth ? maxX : minX;
        int innerY = spawnLocation.y * 2 < mapHeight ? maxY : minY;

        return new MapLocation[] {
                new MapLocation(midX, midY),
                new MapLocation(innerX, midY),
                new MapLocation(midX, innerY),
                new MapLocation(innerX, innerY)
        };
    }

    protected boolean localAreaFullyAllied() {
        boolean foundWalkable = false;
        for (MapInfo info : nearbyMapInfos) {
            if (!info.isPassable() || info.hasRuin()) {
                continue;
            }
            foundWalkable = true;
            if (!info.getPaint().isAlly()) {
                return false;
            }
        }
        return foundWalkable;
    }

    protected MapLocation extendExplorationTarget(MapLocation currentTarget) {
        MapLocation current = rc.getLocation();
        int dx = Integer.compare(currentTarget.x, current.x);
        int dy = Integer.compare(currentTarget.y, current.y);
        if (dx == 0 && dy == 0) {
            dx = current.x < mapWidth / 2 ? 1 : -1;
            dy = current.y < mapHeight / 2 ? 1 : -1;
        }

        int nextX = currentTarget.x;
        int nextY = currentTarget.y;
        for (int step = 0; step < 6; step++) {
            int candidateX = nextX + dx;
            int candidateY = nextY + dy;
            if (candidateX < 0 || candidateX >= mapWidth || candidateY < 0 || candidateY >= mapHeight) {
                break;
            }
            nextX = candidateX;
            nextY = candidateY;
        }
        return new MapLocation(nextX, nextY);
    }

    protected boolean refillFromKnownTower() throws GameActionException {
        MapLocation target = knownPaintTower != null ? knownPaintTower : getNearestKnownTower(TowerInfo.STATUS_ALLY);
        if (target == null) {
            return false;
        }
        if (rc.getLocation().distanceSquaredTo(target) > 2) {
            moveGreedy(target);
            return true;
        }
        if (rc.canSenseRobotAtLocation(target)) {
            RobotInfo tower = rc.senseRobotAtLocation(target);
            int missing = rc.getType().paintCapacity - rc.getPaint();
            int amount = Math.min(missing, tower.getPaintAmount());
            if (amount > 0 && rc.canTransferPaint(target, -amount)) {
                rc.transferPaint(target, -amount);
                return true;
            }
        }
        return false;
    }

    protected MapLocation nearestEnemyTower() {
        return getNearestKnownTower(TowerInfo.STATUS_ENEMY);
    }

    protected void markNearbySafeRuinPattern() throws GameActionException {
        if (rc.getPaint() < 35) {
            return;
        }
        MapLocation ruin = nearestMarkableSafeRuin();
        if (ruin == null || ruinHasAnyPatternMark(ruin)) {
            return;
        }
        UnitType towerType = chooseTowerTypeForRuin(ruin);
        if (towerType != null && rc.canMarkTowerPattern(towerType, ruin)) {
            rc.markTowerPattern(towerType, ruin);
        }
    }

    protected MapLocation nearestMarkableSafeRuin() throws GameActionException {
        if (sensedNearestSafeRuin != null) {
            return sensedNearestSafeRuin;
        }
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        MapLocation current = rc.getLocation();
        for (int i = 0; i < towerInfoCount; i++) {
            TowerInfo info = towerInfos[i];
            if (info.status != TowerInfo.STATUS_RUIN) {
                continue;
            }
            MapLocation ruin = info.location;
            if (!rc.canSenseLocation(ruin) || ruinHasEnemyPaint(ruin)) {
                continue;
            }
            int distance = current.distanceSquaredTo(ruin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ruin;
            }
        }
        return best;
    }

    protected boolean ruinHasAnyPatternMark(MapLocation ruin) throws GameActionException {
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
                if (info.getMark() == PaintType.ALLY_PRIMARY || info.getMark() == PaintType.ALLY_SECONDARY) {
                    return true;
                }
            }
        }
        return false;
    }

    protected UnitType chooseTowerTypeForRuin(MapLocation ruin) {
        if (rc.getRoundNum() < 250 && knownPaintTower == null) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        if (rc.getChips() < UnitType.LEVEL_ONE_PAINT_TOWER.moneyCost + 250) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        if (rc.getRoundNum() > 900 && rc.getNumberTowers() >= 6 && rc.getChips() > 1800) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        if (knownPaintTower == null || rc.getPaint() < 160) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    protected boolean ruinHasEnemyPaint(MapLocation ruin) throws GameActionException {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation tile = ruin.translate(dx, dy);
                if (!rc.onTheMap(tile) || !rc.canSenseLocation(tile)) {
                    continue;
                }
                MapInfo info = rc.senseMapInfo(tile);
                if (info.getPaint().isEnemy()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected MapLocation firstEnemyPaintAroundRuin(MapLocation ruin) throws GameActionException {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation tile = ruin.translate(dx, dy);
                if (!rc.onTheMap(tile) || !rc.canSenseLocation(tile)) {
                    continue;
                }
                MapInfo info = rc.senseMapInfo(tile);
                if (!info.getPaint().isEnemy()) {
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

    protected void paintNearbyNeutralTile() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }
        if (sensedNearestNeutral != null && rc.canAttack(sensedNearestNeutral)) {
            rc.attack(sensedNearestNeutral);
            return;
        }
        if (currentTileInfo != null && currentTileInfo.getPaint() == PaintType.EMPTY && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }

}
