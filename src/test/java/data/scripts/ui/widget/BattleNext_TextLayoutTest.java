package data.scripts.ui.widget;

import data.scripts.ui.widget.BattleNext_TextField.TextLayout;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_TextLayoutTest {

	@Test
	void longRepeatedTextMeasuresDistinctPairsAndLocatesWithoutRemeasuring() {
		AtomicInteger measurements = new AtomicInteger();
		TextLayout layout = new TextLayout("a".repeat(8000), text -> {
			measurements.incrementAndGet();
			return text.length() * 7d;
		});
		assertEquals(56000f, layout.width());
		assertEquals(2, measurements.get());
		for (int offset = 0; offset <= 8000; offset++) assertEquals(offset, layout.nearest(offset * 7f));
		assertEquals(2, measurements.get());
	}

	@Test
	void caretAndDeletionRespectCombiningCharactersAndSupplementaryCodePoints() {
		TextLayout layout = new TextLayout("中e\u0301😀文", text -> text.codePointCount(0, text.length()) * 10d);
		assertEquals(1, layout.previous(3));
		assertEquals(3, layout.next(1));
		assertEquals(5, layout.next(3));
		assertEquals(3, layout.previous(5));
		assertEquals(0, layout.nearest(-50f));
		assertEquals(6, layout.nearest(1000f));
	}
}
