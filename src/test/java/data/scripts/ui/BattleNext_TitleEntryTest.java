package data.scripts.ui;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
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
	void obfuscatedMissionRefitEntryPointsExistInTheTargetGame() throws Exception {
		try (ZipFile game = new ZipFile(Path.of("../../starsector-core/starfarer_obf.jar").toFile())) {
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
			assertTrue(refitScreen.contains("getShipDisplay"));
			assertTrue(refitScreen.contains("saveCurrentVariant"));
			assertTrue(refitScreen.contains("syncWithCurrentVariant"));
			assertTrue(shipDisplay.contains("getCurrentVariant"));
			assertTrue(shipDisplay.contains("syncWithVariant"));
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

}
