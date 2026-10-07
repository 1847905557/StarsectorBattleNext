package data.scripts.ai.rtt;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.Global;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver;
import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

class BattleNext_RttMoveManeuverTest extends BattleNext_TestGameFixture {

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
	void rttHoldFacesEnemyIndependentlyOfDestination() {
		List<ShipCommand> commands = new ArrayList<>();
		ShipwideAIFlags flags = new ShipwideAIFlags();
		ShipAPI ship = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "getLocation" -> new Vector2f();
			case "getHullSize" -> ShipAPI.HullSize.FRIGATE;
			case "getCustomData" -> Map.of();
			case "getAIFlags" -> flags;
			case "giveCommand" -> {
				commands.add((ShipCommand) args[0]);
				yield null;
			}
			default -> null;
		});
		ShipAPI enemy = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "getLocation" -> new Vector2f(-100f, 0f);
			case "getOwner" -> 1;
			case "getHullSize" -> ShipAPI.HullSize.FRIGATE;
			case "isTargetable" -> true;
			default -> null;
		});
		BattleNext_RttMoveManeuver maneuver = new BattleNext_RttMoveManeuver(ship, () -> new Vector2f(50f, 0f));
		CombatEngineAPI previous = Global.getCombatEngine();
		try {
			Global.setCombatEngine(proxy(CombatEngineAPI.class, (method, args) -> method.equals("getShips") ? List.of(ship, enemy) : null));
			maneuver.init();
			assertEquals(List.of(ShipCommand.DECELERATE), commands);
			assertEquals(Float.MAX_VALUE, maneuver.getDesiredFacing());
			assertEquals(180f, maneuver.getTargetFacing(), 0.001f);
			assertSame(enemy, maneuver.getTarget());
			assertSame(enemy, flags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET));
		} finally {
			Global.setCombatEngine(previous);
		}
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
