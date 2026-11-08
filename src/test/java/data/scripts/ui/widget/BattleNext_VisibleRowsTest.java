package data.scripts.ui.widget;

import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

	@Test
	void jumpingMountsTheWholeNewViewportBeforeReleasingPreviousRows() {
		Set<Integer> mounted = new LinkedHashSet<>();
		AtomicBoolean jumped = new AtomicBoolean();
		try (VisibleRows rows = new VisibleRows(1000, index -> 20f, mounted::add, index -> {
			if (jumped.get()) assertTrue(mounted.containsAll(Set.of(500, 501, 502, 503, 504)));
			mounted.remove(index);
		}, ignored -> false)) {
			rows.update(0f, 100f);
			jumped.set(true);
			rows.update(10000f, 100f);
			jumped.set(false);
		}
		assertTrue(mounted.isEmpty());
	}

	@Test
	void measuringShorterRowsFillsTheViewportInTheSameUpdate() {
		Set<Integer> mounted = new LinkedHashSet<>();
		VisibleRows[] holder = new VisibleRows[1];
		try (VisibleRows rows = new VisibleRows(1000, index -> 40f, index -> {
			mounted.add(index);
			holder[0].setHeight(index, 10f);
		}, mounted::remove, ignored -> false)) {
			holder[0] = rows;
			rows.update(0f, 100f);
			assertTrue(mounted.containsAll(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
			assertEquals(10f, rows.top(1));
		}
		assertTrue(mounted.isEmpty());
	}
}
