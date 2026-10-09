package data.test.fixture;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.page.BattleNext_PageView;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_TableWidget;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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

	protected void addFleetMembers(BattleNext_FleetEditor editor, List<String> ids) throws Exception {

		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
					if (method.getName().equals("getAllVariantIds")) return ids.stream().filter(id -> !id.startsWith("pinned:")).distinct().toList();
					if (method.getName().equals("getVariant") && ids.contains(args[0]) && registeredVariants.get(args[0]) == null) return source().getApi();
					try {
						return method.invoke(delegate, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}));
		try {
			editor.buttonPressed("add_member");
			BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(editor, "memberPicker");
			for (String id : ids) {
				clickPickerOption(picker, id);
			}
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			popup.getPlugin().buttonPressed(buttonIds.get("CONFIRM"));
		} finally {
			Global.setSettings(delegate);
		}
	}

	protected void clickPickerOption(BattleNext_OptionPicker picker, String id) throws Exception {
		CustomPanelAPI grid = (CustomPanelAPI) getField(picker, "gridPanel");
		TooltipMakerAPI content = (TooltipMakerAPI) children.get(grid).get(0);
		for (UIComponentAPI component : tooltips.get(content).getComponents()) {
			if (!(component instanceof CustomPanelAPI tile)) continue;
			var option = (BattleNext_OptionPicker.OptionChoice) getField(tile.getPlugin(), "option");
			if (!option.getId().equals(id)) continue;
			PositionAPI bounds = tile.getPosition();
			InputEventAPI event = proxy(InputEventAPI.class, (method, args) -> switch (method) {
				case "isLMBDownEvent" -> true;
				case "isConsumed" -> false;
				case "getX" -> (int) (bounds.getX() + bounds.getWidth() * 0.5f);
				case "getY" -> (int) (bounds.getY() + bounds.getHeight() * 0.5f);
				default -> null;
			});
			tile.getPlugin().processInput(List.of(event));
			return;
		}
		throw new AssertionError("Picker option missing: " + id);
	}

	protected BattleNext_OptionPicker openQuickBattleFleet(BattleNext_QuickBattleDisplay display, int index,
			BattleNext_QuickBattleDisplay.Column column) throws Exception {
		var row = display.getRows().get(index);
		BattleNext_OptionField field = ((Map<BattleNext_QuickBattleDisplay.Column, BattleNext_OptionField>) getField(row, "fleetFields")).get(column);
		if (field == null) return null;
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(field, "picker");
		((CustomPanelAPI) getField(picker, "row")).getPlugin().buttonPressed(buttonIds.get("TOGGLE"));
		return picker;
	}

	protected void selectQuickBattleFleet(BattleNext_QuickBattleDisplay display, int index,
			BattleNext_QuickBattleDisplay.Column column, String id) throws Exception {
		BattleNext_OptionPicker picker = openQuickBattleFleet(display, index, column);
		if (!id.isEmpty()) {
			CustomPanelAPI grid = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI content = (TooltipMakerAPI) children.get(grid).get(0);
			for (UIComponentAPI component : tooltips.get(content).getComponents()) {
				if (!(component instanceof CustomPanelAPI tile)) continue;
				var option = (BattleNext_OptionPicker.OptionChoice) getField(tile.getPlugin(), "option");
				if (!option.getId().equals(id)) continue;
				PositionAPI bounds = tile.getPosition();
				InputEventAPI event = proxy(InputEventAPI.class, (method, args) -> switch (method) {
					case "isLMBDownEvent" -> true;
					case "isConsumed" -> false;
					case "getX" -> (int) (bounds.getX() + bounds.getWidth() * 0.5f);
					case "getY" -> (int) (bounds.getY() + bounds.getHeight() * 0.5f);
					default -> null;
				});
				tile.getPlugin().processInput(List.of(event));
				break;
			}
		}
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		popup.getPlugin().buttonPressed(buttonIds.get("CONFIRM"));
	}

	protected void assertTablePickerAlignment(BattleNext_TableWidget table, BattleNext_OptionPicker picker) throws Exception {
		PositionAPI tableBounds = table.getPanel().getPosition();
		CustomPanelAPI pickerRow = (CustomPanelAPI) getField(picker, "row");
		ButtonAPI button = (ButtonAPI) getField(picker, "toggle");
		float left = tableBounds.getX();
		float rowWidth = tableBounds.getWidth();
		assertEquals(left, pickerRow.getPosition().getX(), 0.001f);
		assertEquals(left, button.getPosition().getX(), 0.001f);
		assertEquals(BattleNext_PageLayout.editorButtonWidth(rowWidth), button.getPosition().getWidth(), 0.001f);
		assertEquals(BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT, button.getPosition().getHeight(), 0.001f);
		assertTrue(button.getPosition().getX() + button.getPosition().getWidth()
				<= tableBounds.getX() + tableBounds.getWidth() + 0.001f);
		int rows = 0;
		for (UIComponentAPI component : tooltips.get(table.getContent()).getComponents()) {
			if (component == pickerRow) continue;
			assertEquals(left, component.getPosition().getX(), 0.001f);
			rows++;
		}
		PositionAPI contentBounds = table.getContent().getPosition();
		assertEquals(rows * BattleNext_PageLayout.TABLE_ROW_HEIGHT + BattleNext_PageLayout.EDITOR_BUTTON_GAP,
				contentBounds.getY() + contentBounds.getHeight() - pickerRow.getPosition().getY() - pickerRow.getPosition().getHeight(), 0.001f);
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
		while (true) {
			for (Field field : owner.getDeclaredFields()) {
				if (!field.getName().equals(name)) continue;
				field.setAccessible(true);
				return field.get(target);
			}
			owner = owner.getSuperclass();
		}
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
		} else if (expected instanceof Number number) {
			assertEquals(0, new BigDecimal(number.toString()).compareTo(new BigDecimal(((Number) actual).toString())));
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
