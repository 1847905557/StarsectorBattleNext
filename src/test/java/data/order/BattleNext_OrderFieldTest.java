package data.order;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.order.BattleNext_OrderField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_OrderFieldTest {

	@Test
	void mergesSameTypeAndRequiresHigherPriorityForDifferentType() {

		Fixture fixture = new Fixture();
		BattleNext_OrderField field = fixture.field();
		ShipAPI first = fixture.ship();
		ShipAPI second = fixture.ship();
		ShipAPI third = fixture.ship();
		AssignmentTargetAPI target = fixture.objective();
		assertTrue(field.apply(first, CombatAssignmentType.ENGAGE, target, 0f));
		assertTrue(field.apply(second, CombatAssignmentType.ENGAGE, target, 0f));
		assertSame(fixture.assigned.get(first), fixture.assigned.get(second));
		assertEquals(1, fixture.assignments.size());
		assertFalse(field.apply(third, CombatAssignmentType.HARASS, target, 0f));
		assertTrue(field.apply(third, CombatAssignmentType.HARASS, target, 1f));
		assertNull(fixture.assigned.get(first));
		assertNull(fixture.assigned.get(second));
		assertEquals(CombatAssignmentType.HARASS, fixture.assigned.get(third).getType());
		field.registerShip(third);
		field.pruneAssignments();
		assertEquals(1, fixture.assignments.size());
	}

	@Test
	void protectsManualAssignmentsAndReleasesDeadShipsAssignments() {
		Fixture fixture = new Fixture();
		BattleNext_OrderField field = fixture.field();
		ShipAPI automated = fixture.ship();
		ShipAPI manual = fixture.ship();
		AssignmentTargetAPI target = fixture.objective();
		AssignmentTargetAPI manualTarget = fixture.objective();
		assertTrue(field.apply(automated, CombatAssignmentType.ENGAGE, target, 0f));
		AssignmentInfo playerOrder = fixture.assignment(CombatAssignmentType.DEFEND, manualTarget);
		fixture.assigned.put(manual, playerOrder);
		assertTrue(field.hasPlayerForcedAssignment(manual));
		assertFalse(field.apply(manual, CombatAssignmentType.ENGAGE, target, 10f));
		assertFalse(field.apply(automated, CombatAssignmentType.ENGAGE, manualTarget, 10f));
		field.pruneAssignments();
		assertNull(field.getOneAutomationInfoOnTarget(target));
		assertEquals(List.of(playerOrder), fixture.assignments);
	}

	private static class Fixture {

		private final Map<String, Object> custom = new HashMap<>();
		private final List<AssignmentInfo> assignments = new ArrayList<>();
		private final Map<ShipAPI, AssignmentInfo> assigned = new IdentityHashMap<>();
		private final Map<ShipAPI, DeployedFleetMemberAPI> deployed = new IdentityHashMap<>();
		private final CombatTaskManagerAPI tasks = proxy(CombatTaskManagerAPI.class, (method, args) -> switch (method) {
			case "getAssignmentFor" -> assigned.get(args[0]);
			case "getAllAssignments" -> List.copyOf(assignments);
			case "createAssignment" -> assignment((CombatAssignmentType)args[0], (AssignmentTargetAPI)args[1]);
			case "giveAssignment" -> {
				assigned.put(((DeployedFleetMemberAPI)args[0]).getShip(), (AssignmentInfo)args[1]);
				yield null;
			}
			case "orderSearchAndDestroy" -> {
				assigned.remove(((DeployedFleetMemberAPI)args[0]).getShip());
				yield null;
			}
			case "removeAssignment" -> {
				assignments.remove(args[0]);
				yield null;
			}
			default -> null;
		});
		private final CombatFleetManagerAPI manager = proxy(CombatFleetManagerAPI.class, (method, args) -> switch (method) {
			case "getTaskManager" -> tasks;
			case "getDeployedFleetMember" -> deployed.computeIfAbsent((ShipAPI)args[0], ship ->
					proxy(DeployedFleetMemberAPI.class, (name, ignored) -> "getShip".equals(name) ? ship : null));
			default -> null;
		});
		private final CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getCustomData" -> custom;
			case "getFleetManager" -> manager;
			default -> null;
		});

		private BattleNext_OrderField field() {
			return BattleNext_OrderField.getInstance(engine, 0, false);
		}

		private ShipAPI ship() {
			return proxy(ShipAPI.class, (method, args) -> "getOwner".equals(method) ? 0 : null);
		}

		private AssignmentTargetAPI objective() {
			return proxy(BattleObjectiveAPI.class, (method, args) -> null);
		}

		private AssignmentInfo assignment(CombatAssignmentType type, AssignmentTargetAPI target) {
			AssignmentInfo info = new AssignmentInfo() {
				public CombatAssignmentType getType() {
					return type;
				}

				public AssignmentTargetAPI getTarget() {
					return target;
				}

				public List<DeployedFleetMemberAPI> getAssignedMembers() {
					return List.of();
				}
			};
			assignments.add(info);
			return info;
		}
	}

	private interface MethodCall {
		Object invoke(String method, Object[] args);
	}

	private static <T> T proxy(Class<T> type, MethodCall call) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
			if ("equals".equals(method.getName())) return instance == args[0];
			if ("hashCode".equals(method.getName())) return System.identityHashCode(instance);
			return call.invoke(method.getName(), args);
		}));
	}
}
