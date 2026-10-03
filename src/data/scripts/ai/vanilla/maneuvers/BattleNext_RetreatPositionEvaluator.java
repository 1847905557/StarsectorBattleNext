package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.RetreatPositionEvaluator.
 * Candidate filter for counting hostile ships in the retreat direction.
 * The engine class extends the engine evaluator base OO0O$Oo, whose decompiled
 * source was lost to a file-name collision in the deobfuscation export; the
 * base behavior is reconstructed here from its construction site and its use
 * in the engine enemy counting.
 */
public class BattleNext_RetreatPositionEvaluator implements BattleNext_ShipAIHelper.TargetValidityFilter {
	private final HullSize minHullSize;
	private final boolean checkOwnership;
	private final boolean checkTargetable;
	// f_722efca7_3: vanilla flag letting the queried ship's own station or ship
	// through the count; its mapping to the constructor booleans is uncertain
	private final boolean allowSameStationOrShip;

	public BattleNext_RetreatPositionEvaluator(HullSize minHullSize, boolean checkOwnership, boolean checkTargetable, boolean allowSameStationOrShip) {
		this.minHullSize = minHullSize;
		this.checkOwnership = checkOwnership;
		this.checkTargetable = checkTargetable;
		this.allowSameStationOrShip = allowSameStationOrShip;
	}

	public boolean isSameStationOrShipAllowed() {
		return this.allowSameStationOrShip;
	}

	@Override
	public boolean allowsSameStationOrShip() {
		return this.allowSameStationOrShip;
	}

	// approximation: reconstructed base-class behavior; ships at or above the
	// configured hull size count when they pass the ownership and targetability
	// checks
	@Override
	public boolean isShipValid(ShipAPI ship, Vector2f location, ShipAPI candidate) {
		if (this.checkOwnership && candidate.getOwner() == ship.getOwner()) {
			return false;
		}

		if (candidate.getHullSize() != null && this.minHullSize != null && candidate.getHullSize().ordinal() < this.minHullSize.ordinal()) {
			return false;
		}

		if (this.checkTargetable && !candidate.isTargetable()) {
			return false;
		}

		return true;
	}
}
