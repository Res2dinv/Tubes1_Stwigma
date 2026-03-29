package alternative_bot_1;

import alternative_bot_1.*;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public final class RobotPlayer {
    private RobotPlayer() {}

    public static void run(RobotController rc) throws GameActionException {
        Robot robot = createRobot(rc);
        robot.run();
    }

    private static Robot createRobot(RobotController rc) throws GameActionException {
        UnitType type = rc.getType();
        return switch (type) {
            case SOLDIER -> new Soldier(rc);
            case MOPPER -> new Mopper(rc);
            case SPLASHER -> new Splasher(rc);
            case LEVEL_ONE_PAINT_TOWER, LEVEL_TWO_PAINT_TOWER, LEVEL_THREE_PAINT_TOWER -> new PaintTower(rc);
            case LEVEL_ONE_MONEY_TOWER, LEVEL_TWO_MONEY_TOWER, LEVEL_THREE_MONEY_TOWER -> new MoneyTower(rc);
            case LEVEL_ONE_DEFENSE_TOWER, LEVEL_TWO_DEFENSE_TOWER, LEVEL_THREE_DEFENSE_TOWER -> new DefenseTower(rc);
        };
    }
}
