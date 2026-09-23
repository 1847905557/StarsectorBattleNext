package data.scripts.ai.vanilla;

/**
 * Port of engine enum com.fs.starfarer.combat.ai.AttackMode.
 * In the current vanilla build HOVER and BACK_AWAY are always forced back to
 * STRAFE by BasicShipAI, so only STRAFE is ever active (dead code kept on purpose).
 */
enum BattleNext_AttackMode {
	STRAFE,
	BACK_AWAY,
	HOVER;
}
