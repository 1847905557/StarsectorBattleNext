package data.test.fixture;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.scripts.ui.page.BattleNext_PageView;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionField;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_TestPageFixture extends BattleNext_TestUiFixture {

	protected void attachEditor(BattleNext_EditorView editor, CustomPanelAPI host) {
		editor.setOverlayHost(host);
		editor.attach(host);
	}

	protected List<BattleNext_OptionField> optionFields(BattleNext_EditorView editor) throws Exception {
		return ((List<?>) getField(editor, "tooltipWidgets")).stream()
				.filter(BattleNext_OptionField.class::isInstance).map(BattleNext_OptionField.class::cast).toList();
	}

	protected void confirmTournamentFleets(BattleNext_TournamentEditor editor, List<String> ids) throws Exception {
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(editor, "fleetPicker");
		CustomPanelAPI row = (CustomPanelAPI) getField(picker, "row");
		plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
		Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
		selection.clear();
		for (String id : ids) {
			selection.put(id, 1);
		}
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		plugins.get(popup).buttonPressed(buttonIds.get("CONFIRM"));
	}

	protected BattleNext_PinnedVariantPage page(BattleNext_PinnedVariantStore store,
			Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, Runnable back) throws Exception {
		var constructor = BattleNext_PinnedVariantPage.class.getDeclaredConstructor(Runnable.class,
				Consumer.class, BiConsumer.class, BattleNext_PinnedVariantStore.class);
		constructor.setAccessible(true);
		return constructor.newInstance(back, navigation, deletion, store);
	}

	protected Object getField(Object target, String name) throws Exception {
		Class<?> owner = target.getClass();
		while (Arrays.stream(owner.getDeclaredFields()).noneMatch(field -> field.getName().equals(name))) {
			owner = owner.getSuperclass();
		}
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	protected void assertJsonEquals(Object expected, Object actual) throws Exception {
		if (expected instanceof JSONObject object) {
			JSONObject other = (JSONObject) actual;
			assertEquals(object.length(), other.length());
			if (object.length() == 0) return;
			for (String key : JSONObject.getNames(object)) {
				assertTrue(other.has(key), key);
				assertJsonEquals(object.get(key), other.get(key));
			}
		} else if (expected instanceof JSONArray array) {
			JSONArray other = (JSONArray) actual;
			assertEquals(array.length(), other.length());
			for (int index = 0; index < array.length(); index++) {
				assertJsonEquals(array.get(index), other.get(index));
			}
		} else {
			assertEquals(expected, actual);
		}
	}

	public class ProbePage extends BattleNext_PageView {
		private final List<String> events = new ArrayList<>();
		private boolean dirty;

		public List<String> getEvents() {
			return events;
		}

		public void setDirty(boolean value) {
			dirty = value;
		}

		@Override
		protected void loadPageState() {
			events.add("load");
		}

		@Override
		protected void buildPage() {
			events.add("build");
			TooltipMakerAPI content = panel.createUIElement(100f, 20f, false);
			statusLabel = content.addPara("", 0f);
			panel.addUIElement(content).inTL(0f, 0f);
		}

		@Override
		protected void refreshPage() {
			assertTrue(inputRouter.isEmpty());
			events.add("refresh");
		}

		@Override
		protected void establishPageState() {
			events.add("baseline");
		}

		@Override
		protected boolean isPageDirty() {
			return dirty;
		}

		@Override
		protected boolean savePage() {
			setStatus("saved");
			dirty = false;
			return true;
		}

		@Override
		protected void discardPage() {
			dirty = false;
		}
	}

	public interface ChildrenHost extends UIPanelAPI {
		List<Object> getChildrenCopy();
	}

	public static class FailingStore extends BattleNext_PinnedVariantStore {
		private final FailingSaver writer = new FailingSaver();
		private boolean failDelete;
		private boolean failLoad;

		public FailingSaver getWriter() {
			return writer;
		}

		public void setFailDelete(boolean value) {
			failDelete = value;
		}

		public void setFailLoad(boolean value) {
			failLoad = value;
		}

		@Override
		public void reload() throws IOException, JSONException {
			if (failLoad) throw new IOException("Injected load failure");
			super.reload();
		}

		@Override
		public void deleteFile(String path) throws IOException, JSONException {
			if (failDelete) throw new IOException("Injected delete failure");
			super.deleteFile(path);
		}

		@Override
		protected BattleNext_PinnedVariantSaver saver() {
			return writer;
		}
	}

	public static class FailingSaver extends BattleNext_PinnedVariantSaver {
		private boolean fail = true;

		public void setFail(boolean value) {
			fail = value;
		}

		@Override
		public String save(BattleNext_PinnedVariantModel model) throws IOException, JSONException {
			if (fail) throw new IOException("Injected write failure");
			return super.save(model);
		}
	}

	public static class Core {
		private final Tab tab;
		public Core(Tab tab) {
			this.tab = tab;
		}
		public Tab getCurrentTab() {
			return tab;
		}
	}

	public static class Tab {
		private final List<Object> children;
		public Tab(List<Object> children) {
			this.children = children;
		}
		public List<Object> getChildrenCopy() {
			return children;
		}
	}

	public static class Refit {
		private FleetMemberAPI member;
		private final ShipVariantAPI variant;
		public Refit(FleetMemberAPI member, ShipVariantAPI variant) {
			this.member = member;
			this.variant = variant;
		}
		public void syncWithCurrentVariant() {

		}
		public FleetMemberAPI getMember() {
			return member;
		}

		public void setMember(FleetMemberAPI value) {
			member = value;
		}
		public Refit getShipDisplay() {
			return this;
		}
		public ShipVariantAPI getCurrentVariant() {
			return variant;
		}
	}

}
