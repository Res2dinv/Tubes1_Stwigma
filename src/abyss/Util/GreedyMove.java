package abyss.Util;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;

public final class GreedyMove {
    private GreedyMove() {}

    public static Direction bestDirection(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) {
            return Direction.CENTER;
        }
        MapLocation current = rc.getLocation();
        Direction direct = current.directionTo(target);
        Direction best = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;
        for (Direction direction : directionalSweep(direct)) {
            if (!rc.canMove(direction)) {
                continue;
            }
            MapLocation next = current.add(direction);
            int score = movementScore(rc, current, next, target, false);
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return best;
    }

    public static Direction localDijkstraDirection(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) {
            return Direction.CENTER;
        }
        MapLocation origin = rc.getLocation();
        Direction best = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;
        for (Direction direction : directionalSweep(origin.directionTo(target))) {
            if (!rc.canMove(direction)) {
                continue;
            }
            MapLocation next = origin.add(direction);
            int score = movementScore(rc, origin, next, target, true);
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return best;
    }

    public static Direction[] directionalSweep(Direction direction) {
        return new Direction[] {
                direction,
                direction.rotateLeft(),
                direction.rotateRight(),
                direction.rotateLeft().rotateLeft(),
                direction.rotateRight().rotateRight(),
                direction.rotateLeft().rotateLeft().rotateLeft(),
                direction.rotateRight().rotateRight().rotateRight(),
                direction.opposite()
        };
    }

    private static int movementScore(RobotController rc, MapLocation current, MapLocation next, MapLocation target, boolean strongerTargetBias) throws GameActionException {
        int score = -next.distanceSquaredTo(target) * (strongerTargetBias ? 16 : 12);
        score += tileScore(rc, current, next);
        score += frontierScore(rc, next, target);
        score -= allyCrowdingPenalty(rc, next);
        return score;
    }

    private static int frontierScore(RobotController rc, MapLocation location, MapLocation target) throws GameActionException {
        int unseenNeighbors = 0;
        int neutralNeighbors = 0;
        for (Direction direction : directionalSweep(location.directionTo(target))) {
            if (direction == Direction.CENTER) {
                continue;
            }
            MapLocation probe = location.add(direction);
            if (!rc.onTheMap(probe)) {
                continue;
            }
            if (!rc.canSenseLocation(probe)) {
                unseenNeighbors++;
                continue;
            }
            MapInfo info = rc.senseMapInfo(probe);
            if (!info.isPassable() || info.hasRuin()) {
                continue;
            }
            if (info.getPaint() == PaintType.EMPTY) {
                neutralNeighbors++;
            }
        }
        return unseenNeighbors * 3 + neutralNeighbors * 2;
    }

    private static int allyCrowdingPenalty(RobotController rc, MapLocation location) throws GameActionException {
        int penalty = 0;
        for (battlecode.common.RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (ally.location.equals(rc.getLocation())) {
                continue;
            }
            if (ally.location.distanceSquaredTo(location) <= 2) {
                penalty += 3;
            }
        }
        return penalty;
    }

    private static int tileScore(RobotController rc, MapLocation current, MapLocation next) throws GameActionException {
        if (!rc.canSenseLocation(next)) {
            return 0;
        }
        PaintType paint = rc.senseMapInfo(next).getPaint();
        if (paint.isAlly()) {
            return 2;
        }
        if (paint == PaintType.EMPTY) {
            return 10;
        }
        if (paint.isEnemy()) {
            int penalty = rc.getType() == battlecode.common.UnitType.MOPPER ? -14 : -10;
            if (rc.canSenseLocation(current) && rc.senseMapInfo(current).getPaint().isEnemy()) {
                penalty += 6;
            }
            return penalty;
        }
        return 0;
    }
}
