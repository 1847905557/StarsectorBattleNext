package data.scripts.ui;

import com.fs.starfarer.api.ui.ButtonAPI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

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

		assertEquals("ui_button_mouseover", hover.get());
		assertEquals("ui_button_pressed", pressed.get());
	}
}
