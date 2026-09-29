package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.DroneLauncherShipSystemAPI;
import com.fs.starfarer.api.combat.DroneLauncherShipSystemAPI.DroneOrders;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.DroneLauncherSystemAI.
 */
public class BattleNext_DroneLauncherSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final BattleNext_ThreatEvalAI threatEval;
	private final boolean obeyMouseOrders;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private boolean pendingModeChange;

	public BattleNext_DroneLauncherSystemAI(ShipAPI ship, BattleNext_ThreatEvalAI threatEval, boolean obeyMouseOrders) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.threatEval = threatEval;
		this.obeyMouseOrders = obeyMouseOrders;
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		if (ship.isShipSystemDisabled()) return;
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (!(system instanceof DroneLauncherShipSystemAPI launcher)) return;
		if (pendingModeChange && system.isActive()) return;
		if (pendingModeChange) pendingModeChange = false;
		DroneOrders desired;
		if (system.getAmmoPerSecond() <= 0.0F && !system.getSpecAPI().isAllowFreeRoam()) {
			desired = DroneOrders.DEPLOY;
		} else {
			float totalHull = 0.0F;
			int count = 0;
			for (ShipAPI drone : ship.getDeployedDrones()) {
				totalHull += drone.getHullLevel();
				count++;
			}
			boolean damaged = count > 0 && totalHull / count < 0.5F;
			boolean healthy = totalHull * 1.05F >= count;
			if ((missileDangerDir == null || !obeyMouseOrders) && threatEval.isThreatHigh() && damaged) {
				desired = DroneOrders.RECALL;
			} else if (system.getAmmo() + count < system.getSpecAPI().getMaxDrones() - 2 || threatEval.isThreatHigh()) {
				desired = DroneOrders.DEPLOY;
			} else if (system.getSpecAPI().isAllowFreeRoam()) {
				desired = DroneOrders.ATTACK;
			} else if (healthy) {
				desired = DroneOrders.DEPLOY;
			} else if (!threatEval.isThreatLow()) {
				desired = DroneOrders.RECALL;
			} else {
				return;
			}
		}
		// approximation: DroneLauncherSystem.setDeploy/setAttack/setRecall are
		// internal; USE_SYSTEM advances the same runtime order cycle one step.
		if (launcher.getDroneOrders() != desired) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			pendingModeChange = true;
		}
	}
}
