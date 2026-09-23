package data.scripts.ai.vanilla.movement;

/**
 * Port of engine interface com.fs.starfarer.combat.ai.movement.EngineAI.
 * Turns desired heading/facing into concrete engine commands.
 * Deviation from vanilla: the engine interface extends the engine AI marker
 * interface whose render() is an engine-side debug hook and is dropped.
 */
public interface BattleNext_EngineAI {
	void advance(float amount);

	boolean isAlwaysAccelerate();

	void setAlwaysAccelerate(boolean alwaysAccelerate);

	void setDesiredHeading(float heading, float speedFraction);

	void setDesiredFacing(float facing);

	void setAvoidingCollision(boolean avoidingCollision);
}
