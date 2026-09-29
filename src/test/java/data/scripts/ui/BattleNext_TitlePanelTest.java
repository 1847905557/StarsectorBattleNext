package data.scripts.ui;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_TitlePanelTest {

	@Test
	void normalAndHoveredEntriesShareFixedBoundsWithDifferentTints() {
		assertEquals(96f, BattleNext_TitlePanel.ENTRY_WIDTH);
		assertEquals(24f, BattleNext_TitlePanel.ENTRY_HEIGHT);
		assertEquals(8f, BattleNext_TitlePanel.ENTRY_BORDER_WIDTH);
		assertTrue(BattleNext_TitlePanel.containsEntry(5f, 133f));
		assertTrue(BattleNext_TitlePanel.containsEntry(117f, 173f));
		assertFalse(BattleNext_TitlePanel.containsEntry(118f, 150f));
		assertFalse(BattleNext_TitlePanel.containsEntry(30f, 174f));
		assertSame(Color.GRAY, BattleNext_TitlePanel.iconTint(false, Color.GRAY, Color.CYAN));
		assertSame(Color.CYAN, BattleNext_TitlePanel.iconTint(true, Color.GRAY, Color.CYAN));
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

		BattleNext_TitlePanel.renderIcon(sprite, false, 13f, 141f, Color.GRAY, Color.CYAN);
		BattleNext_TitlePanel.renderIcon(sprite, true, 13f, 141f, Color.GRAY, Color.CYAN);

		assertEquals(List.of(Color.GRAY, Color.CYAN), renderedColors);
		assertSame(Color.WHITE, color.get());
		assertEquals(128f, width.get());
		assertEquals(32f, height.get());
	}
}
