package abyss.Util;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public final class GreedyMove {
    private GreedyMove() {}

    public static Direction bestDirection(RobotController rc, MapLocation target) throws GameActionException {
        return bestDirection(rc, target, false);
    }

    public static Direction localDijkstraDirection(RobotController rc, MapLocation target) throws GameActionException {
        return bestDirection(rc, target, true);
    }

    private static Direction bestDirection(RobotController rc, MapLocation target, boolean tighterGoal) throws GameActionException {
        if (!rc.isMovementReady()) {
            return Direction.CENTER;
        }

        if(target == null){
            MapLocation edge = rc.getLocation();
            edge.add(Direction.EAST);
            freeMove(rc, edge);
        }

        MapLocation current = rc.getLocation();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        Direction preferred = current.directionTo(target);
        Direction best = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;

        for (Direction direction : orderedDirections(preferred)) {
            if (!rc.canMove(direction)) {
                continue;
            }
            MapLocation next = current.add(direction);
            MapInfo nextInfo = findInfo(nearby, next);
            int score = scoreMove(rc, current, next, nextInfo, nearby, allies, target, tighterGoal);
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return best;
    }

    private static int scoreMove(RobotController rc,MapLocation current,
            MapLocation next,
            MapInfo nextInfo,
            MapInfo[] nearby,
            RobotInfo[] allies,
            MapLocation target,
            boolean tighterGoal) {
        int score = -next.distanceSquaredTo(target) * (tighterGoal ? 18 : 12);
        if (nextInfo != null) {
            score += paintScore(rc, current, nextInfo.getPaint());
        }
        score += frontierScore(next, target, nearby);
        score -= crowdPenalty(next, allies);
        score -= edgePenalty(rc, next);
        if (isBacktracking(current, next, target)) {
            score -= 8;
        }
        return score;
    }

    static void freeMove(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction first = rc.getLocation().directionTo(target);
        Direction[] attempts = {first, first.rotateLeft(), first.rotateRight(), first.rotateLeft().rotateLeft(), first.rotateRight().rotateRight()};
        for (Direction d : attempts) { if (rc.canMove(d)) { rc.move(d); return; } }
    }

    private static int paintScore(RobotController rc, MapLocation current, PaintType paint) {
        if (paint == PaintType.EMPTY) {
            return 14;
        }
        if (paint.isAlly()) {
            return 4;
        }
        if (paint.isEnemy()) {
            int penalty = rc.getType() == UnitType.MOPPER ? -16 : -24;
            if (current != null) {
                penalty -= 4;
            }
            return penalty;
        }
        return 0;
    }

    private static int frontierScore(MapLocation next, MapLocation target, MapInfo[] nearby) {
        int score = 0;
        for (Direction direction : orderedDirections(next.directionTo(target))) {
            if (direction == Direction.CENTER) {
                continue;
            }
            MapLocation probe = next.add(direction);
            MapInfo info = findInfo(nearby, probe);
            if (info == null) {
                score += 3;
                continue;
            }
            if (!info.isPassable() || info.hasRuin()) {
                continue;
            }
            if (info.getPaint() == PaintType.EMPTY) {
                score += 3;
            } else if (info.getPaint().isEnemy()) {
                score -= 2;
            }
        }
        return score;
    }

    private static int crowdPenalty(MapLocation next, RobotInfo[] allies) {
        int penalty = 0;
        for (RobotInfo ally : allies) {
            if (ally.location.distanceSquaredTo(next) <= 2) {
                penalty += 4;
            }
        }
        return penalty;
    }

    private static int edgePenalty(RobotController rc, MapLocation next) {
        int minToEdge = Math.min(Math.min(next.x, next.y), Math.min(rc.getMapWidth() - 1 - next.x, rc.getMapHeight() - 1 - next.y));
        if (minToEdge <= 0) {
            return 10;
        }
        if (minToEdge == 1) {
            return 4;
        }
        return 0;
    }

    private static boolean isBacktracking(MapLocation current, MapLocation next, MapLocation target) {
        return next.distanceSquaredTo(target) > current.distanceSquaredTo(target) + 2;
    }

    private static MapInfo findInfo(MapInfo[] infos, MapLocation location) {
        for (MapInfo info : infos) {
            if (info.getMapLocation().equals(location)) {
                return info;
            }
        }
        return null;
    }

    private static Direction[] orderedDirections(Direction direction) {
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
}
