package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionTimeRange;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.EscortTaskData.
 * The unsafe arc around the escorted ship plus the ship driving that threat.
 */
import data.scripts.battlesnext.util.annotation.PublicData;

@PublicData
public class BattleNext_EscortTaskData {
	public ShipAPI threatShip;
	public BattleNext_CollisionTimeRange collisionTimeRange;
}
