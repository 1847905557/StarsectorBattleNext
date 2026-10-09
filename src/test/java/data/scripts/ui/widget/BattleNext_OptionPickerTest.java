package data.scripts.ui.widget;

import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BattleNext_OptionPickerTest extends BattleNext_TestGameFixture {

	@Test
	void textTilesWrapMeasuredWidthsAndEllipsizeOnlyBeyondThreeLines() {
		assertEquals("Short", BattleNext_OptionPicker.tileLabel("Short", value -> value.length() * 10d));
		assertEquals("Ship\nlimit", BattleNext_OptionPicker.tileLabel("Ship limit", value -> value.length() * 10d));
		assertEquals("Same\nhull\nlimit", BattleNext_OptionPicker.tileLabel("Same hull limit", value -> value.length() * 10d));
		assertEquals("Same\nhull\nlim...", BattleNext_OptionPicker.tileLabel("Same hull limit extra", value -> value.length() * 10d));
		assertEquals("Weapon\nblacklist", BattleNext_OptionPicker.tileLabel("Weapon blacklist",
				value -> value.equals("Weapon") ? 65d : value.length() * 10d));
		assertEquals("Aggres\nsive", BattleNext_OptionPicker.tileLabel("Aggressive", value -> value.length() * 10d));
		assertEquals("慎重\n型", BattleNext_OptionPicker.tileLabel("慎重型", value -> value.length() * 24d));
		assertEquals("ABCDEF\nGHIJKL\nMNOPQR", BattleNext_OptionPicker.tileLabel("ABCDEFGHIJKLMNOPQR", value -> value.length() * 10d));
		assertEquals("ABCDEF\nGHIJKL\nMNO...", BattleNext_OptionPicker.tileLabel("ABCDEFGHIJKLMNOPQRS", value -> value.length() * 10d));
		assertEquals("A😀\nB😀\nC😀", BattleNext_OptionPicker.tileLabel("A😀B😀C😀", value -> value.codePointCount(0, value.length()) * 24d));
		assertEquals("A😀\nB😀\nC...", BattleNext_OptionPicker.tileLabel("A😀B😀C😀D", value -> value.codePoints().map(code -> code == '.' ? 6 : 24).sum()));
		assertEquals("WWiiii", BattleNext_OptionPicker.tileLabel("WWiiii", value -> value.chars().map(character -> character == 'W' ? 20 : 3).sum()));
		assertEquals("A B C", BattleNext_OptionPicker.tileLabel("  A  B\nC  ", value -> value.length() * 10d));
	}
}
