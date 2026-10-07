package data.scripts.plugin;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.report.BattleNext_DamageAttribution;
import data.scripts.report.BattleNext_MemberReport;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

class BattleNext_ScorekeeperReportIntegrationTest {
	private final Map<ShipAPI, Object[]> damageParams = new IdentityHashMap<>();
	private final Map<BattleNext_ScorekeeperPlugin, Map<String, Object>> registrations = new IdentityHashMap<>();
	private final List<Object> listeners = new ArrayList<>();
	private final List<Object> removed = new ArrayList<>();

	@AfterEach
	void releaseScorekeepers() {
		for (var entry : registrations.entrySet()) {
			entry.getKey().cleanup();
			assertTrue(entry.getValue().isEmpty());
		}
		assertTrue(listeners.isEmpty());
		assertEquals(registrations.size(), removed.size());
	}

	@Test
	void projectileBeamFighterAndModuleDamageUseTheirExpectedBuckets() throws Exception {
		FleetMemberAPI playerMember = member("player");
		FleetMemberAPI enemyMember = member("enemy");
		ShipAPI player = ship(playerMember, false, false, null, null);
		ShipAPI enemy = ship(enemyMember, false, false, null, null);
		FighterWingAPI wing = proxy(FighterWingAPI.class, (name, args) -> switch (name) {
			case "getWingId" -> "talon_wing";
			case "getSourceShip" -> player;
			default -> null;
		});
		ShipAPI fighter = ship(null, true, false, wing, null);
		ShipAPI module = ship(null, false, true, null, player);
		WeaponAPI gun = weapon("lightmg", player);
		WeaponAPI fighterGun = weapon("fightergun", fighter);
		WeaponAPI moduleGun = weapon("modulegun", module);
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(
				List.of(playerMember), List.of(enemyMember));
		ApplyDamageResultAPI damage = damage(1f, 2f, 3f, 4f);

		apply(scorekeeper, player, enemy, projectile(player, gun, "lightmg_proj"), damage);
		apply(scorekeeper, player, enemy, beam(player, gun), damage);
		apply(scorekeeper, fighter, enemy, projectile(fighter, fighterGun, "fighter_proj"), damage);
		apply(scorekeeper, module, enemy, projectile(module, moduleGun, "module_proj"), damage);
		apply(scorekeeper, player, fighter, projectile(player, gun, "lightmg_proj"), damage);
		apply(scorekeeper, player, module, projectile(player, gun, "lightmg_proj"), damage);

		BattleNext_MemberReport playerReport = report(scorekeeper, 0);
		BattleNext_MemberReport enemyReport = report(scorekeeper, 1);
		assertEquals(36d, playerReport.getDamageDealt());
		assertEquals(3, playerReport.getDealtBySource().size());
		assertEquals("weapon", playerReport.getDealtBySource().get(0).getKind());
		assertEquals("lightmg", playerReport.getDealtBySource().get(0).getId());
		assertEquals(24d, playerReport.getDealtBySource().get(0).getTotal());
		assertEquals("fighter_wing", playerReport.getDealtBySource().get(1).getKind());
		assertEquals("talon_wing", playerReport.getDealtBySource().get(1).getId());
		assertEquals("modulegun", playerReport.getDealtBySource().get(2).getId());
		assertEquals(24d, enemyReport.getDamageTaken());
		assertEquals(6d, playerReport.getDamageTaken());
		assertEquals(12d, playerReport.getArmorDamageDealt());
		assertEquals(24d, playerReport.getEmpDamageDealt());
	}

	@Test
	void targetModuleAddsRootIncomingWhileFightersRemainSeparateAndFriendlyHitsCount() throws Exception {
		FleetMemberAPI playerMember = member("player");
		ShipAPI player = ship(playerMember, false, false, null, null);
		FighterWingAPI wing = proxy(FighterWingAPI.class, (name, args) -> switch (name) {
			case "getSourceShip" -> player;
			case "getWingId" -> "talon_wing";
			default -> null;
		});
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(List.of(playerMember), List.of());
		ApplyDamageResultAPI damage = damage(1f, 2f, 3f, 4f);
		WeaponAPI gun = weapon("lightmg", player);

		apply(scorekeeper, player, ship(null, true, false, wing, null), projectile(player, gun, "proj"), damage);
		apply(scorekeeper, player, ship(null, false, true, null, player), projectile(player, gun, "proj"), damage);
		apply(scorekeeper, player, player, projectile(player, gun, "proj"), damage);
		apply(scorekeeper, null, player, null, damage);

		BattleNext_MemberReport report = report(scorekeeper, 0);
		assertEquals(18d, report.getDamageDealt());
		assertEquals(18d, report.getDamageTaken());
		assertEquals("damage_type", report.getTakenBySource().get(1).getKind());
	}

	@Test
	void unarmedProjectilesAndBeamsUseDamageTypeWhileDirectShipDamageKeepsHullId() {
		FleetMemberAPI member = member("player");
		ShipAPI ship = ship(member, false, false, null, null);
		ApplyDamageResultAPI damage = damage(1f, 2f, 3f, 4f);

		assertEquals(new BattleNext_DamageAttribution.SourceKey("damage_type", "ENERGY"),
				BattleNext_DamageAttribution.sourceKey(ship, projectile(ship, null, "proj"), damage));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("damage_type", "ENERGY"),
				BattleNext_DamageAttribution.sourceKey(ship, beam(ship, null), damage));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("ship", "wolf_hull"),
				BattleNext_DamageAttribution.sourceKey(ship, null, damage));
		ShipAPI module = ship(null, false, true, null, ship);
		assertEquals(new BattleNext_DamageAttribution.SourceKey("ship", "wolf_hull"),
				BattleNext_DamageAttribution.sourceKey(module, null, damage));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("damage_type", "ENERGY"),
				BattleNext_DamageAttribution.sourceKey(null, projectile(null, null, "proj"), damage));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("damage_type", "ENERGY"),
				BattleNext_DamageAttribution.sourceKey(null, null, damage));
		assertEquals("source_type", BattleNext_DamageAttribution.sourceKey(new Object(), null, damage).kind());
	}

	@Test
	void unarmedProjectileDamageAccumulatesUnderDamageType() throws Exception {
		FleetMemberAPI playerMember = member("player");
		FleetMemberAPI enemyMember = member("enemy");
		ShipAPI player = ship(playerMember, false, false, null, null);
		ShipAPI enemy = ship(enemyMember, false, false, null, null);
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(
				List.of(playerMember), List.of(enemyMember));

		apply(scorekeeper, player, enemy, projectile(player, null, "first_proj"), damage(1f, 2f, 3f, 4f));
		apply(scorekeeper, player, enemy, projectile(player, null, "second_proj"), damage(5f, 6f, 7f, 8f));
		apply(scorekeeper, player, enemy, null, damage(1f, 0f, 0f, 0f));

		BattleNext_MemberReport playerReport = report(scorekeeper, 0);
		BattleNext_MemberReport enemyReport = report(scorekeeper, 1);
		assertEquals(2, playerReport.getDealtBySource().size());
		assertEquals("damage_type", playerReport.getDealtBySource().get(0).getKind());
		assertEquals("ENERGY", playerReport.getDealtBySource().get(0).getId());
		assertEquals(6d, playerReport.getDealtBySource().get(0).getHull());
		assertEquals(8d, playerReport.getDealtBySource().get(0).getArmor());
		assertEquals(10d, playerReport.getDealtBySource().get(0).getShields());
		assertEquals(12d, playerReport.getDealtBySource().get(0).getEmp());
		assertEquals("ship", playerReport.getDealtBySource().get(1).getKind());
		assertEquals(2, enemyReport.getTakenBySource().size());
		assertEquals("ENERGY", enemyReport.getTakenBySource().get(0).getId());
	}

	@Test
	void impactParameterTakesWeaponPriorityAndMissingSourceUsesProjectileShip() throws Exception {
		FleetMemberAPI playerMember = member("player");
		FleetMemberAPI enemyMember = member("enemy");
		ShipAPI player = ship(playerMember, false, false, null, null);
		ShipAPI enemy = ship(enemyMember, false, false, null, null);
		WeaponAPI gun = weapon("lightmg", player);
		WeaponAPI otherGun = weapon("othergun", player);
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(
				List.of(playerMember), List.of(enemyMember));
		ApplyDamageResultAPI damage = damage(1f, 2f, 3f, 4f);

		apply(scorekeeper, otherGun, enemy, projectile(player, gun, "lightmg_proj"), damage);
		apply(scorekeeper, null, enemy, projectile(player, gun, "lightmg_proj"), damage);
		apply(scorekeeper, player, enemy, projectile(player, gun, "lightmg_proj"), damage(0f, 0f, 0f, 0f));

		BattleNext_MemberReport playerReport = report(scorekeeper, 0);
		BattleNext_MemberReport enemyReport = report(scorekeeper, 1);
		assertEquals(12d, playerReport.getDamageDealt());
		assertEquals(1, playerReport.getDealtBySource().size());
		assertEquals("weapon", playerReport.getDealtBySource().get(0).getKind());
		assertEquals("lightmg", playerReport.getDealtBySource().get(0).getId());
		assertEquals(2d, playerReport.getDealtBySource().get(0).getHull());
		assertEquals(4d, playerReport.getDealtBySource().get(0).getArmor());
		assertEquals(6d, playerReport.getDealtBySource().get(0).getShields());
		assertEquals(8d, playerReport.getDealtBySource().get(0).getEmp());
		assertEquals(1, enemyReport.getTakenBySource().size());
		assertEquals("lightmg", enemyReport.getTakenBySource().get(0).getId());
	}

	@Test
	void zeroDamageDoesNotCreateSourceRows() throws Exception {
		FleetMemberAPI playerMember = member("player");
		FleetMemberAPI enemyMember = member("enemy");
		ShipAPI player = ship(playerMember, false, false, null, null);
		ShipAPI enemy = ship(enemyMember, false, false, null, null);
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(
				List.of(playerMember), List.of(enemyMember));

		apply(scorekeeper, player, enemy, projectile(player, weapon("lightmg", player), "proj"),
				damage(0f, 0f, 0f, 0f));

		assertEquals(0, report(scorekeeper, 0).getDealtBySource().size());
		assertEquals(0, report(scorekeeper, 1).getTakenBySource().size());

		scorekeeper = new BattleNext_ScorekeeperPlugin(List.of(playerMember), List.of(enemyMember));
		apply(scorekeeper, player, enemy, projectile(player, weapon("lightmg", player), "proj"),
				damage(0f, 0f, 0f, 4f));

		assertEquals(1, report(scorekeeper, 0).getDealtBySource().size());
		assertEquals(4d, report(scorekeeper, 0).getDealtBySource().get(0).getEmp());
		assertEquals(1, report(scorekeeper, 1).getTakenBySource().size());
	}

	@Test
	void arcSourcesMergeForShipsAndModulesWhileFightersKeepWingAttribution() throws Exception {
		FleetMemberAPI playerMember = member("player");
		FleetMemberAPI enemyMember = member("enemy");
		ShipAPI player = ship(playerMember, false, false, null, null);
		ShipAPI enemy = ship(enemyMember, false, false, null, null);
		ShipAPI module = ship(null, false, true, null, player);
		FighterWingAPI wing = proxy(FighterWingAPI.class, (name, args) -> switch (name) {
			case "getWingId" -> "talon_wing";
			case "getSourceShip" -> player;
			default -> null;
		});
		ShipAPI fighter = ship(null, true, false, wing, null);
		EmpArcEntityAPI arc = proxy(EmpArcEntityAPI.class, (name, args) -> null);
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(
				List.of(playerMember), List.of(enemyMember));

		apply(scorekeeper, player, enemy, arc, damage(1f, 2f, 3f, 4f));
		apply(scorekeeper, player, enemy, DamageDealtModifier.EMP_SHIP_SYSTEM_PARAM,
				damage(5f, 6f, 7f, 8f));
		apply(scorekeeper, module, enemy, arc, damage(2f, 0f, 0f, 1f));
		apply(scorekeeper, null, enemy, arc, damage(0f, 0f, 0f, 2f));
		apply(scorekeeper, fighter, enemy, arc, damage(1f, 0f, 0f, 3f));

		BattleNext_MemberReport playerReport = report(scorekeeper, 0);
		BattleNext_MemberReport enemyReport = report(scorekeeper, 1);
		assertEquals(2, playerReport.getDealtBySource().size());
		assertEquals(new BattleNext_DamageAttribution.SourceKey("arc", "arc"),
				new BattleNext_DamageAttribution.SourceKey(playerReport.getDealtBySource().get(0).getKind(),
						playerReport.getDealtBySource().get(0).getId()));
		assertEquals(8d, playerReport.getDealtBySource().get(0).getHull());
		assertEquals(8d, playerReport.getDealtBySource().get(0).getArmor());
		assertEquals(10d, playerReport.getDealtBySource().get(0).getShields());
		assertEquals(13d, playerReport.getDealtBySource().get(0).getEmp());
		assertEquals("fighter_wing", playerReport.getDealtBySource().get(1).getKind());
		assertEquals("talon_wing", playerReport.getDealtBySource().get(1).getId());
		assertEquals(2, enemyReport.getTakenBySource().size());
		assertEquals("arc", enemyReport.getTakenBySource().get(0).getKind());
		assertEquals(15d, enemyReport.getTakenBySource().get(0).getEmp());
	}

	private void apply(BattleNext_ScorekeeperPlugin scorekeeper, Object source, ShipAPI target,
			Object damageParam, ApplyDamageResultAPI result) {
		if (!registrations.containsKey(scorekeeper)) {
			Map<String, Object> custom = new HashMap<>();
			CombatListenerManagerAPI manager = proxy(CombatListenerManagerAPI.class, (name, args) -> switch (name) {
				case "addListener" -> listeners.add(args[0]);
				case "removeListener" -> listeners.remove(args[0]);
				default -> null;
			});
			scorekeeper.init(proxy(CombatEngineAPI.class, (name, args) -> switch (name) {
				case "getCustomData" -> custom;
				case "getListenerManager" -> manager;
				case "getShips" -> List.copyOf(damageParams.keySet());
				case "getTotalElapsedTime" -> 2f;
				case "removePlugin" -> removed.add(args[0]);
				default -> null;
			}));
			assertSame(scorekeeper, custom.get(scorekeeper.getCustomDataKey()));
			assertTrue(listeners.contains(scorekeeper));
			registrations.put(scorekeeper, custom);
		}
		Object[] param = damageParams.get(target);
		param[0] = damageParam;
		try {
			scorekeeper.reportDamageApplied(source, target, result);
		} finally {
			param[0] = null;
		}
	}

	private BattleNext_MemberReport report(BattleNext_ScorekeeperPlugin scorekeeper, int index) throws Exception {
		scorekeeper.advance(0.1f, List.of());
		assertTrue(scorekeeper.isFrozen());
		var frozen = scorekeeper.getFrozenReport();
		assertEquals("player", frozen.getWinner());
		assertEquals(2000L, frozen.getDurationMillis());
		return (index == 0 ? frozen.getPlayerMembers() : frozen.getEnemyMembers()).get(0);
	}

	private FleetMemberAPI member(String id) {
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (name, args) -> name.equals("getHullId") ? "wolf_hull" : null);
		ShipVariantAPI variant = proxy(ShipVariantAPI.class, (name, args) -> switch (name) {
			case "getHullVariantId" -> "wolf_CS";
			case "getHullSpec" -> hull;
			default -> null;
		});
		return proxy(FleetMemberAPI.class, (name, args) -> switch (name) {
			case "getId" -> id;
			case "getVariant" -> variant;
			case "getShipName" -> id;
			case "getRepairTracker" -> proxy(RepairTrackerAPI.class, (repairMethod, values) -> repairMethod.equals("getCR") ? 0.7f : null);
			default -> null;
		});
	}

	private ShipAPI ship(FleetMemberAPI member, boolean fighter, boolean module, FighterWingAPI wing, ShipAPI parent) {
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (name, args) ->
				"getHullId".equals(name) ? (module ? "module_hull" : "wolf_hull") : null);
		Object[] damageParam = new Object[1];
		ShipAPI ship = proxy(ShipAPI.class, (name, args) -> switch (name) {
			case "getFleetMember" -> member;
			case "getOwner" -> member != null && member.getId().equals("enemy") ? 1 : 0;
			case "isAlive" -> member != null && member.getId().equals("player");
			case "getHullLevel" -> member != null && member.getId().equals("player") ? 0.8f : 0f;
			case "getHullSpec" -> hull;
			case "getParamAboutToApplyDamage" -> damageParam[0];
			case "isFighter" -> fighter;
			case "isStationModule" -> module;
			case "getWing" -> wing;
			case "getParentStation" -> parent;
			default -> null;
		});
		damageParams.put(ship, damageParam);
		return ship;
	}

	private WeaponAPI weapon(String id, ShipAPI ship) {
		WeaponSpecAPI spec = proxy(WeaponSpecAPI.class, (name, args) ->
				"getWeaponId".equals(name) ? id : null);
		return proxy(WeaponAPI.class, (name, args) -> switch (name) {
			case "getSpec" -> spec;
			case "getShip" -> ship;
			default -> null;
		});
	}

	private DamagingProjectileAPI projectile(ShipAPI ship, WeaponAPI weapon, String spec) {
		return proxy(DamagingProjectileAPI.class, (name, args) -> switch (name) {
			case "getSource" -> ship;
			case "getWeapon" -> weapon;
			case "getProjectileSpecId" -> spec;
			default -> null;
		});
	}

	private BeamAPI beam(ShipAPI ship, WeaponAPI weapon) {
		return proxy(BeamAPI.class, (name, args) -> switch (name) {
			case "getSource" -> ship;
			case "getWeapon" -> weapon;
			default -> null;
		});
	}

	private ApplyDamageResultAPI damage(float hull, float armor, float shields, float emp) {
		return proxy(ApplyDamageResultAPI.class, (name, args) -> switch (name) {
			case "getDamageToHull" -> hull;
			case "getTotalDamageToArmor" -> armor;
			case "getDamageToShields" -> shields;
			case "getEmpDamage" -> emp;
			case "getType" -> DamageType.ENERGY;
			default -> null;
		});
	}

	private interface Call {
		Object invoke(String name, Object[] args);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> {
					if ("hashCode".equals(method.getName())) return System.identityHashCode(instance);
					if ("equals".equals(method.getName())) return instance == args[0];
					Object value = call.invoke(method.getName(), args);
					if (value == null && method.getReturnType() == boolean.class) return false;
					return value;
				}));
	}
}
