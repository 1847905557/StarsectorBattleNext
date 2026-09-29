package data.scripts.ai.vanilla.system.drones;

import com.fs.starfarer.api.combat.DroneLauncherShipSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_OrderResponseData;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.drones.DroneAI.
 */
public class BattleNext_DroneAI extends BattleNext_BasicShipAI {
	private final ShipAPI mothership;
	private final ShipSystemAPI launcherSystem;
	private final Vector2f desiredLocation = new Vector2f();
	private boolean engaged;

	public BattleNext_DroneAI(ShipAPI drone, ShipAPI mothership, DroneLauncherShipSystemAPI launcher) {
		super(drone);
		this.mothership = mothership;
		this.launcherSystem = mothership.getSystem();
		this.desiredLocation.set(drone.getLocation());
	}

	@Override
	public void advance(float amount) {
		// approximation: the engine DroneAI has a dedicated movement pipeline and
		// directly writes position, velocity, and death effects. The public AI
		// delegates those controls to the ported ship AI and drone maneuver.
		if (mothership.isHulk() || !data.scripts.ai.vanilla.BattleNext_ShipAIHelper.getEngine().isInPlay(mothership)) {
			getShip().setHitpoints(0.0F);
			return;
		}
		super.advance(amount);
	}

	@Override
	protected BattleNext_OrderResponseData getRttOrderResponse() {
		BattleNext_OrderResponseData response = new BattleNext_OrderResponseData(null);
		response.maneuver = new BattleNext_DroneCombatManeuver(getShip(), mothership, getFlockingAI(), launcherSystem, this);
		response.duration = 3600.0F;
		return response;
	}

	public Vector2f getDesiredLocation() {
		return desiredLocation;
	}

	public ShipAPI getMothership() {
		return mothership;
	}

	public void setEngaged(boolean engaged) {
		this.engaged = engaged;
	}

	@Override
	public boolean isEngaged() {
		return engaged;
	}
}
