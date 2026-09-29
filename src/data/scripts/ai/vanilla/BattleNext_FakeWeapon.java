package data.scripts.ai.vanilla;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.AmmoTrackerAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileRenderDataAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.MuzzleFlashSpec;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

/**
 * Minimal port of engine class com.fs.starfarer.combat.ai.FakeWeapon.
 * A WeaponAPI view over a ship system so FiringSolutionEval.computeIncomingWeaponDamage
 * can treat an active system like a firing weapon when predicting incoming damage.
 * Deviation from vanilla: the engine class implements the engine-internal Weapon and
 * WeaponPivotProvider interfaces. Only the members FiringSolutionEval actually reads
 * carry real behavior here; rendering, pivot, beam and health members are inert stubs
 * required by the WeaponAPI interface.
 */
public class BattleNext_FakeWeapon implements WeaponAPI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final ShipSystemSpecAPI systemSpec;
	private float aimFacing;
	private float aimTurnRate = 0.0F;
	private final FakeSlot slot;
	private final FakeDamage damage;
	private final FakeDerivedStats derivedStats;
	private final FakeAmmoTracker ammoTracker;
	private Object custom;

	public BattleNext_FakeWeapon(ShipAPI ship, ShipSystemAPI system) {
		this.ship = ship;
		this.system = system;
		this.systemSpec = system.getSpecAPI();
		this.aimFacing = this.systemSpec.getThreatAngle();
		this.slot = new FakeSlot(this.systemSpec.getThreatAngle(), this.systemSpec.getThreatArc());
		this.damage = new FakeDamage(this.systemSpec.getDamage(), this.systemSpec.getDamageType());
		this.damage.setFluxComponent(this.systemSpec.getEmpDamage());
		this.derivedStats = new FakeDerivedStats();
		this.derivedStats.dps = this.damage.getDamage() * 1.0F;
		this.derivedStats.sustainedDps = this.damage.getDamage() * 1.0F;
		this.derivedStats.empPerSecond = this.damage.getFluxComponent() * 1.0F;
		this.derivedStats.burstDamage = this.damage.getDamage() * 1.0F;
		this.derivedStats.fluxPerDam = 1.0F;
		this.derivedStats.rof = 2.0F;
		this.derivedStats.damageOver30Sec = this.derivedStats.dps * 30.0F;
		this.derivedStats.burstFireDuration = -1.0F;
		this.derivedStats.damagePerShot = this.damage.getDamage() * 0.5F;
		this.derivedStats.empPerShot = this.damage.getFluxComponent() * 0.5F;
		this.ammoTracker = new FakeAmmoTracker(this.system);
	}

	@Override
	public float getCurrAngle() {
		return this.aimFacing;
	}

	@Override
	public float getTurnRate() {
		return this.aimTurnRate;
	}

	@Override
	public void setTurnRateOverride(Float turnRateOverride) {
		if (turnRateOverride == null) {
			this.aimTurnRate = 0.0F;
		} else {
			this.aimTurnRate = turnRateOverride;
		}
	}

	@Override
	public void setFacing(float facing) {
		this.aimFacing = facing;
	}

	@Override
	public float distanceFromArc(Vector2f target) {
		Vector2f origin = this.slot.computePosition(this.ship);
		float angleToTarget = (float) Math.toDegrees(Math.atan2(target.y - origin.y, target.x - origin.x));
		float diff = angleToTarget - (this.ship.getFacing() + this.slot.getAngle());
		while (diff > 180.0F) {
			diff -= 360.0F;
		}
		while (diff < -180.0F) {
			diff += 360.0F;
		}
		float halfArc = this.slot.getArc() * 0.5F;
		if (diff < 0.0F) {
			diff = -diff;
		}
		if (diff <= halfArc) {
			return 0.0F;
		}
		return diff - halfArc;
	}

	@Override
	public float getCurrSpread() {
		return 0.0F;
	}

	@Override
	public int getAmmo() {
		return this.system.getAmmo();
	}

	@Override
	public AmmoTrackerAPI getAmmoTracker() {
		return this.ammoTracker;
	}

	@Override
	public float getChargeLevel() {
		return 0.0F;
	}

	@Override
	public float getCooldown() {
		return this.system.getCooldown();
	}

	@Override
	public float getCooldownRemaining() {
		return this.system.getCooldownRemaining();
	}

	@Override
	public DamageAPI getDamage() {
		return this.damage;
	}

	@Override
	public DamageType getDamageType() {
		return this.systemSpec.getDamageType();
	}

	@Override
	public float getDisplayArcRadius() {
		return 0.0F;
	}

	@Override
	public String getDisplayName() {
		return null;
	}

	@Override
	public float getFluxCostToFire() {
		return this.system.getFluxPerUse();
	}

	@Override
	public String getId() {
		return "fake";
	}

	@Override
	public int getMaxAmmo() {
		return this.system.getMaxAmmo();
	}

	@Override
	public CollisionClass getProjectileCollisionClass() {
		return CollisionClass.PROJECTILE_FF;
	}

	@Override
	public float getProjectileSpeed() {
		return 1000.0F;
	}

	@Override
	public float getRange() {
		return this.systemSpec.getThreatRange(this.ship.getMutableStats());
	}

	@Override
	public WeaponSize getSize() {
		return WeaponSize.LARGE;
	}

	@Override
	public WeaponSpecAPI getSpec() {
		return null;
	}

	@Override
	public WeaponType getType() {
		return WeaponType.SYSTEM;
	}

	@Override
	public boolean hasAIHint(AIHints var1) {
		return false;
	}

	@Override
	public boolean isAlwaysFire() {
		return false;
	}

	@Override
	public boolean isBeam() {
		return false;
	}

	@Override
	public boolean isBurstBeam() {
		return false;
	}

	@Override
	public boolean isDisabled() {
		return false;
	}

	@Override
	public boolean isFiring() {
		return this.system.isActive();
	}

	@Override
	public boolean isInBurst() {
		return false;
	}

	@Override
	public boolean isPulse() {
		return false;
	}

	@Override
	public boolean requiresFullCharge() {
		return false;
	}

	@Override
	public void resetAmmo() {
		this.ammoTracker.resetAmmo();
	}

	@Override
	public void setGlowAmount(float var1, Color var2) {
	}

	@Override
	public void setPD(boolean var1) {
	}

	@Override
	public void setRemainingCooldownTo(float var1) {
	}

	@Override
	public boolean usesAmmo() {
		return this.ammoTracker.usesAmmo();
	}

	@Override
	public boolean usesEnergy() {
		return this.system.getFluxPerSecond() > 0.0F || this.system.getFluxPerUse() > 0.0F;
	}

	@Override
	public ShipAPI getShip() {
		return this.ship;
	}

	@Override
	public WeaponSlotAPI getSlot() {
		return this.slot;
	}

	@Override
	public void beginSelectionFlash() {
	}

	@Override
	public AnimationAPI getAnimation() {
		return null;
	}

	public void disable() {
	}

	public void disable(boolean var1) {
	}

	public float getArc() {
		return this.slot.getArc();
	}

	public float getArcFacing() {
		return this.slot.getAngle();
	}

	public SpriteAPI getBarrelSpriteAPI() {
		return null;
	}

	public float getCurrHealth() {
		return 0.0F;
	}

	public DerivedWeaponStatsAPI getDerivedStats() {
		return this.derivedStats;
	}

	public float getDisabledDuration() {
		return 0.0F;
	}

	public EveryFrameWeaponEffectPlugin getEffectPlugin() {
		return null;
	}

	public Vector2f getLocation() {
		return this.slot.computePosition(this.ship);
	}

	public float getMaxHealth() {
		return 0.0F;
	}

	public List<MissileRenderDataAPI> getMissileRenderData() {
		return null;
	}

	public float getProjectileFadeRange() {
		return 0.0F;
	}

	public SpriteAPI getSprite() {
		return null;
	}

	public SpriteAPI getUnderSpriteAPI() {
		return null;
	}

	public boolean isPermanentlyDisabled() {
		return false;
	}

	public boolean isRenderBarrelBelow() {
		return false;
	}

	public void renderBarrel(SpriteAPI var1, Vector2f var2, float var3) {
	}

	public void repair() {
	}

	public void setAmmo(int var1) {
		this.system.setAmmo(var1);
	}

	public void setCurrAngle(float var1) {
	}

	public void setMaxAmmo(int var1) {
		this.ammoTracker.setMaxAmmo(var1);
	}

	public boolean isDecorative() {
		return this.getType() == WeaponType.DECORATIVE;
	}

	public void ensureClonedSpec() {
	}

	public float getAmmoPerSecond() {
		return this.system.getAmmoPerSecond();
	}

	public void setPDAlso(boolean var1) {
	}

	public void setCurrHealth(float var1) {
	}

	public MuzzleFlashSpec getMuzzleFlashSpec() {
		return null;
	}

	public List<BeamAPI> getBeams() {
		return new ArrayList<>();
	}

	public Vector2f getFirePoint(int var1) {
		return new Vector2f(this.getLocation());
	}

	public SpriteAPI getGlowSpriteAPI() {
		return null;
	}

	public void setRefireDelay(float var1) {
	}

	public float getRefireDelay() {
		return 0.0F;
	}

	public float getBurstFireTimeRemaining() {
		return 0.0F;
	}

	public Vector2f getRenderOffsetForDecorativeBeamWeaponsOnly() {
		return null;
	}

	public void setRenderOffsetForDecorativeBeamWeaponsOnly(Vector2f var1) {
	}

	public void updateBeamFromPoints() {
	}

	public boolean isKeepBeamTargetWhileChargingDown() {
		return false;
	}

	public void setKeepBeamTargetWhileChargingDown(boolean var1) {
	}

	public void setScaleBeamGlowBasedOnDamageEffectiveness(boolean var1) {
	}

	public void setForceFireOneFrame(boolean var1) {
	}

	public void setForceNoFireOneFrame(boolean var1) {
	}

	public void setSuspendAutomaticTurning(boolean var1) {
	}

	public Object getCustom() {
		return this.custom;
	}

	public void setCustom(Object var1) {
		this.custom = var1;
	}

	public void forceShowBeamGlow() {
	}

	public WeaponSpecAPI getOriginalSpec() {
		return this.getSpec();
	}

	public void setWeaponGlowWidthMult(float var1) {
	}

	public void setWeaponGlowHeightMult(float var1) {
	}

	public void stopFiring() {
	}

	public boolean isForceDisabled() {
		return false;
	}

	public void setForceDisabled(boolean var1) {
	}

	public boolean isForceNoFireOneFrame() {
		return false;
	}

	/**
	 * Port of the engine WeaponSlot built by FakeWeapon's constructor: id "fake",
	 * SYSTEM/SMALL/HARDPOINT at the ship origin with the system's threat angle and arc.
	 */
	private static class FakeSlot implements WeaponSlotAPI {
		private final float angle;
		private final float arc;

		private FakeSlot(float angle, float arc) {
			this.angle = angle;
			this.arc = arc;
		}

		@Override
		public boolean isHardpoint() {
			return true;
		}

		@Override
		public boolean isTurret() {
			return false;
		}

		@Override
		public boolean isHidden() {
			return false;
		}

		@Override
		public boolean isSystemSlot() {
			return false;
		}

		@Override
		public boolean isBuiltIn() {
			return false;
		}

		@Override
		public boolean isDecorative() {
			return false;
		}

		@Override
		public String getId() {
			return "fake";
		}

		@Override
		public WeaponAPI.WeaponType getWeaponType() {
			return WeaponAPI.WeaponType.SYSTEM;
		}

		@Override
		public WeaponAPI.WeaponSize getSlotSize() {
			return WeaponAPI.WeaponSize.SMALL;
		}

		@Override
		public float getArc() {
			return this.arc;
		}

		@Override
		public void setArc(float var1) {
		}

		@Override
		public float getAngle() {
			return this.angle;
		}

		@Override
		public Vector2f computePosition(CombatEntityAPI ship) {
			return new Vector2f(ship.getLocation());
		}

		@Override
		public boolean isStationModule() {
			return false;
		}

		@Override
		public boolean weaponFits(WeaponSpecAPI var1) {
			return false;
		}

		@Override
		public void setAngle(float var1) {
		}

		@Override
		public Vector2f getLocation() {
			return new Vector2f(0.0F, 0.0F);
		}

		@Override
		public float getRenderOrderMod() {
			return 0.0F;
		}

		@Override
		public void setRenderOrderMod(float var1) {
		}

		@Override
		public float computeMidArcAngle(ShipAPI ship) {
			return ship.getFacing() + this.angle;
		}

		@Override
		public List<Vector2f> getLaunchPointOffsets() {
			return new ArrayList<>();
		}

		@Override
		public boolean isWeaponSlot() {
			return true;
		}
	}

	/**
	 * Port of the engine Damage created by FakeWeapon's constructor.
	 */
	private static class FakeDamage implements DamageAPI {
		private float damage;
		private DamageType type;
		private float fluxComponent;
		private float multiplier = 1.0F;
		private boolean softFlux = false;
		private float dpsDuration = 0.0F;
		private boolean missile = false;
		private MutableShipStatsAPI stats = null;
		private MutableStat modifier = null;

		private FakeDamage(float damage, DamageType type) {
			this.damage = damage;
			this.type = type;
		}

		@Override
		public float computeFluxDealt(float amount) {
			return this.fluxComponent;
		}

		@Override
		public float computeDamageDealt(float amount) {
			return this.damage;
		}

		@Override
		public boolean isMissile() {
			return this.missile;
		}

		@Override
		public void setMissile(boolean isMissile) {
			this.missile = isMissile;
		}

		@Override
		public void setStats(MutableShipStatsAPI stats) {
			this.stats = stats;
		}

		@Override
		public MutableShipStatsAPI getStats() {
			return this.stats;
		}

		@Override
		public float getDamage() {
			return this.damage;
		}

		@Override
		public void setDamage(float amount) {
			this.damage = amount;
		}

		@Override
		public boolean isDps() {
			return false;
		}

		@Override
		public float getMultiplier() {
			return this.multiplier;
		}

		@Override
		public void setMultiplier(float multiplier) {
			this.multiplier = multiplier;
		}

		@Override
		public DamageType getType() {
			return this.type;
		}

		@Override
		public void setType(DamageType type) {
			this.type = type;
		}

		@Override
		public float getFluxComponent() {
			return this.fluxComponent;
		}

		@Override
		public void setFluxComponent(float fluxComponent) {
			this.fluxComponent = fluxComponent;
		}

		@Override
		public boolean isSoftFlux() {
			return this.softFlux;
		}

		@Override
		public void setSoftFlux(boolean isSoftFlux) {
			this.softFlux = isSoftFlux;
		}

		@Override
		public float getDpsDuration() {
			return this.dpsDuration;
		}

		@Override
		public void setDpsDuration(float dpsDuration) {
			this.dpsDuration = dpsDuration;
		}

		@Override
		public float getBaseDamage() {
			return this.damage;
		}

		@Override
		public MutableStat getModifier() {
			if (this.modifier == null) {
				this.modifier = new MutableStat(1.0F);
			}
			return this.modifier;
		}

		@Override
		public boolean isForceHardFlux() {
			return false;
		}

		@Override
		public void setForceHardFlux(boolean forceHardFlux) {
		}

		@Override
		public DamageAPI clone() {
			FakeDamage copy = new FakeDamage(this.damage, this.type);
			copy.fluxComponent = this.fluxComponent;
			return copy;
		}
	}

	/**
	 * Port of the engine WeaponSpreadsheetLoader.DerivedWeaponStats filled by FakeWeapon's constructor.
	 */
	private static class FakeDerivedStats implements DerivedWeaponStatsAPI {
		private float dps = 0.0F;
		private float sustainedDps = 0.0F;
		private float empPerSecond = 0.0F;
		private float burstDamage = 0.0F;
		private float fluxPerDam = 0.0F;
		private float rof = 0.0F;
		private float damageOver30Sec = 0.0F;
		private float burstFireDuration = 0.0F;
		private float damagePerShot = 0.0F;
		private float empPerShot = 0.0F;

		@Override
		public float getBurstFireDuration() {
			return this.burstFireDuration;
		}

		@Override
		public float getSustainedDps() {
			return this.sustainedDps;
		}

		@Override
		public float getEmpPerSecond() {
			return this.empPerSecond;
		}

		@Override
		public float getDamageOver30Sec() {
			return this.damageOver30Sec;
		}

		@Override
		public float getDps() {
			return this.dps;
		}

		@Override
		public float getBurstDamage() {
			return this.burstDamage;
		}

		@Override
		public float getFluxPerDam() {
			return this.fluxPerDam;
		}

		@Override
		public float getRoF() {
			return this.rof;
		}

		@Override
		public float getFluxPerSecond() {
			return 0.0F;
		}

		@Override
		public float getSustainedFluxPerSecond() {
			return 0.0F;
		}

		@Override
		public float getDamagePerShot() {
			return this.damagePerShot;
		}

		@Override
		public float getEmpPerShot() {
			return this.empPerShot;
		}
	}

	/**
	 * Backing for the ammo-tracker reads FiringSolutionEval performs on weapons.
	 * approximation: ShipSystemAPI.getAmmoTracker() does not exist; the runtime
	 * ammo sentinel and exposed regeneration rate provide the consumed values.
	 */
	private static class FakeAmmoTracker implements AmmoTrackerAPI {
		private final ShipSystemAPI system;

		private FakeAmmoTracker(ShipSystemAPI system) {
			this.system = system;
		}

		@Override
		public void setAmmoPerSecond(float ammoPerSecond) {
		}

		@Override
		public float getReloadProgress() {
			return 0.0F;
		}

		@Override
		public void setAmmo(int ammo) {
			this.system.setAmmo(ammo);
		}

		@Override
		public boolean usesAmmo() {
			return this.system.getAmmo() != Integer.MAX_VALUE;
		}

		@Override
		public void addOneAmmo() {
			this.system.setAmmo(this.system.getAmmo() + 1);
		}

		@Override
		public boolean deductOneAmmo() {
			if (this.system.getAmmo() <= 0) {
				return false;
			}
			this.system.setAmmo(this.system.getAmmo() - 1);
			return true;
		}

		@Override
		public int getAmmo() {
			return this.system.getAmmo();
		}

		@Override
		public float getAmmoPerSecond() {
			return this.system.getAmmoPerSecond();
		}

		@Override
		public int getMaxAmmo() {
			return this.system.getMaxAmmo();
		}

		@Override
		public void resetAmmo() {
		}

		@Override
		public void setMaxAmmo(int maxAmmo) {
		}

		@Override
		public float getReloadSize() {
			return 0.0F;
		}

		@Override
		public void setReloadSize(float reloadSize) {
		}

		@Override
		public void setReloadProgress(float progress) {
		}
	}
}
