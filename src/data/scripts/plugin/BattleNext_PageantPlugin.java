package data.scripts.plugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.Misc;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.battlesnext.util.BattleNext_FontData;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.BattleNext_BoxBasedUtils;
import data.scripts.tools.BattleNext_Settings;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.boxutil.util.TransformUtil;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_PageantPlugin extends BaseEveryFrameCombatPlugin {

	private static final float PLATE_WIDTH = 360f;
	private static final float PLATE_HEIGHT = 80f;
	private static final float END_DURATION = 11.5f;

	private final BattleNext_ScorekeeperPlugin scorekeeper;
	private final String playerName;
	private final String enemyName;
	private final Map<String, SpriteEntity> sprites = new HashMap<>();

	private CombatEngineAPI engine;
	private BattleNext_FontData.TextObject playerText;
	private BattleNext_FontData.TextObject enemyText;
	private BattleNext_FontData.TextObject playerSidebarText;
	private BattleNext_FontData.TextObject enemySidebarText;
	private BattleNext_FontData.TextObject drawText;

	private boolean graphicEnabled;
	private boolean visualsInitialized;
	private boolean versusSoundPlayed;
	private boolean endingSoundPlayed;
	private float endingStart = -1f;
	private String endingKey = "";
	private boolean playerWon;

	public BattleNext_PageantPlugin(BattleNext_ScorekeeperPlugin scorekeeper, String playerName, String enemyName) {
		this.scorekeeper = scorekeeper;
		this.playerName = playerName;
		this.enemyName = enemyName;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		graphicEnabled = BattleNext_Settings.isFullGraphicEffect();
	}

	private void initVisuals() {
		BoxUtilModPlugin.initLater();
		for (String key : new String[]{"plateOpeningPlayer", "plateOpeningEnemy", "plateSidebarPlayer",
				"plateSidebarEnemy", "plateWinner", "BattleNext_versus", "BattleNext_versusFlash",
				"BattleNext_win", "BattleNext_winFlash", "BattleNext_shame", "BattleNext_shameFlash"}) {
			SpriteAPI sprite = Global.getSettings().getSprite("misc", key.startsWith("plate") ? "BattleNext_nameplate" : key);
			SpriteEntity entity = BattleNext_BoxBasedUtils.addSprite(sprite, CombatEngineLayers.JUST_BELOW_WIDGETS);
			entity.setCustomPrimeMatrix();
			entity.setPrimeMatrix(TransformUtil.createWindowOrthoMatrix(null));
			if (key.endsWith("Flash")) entity.setAdditiveBlend();
			else entity.setNormalBlend();
			entity.getMaterialData().setColor(Color.WHITE);
			entity.getMaterialData().setColorAlpha(0f);
			sprites.put(key, entity);
		}
		playerText = nameText(playerName, Misc.getPositiveHighlightColor(), 320f, 48f);
		enemyText = nameText(enemyName, Misc.getNegativeHighlightColor(), 320f, 48f);
		playerSidebarText = nameText(playerName, Misc.getPositiveHighlightColor(), 156f, 24f);
		enemySidebarText = nameText(enemyName, Misc.getNegativeHighlightColor(), 156f, 24f);
		drawText = BattleNext_FontData.getOrbitronBold();
		drawText.setText(BattleNext_I18nSection.getInstance("Combat", "BattleNext_combat_").get("draw"));
		drawText.setFontSize(90f);
		drawText.setBaseColor(Color.CYAN);
		visualsInitialized = true;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {

		if (engine == null || !graphicEnabled) return;
		if (!visualsInitialized) initVisuals();

		float elapsed = engine.getTotalElapsedTime(false);
		if (!versusSoundPlayed && elapsed >= 0.5f) {
			versusSoundPlayed = true;
			Global.getSoundPlayer().playUISound("BattleNext_versus", 1f, 1f);
		}

		BattleNext_BattleReportModel report = scorekeeper.getFrozenReport();
		if (report == null) return;
		if (endingStart < 0f) {
			endingStart = elapsed;
			playerWon = "player".equals(report.getWinner());
			endingKey = endingKey(report);
		}

		if (!endingSoundPlayed && !"draw".equals(endingKey)) {
			endingSoundPlayed = true;
			Global.getSoundPlayer().playUISound("shame".equals(endingKey)
					? "BattleNext_shame" : "BattleNext_victory", 1f, 1f);
		}
	}

	public boolean isEndingComplete() {
		if (!graphicEnabled) return true;
		return endingStart >= 0f && engine.getTotalElapsedTime(false) - endingStart >= END_DURATION;
	}

	public void cleanup() {
		graphicEnabled = false;
		for (SpriteEntity entity : sprites.values()) {
			entity.delete();
		}
		sprites.clear();
		if (playerText != null) playerText.dispose();
		if (enemyText != null) enemyText.dispose();
		if (playerSidebarText != null) playerSidebarText.dispose();
		if (enemySidebarText != null) enemySidebarText.dispose();
		if (drawText != null) drawText.dispose();
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {
		if (engine == null || !graphicEnabled || !visualsInitialized) return;

		float elapsed = engine.getTotalElapsedTime(false);
		for (SpriteEntity entity : sprites.values()) {
			entity.getMaterialData().setColorAlpha(0f);
		}

		float width = Global.getSettings().getScreenWidth();
		float height = Global.getSettings().getScreenHeight();
		if (endingStart >= 0f) {
			renderEnding(elapsed - endingStart, width, height);
			return;
		}

		renderOpening(elapsed, width, height);
		if (elapsed >= 4.4f) {
			drawPlate("plateSidebarPlayer", 100f, height * 0.5f + 40f, 180f, 40f, 1f);
			drawPlate("plateSidebarEnemy", 100f, height * 0.5f - 40f, 180f, 40f, 1f);
			drawName(playerSidebarText, 100f, height * 0.5f + 40f, 1f);
			drawName(enemySidebarText, 100f, height * 0.5f - 40f, 1f);
		}
	}

	private void renderOpening(float elapsed, float width, float height) {
		if (elapsed >= 0.5f && elapsed < 4.4f) {
			float offset = elapsed < 1f ? (1f - elapsed) * 3000f : -10f * (elapsed - 1f);
			float alpha = elapsed < 1f ? 1f : fade(elapsed - 1f, 0f, 3.2f, 0.2f);
			float playerX = width * 0.5f + PLATE_WIDTH * 1.2f + offset;
			float enemyX = width * 0.5f - PLATE_WIDTH * 1.2f - offset;
			drawPlate("plateOpeningPlayer", playerX, height * 0.5f, PLATE_WIDTH, PLATE_HEIGHT, alpha);
			drawPlate("plateOpeningEnemy", enemyX, height * 0.5f, PLATE_WIDTH, PLATE_HEIGHT, alpha);
			drawName(playerText, playerX, height * 0.5f, alpha);
			drawName(enemyText, enemyX, height * 0.5f, alpha);
		}
		if (elapsed < 1f || elapsed >= 4.4f) return;

		float time = elapsed - 1f;
		drawSprite("BattleNext_versus", width * 0.5f, height * 0.5f + 105f - time * 42f,
				180f, 420f, fade(time, 0f, 3.2f, 0.2f));
		if (time < 0.5f) {
			drawSprite("BattleNext_versusFlash", width * 0.5f, height * 0.5f + 105f - time * 42f,
					180f, 420f, 1f - time / 0.5f);
		}
	}

	private void renderEnding(float time, float width, float height) {
		if (time >= END_DURATION) return;
		if ("draw".equals(endingKey)) {
			if (time < 11.25f) drawText(drawText, width * 0.5f, height * 0.5f,
					fade(time, 0.25f, 10f, 1f));
			return;
		}
		BattleNext_FontData.TextObject winnerText = playerWon
				? playerText : enemyText;
		if (time < 11.25f) {
			float centerY = height * 0.5f - 50f + time * 5f;
			float alpha = fade(time, 0.25f, 10f, 1f);
			drawPlate("plateWinner", width * 0.5f, centerY, PLATE_WIDTH, PLATE_HEIGHT, alpha);
			drawName(winnerText, width * 0.5f, centerY, alpha);
		}
		if (time < 0.5f) return;
		float bannerTime = time - 0.5f;
		String key = "shame".equals(endingKey) ? "BattleNext_shame" : "BattleNext_win";
		float centerY = height * 0.5f + ("shame".equals(endingKey) ? 0f : 120f - bannerTime * 3f);
		drawSprite(key, width * 0.5f, centerY,
				"shame".equals(endingKey) ? 764f : 440f,
				"shame".equals(endingKey) ? 629f : 220f,
				fade(bannerTime, 0f, 10f, 1f));
		if (bannerTime < 0.5f) {
			drawSprite(key + "Flash", width * 0.5f, centerY,
					"shame".equals(endingKey) ? 764f : 440f,
					"shame".equals(endingKey) ? 629f : 220f,
					1f - bannerTime / 0.5f);
		}
	}

	private String endingKey(BattleNext_BattleReportModel report) {
		if ("draw".equals(report.getWinner())) return "draw";
		FleetSide side = "player".equals(report.getWinner()) ? FleetSide.PLAYER : FleetSide.ENEMY;
		int losses = 0;
		for (var member : engine.getFleetManager(side).getDisabledCopy()) {
			if (!member.isFighterWing()) losses++;
		}
		for (var member : engine.getFleetManager(side).getDestroyedCopy()) {
			if (!member.isFighterWing()) losses++;
		}
		return losses == 0 ? "shame" : report.getWinner();
	}

	private BattleNext_FontData.TextObject nameText(String value, Color color, float maxWidth, float maxSize) {
		BattleNext_FontData.TextObject text = BattleNext_FontData.getOrbitronNormal();
		text.setText(value);
		text.setBaseColor(color);
		for (float size = maxSize; size >= 12f; size--) {
			text.setFontSize(size);
			if (text.getWidth() <= maxWidth) return text;
		}
		String shortened = value;
		while (!shortened.isEmpty() && text.getWidth() > maxWidth) {
			shortened = shortened.substring(0, shortened.length() - 1);
			text.setText(shortened + "...");
		}
		return text;
	}

	private void drawPlate(String key, float x, float y, float width, float height, float alpha) {
		drawSprite(key, x, y, width, height, alpha);
	}

	private void drawName(BattleNext_FontData.TextObject text, float x, float y, float alpha) {
		if (alpha <= 0f) return;
		Color base = text == playerText || text == playerSidebarText
				? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		text.setBaseColor(new Color(base.getRed(), base.getGreen(), base.getBlue(),
				Math.min(255, Math.max(0, Math.round(alpha * 255f)))));
		text.draw(x - text.getWidth() * 0.5f, y + text.getHeight() * 0.5f);
	}

	private void drawText(BattleNext_FontData.TextObject text, float x, float y, float alpha) {
		if (alpha <= 0f) return;
		text.setBaseColor(new Color(0, 255, 255, Math.min(255, Math.max(0, Math.round(alpha * 255f)))));
		text.draw(x - text.getWidth() * 0.5f, y + text.getHeight() * 0.5f);
	}

	private void drawSprite(String key, float x, float y, float width, float height, float alpha) {
		if (alpha <= 0f) return;
		SpriteEntity entity = sprites.get(key);
		entity.setBaseSizePerTiles(width * 0.5f, height * 0.5f);
		entity.setLocation(x, y);
		entity.getMaterialData().setColorAlpha(alpha);
	}

	private float fade(float time, float in, float full, float out) {
		if (time < 0f || time >= in + full + out) return 0f;
		if (in > 0f && time < in) return time / in;
		if (out > 0f && time > in + full) return 1f - (time - in - full) / out;
		return 1f;
	}
}
