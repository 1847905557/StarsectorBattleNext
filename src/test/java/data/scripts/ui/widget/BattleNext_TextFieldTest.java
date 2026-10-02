package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import sun.misc.Unsafe;

import data.scripts.ui.BattleNext_UiInputRouter;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_TextFieldTest {
	private final List<String> sounds = new ArrayList<>();

	@AfterEach
	void clearFocus() {
		BattleNext_TextField.clearRegisteredFields();
	}

	@Test
	void visibleUnicodeAndSpacesPassWhileInvisibleInputIsRejected() {
		assertTrue(BattleNext_TextField.isAcceptedInputCodePoint('中'));
		assertTrue(BattleNext_TextField.isAcceptedInputCodePoint(' '));
		assertTrue(BattleNext_TextField.isAcceptedInputCodePoint(0x1f600));
		for (int codePoint : new int[]{0, '\n', '\t', 0x7f, 0x200b, 0x2028, 0x2029,
				'%', '$', Character.highSurrogate(0x1f600), Character.lowSurrogate(0x1f600)}) {
			assertFalse(BattleNext_TextField.isAcceptedInputCodePoint(codePoint));
		}
	}

	@Test
	void unsupportedControlShortcutsAreConsumedWithoutChangingText() throws Exception {
		BattleNext_TextField field = field();
		field.setText("abc");
		BattleNext_UiInputRouter.current().getSession().register(field);
		field.grabFocus();
		for (InputEventAPI event : List.of(key(Keyboard.KEY_B, 'b', true, false),
				key(Keyboard.KEY_LEFT, (char) 2, true, false),
				key(Keyboard.KEY_BACK, '\b', true, true))) {
			assertTrue(BattleNext_TextField.handleGlobalInput(List.of(event)));
			assertTrue(event.isConsumed());
			assertEquals("abc", field.getText());
		}
	}

	@Test
	void existingShortcutsAndOrdinaryEditingStillWork() throws Exception {
		BattleNext_TextField field = field();
		field.setText("abc");
		field.processKeyboard(key(Keyboard.KEY_A, (char) 1, true, false));
		field.processKeyboard(key(Keyboard.KEY_C, (char) 3, true, false));
		assertEquals("abc", field.getText());
		field.processKeyboard(key(Keyboard.KEY_X, (char) 24, true, false));
		assertEquals("", field.getText());
		field.processKeyboard(key(Keyboard.KEY_LEFT, (char) 0, false, false));
		field.processKeyboard(key(Keyboard.KEY_Z, 'z', false, false));
		assertEquals("z", field.getText());
		field.processKeyboard(key(Keyboard.KEY_BACK, '\b', false, false));
		assertEquals("", field.getText());
	}

	@Test
	void ctrlAltShortcutsAndDisabledPasteCannotInsertEventCharacters() throws Exception {
		BattleNext_TextField field = field();
		field.setText("abc");
		field.processKeyboard(key(Keyboard.KEY_A, (char) 1, true, true));
		field.processKeyboard(key(Keyboard.KEY_X, (char) 24, true, false));
		assertEquals("abc", field.getText());
		field.setHandleCtrlV(false);
		field.processKeyboard(key(Keyboard.KEY_V, (char) 22, true, false));
		assertEquals("abc", field.getText());
	}

	@Test
	void pasteSkipsInvalidCodePointsAndPreservesCompleteEmoji() throws Exception {
		BattleNext_TextField field = field();
		field.insertPastedText("A\n中\u200b\ud83d\ude00%$B\ud83d");
		assertEquals("A中😀B", field.getText());
		field.setText("");
		field.setMaxChars(3);
		field.insertPastedText("A😀B");
		assertEquals("A😀", field.getText());
	}

	@Test
	void splitEmojiInputAndCursorEditingKeepSurrogatePairIntact() throws Exception {
		BattleNext_TextField field = field();
		field.processKeyboard(key(Keyboard.KEY_1, '\ud83d', false, false));
		assertEquals("", field.getText());
		field.processKeyboard(key(Keyboard.KEY_2, '\ude00', false, false));
		assertEquals("😀", field.getText());
		field.processKeyboard(key(Keyboard.KEY_LEFT, (char) 0, false, false));
		field.processKeyboard(key(Keyboard.KEY_DELETE, (char) 0, false, false));
		assertEquals("", field.getText());
		field.setText("😀");
		field.processKeyboard(key(Keyboard.KEY_BACK, '\b', false, false));
		assertEquals("", field.getText());
	}

	@Test
	void interruptedSurrogateAndFocusChangeDiscardPendingInput() throws Exception {
		BattleNext_TextField field = field();
		field.processKeyboard(key(Keyboard.KEY_1, '\ud83d', false, false));
		field.processKeyboard(key(Keyboard.KEY_LEFT, (char) 0, false, false));
		field.processKeyboard(key(Keyboard.KEY_2, '\ude00', false, false));
		assertEquals("", field.getText());
		field.processKeyboard(key(Keyboard.KEY_1, '\ud83d', false, false));
		field.endFocus(true);
		field.processKeyboard(key(Keyboard.KEY_2, '\ude00', false, false));
		assertEquals("", field.getText());
	}

	@Test
	void focusClickAndProgrammaticEditsUseExpectedSounds() throws Exception {
		BattleNext_TextField field = field();
		field.setText("initial");
		assertTrue(sounds.isEmpty());
		field.grabFocus(false);
		assertTrue(sounds.isEmpty());
		field.grabFocus();
		assertEquals(List.of(BattleNext_TextField.BUZZ_SOUND_ID), sounds);
		BattleNext_UiInputRouter.current().getSession().register(field);
		BattleNext_TextField.handleGlobalInput(List.of(pointer()));
		BattleNext_TextField.handleGlobalInput(List.of(pointer()));
		assertEquals(List.of("ui_typer_buzz", "ui_typer_buzz", "ui_typer_buzz"), sounds);
		field.setText("changed");
		assertEquals(3, sounds.size());
	}

	@Test
	void typingDeletionAndEscapeReportAcceptedOrRejectedEdit() throws Exception {
		BattleNext_TextField field = field();
		field.setMaxChars(1);
		field.processKeyboard(key(Keyboard.KEY_A, 'a', false, false));
		field.processKeyboard(key(Keyboard.KEY_B, 'b', false, false));
		field.processKeyboard(key(Keyboard.KEY_5, '%', false, false));
		field.processKeyboard(key(Keyboard.KEY_BACK, '\b', false, false));
		field.processKeyboard(key(Keyboard.KEY_BACK, '\b', false, false));
		field.processKeyboard(key(Keyboard.KEY_DELETE, (char) 0, false, false));
		field.processKeyboard(key(Keyboard.KEY_ESCAPE, (char) 0, false, false));
		assertEquals(List.of("ui_typer_type", "ui_typer_buzz", "ui_typer_buzz",
				"ui_typer_type", "ui_typer_buzz", "ui_typer_buzz", "ui_typer_type"), sounds);
	}

	@Test
	void pastePlaysAtMostOneSoundPerOutcomeAndEmojiOnce() throws Exception {
		BattleNext_TextField field = field();
		field.insertPastedText("A\n😀B");
		assertEquals("A😀B", field.getText());
		assertEquals(List.of("ui_typer_type", "ui_typer_buzz"), sounds);
		sounds.clear();
		field.insertPastedText("XY");
		assertEquals(List.of("ui_typer_type"), sounds);
		sounds.clear();
		field.insertPastedText("");
		assertEquals(List.of("ui_typer_buzz"), sounds);
		sounds.clear();
		field.setMaxChars(field.getText().length());
		field.insertPastedText("Z");
		assertEquals(List.of("ui_typer_buzz"), sounds);
	}

	@Test
	void silentMethodsAndIgnoredShortcutsStaySilent() throws Exception {
		BattleNext_TextField field = field();
		field.setText("abc");
		assertTrue(field.appendCharIfPossible('d', false));
		assertFalse(field.appendCharIfPossible('%', false));
		field.deleteAll(false);
		field.processKeyboard(key(Keyboard.KEY_B, 'b', true, false));
		field.processKeyboard(key(Keyboard.KEY_LEFT, (char) 0, false, false));
		assertTrue(sounds.isEmpty());
		field.deleteAll();
		assertEquals(List.of("ui_typer_buzz"), sounds);
		sounds.clear();
		field.setText("two words");
		field.deleteLastWord();
		assertEquals(List.of("ui_typer_type"), sounds);
	}

	@Test
	void cutAndPublicDeleteMethodsRespectChangeAndSoundFlags() throws Exception {
		BattleNext_TextField field = field();
		field.setText("abc");
		field.processKeyboard(key(Keyboard.KEY_A, (char) 1, true, false));
		field.processKeyboard(key(Keyboard.KEY_C, (char) 3, true, false));
		assertTrue(sounds.isEmpty());
		field.processKeyboard(key(Keyboard.KEY_X, (char) 24, true, false));
		assertEquals("", field.getText());
		assertEquals(List.of("ui_typer_type"), sounds);
		sounds.clear();
		field.setText("text");
		field.deleteAll();
		assertEquals(List.of("ui_typer_type"), sounds);
		sounds.clear();
		assertTrue(field.appendCharIfPossible('q'));
		assertFalse(field.appendCharIfPossible('%'));
		assertEquals(List.of("ui_typer_type", "ui_typer_buzz"), sounds);
	}

	@Test
	void keyboardSurrogatePairProducesOneTypeSoundAndWidthRejectionBuzzes() throws Exception {
		BattleNext_TextField field = field();
		field.processKeyboard(key(Keyboard.KEY_1, '\ud83d', false, false));
		assertTrue(sounds.isEmpty());
		field.processKeyboard(key(Keyboard.KEY_2, '\ude00', false, false));
		assertEquals("😀", field.getText());
		assertEquals(List.of("ui_typer_type"), sounds);
		field.setText("");
		field.setLimitByStringWidth(true);
		field.setPad(150f);
		field.processKeyboard(key(Keyboard.KEY_A, 'a', false, false));
		assertEquals("", field.getText());
		assertEquals(List.of("ui_typer_type", "ui_typer_buzz"), sounds);
	}

	private BattleNext_TextField field() throws Exception {
		PositionAPI position = proxy(PositionAPI.class, (name, args) -> switch (name) {
			case "getX", "getY" -> 0f;
			case "getWidth" -> 300f;
			case "getHeight" -> 28f;
			case "containsEvent" -> true;
			default -> null;
		});
		LabelAPI label = proxy(LabelAPI.class, (name, args) -> switch (name) {
			case "computeTextWidth" -> ((String) args[0]).length() * 8f;
			case "getPosition" -> position;
			default -> null;
		});
		CustomPanelAPI panel = proxy(CustomPanelAPI.class, (name, args) -> switch (name) {
			case "getPosition" -> position;
			default -> null;
		});
		Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		Unsafe unsafe = (Unsafe) unsafeField.get(null);
		BattleNext_TextField field = (BattleNext_TextField) unsafe.allocateInstance(BattleNext_TextField.class);
		setField(field, "session", BattleNext_UiInputRouter.current().getSession());
		setField(field, "panel", panel);
		setField(field, "textLabel", label);
		setField(field, "width", 300f);
		setField(field, "height", 28f);
		setField(field, "pad", 3f);
		setField(field, "maxChars", -1);
		setField(field, "text", "");
		setField(field, "undoText", "");
		setField(field, "handleCtrlV", true);
		setField(field, "soundSink", (Consumer<String>) sounds::add);
		return field;
	}

	private InputEventAPI pointer() {
		AtomicBoolean consumed = new AtomicBoolean();
		return proxy(InputEventAPI.class, (name, args) -> switch (name) {
			case "isLMBDownEvent" -> true;
			case "isConsumed" -> consumed.get();
			case "getX" -> 8;
			case "getY" -> 8;
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			default -> null;
		});
	}

	private void setField(BattleNext_TextField field, String name, Object value) throws Exception {
		Field target = BattleNext_TextField.class.getDeclaredField(name);
		target.setAccessible(true);
		target.set(field, value);
	}

	private InputEventAPI key(int key, char character, boolean ctrl, boolean alt) {
		AtomicBoolean consumed = new AtomicBoolean();
		return proxy(InputEventAPI.class, (name, args) -> switch (name) {
			case "getEventValue" -> key;
			case "getEventChar" -> character;
			case "isCtrlDown" -> ctrl;
			case "isAltDown" -> alt;
			case "isKeyboardEvent", "isKeyDownEvent" -> true;
			case "isConsumed" -> consumed.get();
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			default -> null;
		});
	}

	private interface Call {
		Object invoke(String name, Object[] args);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> {
					if ("hashCode".equals(method.getName())) return System.identityHashCode(instance);
					if ("equals".equals(method.getName())) return instance == args[0];
					Object result = call.invoke(method.getName(), args);
					if (result == null && method.getReturnType() == boolean.class) return false;
					return result;
				}));
	}
}
