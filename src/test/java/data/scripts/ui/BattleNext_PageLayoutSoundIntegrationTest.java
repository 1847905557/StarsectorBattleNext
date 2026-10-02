package data.scripts.ui;

import com.fs.starfarer.api.ui.ButtonAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleNext_PageLayoutSoundIntegrationTest {

	@Test
	void buttonsUseRegisteredVanillaHoverAndPressSounds() {
		AtomicReference<String> hover = new AtomicReference<>();
		AtomicReference<String> pressed = new AtomicReference<>();
		ButtonAPI button = (ButtonAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ButtonAPI.class}, (instance, method, args) -> {
					if ("setMouseOverSound".equals(method.getName())) hover.set((String) args[0]);
					if ("setButtonPressedSound".equals(method.getName())) pressed.set((String) args[0]);
					return null;
				});

		BattleNext_PageLayout.applyButtonSounds(button);

		assertEquals("ui_button_mouseover", BattleNext_UiTheme.BUTTON_HOVER_SOUND_ID);
		assertEquals("ui_button_pressed", BattleNext_UiTheme.BUTTON_PRESSED_SOUND_ID);
		assertEquals(BattleNext_UiTheme.BUTTON_HOVER_SOUND_ID, hover.get());
		assertEquals(BattleNext_UiTheme.BUTTON_PRESSED_SOUND_ID, pressed.get());
	}
}
