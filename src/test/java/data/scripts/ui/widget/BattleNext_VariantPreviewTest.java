package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.loading.specs.Q;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.ui.BattleNext_RefitPanelPlugin;
import data.scripts.ui.BattleNext_UiTheme;
import data.test.fixture.BattleNext_TestVariantPreviewFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

class BattleNext_VariantPreviewTest extends BattleNext_TestVariantPreviewFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void turretAndHardpointUseTheirOwnBodiesBarrelsPivotsAndMissileOffsets(boolean missile) throws Exception {

		ShipHullSpecAPI hull = previewHull("root", Map.of(
				"turret", previewSlot("turret", "turret", new Vector2f(20f, -10f), 0f),
				"hardpoint", previewSlot("hardpoint", "hardpoint", new Vector2f(-20f, 10f), -90f)),
				Map.of(), null, false);
		previewWeapon("turret", missile, List.of(new Vector2f(4f, 0f), new Vector2f(3f, 0f)), List.of(0f, 90f));
		previewWeapon("hardpoint", missile, List.of(new Vector2f(5f, 0f), new Vector2f(0f, 6f)), List.of(0f, 90f));
		JSONObject json = previewJson("draft", "root", Map.of("turret", "turret", "hardpoint", "hardpoint"));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		assertTrue(preview.getErrors().isEmpty());
		renderPreview(preview, 1000f, 1000f);
		assertEquals(missile ? 9 : 5, renderedSprites.size());
		int root = renderIndex("graphics/root.png", 0);
		for (String kind : List.of("turret", "hardpoint")) {
			int body = renderIndex("graphics/" + kind + "_" + kind + ".png", 0);
			int barrel = renderIndex("graphics/" + kind + "_" + kind + "_gun.png", 0);
			assertArrayEquals(spriteCenters.get(body), spriteCenters.get(barrel));
			assertEquals(kind.equals("turret") ? 10f : -10f,
					spriteCenters.get(body)[0] - spriteCenters.get(root)[0], 0.001f);
			assertEquals(kind.equals("turret") ? 20f : -20f,
					spriteCenters.get(body)[1] - spriteCenters.get(root)[1], 0.001f);
			assertEquals(kind.equals("turret") ? 0f : -90f, spriteRenderTransforms.get(body)[0], 0.001f);
			assertEquals(8f, spriteRenderStates.get(body)[2]);
			assertEquals(kind.equals("turret") ? 12f : 6f, spriteRenderStates.get(body)[3]);
			if (!missile) continue;
			int first = renderIndex("graphics/" + kind + "_missile.png", 0);
			int second = renderIndex("graphics/" + kind + "_missile.png", 1);
			assertEquals(kind.equals("turret") ? 0f : 5f,
					spriteCenters.get(first)[0] - spriteCenters.get(body)[0], 0.001f);
			assertEquals(kind.equals("turret") ? 4f : 0f,
					spriteCenters.get(first)[1] - spriteCenters.get(body)[1], 0.001f);
			assertEquals(0f,
					spriteCenters.get(second)[0] - spriteCenters.get(body)[0], 0.001f);
			assertEquals(kind.equals("turret") ? 3f : 6f,
					spriteCenters.get(second)[1] - spriteCenters.get(body)[1], 0.001f);
			assertEquals(kind.equals("turret") ? 90f : 0f, spriteRenderTransforms.get(second)[0], 0.001f);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"turret", "hardpoint"})
	void loadedHullCenterPlacesEveryWeaponAtItsSocketInsteadOfTheImageCenter(String mount) throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of(
				"first", previewSlot("first", mount, new Vector2f(-2f, 35f), 45f),
				"second", previewSlot("second", mount, new Vector2f(148f, -25f), 0f)), Map.of(), null, false);
		spriteSpecs.put("root", new Q("graphics/root.png", 140f, 280f, 70f, 115f, 220f, null));
		previewWeapon("gun", false, List.of(), List.of());
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root",
				Map.of("first", "gun", "second", "gun")), hull, Global.getSettings());
		assertTrue(preview.getErrors().isEmpty());
		renderPreview(preview, 1000f, 1000f);
		int root = renderIndex("graphics/root.png", 0);
		assertArrayEquals(new float[]{140f, 280f, 70f, 115f}, Arrays.copyOf(spriteRenderStates.get(root), 4));
		float imageCenterY = spriteCenters.get(root)[1] + 280f * 0.5f - 115f;
		for (int index = 0; index < 2; index++) {
			int body = renderIndex("graphics/gun_" + mount + ".png", index);
			int barrel = renderIndex("graphics/gun_" + mount + "_gun.png", index);
			assertArrayEquals(spriteCenters.get(body), spriteCenters.get(barrel));
			assertEquals(index == 0 ? -35f : 25f, spriteCenters.get(body)[0] - spriteCenters.get(root)[0], 0.001f);
			assertEquals(index == 0 ? -27f : 123f, spriteCenters.get(body)[1] - imageCenterY, 0.001f);
		}
	}

	@Test
	void rotatedModuleUsesItsOwnLoadedCenterAndDimensionSnapshot() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("module",
				previewSlot("module", "module", new Vector2f(50f, 0f), 90f)), Map.of(), null, false);
		previewHull("child", Map.of("gun", previewSlot("gun", "turret", new Vector2f(10f, 0f), 0f)),
				Map.of(), new Vector2f(5f, 0f), false);
		spriteSpecs.put("child", new Q("graphics/child.png", 80f, 100f, 19f, 77f, 0f, null));
		previewWeapon("gun", false, List.of(), List.of());
		JSONObject json = previewJson("draft", "root", Map.of()).put("modules",
				new JSONObject().put("module", previewJson("child_variant", "child", Map.of("gun", "gun"))));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		int root = renderIndex("graphics/root.png", 0);
		int child = renderIndex("graphics/child.png", 0);
		int gun = renderIndex("graphics/gun_turret.png", 0);
		assertEquals(5f, spriteCenters.get(child)[0] - spriteCenters.get(root)[0], 0.001f);
		assertEquals(50f, spriteCenters.get(child)[1] - spriteCenters.get(root)[1], 0.001f);
		assertEquals(-10f, spriteCenters.get(gun)[0] - spriteCenters.get(child)[0], 0.001f);
		assertEquals(0f, spriteCenters.get(gun)[1] - spriteCenters.get(child)[1], 0.001f);
		assertArrayEquals(new float[]{80f, 100f, 19f, 77f}, Arrays.copyOf(spriteRenderStates.get(child), 4));
		assertEquals(90f, spriteRenderTransforms.get(child)[0]);
		spriteSpecs.put("child", new Q("graphics/child.png", 140f, 180f, 61f, 83f, 0f, null));
		renderPreview(preview, 1000f, 1000f);
		assertArrayEquals(new float[]{80f, 100f, 19f, 77f}, Arrays.copyOf(spriteRenderStates.get(child), 4));
		BattleNext_VariantPreview refreshed = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(refreshed, 1000f, 1000f);
		assertArrayEquals(new float[]{140f, 180f, 61f, 83f}, Arrays.copyOf(spriteRenderStates.get(child), 4));
	}

	@Test
	void loadedMissileCenterAndAngleOffsetsFollowTheNativePlacementRules() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("gun",
				previewSlot("gun", "hardpoint", new Vector2f(10f, 20f), -90f)), Map.of(), null, false);
		previewWeapon("gun", true, List.of(new Vector2f(3f, 7f)), List.of(60f));
		spriteSpecs.put("gun_missile", new Q("graphics/gun_missile.png", 18f, 26f, 4f, 8f, 0f, null));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root", Map.of("gun", "gun")), hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		int body = renderIndex("graphics/gun_hardpoint.png", 0);
		int missile = renderIndex("graphics/gun_missile.png", 0);
		assertArrayEquals(new float[]{18f, 26f, 4f, 8f}, Arrays.copyOf(spriteRenderStates.get(missile), 4));
		assertEquals(3f, spriteCenters.get(missile)[0] - spriteCenters.get(body)[0], 0.001f);
		assertEquals(7f, spriteCenters.get(missile)[1] - spriteCenters.get(body)[1], 0.001f);
		assertEquals(-30f, spriteRenderTransforms.get(missile)[0]);
	}

	@ParameterizedTest
	@ValueSource(strings = {"root", "child", "gun_missile"})
	void loadedSpriteSpecFailureIsReportedOnceAndKeepsOtherParts(String id) throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of(
				"module", previewSlot("module", "module", new Vector2f(), 0f),
				"gun", previewSlot("gun", "turret", new Vector2f(), 0f)), Map.of(), null, false);
		previewHull("child", Map.of(), Map.of(), null, false);
		previewWeapon("gun", true, List.of(new Vector2f()), List.of());
		failedSpriteSpecs.add(id);
		JSONObject json = previewJson("draft", "root", Map.of("gun", "gun")).put("modules",
				new JSONObject().put("module", previewJson("child_variant", "child", Map.of())));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		assertTrue(preview.hasContent());
		assertEquals(1, preview.getErrors().size());
		assertTrue(preview.getErrors().get(0).contains("Could not read loaded sprite spec for hull " + id));
		int reads = spriteSpecReads.size();
		for (int frame = 0; frame < 3; frame++) {
			renderPreview(preview, 1000f, 1000f);
			assertFalse(renderedPaths().contains("graphics/" + id + ".png"));
			assertTrue(renderedPaths().contains("graphics/gun_turret.png"));
			assertEquals(reads, spriteSpecReads.size());
		}
		assertEquals(1, previewLogs().size());
		assertEquals(id.equals("child") ? "module" : "", previewLogs().get(0).getJSONObject("fields").getString("module_path"));
	}

	@Test
	void hiddenWeaponsIncludingMissilesAreNeverQueriedOrDrawn() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("hidden",
				previewSlot("hidden", "hidden", new Vector2f(500f, 400f), 45f)), Map.of("hidden", "missile"), null, false);
		previewWeapon("missile", true, List.of(new Vector2f()), List.of(0f));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root",
				Map.of("hidden", "missile")), hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		assertEquals(List.of("graphics/root.png"), renderedPaths());
		assertEquals(0, weaponReads.size());
		assertTrue(preview.getErrors().isEmpty());
		assertTrue(previewLogs().isEmpty());
	}

	@Test
	void builtInDecorativeAndRepeatedGroupReferencesRenderEachInstalledSlotOnce() throws Exception {
		Map<String, WeaponSlotAPI> slots = Map.of(
				"built", previewSlot("built", "turret", new Vector2f(), 0f),
				"decorative", previewSlot("decorative", "decorative", new Vector2f(), 0f));
		ShipHullSpecAPI hull = previewHull("root", slots, Map.of("built", "built", "decorative", "decorative"), null, false);
		previewWeapon("built", false, List.of(), List.of());
		previewWeapon("decorative", false, List.of(), List.of());
		JSONObject json = previewJson("draft", "root", Map.of("built", "built"));
		json.getJSONArray("weaponGroups").put(json.getJSONArray("weaponGroups").getJSONObject(0));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		assertEquals(List.of("built", "decorative"), weaponReads);
		assertEquals(5, renderedSprites.size());
		assertTrue(renderedPaths().contains("graphics/decorative_turret.png"));
	}

	@Test
	void nestedModulesComposeSlotRotationsAnchorsAndUnderParentLayering() throws Exception {

		ShipHullSpecAPI rootHull = previewHull("root", Map.of(
				"under", previewSlot("under", "module", new Vector2f(50f, 0f), 90f),
				"above", previewSlot("above", "module", new Vector2f(-50f, 0f), 0f)), Map.of(), null, false);
		previewHull("under", Map.of(
				"gun", previewSlot("gun", "turret", new Vector2f(10f, 0f), 0f),
				"nested", previewSlot("nested", "module", new Vector2f(0f, 20f), -90f)),
				Map.of(), new Vector2f(5f, 0f), true);
		previewHull("nested", Map.of(), Map.of(), new Vector2f(0f, 4f), false);
		previewHull("above", Map.of(), Map.of(), null, false);
		previewWeapon("gun", false, List.of(), List.of());
		JSONObject under = previewJson("under_variant", "under", Map.of("gun", "gun"))
				.put("modules", new JSONObject().put("nested", previewJson("nested_variant", "nested", Map.of())));
		JSONObject json = previewJson("draft", "root", Map.of()).put("modules", new JSONObject()
				.put("under", under).put("above", previewJson("above_variant", "above", Map.of())));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, rootHull, Global.getSettings());
		assertTrue(preview.getErrors().isEmpty());
		renderPreview(preview, 1000f, 1000f);
		assertEquals(List.of("graphics/under.png", "graphics/gun_turret.png", "graphics/gun_turret_gun.png",
				"graphics/nested.png", "graphics/root.png", "graphics/above.png"), renderedPaths());
		int root = renderIndex("graphics/root.png", 0);
		float[] expectedX = {5f, -5f, -5f, 9f, 0f, 0f};
		float[] expectedY = {50f, 50f, 50f, 30f, 0f, -50f};
		float[] expectedAngles = {90f, 90f, 90f, 0f, 0f, 0f};
		for (int index = 0; index < renderedSprites.size(); index++) {
			assertEquals(expectedX[index], spriteCenters.get(index)[0] - spriteCenters.get(root)[0], 0.001f);
			assertEquals(expectedY[index], spriteCenters.get(index)[1] - spriteCenters.get(root)[1], 0.001f);
			assertEquals(expectedAngles[index], spriteRenderTransforms.get(index)[0], 0.001f);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"inline", "registered", "array"})
	void moduleFormsAndRepeatedVariantIdsKeepEachModuleInstance(String form) throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of(
				"left", previewSlot("left", "module", new Vector2f(0f, 60f), 0f),
				"right", previewSlot("right", "module", new Vector2f(0f, -60f), 0f)), Map.of(), null, false);
		ShipHullSpecAPI child = previewHull("child", Map.of(), Map.of(), null, false);
		JSONObject module = previewJson("shared", "child", Map.of());
		registeredVariants.put("shared", variant("shared", child).getApi());
		Object reference = form.equals("registered") ? "shared" : module;
		JSONObject modules = new JSONObject().put("right", reference).put("left", reference);
		JSONObject json = previewJson("draft", "root", Map.of())
				.put("modules", form.equals("array") ? new JSONArray().put(modules) : modules);
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		assertTrue(preview.getErrors().isEmpty());
		assertEquals(List.of("graphics/root.png", "graphics/child.png", "graphics/child.png"), renderedPaths());
		int left = renderIndex("graphics/child.png", 0);
		int right = renderIndex("graphics/child.png", 1);
		assertEquals(120f, spriteCenters.get(right)[0] - spriteCenters.get(left)[0], 0.001f);
	}

	@ParameterizedTest
	@CsvSource({"1000,1000", "40,200", "300,30", "1,1"})
	void allRotatedCornersFitInTheCenteredJointBoundsWithoutUpscaling(float width, float height) throws Exception {

		ShipHullSpecAPI hull = previewHull("root", Map.of("gun",
				previewSlot("gun", "hardpoint", new Vector2f(100f, -70f), 37f)), Map.of(), null, false);
		previewWeapon("gun", true, List.of(new Vector2f(20f, 10f)), List.of(45f));
		spriteSpecs.put("root", new Q("graphics/root.png", 80f, 100f, 19f, 77f, 0f, null));
		spriteSpecs.put("gun_missile", new Q("graphics/gun_missile.png", 10f, 14f, 3f, 4f, 0f, null));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root", Map.of("gun", "gun")),
				hull, Global.getSettings());
		renderPreview(preview, width, height);
		float left = Float.POSITIVE_INFINITY;
		float right = Float.NEGATIVE_INFINITY;
		float bottom = Float.POSITIVE_INFINITY;
		float top = Float.NEGATIVE_INFINITY;
		float scale = spriteRenderStates.get(0)[0] / 80f;
		assertTrue(scale <= 1f);
		for (int index = 0; index < renderedSprites.size(); index++) {
			float[] state = spriteRenderStates.get(index);
			assertEquals(scale, state[0] / sprites.get(renderedSprites.get(index))[0], 0.001f);
			double radians = Math.toRadians(spriteRenderTransforms.get(index)[0]);
			for (float horizontal : new float[]{-state[2], state[0] - state[2]}) {
				for (float vertical : new float[]{-state[3], state[1] - state[3]}) {
					float cornerX = spriteCenters.get(index)[0] + horizontal * (float) Math.cos(radians)
							- vertical * (float) Math.sin(radians);
					float cornerY = spriteCenters.get(index)[1] + horizontal * (float) Math.sin(radians)
							+ vertical * (float) Math.cos(radians);
					left = Math.min(left, cornerX);
					right = Math.max(right, cornerX);
					bottom = Math.min(bottom, cornerY);
					top = Math.max(top, cornerY);
				}
			}
		}
		assertEquals(100f, (left + right) * 0.5f, 0.001f);
		assertEquals(200f, (bottom + top) * 0.5f, 0.001f);
		assertTrue(right - left <= width + 0.001f);
		assertTrue(top - bottom <= height + 0.001f);
	}

	@Test
	void sharedSpritesRestoreEveryChangedStateAndRenderingUsesOnlyBuiltResources() throws Exception {

		ShipHullSpecAPI hull = previewHull("root", Map.of(
				"first", previewSlot("first", "turret", new Vector2f(), 0f),
				"second", previewSlot("second", "hardpoint", new Vector2f(), 90f)), Map.of(), null, false);
		previewWeapon("gun", true, List.of(new Vector2f(10f, 0f)), List.of());
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root",
				Map.of("first", "gun", "second", "gun")), hull, Global.getSettings());
		Map<SpriteAPI, float[]> originalStates = new IdentityHashMap<>();
		Map<SpriteAPI, float[]> originalTransforms = new IdentityHashMap<>();
		for (SpriteAPI sprite : sharedSprites.values()) {
			sprite.setColor(Color.MAGENTA);
			sprite.setAngle(73f);
			sprite.setAdditiveBlend();
			originalStates.put(sprite, sprites.get(sprite).clone());
			originalTransforms.put(sprite, spriteTransforms.get(sprite).clone());
		}
		int reads = imageReads.size() + weaponReads.size() + spriteSpecReads.size();
		for (int cycle = 0; cycle < 4; cycle++) {
			renderPreview(preview, 1000f, 1000f);
			assertEquals(7, renderedSprites.size());
			for (int index = 0; index < renderedSprites.size(); index++) {
				SpriteAPI sprite = renderedSprites.get(index);
				assertArrayEquals(originalStates.get(sprite), sprites.get(sprite));
				assertArrayEquals(originalTransforms.get(sprite), spriteTransforms.get(sprite));
				assertEquals(Color.MAGENTA, sprite.getColor());
				assertEquals(Color.WHITE, spriteRenderColors.get(index));
				assertEquals(0.4f, spriteRenderStates.get(index)[4]);
				assertEquals(770f, spriteRenderTransforms.get(index)[1]);
				assertEquals(771f, spriteRenderTransforms.get(index)[2]);
			}
		}
		assertEquals(reads, imageReads.size() + weaponReads.size() + spriteSpecReads.size());
		assertTrue(previewLogs().isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"hull", "body", "barrel", "missile", "weapon", "module", "group", "module_array"})
	void individualFailuresKeepSuccessfulPartsAndLogTheirBuildContextOnce(String failure) throws Exception {

		ShipHullSpecAPI hull = previewHull("root", Map.of(
				"gun", previewSlot("gun", "turret", new Vector2f(), 0f),
				"module", previewSlot("module", "module", new Vector2f(), 0f)), Map.of(), null, false);
		previewWeapon("gun", true, List.of(new Vector2f()), List.of(0f));
		previewHull("child", Map.of(), Map.of(), null, false);
		JSONObject json = previewJson("draft", "root", Map.of("gun", "gun"));
		if (failure.equals("hull")) failedImages.add("graphics/root.png");
		if (failure.equals("body")) failedImages.add("graphics/gun_turret.png");
		if (failure.equals("barrel")) failedImages.add("graphics/gun_turret_gun.png");
		if (failure.equals("missile")) failedImages.add("graphics/gun_missile.png");
		if (failure.equals("weapon")) previewWeapons.put("gun", null);
		if (failure.equals("module")) json.put("modules", new JSONObject().put("module", previewJson("broken", "unknown", Map.of())));
		if (failure.equals("group")) json.getJSONArray("weaponGroups").put("invalid");
		if (failure.equals("module_array")) json.put("modules", new JSONArray().put("invalid")
				.put(new JSONObject().put("module", previewJson("child_variant", "child", Map.of()))));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		assertTrue(preview.hasContent());
		assertEquals(1, preview.getErrors().size());
		List<JSONObject> logs = previewLogs();
		assertEquals(1, logs.size());
		JSONObject fields = logs.get(0).getJSONObject("fields");
		assertEquals("draft", fields.getString("variant_id"));
		assertEquals("root", fields.getString("hull_id"));
		assertTrue(fields.has("module_path"));
		assertTrue(fields.has("slot_id"));
		assertTrue(fields.has("object_id"));
		assertTrue(fields.has("resource"));
		assertTrue(fields.has("stage"));
		for (int frame = 0; frame < 3; frame++) {
			renderPreview(preview, 1000f, 1000f);
			assertTrue(renderedSprites.size() > 0);
		}
		assertEquals(1, previewLogs().size());
		if (failure.equals("body")) assertTrue(renderedPaths().contains("graphics/gun_turret_gun.png"));
		if (failure.equals("module_array")) assertTrue(renderedPaths().contains("graphics/child.png"));
	}

	@Test
	void weaponBodyReadFailureKeepsItsConfiguredBarrelAndMissile() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("gun", previewSlot("gun", "turret", new Vector2f(), 0f)),
				Map.of(), null, false);
		previewWeapon("gun", true, List.of(new Vector2f()), List.of(0f));
		failedImages.add("graphics/gun_turret.png");
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root", Map.of("gun", "gun")),
				hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		assertEquals(List.of("graphics/root.png", "graphics/gun_turret_gun.png", "graphics/gun_missile.png"), renderedPaths());
	}

	@Test
	void publicApiSpecWithoutProjectileBarrelsStillDrawsItsWeaponBody() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("beam", previewSlot("beam", "turret", new Vector2f(), 0f)),
				Map.of(), null, false);
		previewWeapons.put("beam", proxy(WeaponSpecAPI.class, (method, arguments) -> switch (method) {
			case "getWeaponId" -> "beam";
			case "getTurretSpriteName" -> "graphics/beam.png";
			default -> null;
		}));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root", Map.of("beam", "beam")),
				hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		assertEquals(List.of("graphics/root.png", "graphics/beam.png"), renderedPaths());
		assertTrue(preview.getErrors().isEmpty());
	}

	@Test
	void projectileQueryAndSpritePathFailuresRemainIndependentStages() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("gun", previewSlot("gun", "turret", new Vector2f(), 0f)),
				Map.of(), null, false);
		previewWeapon("gun", true, List.of(new Vector2f()), List.of(0f));
		failedWeaponQueries.put("gun", "getTurretSpriteName");
		JSONObject json = previewJson("draft", "root", Map.of("gun", "gun"));
		BattleNext_VariantPreview bodyFailure = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(bodyFailure, 1000f, 1000f);
		assertEquals(List.of("graphics/root.png", "graphics/gun_turret_gun.png", "graphics/gun_missile.png"), renderedPaths());
		assertEquals(1, bodyFailure.getErrors().size());
		failedWeaponQueries.put("gun", "getProjectileSpec");
		BattleNext_VariantPreview missileFailure = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(missileFailure, 1000f, 1000f);
		assertEquals(List.of("graphics/root.png", "graphics/gun_turret.png", "graphics/gun_turret_gun.png"), renderedPaths());
		assertEquals(1, missileFailure.getErrors().size());
	}

	@Test
	void spriteStateIsRestoredWhenThePublicDrawCallFails() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of(), Map.of(), null, false);
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(previewJson("draft", "root", Map.of()),
				hull, Global.getSettings());
		SpriteAPI sprite = sharedSprites.get("graphics/root.png");
		sprite.setColor(Color.MAGENTA);
		sprite.setAngle(72f);
		sprite.setAdditiveBlend();
		float[] state = sprites.get(sprite).clone();
		float[] transform = spriteTransforms.get(sprite).clone();
		failSpriteRender = true;
		assertThrows(IllegalStateException.class, () -> renderPreview(preview, 30f, 50f));
		assertArrayEquals(state, sprites.get(sprite));
		assertArrayEquals(transform, spriteTransforms.get(sprite));
		assertEquals(Color.MAGENTA, sprite.getColor());
	}

	@Test
	void refreshReadsTheNewSnapshotWhileExistingModelsRetainTheirComposition() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("gun", previewSlot("gun", "turret", new Vector2f(), 0f)),
				Map.of(), null, false);
		previewWeapon("first", false, List.of(), List.of());
		previewWeapon("second", false, List.of(), List.of());
		JSONObject json = previewJson("draft", "root", Map.of("gun", "first"));
		BattleNext_VariantPreview first = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		json.getJSONArray("weaponGroups").getJSONObject(0).getJSONObject("weapons").put("gun", "second");
		renderPreview(first, 1000f, 1000f);
		assertTrue(renderedPaths().contains("graphics/first_turret.png"));
		BattleNext_VariantPreview second = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(second, 1000f, 1000f);
		assertTrue(renderedPaths().contains("graphics/second_turret.png"));
	}

	@Test
	void cyclicInlineModulesProduceOneDiagnosticAndKeepTheParent() throws Exception {
		ShipHullSpecAPI hull = previewHull("root", Map.of("module", previewSlot("module", "module", new Vector2f(), 0f)),
				Map.of(), null, false);
		JSONObject json = previewJson("draft", "root", Map.of());
		json.put("modules", new JSONObject().put("module", json));
		BattleNext_VariantPreview preview = new BattleNext_VariantPreview(json, hull, Global.getSettings());
		renderPreview(preview, 1000f, 1000f);
		assertEquals(List.of("graphics/root.png"), renderedPaths());
		assertEquals(1, preview.getErrors().size());
		assertTrue(preview.getErrors().get(0).contains("Cyclic preview modules"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"catalog", "campaign", "refit"})
	void sharedEntrypointsBindCompleteVariantDisplayRefreshAndRelease(String entry) throws Exception {

		Variant variant = source();
		variant.getApi().addMod("installed");
		installConsumerHullmod();
		Q hullSprite = ((HullSpriteSpec) variant.getApi().getHullSpec()).getSpriteSpec();
		hullSprite.getClass().getMethod("o00000", float.class, float.class).invoke(hullSprite, 31f, 83f);
		previewWeapon("laser", false, List.of(), List.of());
		BattleNext_VariantDetails details;
		Runnable refresh;
		Runnable close;
		if (entry.equals("catalog")) {
			BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
			store.reload();
			var pin = store.saveVariant("Example", variant.getApi());
			var page = page(store, Runnable::run, (description, action) -> {}, () -> {});
			page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
			page.buttonPressed("pin:" + pin.getSourcePath());
			details = (BattleNext_VariantDetails) getField(getField(page, "editor"), "details");
			refresh = page::refreshContent;
			close = page::close;
		} else if (entry.equals("campaign")) {
			var display = open(variant, new BattleNext_PinnedVariantStore(), 1000);
			details = (BattleNext_VariantDetails) getField(display, "details");
			refresh = () -> display.buttonPressed("pin_save");
			close = () -> {
				try {
					var method = display.getClass().getDeclaredMethod("close");
					method.setAccessible(true);
					method.invoke(display);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				}
			};
		} else {
			BattleNext_RefitPanelPlugin plugin = new BattleNext_RefitPanelPlugin(() -> {});
			plugin.init(panel(1000f, 700f, plugin), 1000f, 700f);
			ShipVariantAPI current = variant.getApi();
			FleetMemberAPI member = proxy(FleetMemberAPI.class, (method, arguments) -> method.equals("getVariant") ? current : null);
			plugin.open(member, new BattleNext_VariantWeaponRowsIntegrationTest.Refit(current));
			details = (BattleNext_VariantDetails) getField(plugin, "details");
			refresh = details::refreshContent;
			close = plugin::close;
		}
		try {
			Object firstPlugin = getField(details, "preview");
			assertNotNull(firstPlugin);
			BattleNext_VariantPreview first = (BattleNext_VariantPreview) getField(firstPlugin, "preview");
			renderPreview(first, 1000f, 1000f);
			assertEquals(List.of("graphics/parent.png", "graphics/laser_turret.png", "graphics/laser_turret_gun.png",
					"graphics/child.png"), renderedPaths());
			assertArrayEquals(new float[]{120f, 120f, 31f, 83f}, Arrays.copyOf(spriteRenderStates.get(0), 4));
			((Set<Integer>) getField(details, "expanded")).add(0);
			details.refreshContent();
			assertConsumerDisplay(details);
			JSONObject snapshot = (JSONObject) getField(details, "variantJson");
			for (String mode : List.of("ALTERNATING", "LINKED")) {
				snapshot.getJSONArray("weaponGroups").getJSONObject(0).put("mode", mode);
				details.refreshContent();
				var currentGroup = (CustomPanelAPI) tooltips.get(lists(details).get("groups")).getComponents().get(0);
				var header = (CustomPanelAPI) children.get(currentGroup).get(0);
				var sprite = (SpriteAPI) getField(plugins.get(header), "primarySprite");
				assertEquals(mode.equals("LINKED") ? "graphics/icons/jump-point.png" : "graphics/icons/jump-point2.png",
						spritePaths.get(sprite));
				assertEquals(BattleNext_UiTheme.ACCENT, getField(plugins.get(header), "primaryTint"));
				assertEquals(Set.of(0), getField(details, "expanded"));
			}
			firstPlugin = getField(details, "preview");
			assertNotNull(getField(firstPlugin, "preview"));
			refresh.run();
			assertNull(getField(firstPlugin, "preview"));
			assertEquals(Set.of(0), getField(details, "expanded"));
			Object currentPlugin = getField(details, "preview");
			assertNotSame(firstPlugin, currentPlugin);
			assertNotNull(getField(currentPlugin, "preview"));
			renderPreview((BattleNext_VariantPreview) getField(currentPlugin, "preview"), 1000f, 1000f);
			assertTrue(renderedPaths().contains("graphics/laser_turret.png"));
			assertArrayEquals(new float[]{120f, 120f, 31f, 83f}, Arrays.copyOf(spriteRenderStates.get(0), 4));
			assertConsumerDisplay(details);
			CustomPanelAPI owner = details.getPanel();
			close.run();
			assertNull(getField(currentPlugin, "preview"));
			assertNull(details.getPanel());
			assertTrue(children.get(owner).isEmpty());
			assertTrue(((List<?>) getField(details, "ownedPanels")).isEmpty());
		} finally {
			close.run();
		}
	}

	private void installConsumerHullmod() {
		BaseHullMod effect = new BaseHullMod() {
			@Override
			public String getDescriptionParam(int index, ShipAPI.HullSize size) {
				return index == 0 ? size + " 20%" : index == 1 ? "Literal %s, %d and 50%" : null;
			}

			@Override
			public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize size,
					ShipAPI ship, float width, boolean isForModSpec) {
				tooltip.addPara("Custom post 50%", 8f);
			}

			@Override
			public boolean hasSModEffectSection(ShipAPI.HullSize size, ShipAPI ship, boolean isForModSpec) {
				return true;
			}

			@Override
			public void addSModSection(TooltipMakerAPI tooltip, ShipAPI.HullSize size, ShipAPI ship,
					float width, boolean isForModSpec, boolean isForBuiltInList) {
				tooltip.addPara("Enhanced: " + size + " 30%.\nComplete extra effect 40%.", 8f);
			}
		};
		HullModSpecAPI spec = proxy(HullModSpecAPI.class, (method, arguments) -> switch (method) {
			case "getId" -> "installed";
			case "getDisplayName" -> "Alpha installed";
			case "getDescriptionFormat" -> "First: %s.\nSecond: %s.";
			case "getEffect" -> effect;
			default -> null;
		});
		effect.init(spec);
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getHullModSpec") && arguments[0].equals("installed")) return spec;
			try {
				Object result = method.invoke(previous, arguments);
				if (!method.getName().equals("getHullSpec") || result == null) return result;
				return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ShipHullSpecAPI.class, HullSpriteSpec.class},
						(hull, query, values) -> {
					if (query.getName().equals("getHullSize")) return ShipAPI.HullSize.FRIGATE;
					try {
						return query.invoke(result, values);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				});
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	private void assertConsumerDisplay(BattleNext_VariantDetails details) throws Exception {
		var group = (CustomPanelAPI) tooltips.get(lists(details).get("groups")).getComponents().get(0);
		var container = (CustomPanelAPI) children.get(group).get(1);
		var body = (CustomPanelAPI) children.get(container).get(0);
		var rows = tooltips.get(children.get(body).get(0)).getComponents();
		assertEquals(1, rows.size());
		var row = (CustomPanelAPI) rows.get(0);
		assertEquals(40f, row.getPosition().getHeight());
		var image = tooltips.get(children.get(row).get(0)).getComponents().get(0);
		assertEquals("graphics/laser_turret.png", imagePaths.get(image));
		for (String section : List.of("modules", "wings")) {
			var tileRow = (CustomPanelAPI) tooltips.get(lists(details).get(section)).getComponents().get(0);
			var tile = (CustomPanelAPI) children.get(tileRow).get(0);
			assertEquals(tile.getPosition().getWidth(), tile.getPosition().getHeight());
			var caption = tooltips.get(children.get(tile).get(0));
			var name = (LabelAPI) caption.getComponents().get(1);
			assertEquals(section.equals("modules") ? "Child" : "Wing wing", name.getText());
			assertEquals(Alignment.MID, labelAlignments.get(name.getPosition()));
			var hover = tooltip(360f, 0f, false);
			attachedTooltips.get(caption.getComponents().get(0)).createTooltip(hover, false, null);
			var identifier = (LabelAPI) tooltips.get(hover).getComponents().get(1);
			assertEquals(section.equals("modules") ? "child" : "wing", identifier.getText());
			assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(identifier.getPosition()));
		}
		var mod = tooltips.get(lists(details).get("plugins")).getComponents().stream()
				.filter(component -> attachedTooltips.containsKey(component))
				.filter(component -> tooltips.get(children.get((CustomPanelAPI) component).get(0)).getComponents().stream()
						.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast)
						.anyMatch(label -> label.getText().equals("Alpha installed"))).findFirst().orElseThrow();
		var hover = tooltip(360f, 0f, false);
		assertEquals(360f, attachedTooltips.get(mod).getTooltipWidth(null));
		attachedTooltips.get(mod).createTooltip(hover, false, null);
		var text = tooltips.get(hover).getComponents().stream().filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).map(LabelAPI::getText).toList();
		assertEquals("installed", text.get(1));
		assertTrue(text.contains("First: FRIGATE 20%.\nSecond: Literal %s, %d and 50%."));
		assertTrue(text.contains("Custom post 50%"));
		assertTrue(text.contains("Enhanced: FRIGATE 30%.\nComplete extra effect 40%."));
		var description = tooltips.get(hover).getComponents().get(2).getPosition();
		assertEquals("First: %s.\nSecond: %s.", labelPatterns.get(description));
		assertEquals(List.of(Misc.getHighlightColor()), labelHighlightColors.get(description));
	}
}
