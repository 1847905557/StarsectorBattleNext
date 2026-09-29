package data.scripts.ai.rtt;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_RttMoveManeuverTest {

	@Test
	void rttHoldReleasesMovingTargetBeyondEightyUnits() {
		Vector2f shipLocation = new Vector2f();
		Vector2f destination = new Vector2f(80f, 0f);
		BattleNext_RttMoveManeuver maneuver = new BattleNext_RttMoveManeuver(rttShip(shipLocation), () -> destination);

		assertFalse(maneuver.isDone());
		assertEquals(Float.MAX_VALUE, maneuver.getDesiredFacing());
		destination.x = 81f;
		assertTrue(maneuver.isDone());
	}

	@Test
	void rttHoldFacesEnemyIndependentlyOfDestination() throws ReflectiveOperationException {
		ShipAPI ship = rttShip(new Vector2f());
		ShipAPI enemy = rttShip(new Vector2f(-100f, 0f));
		BattleNext_RttMoveManeuver maneuver = new BattleNext_RttMoveManeuver(ship, () -> new Vector2f(50f, 0f));
		Field field = BattleNext_RttMoveManeuver.class.getDeclaredField("defenseTarget");
		field.setAccessible(true);
		field.set(maneuver, enemy);

		assertEquals(Float.MAX_VALUE, maneuver.getDesiredFacing());
		assertEquals(180f, maneuver.getTargetFacing(), 0.001f);
		assertSame(enemy, maneuver.getTarget());
	}

	@Test
	void rttMoveHeadsToDestinationWhileEnemyIsBehind() {
		ShipAPI ship = rttShip(new Vector2f());
		ShipAPI enemy = rttShip(new Vector2f(-500f, 0f));
		float movementHeading = BattleNext_TaskMoveManeuver.computeManeuverAngle(ship, enemy, enemy,
				enemy.getLocation(), new Vector2f(3000f, 0f), 0f);

		assertEquals(0f, movementHeading, 0.001f);
		float enemyFacing = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), enemy.getLocation());
		assertNotEquals(movementHeading, enemyFacing);
	}

	@Test
	void rttFollowPointTracksFriendlyPositionAndKeepsGap() {
		Vector2f followerLocation = new Vector2f(600f, 0f);
		Vector2f friendlyLocation = new Vector2f();
		ShipAPI follower = rttShip(followerLocation, 100f);
		ShipAPI friendly = rttShip(friendlyLocation, 150f);

		assertEquals(550f, BattleNext_RttShipAI.followPoint(follower, friendly).x, 0.001f);
		friendlyLocation.x = 100f;
		assertEquals(650f, BattleNext_RttShipAI.followPoint(follower, friendly).x, 0.001f);
	}

	private ShipAPI rttShip(Vector2f location) {
		return rttShip(location, 0f);
	}

	private ShipAPI rttShip(Vector2f location, float radius) {
		ShipwideAIFlags flags = new ShipwideAIFlags();
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ShipAPI.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getLocation" -> location;
					case "getCollisionRadius" -> radius;
					case "getAIFlags" -> flags;
					default -> null;
				});
	}
}
