package data.scripts.ui;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.json.JSONObject;
import org.lwjgl.input.Keyboard;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

class BattleNext_TitleEntryTest {

	@Test
	void normalAndHoveredEntriesShareFixedBoundsWithDifferentTints() {
		assertEquals(96f, BattleNext_TitleEntry.ENTRY_WIDTH);
		assertEquals(24f, BattleNext_TitleEntry.ENTRY_HEIGHT);
		assertEquals(8f, BattleNext_TitleEntry.ENTRY_BORDER_WIDTH);
		assertTrue(BattleNext_TitleEntry.containsEntry(5f, 133f));
		assertTrue(BattleNext_TitleEntry.containsEntry(117f, 173f));
		assertFalse(BattleNext_TitleEntry.containsEntry(118f, 150f));
		assertFalse(BattleNext_TitleEntry.containsEntry(30f, 174f));
		assertSame(Color.GRAY, BattleNext_TitleEntry.iconTint(false, Color.GRAY, Color.CYAN));
		assertSame(Color.CYAN, BattleNext_TitleEntry.iconTint(true, Color.GRAY, Color.CYAN));
	}

	@Test
	void registeredIconPathMatchesTheExistingWideAsset() throws Exception {
		JSONObject settings = new JSONObject(Files.readString(Path.of("data/config/settings.json"),
				StandardCharsets.UTF_8));
		String path = settings.getJSONObject("graphics").getJSONObject("icon").getString("BattleNext_Icon");
		assertEquals("graphics/ui/BattleNext_Icon.png", path);
		assertTrue(Files.isRegularFile(Path.of(path)));
		BufferedImage icon = ImageIO.read(Path.of(path).toFile());
		assertEquals(128, icon.getWidth());
		assertEquals(32, icon.getHeight());
	}

	@Test
	void bothStatesRenderTheSameSpriteAndRestoreItsSharedState() {
		AtomicReference<Color> color = new AtomicReference<>(Color.WHITE);
		AtomicReference<Float> width = new AtomicReference<>(128f);
		AtomicReference<Float> height = new AtomicReference<>(32f);
		List<Color> renderedColors = new ArrayList<>();
		SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SpriteAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getColor" -> color.get();
					case "getWidth" -> width.get();
					case "getHeight" -> height.get();
					case "setColor" -> { color.set((Color) args[0]); yield null; }
					case "setSize" -> {
						width.set((Float) args[0]);
						height.set((Float) args[1]);
						yield null;
					}
					case "render" -> {
						assertEquals(13f, (Float) args[0]);
						assertEquals(141f, (Float) args[1]);
						assertEquals(96f, width.get());
						assertEquals(24f, height.get());
						renderedColors.add(color.get());
						yield null;
					}
					default -> null;
				});

		BattleNext_TitleEntry.renderIcon(sprite, false, 13f, 141f, Color.GRAY, Color.CYAN);
		BattleNext_TitleEntry.renderIcon(sprite, true, 13f, 141f, Color.GRAY, Color.CYAN);

		assertEquals(List.of(Color.GRAY, Color.CYAN), renderedColors);
		assertSame(Color.WHITE, color.get());
		assertEquals(128f, width.get());
		assertEquals(32f, height.get());
	}

	@Test
	void missionRefitRequiresTheMissionProviderAndDialogType() {
		assertTrue(BattleNext_TitleEntry.matchesMissionRefit(
				"com.fs.starfarer.title.Object.ooOO", true));
		assertFalse(BattleNext_TitleEntry.matchesMissionRefit(
				"com.fs.starfarer.title.dev.StockVariantEditorPlugin", true));
		assertFalse(BattleNext_TitleEntry.matchesMissionRefit(null, true));
		assertFalse(BattleNext_TitleEntry.matchesMissionRefit(
				"com.fs.starfarer.title.Object.ooOO", false));
	}

	@Test
	void missionListClickIsDetectedBeforeTheTitleUiConsumesIt() {
		InputEventAPI inside = mouseEvent("isLMBDownEvent", new AtomicBoolean());
		InputEventAPI outside = mouseEvent("isLMBDownEvent", new AtomicBoolean());
		AtomicBoolean consumed = new AtomicBoolean(true);
		InputEventAPI alreadyConsumed = mouseEvent("isLMBDownEvent", consumed);
		PositionAPI bounds = (PositionAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PositionAPI.class}, (instance, method, args) ->
						"containsEvent".equals(method.getName()) && args[0] == inside);

		assertTrue(BattleNext_TitleEntry.isMissionListClick(bounds, List.of(outside, inside)));
		assertFalse(BattleNext_TitleEntry.isMissionListClick(bounds, List.of(outside, alreadyConsumed)));
		assertFalse(BattleNext_TitleEntry.isMissionListClick(bounds, List.of()));
	}

	@Test
	void obfuscatedMissionRefitEntryPointsExistInTheTargetGame() throws Exception {
		try (ZipFile game = new ZipFile(Path.of("../../starsector-core/starfarer_obf.jar").toFile())) {
			assertTrue(game.getEntry("com/fs/starfarer/title/Object/E.class") != null);
			assertTrue(game.getEntry("com/fs/starfarer/coreui/returnnew.class") != null);
			assertTrue(game.getEntry("com/fs/starfarer/title/Object/ooOO.class") != null);
			assertTrue(game.getEntry("com/fs/starfarer/ui/oo0O.class") != null);
			assertTrue(game.getEntry("com/fs/starfarer/coreui/refit/void.class") != null);
			assertTrue(game.getEntry("com/fs/starfarer/coreui/refit/U.class") != null);
			String tabData = new String(game.getInputStream(
					game.getEntry("com/fs/starfarer/ui/null$o.class")).readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(tabData.contains("\u00d200000"));
			String refitScreen = new String(game.getInputStream(
					game.getEntry("com/fs/starfarer/coreui/refit/U.class")).readAllBytes(), StandardCharsets.UTF_8);
			String shipDisplay = new String(game.getInputStream(
					game.getEntry("com/fs/starfarer/coreui/refit/oOOO.class")).readAllBytes(), StandardCharsets.UTF_8);
			String titleState = new String(game.getInputStream(
					game.getEntry("com/fs/starfarer/title/TitleScreenState.class")).readAllBytes(), StandardCharsets.ISO_8859_1);
			String titleDialog = new String(game.getInputStream(
					game.getEntry("com/fs/starfarer/ui/oo0O.class")).readAllBytes(), StandardCharsets.ISO_8859_1);
			assertTrue(refitScreen.contains("getShipDisplay"));
			assertTrue(refitScreen.contains("saveCurrentVariant"));
			assertTrue(refitScreen.contains("syncWithCurrentVariant"));
			assertTrue(shipDisplay.contains("getCurrentVariant"));
			assertTrue(shipDisplay.contains("syncWithVariant"));
			assertTrue(titleState.contains("showMessageDialog"));
			assertTrue(titleState.contains("dialogType"));
			assertTrue(titleDialog.contains("dismiss"));
			assertTrue(titleDialog.contains("makeOptionInstant"));
			assertTrue(titleDialog.contains("getInterceptor"));
			String missionWidget = new String(game.getInputStream(
					game.getEntry("com/fs/starfarer/title/Object/E.class")).readAllBytes(), StandardCharsets.ISO_8859_1);
			assertTrue(missionWidget.contains("getMissionList"));
			assertTrue(missionWidget.contains("getMissionDetail"));
			assertTrue(missionWidget.contains("getFader"));
		}
	}

	@Test
	void emptyPanelConsumesInputAndCloseInvokesItsOwner() {
		AtomicBoolean closed = new AtomicBoolean();
		AtomicBoolean consumed = new AtomicBoolean();
		InputEventAPI event = (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{InputEventAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isConsumed" -> consumed.get();
					case "consume" -> { consumed.set(true); yield null; }
					default -> null;
				});
		BattleNext_EmptyPanelPlugin plugin = new BattleNext_EmptyPanelPlugin(() -> closed.set(true));

		plugin.processInput(List.of(event));
		assertTrue(consumed.get());
		plugin.buttonPressed("other");
		assertFalse(closed.get());
		plugin.buttonPressed("close");
		assertTrue(closed.get());
	}

	@Test
	void titleInputBeforeRefitPanelCreationKeepsTheEntryAvailable() throws Exception {
		SettingsAPI previous = Global.getSettings();
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) ->
						"getCurrentState".equals(method.getName()) ? GameState.TITLE : null);
		CustomPanelAPI configuration = (CustomPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CustomPanelAPI.class}, (instance, method, args) -> null);
		BattleNext_TitleEntry entry = new BattleNext_TitleEntry();
		Field field = BattleNext_TitleEntry.class.getDeclaredField("configurationPanel");
		field.setAccessible(true);
		field.set(entry, configuration);
		Field displayed = BattleNext_TitleEntry.class.getDeclaredField("displayedPanel");
		displayed.setAccessible(true);
		Global.setSettings(settings);
		try {
			assertDoesNotThrow(() -> entry.processInputPreCoreControls(0f, List.of()));
			displayed.set(entry, configuration);
			assertDoesNotThrow(() -> entry.processInputPreCoreControls(0f, List.of()));
		} finally {
			Global.setSettings(previous);
		}
	}

	@Test
	void displayedPanelGoesToTopAndBlocksOutsideMouseEvents() throws Exception {
		SettingsAPI previous = Global.getSettings();
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) ->
						"getCurrentState".equals(method.getName()) ? GameState.TITLE : null);
		AtomicBoolean outsideConsumed = new AtomicBoolean();
		AtomicBoolean insideConsumed = new AtomicBoolean();
		InputEventAPI outside = mouseEvent("isMouseDownEvent", outsideConsumed);
		InputEventAPI inside = mouseEvent("isMouseDownEvent", insideConsumed);
		PositionAPI bounds = (PositionAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PositionAPI.class}, (instance, method, args) ->
						"containsEvent".equals(method.getName()) && args[0] == inside);
		CustomPanelAPI panel = (CustomPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CustomPanelAPI.class}, (instance, method, args) ->
						"getPosition".equals(method.getName()) ? bounds : null);
		List<String> order = new ArrayList<>();
		UIPanelAPI screen = (UIPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{UIPanelAPI.class}, (instance, method, args) -> {
					if ("bringComponentToTop".equals(method.getName())) {
						assertSame(panel, args[0]);
						order.add("top");
					}
					return null;
				});
		BattleNext_TitleEntry entry = new BattleNext_TitleEntry();
		setField(entry, "configurationPanel", panel);
		setField(entry, "displayedPanel", panel);
		setField(entry, "screenPanel", screen);
		Global.setSettings(settings);
		try {
			entry.processInputPreCoreControls(0f, List.of(outside, inside));
		} finally {
			Global.setSettings(previous);
		}
		assertEquals(List.of("top"), order);
		assertTrue(outsideConsumed.get());
		assertFalse(insideConsumed.get());
	}

	@Test
	void outsideGateConsumesPressReleaseMoveAndWheelWhileLeavingKeyboardAlone() {
		InputEventAPI keyboard = (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{InputEventAPI.class}, (instance, method, args) ->
						"isKeyboardEvent".equals(method.getName()));
		PositionAPI outside = (PositionAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PositionAPI.class}, (instance, method, args) -> false);
		for (String kind : List.of("isMouseDownEvent", "isMouseUpEvent", "isMouseMoveEvent", "isMouseScrollEvent")) {
			AtomicBoolean consumed = new AtomicBoolean();
			BattleNext_TitleEntry.consumeOutsideMouseInput(outside, List.of(mouseEvent(kind, consumed)));
			assertTrue(consumed.get(), kind);
		}
		BattleNext_TitleEntry.consumeOutsideMouseInput(outside, List.of(keyboard));
	}

	@Test
	void refitAndEmptyPanelsUseTheSameOutsideGate() throws Exception {
		SettingsAPI previous = Global.getSettings();
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) ->
						"getCurrentState".equals(method.getName()) ? GameState.TITLE : null);
		PositionAPI bounds = (PositionAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PositionAPI.class}, (instance, method, args) -> false);
		CustomPanelAPI configuration = (CustomPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CustomPanelAPI.class}, (instance, method, args) -> null);
		UIPanelAPI screen = (UIPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{UIPanelAPI.class}, (instance, method, args) -> null);
		Global.setSettings(settings);
		try {
			for (boolean refit : new boolean[]{false, true}) {
				AtomicBoolean consumed = new AtomicBoolean();
				Object plugin = refit ? new BattleNext_RefitPanelPlugin(() -> { })
						: new BattleNext_EmptyPanelPlugin(() -> { });
				CustomPanelAPI displayed = (CustomPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class<?>[]{CustomPanelAPI.class}, (instance, method, args) -> switch (method.getName()) {
							case "getPosition" -> bounds;
							case "getPlugin" -> plugin;
							default -> null;
						});
				BattleNext_TitleEntry entry = new BattleNext_TitleEntry();
				setField(entry, "configurationPanel", configuration);
				setField(entry, "displayedPanel", displayed);
				setField(entry, "screenPanel", screen);
				if (refit) setField(entry, "refitPanel", displayed);
				entry.processInputPreCoreControls(0f, List.of(mouseEvent("isMouseDownEvent", consumed)));
				assertTrue(consumed.get());
			}
		} finally {
			Global.setSettings(previous);
		}
	}

	@Test
	void refitCloseReturnsRemainingMouseInputInTheSameFrame() throws Exception {
		SettingsAPI previous = Global.getSettings();
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) ->
						"getCurrentState".equals(method.getName()) ? GameState.TITLE : null);
		BattleNext_TitleEntry entry = new BattleNext_TitleEntry();
		BattleNext_RefitPanelPlugin plugin = new BattleNext_RefitPanelPlugin(() -> {
			try {
				setField(entry, "displayedPanel", null);
			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
		});
		CustomPanelAPI configuration = (CustomPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CustomPanelAPI.class}, (instance, method, args) -> null);
		CustomPanelAPI refit = (CustomPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CustomPanelAPI.class}, (instance, method, args) ->
						"getPlugin".equals(method.getName()) ? plugin : null);
		UIPanelAPI screen = (UIPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{UIPanelAPI.class}, (instance, method, args) -> null);
		setField(entry, "configurationPanel", configuration);
		setField(entry, "refitPanel", refit);
		setField(entry, "displayedPanel", refit);
		setField(entry, "screenPanel", screen);
		AtomicBoolean escapeConsumed = new AtomicBoolean();
		InputEventAPI escape = (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{InputEventAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isConsumed" -> escapeConsumed.get();
					case "isKeyboardEvent", "isKeyDownEvent" -> true;
					case "getEventValue" -> Keyboard.KEY_ESCAPE;
					case "consume" -> { escapeConsumed.set(true); yield null; }
					default -> false;
				});
		AtomicBoolean mouseConsumed = new AtomicBoolean();
		Global.setSettings(settings);
		try {
			entry.processInputPreCoreControls(0f, List.of(escape, mouseEvent("isMouseDownEvent", mouseConsumed)));
		} finally {
			Global.setSettings(previous);
		}
		assertTrue(escapeConsumed.get());
		assertFalse(mouseConsumed.get());
	}

	private InputEventAPI mouseEvent(String kind, AtomicBoolean consumed) {
		return (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{InputEventAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isConsumed" -> consumed.get();
					case "isMouseEvent" -> true;
					case "consume" -> { consumed.set(true); yield null; }
					default -> method.getName().equals(kind);
				});
	}

	private void setField(Object owner, String name, Object value) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(owner, value);
	}

	@Test
	void cleanupBeforeAnyPanelIsDisplayedDoesNotRequireMouseInput() {
		BattleNext_TitleEntry entry = new BattleNext_TitleEntry();

		assertDoesNotThrow(() -> entry.advance(0f, List.of()));
	}

}
