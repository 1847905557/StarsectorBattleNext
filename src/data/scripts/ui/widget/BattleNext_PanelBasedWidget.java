package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;

public abstract class BattleNext_PanelBasedWidget {

	protected CustomPanelAPI panel;
	protected BattleNext_UiInputRouter inputRouter;

	private final List<OwnedPanel> ownedPanels = new ArrayList<>();

	protected BattleNext_PanelBasedWidget() {
		this(BattleNext_UiInputRouter.current());
	}

	protected BattleNext_PanelBasedWidget(BattleNext_UiInputRouter inputRouter) {
		this.inputRouter = inputRouter;
	}

	public final CustomPanelAPI attach(CustomPanelAPI panel) {
		close();
		this.panel = panel;
		clearPageState();
		bindInput();
		buildContent(panel);
		return panel;
	}

	public final CustomPanelAPI refreshContent() {
		if (panel == null) return null;
		closeOverlays();
		clearContent();
		clearOwnedPanels();
		buildContent(panel);
		return panel;
	}

	public final void close() {
		closeOverlays();
		clearContent();
		clearOwnedPanels();
		panel = null;
		clearPageState();
	}

	public final void closeOverlays() {
		clearOverlays();
	}

	public final boolean handleGlobalInput(List<InputEventAPI> events) {
		return handleWidgetInput(events);
	}

	public final CustomPanelAPI getPanel() {
		return panel;
	}

	protected final <T extends CustomPanelAPI> T ownPanel(UIPanelAPI parent, T child) {
		ownedPanels.add(new OwnedPanel(parent, child));
		return child;
	}

	protected final void releasePanel(CustomPanelAPI child) {
		for (int index = ownedPanels.size() - 1; index >= 0; index--) {
			OwnedPanel owned = ownedPanels.get(index);
			if (owned.child != child) continue;
			owned.parent.removeComponent(child);
			ownedPanels.remove(index);
			return;
		}
	}

	private void clearOwnedPanels() {
		while (!ownedPanels.isEmpty()) {
			releasePanel(ownedPanels.get(ownedPanels.size() - 1).child);
		}
	}

	private record OwnedPanel(UIPanelAPI parent, CustomPanelAPI child) {
	}

	protected void bindInput() {
	}

	protected void clearPageState() {
	}

	protected void clearContent() {
	}

	protected void clearOverlays() {
	}

	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		return false;
	}

	protected abstract void buildContent(CustomPanelAPI panel);

	public static final class VisibleRows implements AutoCloseable {
		private final float[] offsets;
		private final IntConsumer mount;
		private final IntConsumer release;
		private final IntPredicate keep;
		private final Set<Integer> mounted = new LinkedHashSet<>();

		public VisibleRows(int count, IntToDoubleFunction height, IntConsumer mount, IntConsumer release, IntPredicate keep) {
			offsets = new float[count + 1];
			for (int index = 0; index < count; index++) offsets[index + 1] = offsets[index] + (float) height.applyAsDouble(index);
			this.mount = mount;
			this.release = release;
			this.keep = keep;
		}

		public float top(int index) {
			return offsets[index];
		}

		public float height() {
			return offsets[offsets.length - 1];
		}

		public void setHeight(int row, float height) {
			float difference = height - (offsets[row + 1] - offsets[row]);
			for (int index = row + 1; index < offsets.length; index++) offsets[index] += difference;
		}

		public void update(float scroll, float viewportHeight) {
			int first = index(Math.max(0f, scroll - viewportHeight));
			int last = index(scroll + viewportHeight * 2f);
			for (int row : Set.copyOf(mounted)) {
				if ((row < first || row > last) && !keep.test(row)) {
					release.accept(row);
					mounted.remove(row);
				}
			}
			for (int row = first; row <= last && row < offsets.length - 1; row++) {
				if (mounted.add(row)) mount.accept(row);
			}
		}

		private int index(float y) {
			int index = Arrays.binarySearch(offsets, y);
			return Math.min(offsets.length - 1, index >= 0 ? index : Math.max(0, -index - 2));
		}

		@Override
		public void close() {
			for (int row : Set.copyOf(mounted)) release.accept(row);
			mounted.clear();
		}
	}
}
