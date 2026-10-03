package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.ui.BattleNext_UiTheme;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class BattleNext_VariantSquareCardsIntegrationTest extends BattleNext_TestVariantFixture {

	private final Set<String> failedImages = new HashSet<>();
	private Variant originalVariant;
	private JSONObject snapshot;

	@BeforeEach
	void configureSquareData() throws Exception {

		shortenLabels = true;
		paragraphLineHeight = 15f;
		originalVariant = source();
		snapshot = BattleNext_VariantBuilder.fromVariant(originalVariant.getApi()).toJson();
		var registeredModule = originalVariant.getModules().get("module");
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getVariant") && arguments[0].equals("registered_module")) return registeredModule;
			if (method.getName().equals("getSprite") && arguments.length == 1
					&& failedImages.contains(arguments[0].toString())) {
				throw new IllegalArgumentException("Missing image: " + arguments[0]);
			}
			try {
				return method.invoke(previous, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	@ParameterizedTest
	@CsvSource({
			"modules,320", "wings,320", "modules,640", "wings,640",
			"modules,1001", "wings,1001", "modules,1400", "wings,1400"
	})
	void bothGridsUseEqualSquareSizesAndKeepTheirLastRowLeftAligned(String section, int width) throws Exception {

		JSONObject modules = new JSONObject();
		JSONArray wings = new JSONArray();
		for (int index = 0; index < 7; index++) {
			int moduleIndex = 6 - index;
			hull("child_" + moduleIndex, "Child " + moduleIndex, List.of());
			modules.put("slot_" + moduleIndex, module().put("hullId", "child_" + moduleIndex));
			wings.put("wing_" + index);
		}
		snapshot.put("modules", modules).put("wings", wings);
		BattleNext_VariantDetails details = mount(width);
		TooltipState grid = tooltips.get(lists(details).get(section));
		assertEquipmentSpacing(grid);
		List<CustomPanelAPI> rows = rows(grid);
		float rowWidth = rows.get(0).getPosition().getWidth();
		int columns = Math.max(1, (int) ((rowWidth + 4f) / 124f));
		float size = (rowWidth - (columns - 1) * 4f) / columns;
		assertEquals((7 + columns - 1) / columns, rows.size());
		int item = 0;
		for (CustomPanelAPI row : rows) {
			assertEquals(size, row.getPosition().getHeight(), 0.001f);
			int expectedCount = Math.min(columns, 7 - item);
			assertEquals(expectedCount, children.get(row).size());
			for (int column = 0; column < expectedCount; column++) {
				CustomPanelAPI card = (CustomPanelAPI) children.get(row).get(column);
				assertEquals(size, card.getPosition().getWidth(), 0.001f);
				assertEquals(size, card.getPosition().getHeight(), 0.001f);
				assertEquals(row.getPosition().getX() + column * (size + 4f), card.getPosition().getX(), 0.001f);
				LabelAPI caption = caption(card);
				assertEquals(Alignment.MID, labelAlignments.get(caption.getPosition()));
				assertEquals(card.getPosition().getCenterX(), caption.getPosition().getCenterX(), 0.001f);
				assertEquals(card.getPosition().getY() + 20f, caption.getPosition().getCenterY(), 0.001f);
				assertEquals(8f, caption.getPosition().getX() - card.getPosition().getX(), 0.001f);
				if (section.equals("modules")) {
					List<String> info = hover(card);
					assertEquals(List.of("Child " + item, "child_" + item), info);
				} else {
					assertEquals("Wing wing_" + item, caption.getText());
				}
				item++;
			}
		}
		assertEquals(7, item);
		float expectedHeight = rows.size() * (size + 4f) + 8f;
		assertEquals(expectedHeight, grid.getMountedHeight(), 0.001f);
		CustomPanelAPI parent = details.getPanel();
		details.close();
		assertTrue(children.get(parent).isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"30,33", "18,21", "320,90"})
	void imagesAreCenteredAboveNamesAndShrinkWithoutExceedingTheirNativeSize(int width, int height) throws Exception {

		spriteWidth = width;
		spriteHeight = height;
		BattleNext_VariantDetails details = mount(1000);
		for (String section : List.of("modules", "wings")) {
			CustomPanelAPI card = cards(details, section).get(0);
			Object plugin = plugins.get(card);
			SpriteAPI sprite = (SpriteAPI) getField(plugin, "sprite");
			assertNotNull(sprite);
			float[] before = sprites.get(sprite).clone();
			var color = sprite.getColor();
			spriteCenters.clear();
			spriteRenderStates.clear();
			plugins.get(card).render(0.5f);
			float imageWidth = card.getPosition().getWidth() - 16f;
			float imageHeight = card.getPosition().getHeight() - 44f;
			float scale = Math.min(1f, Math.min(imageWidth / width, imageHeight / height));
			assertEquals(width * scale, spriteRenderStates.get(0)[0], 0.001f);
			assertEquals(height * scale, spriteRenderStates.get(0)[1], 0.001f);
			assertEquals(card.getPosition().getCenterX(), spriteCenters.get(0)[0], 0.001f);
			assertEquals(card.getPosition().getY() + card.getPosition().getHeight() - 8f - imageHeight * 0.5f,
					spriteCenters.get(0)[1], 0.001f);
			assertEquals(before[4] * 0.5f, spriteRenderStates.get(0)[4]);
			assertArrayEquals(before, sprites.get(sprite));
			assertEquals(color, sprite.getColor());
			assertEquals(section.equals("modules") ? "graphics/child.png" : "graphics/fighter.png",
					spritePaths.get(sprite));
		}
		details.close();
	}

	@Test
	void moduleFormsAndDuplicateWingsKeepIdentityInTheirWholeCardHover() throws Exception {
		snapshot.put("modules", new JSONArray().put(new JSONObject().put("inline", module()))
				.put(new JSONObject().put("registered", "registered_module")));
		snapshot.put("wings", new JSONArray().put("wing").put("wing"));
		BattleNext_VariantDetails details = mount(1400);
		List<CustomPanelAPI> modules = cards(details, "modules");
		assertEquals(2, modules.size());
		for (int index = 0; index < modules.size(); index++) {
			assertEquals("Child", caption(modules.get(index)).getText());
			List<String> info = hover(modules.get(index));
			assertEquals(List.of("Child", "child"), info);
		}
		List<CustomPanelAPI> wings = cards(details, "wings");
		assertEquals(2, wings.size());
		for (CustomPanelAPI card : wings) {
			assertEquals("Wing wing", caption(card).getText());
			List<String> info = hover(card);
			assertEquals(List.of("Wing wing", "wing"), info);
			TooltipState text = tooltips.get(children.get(card).get(0));
			var surface = text.getComponents().get(0).getPosition();
			assertEquals(card.getPosition().getWidth(), surface.getWidth());
			assertEquals(card.getPosition().getHeight(), surface.getHeight());
		}
		details.close();
	}

	@Test
	void longNamesAreSingleLineAndRetainTheirFullNamesInTheHover() throws Exception {
		String hullName = "A module hull with a deliberately long complete display name";
		hull("child", hullName, List.of());
		snapshot.put("wings", new JSONArray().put("wing_with_a_deliberately_long_identity"));
		BattleNext_VariantDetails details = mount(640);
		for (String section : List.of("modules", "wings")) {
			CustomPanelAPI card = cards(details, section).get(0);
			LabelAPI label = caption(card);
			assertTrue(label.getText().endsWith("..."));
			assertEquals(15f, label.getPosition().getHeight());
			String full = hover(card).get(0);
			assertTrue(full.length() > label.getText().length());
			if (section.equals("modules")) assertEquals(hullName, full);
		}
		details.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"module", "wing", "image", "no_path", "array", "root", "empty_slot"})
	void failuresAndEmptySlotsKeepTheirSquareGridLocationAndExplainTheCause(String failure) throws Exception {

		if (failure.equals("module")) snapshot.put("modules", new JSONObject().put("broken",
				new JSONObject().put("hullId", "missing")));
		if (failure.equals("wing")) snapshot.put("wings", new JSONArray().put("aaa_missing_wing"));
		if (failure.equals("image")) failedImages.addAll(List.of("graphics/child.png", "graphics/fighter.png"));
		if (failure.equals("no_path")) {
			hulls.put("child", proxy(ShipHullSpecAPI.class, (method, arguments) -> switch (method) {
				case "getHullId" -> "child";
				case "getHullName" -> "Child";
				default -> null;
			}));
		}
		if (failure.equals("array")) snapshot.put("modules", new JSONArray().put("invalid"));
		if (failure.equals("root")) snapshot.put("modules", "invalid");
		if (failure.equals("empty_slot")) snapshot.put("wings", new JSONArray().put(""));
		BattleNext_VariantDetails details = mount(1000);
		String section = failure.equals("wing") || failure.equals("empty_slot") ? "wings" : "modules";
		CustomPanelAPI card = cards(details, section).get(0);
		assertEquals(card.getPosition().getWidth(), card.getPosition().getHeight());
		assertNull(getField(plugins.get(card), "sprite"));
		List<String> info = hover(card);
		assertTrue(info.stream().anyMatch(value -> value.contains("module_error")
				|| value.contains("unavailable") || value.contains("preview_error")));
		if (failure.equals("empty_slot")) assertEquals("BattleNext_variant_empty_slot", info.get(0));
		spriteCenters.clear();
		plugins.get(card).render(1f);
		float imageHeight = card.getPosition().getHeight() - 44f;
		assertEquals(card.getPosition().getCenterX(), spriteCenters.get(0)[0]);
		assertEquals(card.getPosition().getY() + card.getPosition().getHeight() - 8f - imageHeight * 0.5f,
				spriteCenters.get(0)[1], 0.001f);
		details.close();
	}

	@Test
	void gridRefreshPreservesScrollAndGroupStateAndClampsShorterContent() throws Exception {

		snapshot = equipmentJson();
		BattleNext_VariantDetails details = mount(1000);
		((Set<Integer>) getField(details, "expanded")).add(0);
		details.refreshContent();
		for (String section : List.of("modules", "wings", "groups")) {
			lists(details).get(section).getExternalScroller().setYOffset(60f);
		}
		for (int cycle = 0; cycle < 3; cycle++) {
			details.refreshContent();
			assertEquals(Set.of(0), getField(details, "expanded"));
			for (String section : List.of("modules", "wings", "groups")) {
				assertEquals(60f, lists(details).get(section).getExternalScroller().getYOffset());
			}
			assertEquals(5, children.get(details.getPanel()).size());
		}
		snapshot.put("modules", new JSONObject().put("module", module()));
		snapshot.put("wings", new JSONArray().put("wing"));
		details.refreshContent();
		for (String section : List.of("modules", "wings")) {
			assertEquals(0f, lists(details).get(section).getExternalScroller().getYOffset());
		}
		CustomPanelAPI owner = details.getPanel();
		details.close();
		assertTrue(children.get(owner).isEmpty());
		assertTrue(((List<?>) getField(details, "ownedPanels")).isEmpty());
		details.clear();
		details.attach(owner);
		assertEquals(Set.of(), getField(details, "expanded"));
		assertEquals(0f, lists(details).get("modules").getExternalScroller().getYOffset());
		details.close();
	}

	@Test
	void emptySectionsKeepTheirNonePrompt() throws Exception {
		snapshot.put("modules", new JSONObject()).put("wings", new JSONArray());
		BattleNext_VariantDetails details = mount(1000);
		assertEquals(2, labels.stream().filter("BattleNext_variant_none"::equals).count());
		assertTrue(cards(details, "modules").isEmpty());
		assertTrue(cards(details, "wings").isEmpty());
		details.close();
	}

	@ParameterizedTest
	@ValueSource(floats = {1f, 1.5f, 2f})
	void realOpenGlKeepsTheTileHollowAndItsBorderTwoPixelsAtEachUiScale(float scale) throws Exception {

		BattleNext_VariantDetails details = mount(1000);
		CustomPanelAPI card = cards(details, "modules").get(0);
		var plugin = plugins.get(card);
		var bounds = position(120f, 120f);
		bounds.inTL(32f, -32f);
		setField(plugin, "position", bounds);
		System.setProperty("org.lwjgl.librarypath", Path.of("../../starsector-core/native/windows")
				.toAbsolutePath().normalize().toString());
		Pbuffer buffer = new Pbuffer(384, 384, new PixelFormat(), null, null);
		try {
			buffer.makeCurrent();
			resetGeometryContext();
			GL11.glViewport(0, 0, 384, 384);
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			GL11.glOrtho(0, 384f / scale, 0, 384f / scale, -1, 1);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glLoadIdentity();
			GL11.glDisable(GL11.GL_LINE_SMOOTH);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glClearColor(1f, 0f, 1f, 1f);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			GL11.glLineWidth(7f);
			plugin.renderBelow(1f);
			assertEquals(7f, GL11.glGetFloat(GL11.GL_LINE_WIDTH));
			ByteBuffer pixels = BufferUtils.createByteBuffer(384 * 384 * 4);
			GL11.glReadPixels(0, 0, 384, 384, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			int center = Math.round(92f * scale);
			int edge = Math.round(32f * scale);
			assertEquals(List.of(255, 0, 255), pixel(pixels, center, center));
			int borderPixels = 0;
			for (int horizontal = edge - 4; horizontal <= edge + 4; horizontal++) {
				List<Integer> color = pixel(pixels, horizontal, center);
				if (!color.equals(List.of(255, 0, 255))) {
					assertEquals(List.of(BattleNext_UiTheme.PANEL.getRed(), BattleNext_UiTheme.PANEL.getGreen(),
							BattleNext_UiTheme.PANEL.getBlue()), color);
					borderPixels++;
				}
			}
			assertEquals(2, borderPixels);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			GL11.glEnable(GL11.GL_SCISSOR_TEST);
			GL11.glScissor(edge + 3, 0, 384 - edge - 3, 384);
			plugin.renderBelow(1f);
			assertTrue(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
			GL11.glReadPixels(0, 0, 384, 384, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			assertEquals(List.of(255, 0, 255), pixel(pixels, edge, center));
			assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
		} finally {
			resetGeometryContext();
			buffer.destroy();
			details.close();
		}
	}

	private BattleNext_VariantDetails mount(int width) {
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		details.setVariantJson(snapshot);
		details.attach(panel(width, 601f, new BaseCustomUIPanelPlugin()));
		return details;
	}

	private JSONObject module() throws Exception {
		return BattleNext_VariantBuilder.fromVariant(originalVariant.getModules().get("module")).toJson();
	}

	private List<CustomPanelAPI> rows(TooltipState grid) {
		return grid.getComponents().stream().filter(CustomPanelAPI.class::isInstance)
				.map(CustomPanelAPI.class::cast).toList();
	}

	private List<CustomPanelAPI> cards(BattleNext_VariantDetails details, String section) throws Exception {
		List<CustomPanelAPI> cards = new ArrayList<>();
		for (CustomPanelAPI row : rows(tooltips.get(lists(details).get(section)))) {
			for (Object card : children.get(row)) {
				cards.add((CustomPanelAPI) card);
			}
		}
		return cards;
	}

	private LabelAPI caption(CustomPanelAPI card) {
		var contents = tooltips.get(children.get(card).get(0)).getComponents();
		return (LabelAPI) contents.get(1);
	}

	private List<String> hover(CustomPanelAPI card) {
		var surface = tooltips.get(children.get(card).get(0)).getComponents().get(0);
		int before = labels.size();
		attachedTooltips.get(surface).createTooltip(tooltip(800f, 600f, false), false, null);
		return List.copyOf(labels.subList(before, labels.size()));
	}

	private void resetGeometryContext() throws Exception {
		var initialized = BattleNext_GeometryRenderer.class.getDeclaredField("initialized");
		initialized.setAccessible(true);
		initialized.setBoolean(null, false);
		var capacity = BattleNext_GeometryRenderer.class.getDeclaredField("geometryCapacity");
		capacity.setAccessible(true);
		capacity.setInt(null, 128);
	}

	private List<Integer> pixel(ByteBuffer pixels, int horizontal, int vertical) {
		int offset = (vertical * 384 + horizontal) * 4;
		return List.of(pixels.get(offset) & 255, pixels.get(offset + 1) & 255, pixels.get(offset + 2) & 255);
	}
}
