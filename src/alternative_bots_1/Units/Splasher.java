package alternative_bot_1.Units;

import alternative_bot_1.Unit;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Splasher extends Unit {
    public Splasher(RobotController rc) {
        super(rc);
    }

    @Override
    protected UnitState chooseState() {
        if (lowPaint(0.40)) {
            return UnitState.REFILL;
        }
        return UnitState.ATTACK;
    }

    @Override
    protected void playState() throws GameActionException {
        if (state == UnitState.REFILL) {
            refillFromKnownTower();
            return;
        }

        MapLocation best = bestSplashTarget();
        if (best != null) {
            if (rc.canAttack(best)) {
                rc.attack(best);
            } else {
                moveGreedy(best);
            }
            return;
        }

        if (explorationTarget != null) {
            moveLocalDijkstra(explorationTarget);
        }
    }

    private MapLocation bestSplashTarget() throws GameActionException {
        MapInfo[] infos = rc.senseNearbyMapInfos();
        MapLocation best = null;
        int bestScore = 0;
        for (MapInfo info : infos) {
            MapLocation center = info.getMapLocation();
            if (rc.getLocation().distanceSquaredTo(center) > 4) {
                continue;
            }
            int score = 0;
            for (MapInfo other : infos) {
                if (center.distanceSquaredTo(other.getMapLocation()) > 2) {
                    continue;
                }
                if (other.getPaint().isEnemy()) {
                    score += 2;
                } else if (other.getPaint().isAlly()) {
                    score -= 1;
                } else {
                    score += 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = center;
            }
        }
        return best;
    }
}
