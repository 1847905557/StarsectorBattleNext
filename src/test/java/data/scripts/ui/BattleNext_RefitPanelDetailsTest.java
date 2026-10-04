package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.codehaus.janino.SimpleCompiler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_RefitPanelDetailsTest extends BattleNext_TestVariantFixture {
	private int panelsBeforeFailure = -1;

	@ParameterizedTest
	@ValueSource(ints = {800, 1000, 1400})
	void siblingPanelsUseAgreedProportionsAndIndependentPlugins(int width) throws Exception {
		deferTooltipWidth = true;
		BattleNext_RefitPanelPlugin plugin = initialized(width, 700f, () -> {});
		CustomPanelAPI root = (CustomPanelAPI) getField(plugin, "panel");
		CustomPanelAPI left = (CustomPanelAPI) getField(plugin, "leftPanel");
		CustomPanelAPI right = (CustomPanelAPI) getField(plugin, "rightPanel");
		assertEquals(List.of(left, right), children.get(root).stream().filter(CustomPanelAPI.class::isInstance).toList());
		assertSame(root.getPosition(), geometry.get(left.getPosition()).getParent());
		assertSame(root.getPosition(), geometry.get(right.getPosition()).getParent());
		assertNotSame(plugins.get(root), plugins.get(left));
		assertNotSame(plugins.get(left), plugins.get(right));
		assertEquals(width * 0.60f, left.getPosition().getWidth());
		assertEquals(width * 0.18f, right.getPosition().getWidth());
		assertEquals(560f, left.getPosition().getHeight());
		assertEquals(560f, right.getPosition().getHeight());
		assertEquals(width * 0.10f, left.getPosition().getX());
		assertEquals(width * 0.02f, right.getPosition().getX() - left.getPosition().getX()
				- left.getPosition().getWidth(), 0.0001f);
		assertEquals(width * 0.10f, width - right.getPosition().getX() - right.getPosition().getWidth(), 0.0001f);
		assertEquals(70f, root.getPosition().getY() + 700f - left.getPosition().getY() - 560f);
		assertEquals(left.getPosition().getY(), right.getPosition().getY());
		assertSame(getField(plugin, "inputRouter"), getField(details(plugin), "inputRouter"));
		for (String key : List.of("sModPicker", "dModPicker")) {
			BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(plugin, key);
			PositionAPI button = ((CustomPanelAPI) getField(picker, "row")).getPosition();
			assertSame(root, getField(picker, "overlayHost"));
			assertEquals(width * 0.18f, button.getWidth());
			assertEquals(30f, button.getHeight());
			assertEquals(right.getPosition().getX(), button.getX());
			assertEquals(key.equals("sModPicker") ? 0f : 36f,
					right.getPosition().getY() + 560f - button.getY() - 30f);
			assertEquals(key.equals("sModPicker") ? "BattleNext_refit_s_mods" : "BattleNext_refit_d_mods",
					getField(picker, "title"));
		}
		assertEquals(width - 8f - 160f, buttonPositions.get("close").getX());
		assertEquals(width - 8f - 330f, buttonPositions.get("save").getX());
		assertEquals(root.getPosition().getY() + 8f, buttonPositions.get("save").getY());
		assertEquals(160f, buttonPositions.get("save").getWidth());
		assertEquals(BattleNext_PageLayout.FOOTER_HEIGHT, buttonPositions.get("save").getHeight());
		PositionAPI pin = buttonPositions.get("pin_add");
		PositionAPI pinName = ((TextFieldAPI) getField(plugin, "pinName")).getPosition();
		assertEquals(right.getPosition().getX(), pin.getX());
		assertEquals(right.getPosition().getY(), pin.getY());
		assertEquals(right.getPosition().getWidth(), pin.getWidth(), 0.001f);
		assertEquals(30f, pin.getHeight());
		assertEquals(pin.getX(), pinName.getX());
		assertEquals(pin.getWidth(), pinName.getWidth());
		assertEquals(28f, pinName.getHeight());
		assertEquals(6f, pinName.getY() - pin.getY() - pin.getHeight());
		TooltipMakerAPI nameHost = (TooltipMakerAPI) getField(plugin, "pinNameHost");
		LabelAPI caption = (LabelAPI) tooltips.get(nameHost).getComponents().get(0);
		assertEquals("BattleNext_variant_pin_name", caption.getText());
		assertEquals("small-insignia", labelFonts.get(caption.getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(caption.getPosition()));
		assertEquals(pinName.getX(), caption.getPosition().getX());
		assertEquals(pinName.getWidth(), caption.getPosition().getWidth());
		assertEquals(pinName.getY() + pinName.getHeight(), caption.getPosition().getY());
		assertEquals(caption.getPosition().getHeight() + pinName.getHeight(), nameHost.getHeightSoFar());
		assertTrue(children.get(right).contains(nameHost));
		assertEquals("BattleNext_variant_pin_add_action", buttonLabels.get("pin_add"));
		assertFalse(buttons.get("pin_add").get());
		plugin.close();
	}

	@Test
	void pinActionUsesNameAboveButtonAndSavesCompleteUncommittedDraft() throws Exception {

		DraftVariant current = current();
		current.api.addMod("ordinary");
		String original = BattleNext_VariantBuilder.fromVariant(current.api).toJson().toString();
		BattleNext_RefitPanelPlugin plugin = opened(current);
		TextFieldAPI name = (TextFieldAPI) getField(plugin, "pinName");
		assertEquals("Parent - Unsubmitted", name.getText());
		assertTrue(buttons.get("pin_add").get());
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built", "ordinary"));
		invoke(plugin, "selectDMods", List.class, List.of("d"));
		ShipVariantAPI draft = (ShipVariantAPI) getField(plugin, "draft");
		JSONObject expected = BattleNext_VariantBuilder.fromVariant(draft).toJson();
		name.setText("  My current draft  ");
		CustomPanelAPI right = (CustomPanelAPI) getField(plugin, "rightPanel");
		plugins.get(right).buttonPressed("pin_add");
		BattleNext_PinnedVariantStore store = (BattleNext_PinnedVariantStore) getField(plugin, "pinnedStore");
		assertEquals(1, store.getAll().size());
		var pin = store.getAll().get(0);
		assertEquals("My current draft", pin.getDisplayName());
		assertJsonEquals(expected, pin.getVariantJson());
		assertEquals(7, pin.getVariantJson().getJSONObject("modules").getJSONObject("module").getInt("fluxVents"));
		assertTrue(Files.exists(directory.resolve(pin.getSourcePath())));
		assertEquals(original, BattleNext_VariantBuilder.fromVariant(current.api).toJson().toString());
		assertSame(draft, getField(plugin, "draft"));
		assertFalse(buttons.get("pin_add").get());
		assertTrue(status(plugin).startsWith("BattleNext_variant_pin_saved"));
		plugin.buttonPressed("pin_add");
		assertEquals(1, store.getAll().size());
		BattleNext_VariantSourceProvider source = new BattleNext_VariantSourceProvider();
		assertTrue(source.getOptions().stream().anyMatch(option -> option.getId().equals("pinned:" + pin.getPinId())));
		assertEquals("parent_default_D", source.resolve("pinned:" + pin.getPinId()).getHullSpec().getHullId());
		plugin.close();
	}

	@Test
	void changedNamesAndDraftsAllowNewPinsWhileUnchangedRepeatedClicksStaySingle() throws Exception {

		DraftVariant current = current();
		current.api.addMod("ordinary");
		BattleNext_RefitPanelPlugin plugin = opened(current);
		TextFieldAPI name = (TextFieldAPI) getField(plugin, "pinName");
		plugin.buttonPressed("pin_add");
		BattleNext_PinnedVariantStore store = (BattleNext_PinnedVariantStore) getField(plugin, "pinnedStore");
		String firstId = store.getAll().get(0).getPinId();
		name.setText("Second name");
		plugin.advance(0f);
		assertTrue(buttons.get("pin_add").get());
		plugin.buttonPressed("pin_add");
		assertEquals(2, store.getAll().size());
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built", "ordinary"));
		plugin.advance(0f);
		assertTrue(buttons.get("pin_add").get());
		plugin.buttonPressed("pin_add");
		assertEquals(3, store.getAll().size());
		assertEquals("Second name", store.getAll().get(2).getDisplayName());
		assertFalse(store.get(firstId).getVariantJson().getJSONArray("sMods").toString().contains("ordinary"));
		assertTrue(store.getAll().get(2).getVariantJson().getJSONArray("sMods").toString().contains("ordinary"));
		assertFalse(buttons.get("pin_add").get());
		plugin.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "\t\r\n", "\u3000", "\u2003"})
	void blankNameAndWriteFailurePreserveInputAndDraftForRetry(String blank) throws Exception {

		BattleNext_RefitPanelPlugin plugin = opened(current());
		TextFieldAPI name = (TextFieldAPI) getField(plugin, "pinName");
		ShipVariantAPI draft = (ShipVariantAPI) getField(plugin, "draft");
		name.setText(blank);
		plugin.buttonPressed("pin_add");
		assertEquals("BattleNext_variant_pin_name_required", status(plugin));
		assertEquals(blank, name.getText());
		assertTrue(buttons.get("pin_add").get());
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		name.setText("Retry this draft");
		FailingStore store = new FailingStore();
		setField(plugin, "pinnedStore", store);
		plugin.buttonPressed("pin_add");
		assertTrue(store.getAll().isEmpty());
		assertEquals("Retry this draft", name.getText());
		assertSame(draft, getField(plugin, "draft"));
		assertTrue(buttons.get("pin_add").get());
		assertTrue(status(plugin).startsWith("BattleNext_common_save_failed"));
		store.getWriter().setFail(false);
		plugin.buttonPressed("pin_add");
		assertEquals(1, store.getAll().size());
		assertEquals("Retry this draft", store.getAll().get(0).getDisplayName());
		plugin.close();
	}

	@Test
	void pinInputRespectsModalOwnershipAndReleasesOnCloseAndReopen() throws Exception {

		DraftVariant current = current();
		current.api.addMod("ordinary");
		BattleNext_RefitPanelPlugin plugin = opened(current);
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(plugin, "inputRouter");
		TextFieldAPI name = (TextFieldAPI) getField(plugin, "pinName");
		name.setText("Keep my name");
		name.grabFocus(false);
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(plugin, "sModPicker");
		invoke(picker, "open");
		plugin.advance(0f);
		assertFalse(name.hasFocus());
		assertFalse(buttons.get("pin_add").get());
		plugin.buttonPressed("pin_add");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		picker.closeOverlays();
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built", "ordinary"));
		plugin.requestClose();
		assertFalse(buttons.get("pin_add").get());
		plugin.buttonPressed("pin_add");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		((BattleNext_ConfirmationDialog) getField(plugin, "confirmation")).handle("confirm_cancel");
		plugin.advance(0f);
		assertTrue(buttons.get("pin_add").get());
		assertEquals("Keep my name", name.getText());
		TooltipMakerAPI previousHost = (TooltipMakerAPI) getField(plugin, "pinNameHost");
		CustomPanelAPI right = (CustomPanelAPI) getField(plugin, "rightPanel");
		name.grabFocus(false);
		plugin.close();
		assertFalse(name.hasFocus());
		assertFalse(children.get(right).contains(previousHost));
		assertTrue(removedComponents.contains(previousHost));
		assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
		assertFalse(buttons.get("pin_add").get());
		plugin.close();
		plugin.open(member(current), new Refit(member(current), current.api));
		assertEquals("Parent - Unsubmitted", ((TextFieldAPI) getField(plugin, "pinName")).getText());
		TooltipMakerAPI reopenedHost = (TooltipMakerAPI) getField(plugin, "pinNameHost");
		assertNotSame(previousHost, reopenedHost);
		assertTrue(children.get(right).contains(reopenedHost));
		LabelAPI caption = (LabelAPI) tooltips.get(reopenedHost).getComponents().get(0);
		assertEquals("BattleNext_variant_pin_name", caption.getText());
		assertEquals(1, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		assertTrue(buttons.get("pin_add").get());
		plugin.close();
	}

	@Test
	void openingClonesCompleteDraftAndDisplaysFiveRegions() throws Exception {
		DraftVariant current = current();
		current.api.addMod("ordinary");
		current.api.addPermaMod("d", false);
		current.api.removeSuppressedMod("d");
		BattleNext_RefitPanelPlugin plugin = opened(current);
		ShipVariantAPI draft = (ShipVariantAPI) getField(plugin, "draft");
		assertNotSame(current.api, draft);
		JSONObject snapshot = (JSONObject) getField(details(plugin), "variantJson");
		assertJsonEquals(BattleNext_VariantBuilder.fromVariant(draft).toJson(), snapshot);
		assertEquals(Set.of("identity", "plugins", "modules", "groups", "wings"), lists(plugin).keySet());
		assertEquals(5, children.get(details(plugin).getPanel()).size());
		for (String text : List.of("Hullmod ordinary", "Hullmod sm", "Hullmod built", "Hullmod d",
				"BattleNext_variant_s_mod", "BattleNext_variant_d_mod", "Child", "Wing wing")) {
			assertTrue(labels.contains(text), text);
		}
		assertEquals("LINKED", snapshot.getJSONArray("weaponGroups").getJSONObject(0).getString("mode"));
		assertEquals(7, snapshot.getJSONObject("modules").getJSONObject("module").getInt("fluxVents"));
		assertTrue(labels.stream().anyMatch(text -> text.contains("Unsubmitted")));
		toggle(plugin).run();
		assertTrue(labels.contains("weapon: Weapon laser"));
		plugin.close();
	}

	@Test
	void selectorsConfirmAndCancelWithoutChangingOriginalAndReleaseSearchInput() throws Exception {
		DraftVariant current = current();
		current.api.addMod("ordinary");
		BattleNext_RefitPanelPlugin plugin = opened(current);
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(plugin, "inputRouter");
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(plugin, "sModPicker");
		CustomPanelAPI root = (CustomPanelAPI) getField(plugin, "panel");
		int mounted = children.get(root).size();
		int registered = ((Map<?, ?>) getField(router.getSession(), "registrations")).size();
		for (int iteration = 0; iteration < 3; iteration++) {
			invoke(picker, "open");
			assertFalse(router.isEmpty());
			assertSame(root.getPosition(), geometry.get(((CustomPanelAPI) getField(picker, "popup")).getPosition()).getParent());
			assertEquals(registered + 1, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
			Map<String, Integer> selection = selection(picker);
			selection.put("ordinary", 1);
			picker.closeOverlays();
			assertEquals(Set.of("sm", "built"), BattleNext_RefitPanelPlugin.sModsOf((ShipVariantAPI) getField(plugin, "draft")));
			assertEquals(mounted, children.get(root).size());
			assertTrue(router.isEmpty());
			assertEquals(registered, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		}
		invoke(picker, "open");
		selection(picker).put("ordinary", 1);
		invoke(picker, "confirm");
		assertEquals(Set.of("ordinary", "sm", "built"), BattleNext_RefitPanelPlugin.sModsOf((ShipVariantAPI) getField(plugin, "draft")));
		assertEquals(Set.of("sm", "built"), BattleNext_RefitPanelPlugin.sModsOf(current.api));
		assertTrue(router.isEmpty());
		assertEquals(registered, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		assertJsonEquals(BattleNext_VariantBuilder.fromVariant((ShipVariantAPI) getField(plugin, "draft")).toJson(),
				getField(details(plugin), "variantJson"));
		BattleNext_OptionPicker dPicker = (BattleNext_OptionPicker) getField(plugin, "dModPicker");
		invoke(dPicker, "open");
		selection(dPicker).put("d", 1);
		dPicker.closeOverlays();
		assertTrue(BattleNext_RefitPanelPlugin.dModsOf((ShipVariantAPI) getField(plugin, "draft")).isEmpty());
		invoke(dPicker, "open");
		selection(dPicker).put("d", 1);
		invoke(dPicker, "confirm");
		assertEquals("parent_default_D", ((JSONObject) getField(details(plugin), "variantJson")).getString("hullId"));
		assertTrue(labels.contains("Damaged parent"));
		assertEquals("parent", current.api.getHullSpec().getHullId());
		invoke(dPicker, "open");
		selection(dPicker).clear();
		invoke(dPicker, "confirm");
		assertEquals("parent", ((JSONObject) getField(details(plugin), "variantJson")).getString("hullId"));
		plugin.close();
	}

	@Test
	void sameOpeningRefreshRetainsScrollAndExpansionAndClampsShorterContent() throws Exception {
		JSONObject equipment = equipmentJson();
		current();
		DraftVariant current = new DraftVariant(equipment);
		BattleNext_RefitPanelPlugin plugin = opened(current);
		toggle(plugin).run();
		Map<String, Float> offsets = new LinkedHashMap<>();
		for (String key : List.of("plugins", "modules", "groups", "wings")) {
			TooltipMakerAPI list = lists(plugin).get(key);
			list.getExternalScroller().setYOffset(80f);
			offsets.put(key, list.getExternalScroller().getYOffset());
			assertTrue(offsets.get(key) > 0f, key);
		}
		List<Object> previousFrames = new ArrayList<>(children.get(details(plugin).getPanel()));
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built", "mod_0_with_a_long_name"));
		assertEquals(Set.of(0), getField(details(plugin), "expanded"));
		assertTrue(removedComponents.containsAll(previousFrames));
		assertEquals(6, ((List<?>) getField(details(plugin), "ownedPanels")).size());
		for (String key : offsets.keySet()) {
			assertEquals(offsets.get(key), lists(plugin).get(key).getExternalScroller().getYOffset());
		}
		DraftVariant shorter = current();
		setField(plugin, "draft", shorter.api);
		invoke(plugin, "refreshDetails");
		assertEquals(Set.of(0), getField(details(plugin), "expanded"));
		for (String key : offsets.keySet()) {
			TooltipState state = tooltips.get(lists(plugin).get(key));
			assertEquals(Math.min(offsets.get(key), Math.max(0f, state.getMountedHeight() - state.getViewport().getHeight())),
					state.getScroller().getYOffset());
		}
		plugin.close();
	}

	@Test
	void modalCancellationKeepsDraftAndRestoresDetailsInteraction() throws Exception {
		AtomicBoolean closed = new AtomicBoolean();
		DraftVariant current = current();
		current.api.addMod("ordinary");
		BattleNext_RefitPanelPlugin plugin = initialized(1000f, 700f, () -> closed.set(true));
		plugin.open(member(current), new Refit(member(current), current.api));
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built", "ordinary"));
		ShipVariantAPI draft = (ShipVariantAPI) getField(plugin, "draft");
		Runnable toggle = toggle(plugin);
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(plugin, "sModPicker");
		invoke(picker, "open");
		toggle.run();
		assertEquals(Set.of(), getField(details(plugin), "expanded"));
		picker.closeOverlays();
		plugin.requestClose();
		BattleNext_ConfirmationDialog dialog = (BattleNext_ConfirmationDialog) getField(plugin, "confirmation");
		assertTrue(dialog.isActive());
		assertFalse(buttons.get("save").get());
		toggle.run();
		assertEquals(Set.of(), getField(details(plugin), "expanded"));
		dialog.handle("confirm_cancel");
		plugin.advance(0f);
		assertFalse(closed.get());
		assertSame(draft, getField(plugin, "draft"));
		assertTrue(buttons.get("save").get());
		toggle.run();
		assertEquals(Set.of(0), getField(details(plugin), "expanded"));
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built"));
		assertEquals(Set.of("sm", "built"), BattleNext_RefitPanelPlugin.sModsOf(draft));
		plugin.requestClose();
		assertTrue(closed.get());
		plugin.close();
	}

	@Test
	void closeAndReopenReleasePanelsAndResetBrowsingState() throws Exception {
		JSONObject equipment = equipmentJson();
		current();
		DraftVariant current = new DraftVariant(equipment);
		BattleNext_RefitPanelPlugin plugin = opened(current);
		CustomPanelAPI root = (CustomPanelAPI) getField(plugin, "panel");
		CustomPanelAPI left = (CustomPanelAPI) getField(plugin, "leftPanel");
		int mounted = children.get(root).size();
		for (int iteration = 0; iteration < 3; iteration++) {
			toggle(plugin).run();
			lists(plugin).get("groups").getExternalScroller().setYOffset(80f);
			List<Object> previous = new ArrayList<>(children.get(left));
			invoke(getField(plugin, "sModPicker"), "open");
			plugin.close();
			assertTrue(children.get(left).isEmpty());
			assertTrue(removedComponents.containsAll(previous));
			assertNull(details(plugin).getPanel());
			assertNull(getField(details(plugin), "variantJson"));
			assertTrue(lists(plugin).isEmpty());
			assertEquals(Set.of(), getField(details(plugin), "expanded"));
			assertTrue(((List<?>) getField(details(plugin), "ownedPanels")).isEmpty());
			BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(plugin, "inputRouter");
			assertTrue(router.isEmpty());
			assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
			assertEquals(mounted, children.get(root).size());
			plugin.open(member(current), new Refit(member(current), current.api));
			assertEquals(5, children.get(left).size());
			assertEquals(Set.of(), getField(details(plugin), "expanded"));
			assertEquals(0f, lists(plugin).get("groups").getExternalScroller().getYOffset());
		}
		plugin.close();
	}

	@Test
	void snapshotAndBuildErrorsReleaseDetailsAndAllowRecovery() throws Exception {
		DraftVariant current = current();
		BattleNext_RefitPanelPlugin plugin = opened(current);
		CustomPanelAPI left = (CustomPanelAPI) getField(plugin, "leftPanel");
		failBuild = true;
		invoke(plugin, "refreshDetails");
		assertTrue(children.get(left).isEmpty());
		assertNull(details(plugin).getPanel());
		assertTrue(lists(plugin).isEmpty());
		assertTrue(status(plugin).contains("Unknown hull"));
		failBuild = false;
		invoke(plugin, "refreshDetails");
		assertEquals(5, children.get(left).size());
		panelsBeforeFailure = 2;
		invoke(plugin, "refreshDetails");
		assertTrue(children.get(left).isEmpty());
		assertNull(details(plugin).getPanel());
		assertTrue(((List<?>) getField(details(plugin), "ownedPanels")).isEmpty());
		assertTrue(status(plugin).contains("Panel build failure"));
		panelsBeforeFailure = -1;
		invoke(plugin, "refreshDetails");
		ShipVariantAPI draft = (ShipVariantAPI) getField(plugin, "draft");
		draft.setModuleVariant("module", null);
		invoke(plugin, "refreshDetails");
		assertTrue(children.get(left).isEmpty());
		assertNull(getField(details(plugin), "variantJson"));
		assertTrue(((List<?>) getField(details(plugin), "ownedPanels")).isEmpty());
		assertTrue(status(plugin).startsWith("BattleNext_common_save_failed:"));
		plugin.open(member(current), new Refit(member(current), current.api));
		assertEquals(5, children.get(left).size());
		plugin.close();
	}

	@Test
	void readFailureClearsPreviousDetailsAndReportsStatus() throws Exception {
		DraftVariant current = current();
		BattleNext_RefitPanelPlugin plugin = opened(current);
		CustomPanelAPI left = (CustomPanelAPI) getField(plugin, "leftPanel");
		plugin.open(member(current), new FailingRefit());
		assertTrue(children.get(left).isEmpty());
		assertNull(getField(plugin, "draft"));
		assertNull(getField(details(plugin), "variantJson"));
		assertEquals("BattleNext_common_save_failed: InvocationTargetException", status(plugin));
		plugin.open(member(current), new Refit(member(current), current.api));
		setField(plugin, "refitScreen", new FailingRefit());
		plugin.discardCurrentPage();
		assertTrue(children.get(left).isEmpty());
		assertNull(details(plugin).getPanel());
		assertEquals("BattleNext_common_save_failed: InvocationTargetException", status(plugin));
		plugin.close();
	}

	@Test
	void saveRefreshRetainsBrowsingStateAndEstablishesNewDraftBaseline() throws Exception {
		JSONObject equipment = equipmentJson();
		current();
		DraftVariant current = new DraftVariant(equipment);
		AtomicBoolean saved = new AtomicBoolean();
		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_RefitPanelPlugin plugin = initialized(1000f, 700f, () -> closed.set(true));
		plugin.open(member(current), savingRefit(current, () -> saved.set(true), new AtomicBoolean()));
		toggle(plugin).run();
		lists(plugin).get("groups").getExternalScroller().setYOffset(80f);
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built", "mod_0_with_a_long_name"));
		ShipVariantAPI draft = (ShipVariantAPI) getField(plugin, "draft");
		assertTrue(plugin.saveCurrentPage());
		assertTrue(saved.get());
		assertNotSame(draft, getField(plugin, "draft"));
		assertEquals(Set.of("sm", "built", "mod_0_with_a_long_name"), BattleNext_RefitPanelPlugin.sModsOf(current.api));
		assertEquals(Set.of(0), getField(details(plugin), "expanded"));
		assertEquals(80f, lists(plugin).get("groups").getExternalScroller().getYOffset());
		assertEquals("BattleNext_common_saved", status(plugin));
		assertEquals("", plugin.getSaveError());
		assertJsonEquals(BattleNext_VariantBuilder.fromVariant((ShipVariantAPI) getField(plugin, "draft")).toJson(),
				getField(details(plugin), "variantJson"));
		assertEquals(getField(plugin, "savedSMods"), BattleNext_RefitPanelPlugin.sModsOf((ShipVariantAPI) getField(plugin, "draft")));
		invoke(plugin, "selectSMods", List.class, List.of("sm", "built"));
		plugin.requestClose();
		BattleNext_ConfirmationDialog dialog = (BattleNext_ConfirmationDialog) getField(plugin, "confirmation");
		assertTrue(dialog.isActive());
		dialog.handle("confirm_save");
		assertFalse(dialog.isActive());
		assertTrue(closed.get());
		assertEquals(Set.of("sm", "built"), BattleNext_RefitPanelPlugin.sModsOf(current.api));
		plugin.close();
	}

	@Test
	void committedSaveKeepsSavedStatusWhenDetailRefreshFails() throws Exception {
		DraftVariant current = current();
		current.api.addMod("ordinary");
		BattleNext_RefitPanelPlugin plugin = initialized(1000f, 700f, () -> {});
		plugin.open(member(current), savingRefit(current, () -> failBuild = true, new AtomicBoolean()));
		invoke(plugin, "selectSMods", List.class, List.of("ordinary", "sm", "built"));
		assertTrue(plugin.saveCurrentPage());
		assertEquals("BattleNext_common_saved", status(plugin));
		assertEquals("", plugin.getSaveError());
		assertEquals(Set.of("ordinary", "sm", "built"), BattleNext_RefitPanelPlugin.sModsOf(current.api));
		assertNull(details(plugin).getPanel());
		assertNull(getField(details(plugin), "variantJson"));
		assertTrue(lists(plugin).isEmpty());
		assertTrue(((List<?>) getField(details(plugin), "ownedPanels")).isEmpty());
		assertTrue(children.get((CustomPanelAPI) getField(plugin, "leftPanel")).isEmpty());
		plugin.close();
	}

	@Test
	void committedSaveReadFailureReleasesOldDetailsAndKeepsSavedBaseline() throws Exception {
		DraftVariant current = current();
		current.api.addMod("ordinary");
		AtomicBoolean failRead = new AtomicBoolean();
		BattleNext_RefitPanelPlugin plugin = initialized(1000f, 700f, () -> {});
		plugin.open(member(current), savingRefit(current, () -> failRead.set(true), failRead));
		invoke(plugin, "selectSMods", List.class, List.of("ordinary", "sm", "built"));
		assertTrue(plugin.saveCurrentPage());
		assertEquals("BattleNext_common_saved", status(plugin));
		assertEquals("", plugin.getSaveError());
		assertEquals(Set.of("ordinary", "sm", "built"), getField(plugin, "savedSMods"));
		assertNull(details(plugin).getPanel());
		assertTrue(lists(plugin).isEmpty());
		assertTrue(((List<?>) getField(details(plugin), "ownedPanels")).isEmpty());
		assertTrue(children.get((CustomPanelAPI) getField(plugin, "leftPanel")).isEmpty());
		plugin.close();
	}

	@Test
	void saveFailureKeepsDraftAndBrowsingStateAndAllowsConfirmationCancellation() throws Exception {
		DraftVariant current = current();
		current.api.addMod("ordinary");
		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_RefitPanelPlugin plugin = initialized(1000f, 700f, () -> closed.set(true));
		Object refit = savingRefit(current, () -> { throw new IllegalStateException("Commit failure"); }, new AtomicBoolean());
		plugin.open(member(current), refit);
		invoke(plugin, "selectSMods", List.class, List.of("ordinary", "sm", "built"));
		ShipVariantAPI draft = (ShipVariantAPI) getField(plugin, "draft");
		toggle(plugin).run();
		JSONObject snapshot = (JSONObject) getField(details(plugin), "variantJson");
		plugin.requestClose();
		BattleNext_ConfirmationDialog dialog = (BattleNext_ConfirmationDialog) getField(plugin, "confirmation");
		dialog.handle("confirm_save");
		assertTrue(dialog.isActive());
		assertFalse(closed.get());
		assertEquals("Commit failure", plugin.getSaveError());
		assertSame(draft, getField(plugin, "draft"));
		assertSame(snapshot, getField(details(plugin), "variantJson"));
		assertEquals(Set.of(0), getField(details(plugin), "expanded"));
		assertEquals(Set.of("ordinary", "sm", "built"), BattleNext_RefitPanelPlugin.sModsOf(draft));
		ShipVariantAPI restored = (ShipVariantAPI) refit.getClass().getMethod("getCurrentVariant").invoke(refit);
		assertEquals(Set.of("sm", "built"), BattleNext_RefitPanelPlugin.sModsOf(restored));
		dialog.handle("confirm_cancel");
		plugin.advance(0f);
		assertTrue(buttons.get("save").get());
		toggle(plugin).run();
		assertEquals(Set.of(), getField(details(plugin), "expanded"));
		plugin.close();
	}

	private Object savingRefit(DraftVariant current, Runnable afterSave, AtomicBoolean failRead) throws Exception {

		SimpleCompiler compiler = new SimpleCompiler();
		compiler.setParentClassLoader(current.api.getClass().getClassLoader());
		compiler.cook("public class RefitDisplay {"
				+ " public com.fs.starfarer.api.combat.ShipVariantAPI variant;"
				+ " public Runnable afterSave;"
				+ " public java.util.concurrent.atomic.AtomicBoolean failRead;"
				+ " public RefitDisplay(com.fs.starfarer.api.combat.ShipVariantAPI variant, Runnable afterSave,"
				+ " java.util.concurrent.atomic.AtomicBoolean failRead) {"
				+ " this.variant = variant; this.afterSave = afterSave; this.failRead = failRead; }"
				+ " public RefitDisplay getShipDisplay() { return this; }"
				+ " public com.fs.starfarer.api.combat.ShipVariantAPI getCurrentVariant() {"
				+ " if (failRead.get()) throw new IllegalStateException(\"Read failure\"); return variant; }"
				+ " public void syncWithVariant(" + current.api.getClass().getName() + " variant) { this.variant = variant; }"
				+ " public void syncWithCurrentVariant() {}"
				+ " public void saveCurrentVariant() { afterSave.run(); } }");
		return compiler.getClassLoader().loadClass("RefitDisplay")
				.getConstructor(ShipVariantAPI.class, Runnable.class, AtomicBoolean.class)
				.newInstance(current.api, afterSave, failRead);
	}

	@Override
	protected CustomPanelAPI panel(float width, float height, CustomUIPanelPlugin plugin) {
		if (panelsBeforeFailure == 0) throw new IllegalStateException("Panel build failure");
		if (panelsBeforeFailure > 0) {
			panelsBeforeFailure--;
		}
		return super.panel(width, height, plugin);
	}

	private BattleNext_RefitPanelPlugin initialized(float width, float height, Runnable close) {
		BattleNext_RefitPanelPlugin plugin = new BattleNext_RefitPanelPlugin(close);
		plugin.init(panel(width, height, plugin), width, height);
		return plugin;
	}

	private BattleNext_RefitPanelPlugin opened(DraftVariant current) {
		BattleNext_RefitPanelPlugin plugin = initialized(1000f, 700f, () -> {});
		FleetMemberAPI member = member(current);
		plugin.open(member, new Refit(member, current.api));
		return plugin;
	}

	private FleetMemberAPI member(DraftVariant current) {
		return proxy(FleetMemberAPI.class, (method, args) -> "getVariant".equals(method) ? current.api : null);
	}

	private BattleNext_VariantDetails details(BattleNext_RefitPanelPlugin plugin) throws Exception {
		return (BattleNext_VariantDetails) getField(plugin, "details");
	}

	private String status(BattleNext_RefitPanelPlugin plugin) throws Exception {
		return ((LabelAPI) getField(plugin, "statusLabel")).getText();
	}

	private Runnable toggle(BattleNext_RefitPanelPlugin plugin) throws Exception {
		TooltipState groups = tooltips.get(lists(plugin).get("groups"));
		CustomPanelAPI row = (CustomPanelAPI) groups.getComponents().get(0);
		CustomPanelAPI header = (CustomPanelAPI) children.get(row).get(0);
		return (Runnable) getField(plugins.get(header), "onToggle");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Integer> selection(BattleNext_OptionPicker picker) throws Exception {
		return (Map<String, Integer>) getField(picker, "selection");
	}

	private void invoke(Object target, String name) throws Exception {
		Method method = target.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(target);
	}

	private void invoke(Object target, String name, Class<?> type, Object argument) throws Exception {
		Method method = target.getClass().getDeclaredMethod(name, type);
		method.setAccessible(true);
		method.invoke(target, argument);
	}

	private DraftVariant current() throws Exception {

		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		ShipHullSpecAPI parentTemplate = hulls.get("parent");
		ShipHullSpecAPI parent = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> "parent";
			case "getHullName" -> "Parent";
			case "getSpriteName" -> "graphics/parent.png";
			case "getBuiltInMods" -> List.of("built");
			case "isBuiltInMod" -> "built".equals(args[0]);
			case "getBuiltInWings" -> List.of();
			case "getBuiltInWeapons" -> parentTemplate.getBuiltInWeapons();
			case "getHints" -> parentTemplate.getHints();
			case "getWeaponSlot", "getWeaponSlotAPI" -> parentTemplate.getWeaponSlot((String) args[0]);
			default -> null;
		});
		hulls.put("parent", parent);
		moduleSlots.put(parent, moduleSlots.get(parentTemplate));
		ShipHullSpecAPI damaged = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> "parent_default_D";
			case "getHullName" -> "Damaged parent";
			case "getSpriteName" -> "graphics/parent.png";
			case "isDHull", "isDefaultDHull" -> true;
			case "getDParentHull", "getBaseHull" -> parent;
			case "getBuiltInMods" -> List.of("built");
			case "isBuiltInMod" -> "built".equals(args[0]);
			case "getBuiltInWings" -> List.of();
			case "getBuiltInWeapons" -> parentTemplate.getBuiltInWeapons();
			case "getHints" -> parentTemplate.getHints();
			case "getWeaponSlot", "getWeaponSlotAPI" -> parentTemplate.getWeaponSlot((String) args[0]);
			default -> null;
		});
		hulls.put("parent_default_D", damaged);
		moduleSlots.put(damaged, moduleSlots.get(parentTemplate));
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if ("getHullModSpec".equals(method.getName())) return hullmod((String) args[0]);
			if ("getAllHullModSpecs".equals(method.getName())) return List.of(hullmod("d"));
			try {
				return method.invoke(previous, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		return new DraftVariant(json);
	}

	private HullModSpecAPI hullmod(String id) {
		return proxy(HullModSpecAPI.class, (method, args) -> switch (method) {
			case "getId" -> id;
			case "getDisplayName" -> "Hullmod " + id;
			case "hasTag" -> "d".equals(id) && "dmod".equals(args[0]);
			default -> null;
		});
	}

	public static class FailingRefit {
		public Object getShipDisplay() {
			throw new IllegalStateException("Read failure");
		}
	}

	private class DraftVariant {
		private final JSONObject json;
		private final Map<String, ShipVariantAPI> modules = new LinkedHashMap<>();
		private final Set<String> mods;
		private final Set<String> permaMods;
		private final LinkedHashSet<String> sMods;
		private final LinkedHashSet<String> builtIns;
		private final LinkedHashSet<String> suppressed;
		private ShipHullSpecAPI hull;
		private final ShipVariantAPI api;

		private DraftVariant(JSONObject json) throws JSONException {

			this.json = new JSONObject(json.toString());
			hull = hulls.get(json.getString("hullId"));
			mods = values(json.optJSONArray("hullMods"));
			permaMods = values(json.optJSONArray("permaMods"));
			sMods = values(json.optJSONArray("sMods"));
			builtIns = values(json.optJSONArray("sModdedBuiltIns"));
			suppressed = values(json.optJSONArray("suppressedMods"));
			JSONObject moduleJson = json.optJSONObject("modules");
			if (moduleJson != null && moduleJson.length() > 0) {
				for (String slot : JSONObject.getNames(moduleJson)) {
					modules.put(slot, new DraftVariant(moduleJson.getJSONObject(slot)).api);
				}
			}
			api = (ShipVariantAPI) Proxy.newProxyInstance(ShipVariantAPI.class.getClassLoader(),
					new Class<?>[]{ShipVariantAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "equals" -> instance == args[0];
				case "hashCode" -> System.identityHashCode(instance);
				case "clone" -> new DraftVariant(snapshot()).api;
				case "toJSONObject" -> snapshot();
				case "getHullSpec" -> hull;
				case "setHullSpecAPI" -> { hull = (ShipHullSpecAPI) args[0]; yield null; }
				case "getHullVariantId" -> this.json.getString("variantId");
				case "getDisplayName" -> this.json.getString("displayName");
				case "getHullMods" -> {
					Set<String> installed = new LinkedHashSet<>(mods);
					installed.addAll(permaMods);
					installed.addAll(hull.getBuiltInMods());
					installed.removeAll(suppressed);
					yield installed;
				}
				case "getPermaMods" -> permaMods;
				case "getSMods" -> sMods;
				case "getSModdedBuiltIns" -> builtIns;
				case "getSuppressedMods" -> suppressed;
				case "getModuleSlots" -> new ArrayList<>(modules.keySet());
				case "getStationModules" -> {
					Map<String, String> ids = new LinkedHashMap<>();
					modules.forEach((slot, module) -> ids.put(slot, module == null ? "missing" : module.getHullVariantId()));
					yield ids;
				}
				case "getModuleVariant" -> modules.get(args[0]);
				case "setModuleVariant" -> modules.put((String) args[0], (ShipVariantAPI) args[1]);
				case "hasDMods" -> permaMods.contains("d");
				case "addMod" -> mods.add((String) args[0]);
				case "removeMod" -> mods.remove(args[0]);
				case "addSuppressedMod" -> suppressed.add((String) args[0]);
				case "removeSuppressedMod" -> suppressed.remove(args[0]);
				case "addPermaMod" -> {
					permaMods.add((String) args[0]);
					if (args.length > 1 && (Boolean) args[1]) sMods.add((String) args[0]);
					yield null;
				}
				case "removePermaMod" -> {
					permaMods.remove(args[0]);
					sMods.remove(args[0]);
					yield null;
				}
				default -> method.getReturnType() == boolean.class ? false : null;
			});
		}

		private JSONObject snapshot() throws JSONException {
			JSONObject snapshot = new JSONObject(json.toString()).put("hullId", hull.getHullId())
					.put("hullMods", new JSONArray(mods)).put("permaMods", new JSONArray(permaMods))
					.put("sMods", new JSONArray(sMods)).put("sModdedBuiltIns", new JSONArray(builtIns))
					.put("suppressedMods", new JSONArray(suppressed));
			JSONObject moduleJson = new JSONObject();
			for (var entry : modules.entrySet()) {
				if (entry.getValue() != null) moduleJson.put(entry.getKey(), entry.getValue().toJSONObject());
			}
			return snapshot.put("modules", moduleJson);
		}

		private LinkedHashSet<String> values(JSONArray array) throws JSONException {
			LinkedHashSet<String> values = new LinkedHashSet<>();
			if (array != null) {
				for (int index = 0; index < array.length(); index++) {
					values.add(array.getString(index));
				}
			}
			return values;
		}
	}
}
