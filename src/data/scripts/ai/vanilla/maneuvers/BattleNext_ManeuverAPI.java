package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.attack.BattleNext_AttackingGroup;
import data.scripts.ai.vanilla.attack.BattleNext_WeaponGroupEvaluation;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import data.scripts.ai.vanilla.shields.BattleNext_ShieldAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine interface com.fs.starfarer.combat.ai.movement.maneuvers.ManeuverAPI.
 * The shared view maneuvers get on the ship AI that spawned them.
 */
public interface BattleNext_ManeuverAPI {
	void cancelCurrentManeuver();

	BattleNext_AttackingGroup getAttackingGroup();

	BattleNext_WeaponGroupEvaluation getEvaluationFor(WeaponGroupAPI group);

	boolean doesShipVelocityMatterForAim();

	float getCollisionRadius();

	Vector2f getLocation();

	boolean isFighter();

	boolean keepTarget();

	float getMaxFiringRange();

	float getOptimalNonMissileRange();

	void setMaxTargetRange(float maxTargetRange);

	ShipwideAIFlags getAIFlags();

	BattleNext_ThreatEvalAI getThreatEvaluator();

	BattleNext_AttackAIModule getAttackAI();

	BattleNext_FlockingAI getFlockingAI();

	BattleNext_ShieldAI getShieldAI();

	BattleNext_FiringSolutionEval getEval();

	ShipAIConfig getConfig();

	ShipAPI getShip();
}
