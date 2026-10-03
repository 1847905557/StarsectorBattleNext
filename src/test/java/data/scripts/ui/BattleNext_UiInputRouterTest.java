package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_UiInputRouterTest {

	@Test
	void topOverlayReceivesInputBeforeUnderlyingOverlay() {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		AtomicInteger calls = new AtomicInteger();
		TestOverlay parent = new TestOverlay(calls, false);
		TestOverlay child = new TestOverlay(calls, true);

		router.push(parent);
		router.push(child);

		assertTrue(router.handleGlobalInput(List.of()));
		assertEquals(1, calls.get());
		assertTrue(router.isTop(child));
	}

	@Test
	void clearingRouterClosesAllOverlaysAndRemovesTopState() {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		AtomicBoolean parentClosed = new AtomicBoolean();
		AtomicBoolean childClosed = new AtomicBoolean();
		TestOverlay parent = new TestOverlay(new AtomicInteger(), false, parentClosed);
		TestOverlay child = new TestOverlay(new AtomicInteger(), false, childClosed);

		router.push(parent);
		router.push(child);
		router.clear();

		assertTrue(parentClosed.get());
		assertTrue(childClosed.get());
		assertTrue(router.isEmpty());
		assertFalse(router.isTop(child));
	}

	private static final class TestOverlay implements BattleNext_UiInputRouter.Overlay {
		private final AtomicInteger calls;
		private final boolean handled;
		private final AtomicBoolean closed;

		private TestOverlay(AtomicInteger calls, boolean handled) {
			this(calls, handled, new AtomicBoolean());
		}

		private TestOverlay(AtomicInteger calls, boolean handled, AtomicBoolean closed) {
			this.calls = calls;
			this.handled = handled;
			this.closed = closed;
		}

		@Override
		public PositionAPI getBounds() {
			return null;
		}

		@Override
		public boolean handleGlobalInput(List<InputEventAPI> events) {
			calls.incrementAndGet();
			return handled;
		}

		@Override
		public void close() {
			closed.set(true);
		}
	}
}
