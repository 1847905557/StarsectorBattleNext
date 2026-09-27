package data.scripts.plugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.battlesnext.util.BattleNext_BoxBasedUtils;
import data.scripts.battlesnext.util.BattleNext_FontData;
import data.scripts.tools.BattleNext_Settings;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.boxutil.util.TransformUtil;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

public class BattleNext_BattleClockPlugin extends BaseEveryFrameCombatPlugin {

	private static final float TOP_MARGIN = 20f;
	private static final float SPRITE_WIDTH = 440f;
	private static final float SPRITE_HEIGHT = 220f;
	private static final float TRANSITION_TIME = 0.5f;
	private static final Color TEXT_COLOR = Color.WHITE;

	private final float timeLimit;
	private final BattleNext_ScorekeeperPlugin scorekeeper;

	private CombatEngineAPI engine;
	private BattleNext_FontData.TextObject timerText;
	private SpriteEntity timeoutSprite;
	private int displayedSecond = -1;
	private float resultStart = -1f;
	private float resultTimerAlpha;
	private float resultTimeoutAlpha;
	private boolean graphicEnabled;
	private boolean completed;

	public BattleNext_BattleClockPlugin(float timeLimit, BattleNext_ScorekeeperPlugin scorekeeper) {
		this.timeLimit = Math.max(0f, timeLimit);
		this.scorekeeper = scorekeeper;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		graphicEnabled = BattleNext_Settings.isFullGraphicEffect();
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {

		if (engine == null || completed) return;
		if (engine.isCombatOver()) {
			cleanup();
			return;
		}
		if (!graphicEnabled) {
			if (scorekeeper.isFrozen()) {
				cleanup();
			}
			return;
		}
		if (timerText == null) initVisuals();

		float elapsed = engine.getTotalElapsedTime(false);
		if (resultStart < 0f) {
			int second = Math.max(0, (int) elapsed);
			if (second != displayedSecond) {
				displayedSecond = second;
				timerText.setText(String.format(Locale.ROOT, "%02d:%02d", second / 60, second % 60));
			}
			if (scorekeeper.isFrozen()) {
				resultStart = elapsed;
				resultTimerAlpha = timerAlpha(elapsed);
				resultTimeoutAlpha = timeoutAlpha(elapsed);
			}
		}
		if (resultStart >= 0f && elapsed - resultStart >= TRANSITION_TIME) {
			cleanup();
		}
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {

		if (completed || timerText == null) return;
		float elapsed = engine.getTotalElapsedTime(false);
		float timerAlpha = timerAlpha(elapsed);
		float imageAlpha = timeoutAlpha(elapsed);
		if (resultStart >= 0f) {
			float resultFade = Math.max(0f, 1f - (elapsed - resultStart) / TRANSITION_TIME);
			timerAlpha = resultTimerAlpha * resultFade;
			imageAlpha = resultTimeoutAlpha * resultFade;
		}

		float centerX = Global.getSettings().getScreenWidth() * 0.5f;
		float centerY = Global.getSettings().getScreenHeight() - TOP_MARGIN - SPRITE_HEIGHT * 0.5f;
		timeoutSprite.setLocation(centerX, centerY);
		timeoutSprite.getMaterialData().setColorAlpha(imageAlpha);
		if (timerAlpha > 0f) {
			timerText.setBaseColor(new Color(TEXT_COLOR.getRed(), TEXT_COLOR.getGreen(), TEXT_COLOR.getBlue(),
					Math.min(255, Math.round(timerAlpha * 255f))));
			timerText.draw(centerX - timerText.getWidth() * 0.5f,
					centerY + timerText.getHeight() * 0.5f);
		}
	}

	private void initVisuals() {
		BoxUtilModPlugin.initLater();
		SpriteAPI sprite = Global.getSettings().getSprite("misc", "BattleNext_timeout");
		timeoutSprite = BattleNext_BoxBasedUtils.addSprite(sprite, CombatEngineLayers.JUST_BELOW_WIDGETS);
		timeoutSprite.setCustomPrimeMatrix();
		timeoutSprite.setPrimeMatrix(TransformUtil.createWindowOrthoMatrix(null));
		timeoutSprite.setBaseSizePerTiles(SPRITE_WIDTH * 0.5f, SPRITE_HEIGHT * 0.5f);
		timeoutSprite.setNormalBlend();
		timeoutSprite.getMaterialData().setColor(Color.WHITE);
		timeoutSprite.getMaterialData().setColorAlpha(0f);
		timerText = BattleNext_FontData.getOrbitronNormal();
		timerText.setFontSize(48f);
		timerText.setBaseColor(TEXT_COLOR);
	}

	private float timerAlpha(float elapsed) {
		if (timeLimit <= 0f || elapsed < timeLimit) return 1f;
		float time = elapsed - timeLimit;
		if (time < 0.5f) return 1f - time / TRANSITION_TIME;
		if (time < 3.5f) return 0f;
		if (time < 4f) return (time - 3.5f) / TRANSITION_TIME;
		return 1f;
	}

	private float timeoutAlpha(float elapsed) {
		if (timeLimit <= 0f || elapsed < timeLimit + 0.5f) return 0f;
		float time = elapsed - timeLimit;
		if (time < 1f) return (time - 0.5f) / TRANSITION_TIME;
		if (time < 3f) return 1f;
		if (time < 3.5f) return 1f - (time - 3f) / TRANSITION_TIME;
		return 0f;
	}

	private void cleanup() {
		completed = true;
		if (timeoutSprite != null) {
			timeoutSprite.delete();
		}
		if (timerText != null) {
			timerText.dispose();
		}
		engine.removePlugin(this);
	}
}
