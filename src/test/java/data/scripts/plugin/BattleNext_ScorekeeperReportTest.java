package data.scripts.plugin;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.report.BattleNext_DamageAttribution;
import data.scripts.report.BattleNext_MemberReport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleNext_ScorekeeperReportTest {

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

		scorekeeper.reportDamageApplied(projectile(player, gun, "lightmg_proj"), enemy, damage);
		scorekeeper.reportDamageApplied(beam(player, gun), enemy, damage);
		scorekeeper.reportDamageApplied(projectile(fighter, fighterGun, "fighter_proj"), enemy, damage);
		scorekeeper.reportDamageApplied(projectile(module, moduleGun, "module_proj"), enemy, damage);
		scorekeeper.reportDamageApplied(projectile(player, gun, "lightmg_proj"), fighter, damage);
		scorekeeper.reportDamageApplied(projectile(player, gun, "lightmg_proj"), module, damage);

		BattleNext_MemberReport playerReport = report(scorekeeper, 0);
		BattleNext_MemberReport enemyReport = report(scorekeeper, 1);
		assertEquals(36d, playerReport.getDamageDealt());
		assertEquals(3, playerReport.getDealtBySource().size());
		assertEquals(4, playerReport.getDealtBySource().get(0).getTotal() / 6d);
		assertEquals("fighter_wing", playerReport.getDealtBySource().get(1).getKind());
		assertEquals("talon_wing", playerReport.getDealtBySource().get(1).getId());
		assertEquals("modulegun", playerReport.getDealtBySource().get(2).getId());
		assertEquals(24d, enemyReport.getDamageTaken());
		assertEquals(0d, playerReport.getDamageTaken());
		assertEquals(12d, playerReport.getArmorDamageDealt());
		assertEquals(24d, playerReport.getEmpDamageDealt());
	}

	@Test
	void targetFighterOrModuleDoesNotAddCarrierIncomingAndFriendlyHitsStillCount() throws Exception {
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

		scorekeeper.reportDamageApplied(projectile(player, gun, "proj"),
				ship(null, true, false, wing, null), damage);
		scorekeeper.reportDamageApplied(projectile(player, gun, "proj"),
				ship(null, false, true, null, player), damage);
		scorekeeper.reportDamageApplied(projectile(player, gun, "proj"), player, damage);
		scorekeeper.reportDamageApplied(null, player, damage);

		BattleNext_MemberReport report = report(scorekeeper, 0);
		assertEquals(18d, report.getDamageDealt());
		assertEquals(12d, report.getDamageTaken());
		assertEquals("damage_type", report.getTakenBySource().get(1).getKind());
	}

	@Test
	void nonWeaponSourcesAndDamageTypeFallbackKeepStableKeys() {
		FleetMemberAPI member = member("player");
		ShipAPI ship = ship(member, false, false, null, null);
		ApplyDamageResultAPI damage = damage(1f, 2f, 3f, 4f);

		assertEquals(new BattleNext_DamageAttribution.SourceKey("ship", "wolf_hull"),
				BattleNext_DamageAttribution.sourceKey(projectile(ship, null, "proj"), damage));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("ship", "wolf_hull"),
				BattleNext_DamageAttribution.sourceKey(ship, damage));
		ShipAPI module = ship(null, false, true, null, ship);
		assertEquals(new BattleNext_DamageAttribution.SourceKey("ship", "wolf_hull"),
				BattleNext_DamageAttribution.sourceKey(module, damage));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("projectile_spec", "proj"),
				BattleNext_DamageAttribution.sourceKey(projectile(null, null, "proj"), damage));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("damage_type", "ENERGY"),
				BattleNext_DamageAttribution.sourceKey(null, damage));
		assertEquals("source_type", BattleNext_DamageAttribution.sourceKey(new Object(), damage).kind());
	}

	private BattleNext_MemberReport report(BattleNext_ScorekeeperPlugin scorekeeper, int index) throws Exception {
		Field trackedField = BattleNext_ScorekeeperPlugin.class.getDeclaredField("trackedMembers");
		trackedField.setAccessible(true);
		Object tracked = ((List<?>) trackedField.get(scorekeeper)).get(index);
		Field reportField = tracked.getClass().getDeclaredField("report");
		reportField.setAccessible(true);
		return (BattleNext_MemberReport) reportField.get(tracked);
	}

	private FleetMemberAPI member(String id) {
		ShipVariantAPI variant = proxy(ShipVariantAPI.class, (name, args) ->
				"getHullVariantId".equals(name) ? "wolf_CS" : null);
		return proxy(FleetMemberAPI.class, (name, args) -> switch (name) {
			case "getId" -> id;
			case "getVariant" -> variant;
			case "getShipName" -> id;
			default -> null;
		});
	}

	private ShipAPI ship(FleetMemberAPI member, boolean fighter, boolean module, FighterWingAPI wing, ShipAPI parent) {
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (name, args) ->
				"getHullId".equals(name) ? (module ? "module_hull" : "wolf_hull") : null);
		return proxy(ShipAPI.class, (name, args) -> switch (name) {
			case "getFleetMember" -> member;
			case "getHullSpec" -> hull;
			case "isFighter" -> fighter;
			case "isStationModule" -> module;
			case "getWing" -> wing;
			case "getParentStation" -> parent;
			default -> null;
		});
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
