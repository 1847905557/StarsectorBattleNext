package data.scripts.ui.widget;

import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_VisibleRowsTest {

	@Test
	void scrollingMountsNearbyRowsAndRetainsTheFocusedRowUntilItIsReleased() {
		Set<Integer> mounted = new LinkedHashSet<>();
		Set<Integer> focused = new LinkedHashSet<>();
		try (VisibleRows rows = new VisibleRows(1000, index -> 40f,
				mounted::add, mounted::remove, focused::contains)) {
			rows.update(0f, 400f);
			assertEquals(21, mounted.size());

			focused.add(0);
			rows.update(20000f, 400f);
			assertEquals(32, mounted.size());
			assertTrue(mounted.contains(0));
			assertTrue(mounted.contains(500));

			focused.clear();
			rows.update(20000f, 400f);
			assertEquals(31, mounted.size());

			rows.setHeight(0, 80f);
			assertEquals(40040f, rows.height());
			assertEquals(80f, rows.top(1));
		}
		assertTrue(mounted.isEmpty());
	}
}
