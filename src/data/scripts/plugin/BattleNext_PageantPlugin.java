package data.scripts.plugin;

import data.scripts.ui.BattleNext_PageLayout;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.Misc;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.battlesnext.util.BattleNext_BoxBasedUtils;
import data.scripts.battlesnext.util.BattleNext_FontData;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Settings;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.boxutil.util.TransformUtil;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_PageantPlugin extends BaseEveryFrameCombatPlugin {

	private static final float SIDEBAR_SCALE = 0.5f;
	private static final float SIDEBAR_LEFT_INSET = BattleNext_PageLayout.SECTION_GAP;
	private static final float OPENING_TEXT_INSET = BattleNext_PageLayout.SECTION_GAP;
	private static final float SIDEBAR_TEXT_INSET = BattleNext_PageLayout.SECTION_GAP;
	private static final float END_DURATION = 11.25f;

	private final BattleNext_ScorekeeperPlugin scorekeeper;
	private final String playerName;
	private final String enemyName;
	private final Map<String, Visual> sprites = new HashMap<>();

	private record Visual(SpriteEntity entity, float width, float height) {

	}

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
	private boolean endingComplete;
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
			sprites.put(key, new Visual(entity, sprite.getWidth(), sprite.getHeight()));
		}

		float plateWidth = sprites.get("plateOpeningPlayer").width();
		float openingTextWidth = plateWidth - OPENING_TEXT_INSET * 2f;
		float sidebarTextWidth = plateWidth * SIDEBAR_SCALE - SIDEBAR_TEXT_INSET * 2f;
		playerText = nameText(playerName, Misc.getPositiveHighlightColor(), openingTextWidth, 48f);
		enemyText = nameText(enemyName, Misc.getNegativeHighlightColor(), openingTextWidth, 48f);
		playerSidebarText = nameText(playerName, Misc.getPositiveHighlightColor(), sidebarTextWidth, 48f * SIDEBAR_SCALE);
		enemySidebarText = nameText(enemyName, Misc.getNegativeHighlightColor(), sidebarTextWidth, 48f * SIDEBAR_SCALE);

		drawText = BattleNext_FontData.getOrbitronBold();
		drawText.setText(BattleNext_I18nSection.getInstance("Combat", "BattleNext_combat_").get("draw"));
		drawText.setFontSize(90f);
		drawText.setBaseColor(Color.CYAN);
		visualsInitialized = true;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {

		if (engine == null || endingComplete) return;
		BattleNext_BattleReportModel report = scorekeeper.getFrozenReport();
		if (!graphicEnabled) {
			if (report != null) {
				completeEnding();
			}
			return;
		}
		if (!visualsInitialized) initVisuals();

		float elapsed = engine.getTotalElapsedTime(false);
		if (!versusSoundPlayed && elapsed >= 0.5f) {
			versusSoundPlayed = true;
			Global.getSoundPlayer().playUISound("BattleNext_versus", 1f, 1f);
		}

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
		if (elapsed - endingStart >= END_DURATION) completeEnding();
	}

	public boolean isEndingComplete() {
		return endingComplete;
	}

	private void completeEnding() {
		endingComplete = true;
		cleanup();
		engine.removePlugin(this);
	}

	public void cleanup() {
		graphicEnabled = false;
		for (Visual visual : sprites.values()) {
			visual.entity().delete();
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
		for (Visual visual : sprites.values()) {
			visual.entity().getMaterialData().setColorAlpha(0f);
		}

		float width = Global.getSettings().getScreenWidth();
		float height = Global.getSettings().getScreenHeight();
		if (endingStart >= 0f) {
			renderEnding(elapsed - endingStart, width, height);
			return;
		}

		renderOpening(elapsed, width, height);
		if (elapsed >= 4.4f) drawSidebar(height, 1f);
	}

	private void drawSidebar(float height, float alpha) {
		Visual plate = sprites.get("plateSidebarPlayer");
		float x = SIDEBAR_LEFT_INSET + plate.width() * SIDEBAR_SCALE * 0.5f;
		float offset = plate.height() * SIDEBAR_SCALE;
		drawSprite("plateSidebarPlayer", x, height * 0.5f + offset, SIDEBAR_SCALE, alpha);
		drawSprite("plateSidebarEnemy", x, height * 0.5f - offset, SIDEBAR_SCALE, alpha);
		drawName(playerSidebarText, x, height * 0.5f + offset, alpha);
		drawName(enemySidebarText, x, height * 0.5f - offset, alpha);
	}

	private void renderOpening(float elapsed, float width, float height) {
		if (elapsed >= 0.5f && elapsed < 4.4f) {
			float offset = elapsed < 1f ? (1f - elapsed) * 3000f : -10f * (elapsed - 1f);
			float alpha = elapsed < 1f ? 1f : fade(elapsed - 1f, 0f, 3.2f, 0.2f);
			float playerX = width * 0.5f + sprites.get("plateOpeningPlayer").width() * 1.2f + offset;
			float enemyX = width * 0.5f - sprites.get("plateOpeningPlayer").width() * 1.2f - offset;
			drawSprite("plateOpeningPlayer", playerX, height * 0.5f, 1f, alpha);
			drawSprite("plateOpeningEnemy", enemyX, height * 0.5f, 1f, alpha);
			drawName(playerText, playerX, height * 0.5f, alpha);
			drawName(enemyText, enemyX, height * 0.5f, alpha);
		}
		if (elapsed < 1f || elapsed >= 4.4f) return;

		float time = elapsed - 1f;
		drawSprite("BattleNext_versus", width * 0.5f, height * 0.5f + 105f - time * 42f,
				1f, fade(time, 0f, 3.2f, 0.2f));
		if (time < 0.5f) {
			drawSprite("BattleNext_versusFlash", width * 0.5f, height * 0.5f + 105f - time * 42f,
					1f, 1f - time / 0.5f);
		}
	}

	private void renderEnding(float time, float width, float height) {
		if (time >= END_DURATION) return;

		if (endingStart >= 4.4f && time < 1.5f) {
			float sidebarAlpha = time <= 0.5f ? 1f : 1.5f - time;
			drawSidebar(height, sidebarAlpha);
		}

		if ("draw".equals(endingKey)) {
			drawText(drawText, width * 0.5f, height * 0.5f, fade(time, 0.25f, 10f, 0.5f));
			return;
		}

		BattleNext_FontData.TextObject winnerText = playerWon
				? playerText : enemyText;
		float winnerY = height * 0.5f - 50f + time * 5f;
		float alpha = fade(time, 0.25f, 10f, 1f);
		drawSprite("plateWinner", width * 0.5f, winnerY, 1f, alpha);
		drawName(winnerText, width * 0.5f, winnerY, alpha);

		float bannerTime = time;
		String key = "shame".equals(endingKey) ? "BattleNext_shame" : "BattleNext_win";
		float centerY = height * 0.5f + ("shame".equals(endingKey) ? 0f : 120f - bannerTime * 3f);
		drawSprite(key, width * 0.5f, centerY,
				1f,
				fade(bannerTime, 0.25f, 10f, 0.5f));
		if (bannerTime < 0.5f) {
			drawSprite(key + "Flash", width * 0.5f, centerY,
					1f,
					fade(bannerTime, 0.25f, 10f, 0.5f) * (1f - bannerTime / 0.5f));
		}
	}

	private String endingKey(BattleNext_BattleReportModel report) {
		if ("draw".equals(report.getWinner())) return "draw";
		FleetSide side = "player".equals(report.getWinner()) ? FleetSide.PLAYER : FleetSide.ENEMY;
		int losses = 0;
		for (FleetMemberAPI member : engine.getFleetManager(side).getDisabledCopy()) {
			if (!member.isFighterWing()) losses++;
		}
		for (FleetMemberAPI member : engine.getFleetManager(side).getDestroyedCopy()) {
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

	private void drawSprite(String key, float x, float y, float scale, float alpha) {
		if (alpha <= 0f) return;
		Visual visual = sprites.get(key);
		SpriteEntity entity = visual.entity();
		entity.setBaseSizePerTiles(visual.width() * scale * 0.5f, visual.height() * scale * 0.5f);
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
