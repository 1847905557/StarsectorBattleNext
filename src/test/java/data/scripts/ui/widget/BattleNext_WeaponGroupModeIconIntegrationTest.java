package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.BattleNext_UiTheme;
import data.test.fixture.BattleNext_TestSpriteCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_WeaponGroupModeIconIntegrationTest extends BattleNext_TestVariantFixture {
	private static final Color ORIGINAL_SPRITE_COLOR = new Color(150, 180, 210);

	@Test
	void bothIconsUseTheirActualRegisteredResources() throws Exception {
		JSONObject core = BattleNext_TestSpriteCatalog.read(Path.of("..", "..", "starsector-core", "data/config/settings.json"));
		JSONObject mod = BattleNext_TestSpriteCatalog.read(Path.of("data/config/settings.json"));
		assertEquals("graphics/icons/jump-point2.png",
				core.getJSONObject("graphics").getJSONObject("systemMap").getString("icon_jump_point"));
		assertEquals("graphics/icons/jump-point.png",
				mod.getJSONObject("graphics").getJSONObject("icon").getString("BattleNext_weapon_group_linked"));
		for (String path : List.of("graphics/icons/jump-point.png", "graphics/icons/jump-point2.png")) {
			assertTrue(Files.exists(Path.of("..", "..", "starsector-core", path)));
		}
	}

	@Test
	void commentedRegistrationsAndHashCharactersInsideStringsAreParsedCorrectly() throws Exception {
		String input = "{\"active\":\"graphics/#icon.png\", # inline comment\n"
				+ "# \"active\":\"graphics/wrong.png\",\n"
				+ "\"escaped\":\"quote\\\"#still-string\"}";
		JSONObject parsed = new JSONObject(BattleNext_TestSpriteCatalog.withoutHashComments(input));
		assertEquals("graphics/#icon.png", parsed.getString("active"));
		assertEquals("quote\"#still-string", parsed.getString("escaped"));
		assertEquals(2, parsed.length());
	}

	@ParameterizedTest
	@CsvSource({
			"LINKED,false,false", "LINKED,true,false", "LINKED,false,true", "LINKED,true,true",
			"ALTERNATING,false,false", "ALTERNATING,true,false", "ALTERNATING,false,true", "ALTERNATING,true,true"
	})
	void modeSelectsTheIconAcrossAutofireAndExpansionStates(String mode, boolean autofire, boolean expanded) throws Exception {

		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		json.getJSONArray("weaponGroups").getJSONObject(0).put("mode", mode).put("autofire", autofire);
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		((Set<Integer>) getField(details, "expanded")).addAll(expanded ? Set.of(0) : Set.of());
		details.setVariantJson(json);
		details.attach(panel(1000f, 601f, new BaseCustomUIPanelPlugin()));
		CustomPanelAPI top = header(details);
		Object plugin = plugins.get(top);
		assertEquals(mode.equals("LINKED") ? "graphics/icons/jump-point.png" : "graphics/icons/jump-point2.png",
				spritePaths.get((SpriteAPI) getField(plugin, "primarySprite")));
		assertEquals(BattleNext_UiTheme.ACCENT, getField(plugin, "primaryTint"));
		assertEquals(40f, top.getPosition().getHeight());
		assertTrue(labels.contains(mode + " / BattleNext_variant_autofire_" + (autofire ? "enabled" : "disabled")));
		spriteRenderColors.clear();
		spriteRenderStates.clear();
		plugins.get(top).render(0.5f);
		assertEquals(List.of(BattleNext_UiTheme.ACCENT), spriteRenderColors);
		assertEquals(32f, spriteRenderStates.get(0)[0]);
		assertEquals(32f, spriteRenderStates.get(0)[1]);
		assertEquals(16f, spriteRenderStates.get(0)[2]);
		assertEquals(16f, spriteRenderStates.get(0)[3]);
		assertEquals(0.4f, spriteRenderStates.get(0)[4]);
		details.close();
	}

	@Test
	void changingModesDuringRefreshKeepsExpansionAndScrollState() throws Exception {

		JSONObject json = equipmentJson();
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		((Set<Integer>) getField(details, "expanded")).add(0);
		details.setVariantJson(json);
		details.attach(panel(640f, 601f, new BaseCustomUIPanelPlugin()));
		lists(details).get("groups").getExternalScroller().setYOffset(80f);
		for (String mode : List.of("ALTERNATING", "LINKED", "ALTERNATING")) {
			json.getJSONArray("weaponGroups").getJSONObject(0).put("mode", mode);
			details.refreshContent();
			assertEquals(Set.of(0), getField(details, "expanded"));
			assertEquals(80f, lists(details).get("groups").getExternalScroller().getYOffset());
			SpriteAPI sprite = (SpriteAPI) getField(plugins.get(header(details)), "primarySprite");
			assertEquals(mode.equals("LINKED") ? "graphics/icons/jump-point.png" : "graphics/icons/jump-point2.png",
					spritePaths.get(sprite));
		}
		details.close();
	}

	@ParameterizedTest
	@CsvSource({"report,false,false", "fleet,true,false", "group,true,true"})
	void primaryTintRestoresSharedSpriteStateAndKeepsPortraitsAtTheirOriginalColor(
			String kind, boolean portrait, boolean tint) throws Exception {

		var owner = panel(400f, 140f, new BaseCustomUIPanelPlugin());
		var list = owner.createUIElement(400f, 140f, false);
		BattleNext_ExpandablePanel.Header data = new BattleNext_ExpandablePanel.Header("graphics/ship.png",
				portrait, portrait ? "graphics/portrait.png" : "", kind, "Detail", "", "",
				tint ? BattleNext_UiTheme.ACCENT : null);
		BattleNext_ExpandablePanel.add(list, owner, 380f, false, 0f, data, () -> {}, ignored -> {}, body -> {});
		owner.addUIElement(list).inTL(0f, 0f);
		var root = (CustomPanelAPI) tooltips.get(list).getComponents().get(0);
		var top = (CustomPanelAPI) children.get(root).get(0);
		SpriteAPI primary = (SpriteAPI) getField(plugins.get(top), "primarySprite");
		Color original = ORIGINAL_SPRITE_COLOR;
		primary.setColor(original);
		float[] before = sprites.get(primary).clone();
		spriteRenderColors.clear();
		plugins.get(top).render(0.5f);
		assertEquals(tint ? BattleNext_UiTheme.ACCENT : original, spriteRenderColors.get(0));
		if (portrait) assertEquals(Color.WHITE, spriteRenderColors.get(1));
		assertEquals(original, primary.getColor());
		assertArrayEquals(before, sprites.get(primary));
	}

	@Test
	void otherModesUseTheExistingMissingIconAndKeepTheirModeText() throws Exception {
		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		json.getJSONArray("weaponGroups").getJSONObject(0).put("mode", "CUSTOM");
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		details.setVariantJson(json);
		details.attach(panel(1000f, 601f, new BaseCustomUIPanelPlugin()));
		assertNull(getField(plugins.get(header(details)), "primarySprite"));
		assertTrue(labels.contains("CUSTOM / BattleNext_variant_autofire_enabled"));
		details.close();
	}

	private CustomPanelAPI header(BattleNext_VariantDetails details) throws Exception {
		var root = (CustomPanelAPI) tooltips.get(lists(details).get("groups")).getComponents().get(0);
		return (CustomPanelAPI) children.get(root).get(0);
	}
}
