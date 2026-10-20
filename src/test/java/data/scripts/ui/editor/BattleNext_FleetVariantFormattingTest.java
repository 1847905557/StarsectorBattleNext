package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.io.BattleNext_FleetSaver;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.page.BattleNext_FleetLibraryPage;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_FleetVariantFormattingTest extends BattleNext_TestPageFixture {

	@Test
	void formattingCapturesDraftInputsRefreshesExpandedFieldsAndCommitsRenamedFilesOnRetry() throws Exception {

		AtomicBoolean failWrite = new AtomicBoolean();
		var store = new BattleNext_FleetStore() {
			private final BattleNext_FleetSaver writer = new BattleNext_FleetSaver() {
				@Override
				public String save(BattleNext_FleetModel fleet) throws IOException, JSONException {
					if (failWrite.get()) throw new IOException("Injected formatted fleet write failure");
					return super.save(fleet);
				}
			};

			@Override
			protected BattleNext_FleetSaver saver() {
				return writer;
			}
		};
		store.reload();
		var parent = source().getApi();
		var fleet = fleet(List.of("parent_variant", "parent_variant"));
		fleet.putVariant(BattleNext_VariantBuilder.fromVariant(parent));
		fleet.putVariant(BattleNext_VariantBuilder.fromVariant(parent.getModuleVariant("module")));
		fleet.putVariant(BattleNext_VariantBuilder.fromJson(new JSONObject().put("variantId", "extra").put("hullId", "child"), ""));
		store.save(fleet);
		Map<Path, String> stored = storedFiles();
		var editor = new BattleNext_FleetEditor(store);
		editor.open(fleet, false);
		attachEditor(editor, panel(1000f, 350f, new BaseCustomUIPanelPlugin()));
		editor.buttonPressed("toggle_member:0");
		assertFalse(editor.hasUnsavedChanges());
		field(editor, "variantPrefixField").setText("ctb");
		assertFalse(editor.hasUnsavedChanges());
		editor.refreshContent();
		assertEquals("ctb", field(editor, "variantPrefixField").getText());
		assertFalse(editor.hasUnsavedChanges());
		field(editor, "nameField").setText("Edited fleet");
		Object member = ((List<?>) getField(editor, "memberFields")).get(0);
		field(member, "nameField").setText("Edited member");
		field(member, "ordersField").setText("full_assault");
		Map<?, ?> viewports = (Map<?, ?>) getField(editor, "viewports");
		((TooltipMakerAPI) viewports.get("metadata")).getExternalScroller().setYOffset(15f);
		((TooltipMakerAPI) viewports.get("members")).getExternalScroller().setYOffset(25f);
		editor.buttonPressed("format_ids");
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_format_success"), 2, 2), editor.getStatusText());
		assertTrue(editor.hasUnsavedChanges());
		assertEquals(stored, storedFiles());
		var draft = (BattleNext_FleetModel) getField(editor, "draft");
		assertEquals(List.of("ctb_parent_1", "ctb_parent_1"), draft.getMembers().stream().map(BattleNext_MemberModel::getVariantId).toList());
		assertEquals("Edited member", draft.getMembers().get(0).getName());
		assertEquals("full_assault", draft.getMembers().get(0).getOrders());
		assertEquals("ctb_parent_1", field(((List<?>) getField(editor, "memberFields")).get(0), "variantField").getText());
		assertEquals(Set.of(0), getField(editor, "expandedMembers"));
		viewports = (Map<?, ?>) getField(editor, "viewports");
		assertEquals(15f, ((TooltipMakerAPI) viewports.get("metadata")).getExternalScroller().getYOffset());
		assertEquals(25f, ((TooltipMakerAPI) viewports.get("members")).getExternalScroller().getYOffset());
		editor.refreshContent();
		assertEquals("ctb", field(editor, "variantPrefixField").getText());
		failWrite.set(true);
		assertFalse(editor.save());
		assertTrue(editor.getSaveErrors().get(0).contains("Injected formatted fleet write failure"));
		assertTrue(editor.hasUnsavedChanges());
		assertEquals(stored, storedFiles());
		assertEquals("parent_variant", store.get("format_fleet").getMembers().get(0).getVariantId());
		failWrite.set(false);
		assertTrue(editor.save(), editor.getStatusText());
		assertFalse(editor.hasUnsavedChanges());
		store.reload();
		var loaded = store.get("format_fleet");
		assertEquals("Edited fleet", loaded.getName());
		assertEquals("ctb_parent_1", loaded.getMembers().get(0).getVariantId());
		assertEquals("ctb_child_1", loaded.getVariant("ctb_parent_1").build().getModuleVariant("module").getHullVariantId());
		assertEquals(7, loaded.getVariant("ctb_child_1").build().getNumFluxVents());
		assertNotNull(loaded.getVariant("extra"));
		assertFalse(Files.exists(directory.resolve("tournament/format_fleet/parent_variant.variant")));
		assertFalse(Files.exists(directory.resolve("tournament/format_fleet/child_variant.variant")));
		for (String id : List.of("ctb_parent_1", "ctb_child_1", "extra")) {
			assertEquals(id, new JSONObject(Files.readString(directory.resolve("tournament/format_fleet/" + id + ".variant"), StandardCharsets.UTF_8)).getString("variantId"));
		}
		editor.close();
		assertNull(getField(editor, "variantPrefixField"));
	}

	@Test
	void rejectedPreviewPreservesPinnedSelectionUnsubmittedInputsAndTheOriginalDraft() throws Exception {

		var pins = new BattleNext_PinnedVariantStore();
		pins.reload();
		var parent = source().getApi();
		registeredVariants.put("parent_variant", parent);
		var pin = pins.saveVariant("Pinned", parent);
		var fleet = fleet(List.of("parent_variant", "missing"));
		var editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(fleet, false);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		editor.buttonPressed("toggle_member:0");
		field(editor, "folderNameField").setText("");
		field(editor, "idField").setText("123");
		Object fields = ((List<?>) getField(editor, "memberFields")).get(0);
		field(fields, "variantField").setText("pinned:" + pin.getPinId());
		field(fields, "nameField").setText("Pending name");
		field(editor, "variantPrefixField").setText("ctb");
		String inputs = editor.serializeState();
		var draft = (BattleNext_FleetModel) getField(editor, "draft");
		String original = draft.toJson().toString();
		Map<Path, String> stored = storedFiles();
		editor.buttonPressed("format_ids");
		assertEquals(1, editor.getSaveErrors().size());
		assertTrue(editor.getSaveErrors().get(0).contains("Member 2 / missing"));
		assertTrue(editor.getSaveErrors().get(0).contains("Variant could not be resolved: missing"));
		assertEquals(inputs, editor.serializeState());
		assertSame(draft, getField(editor, "draft"));
		assertEquals(original, draft.toJson().toString());
		assertEquals("", field(editor, "folderNameField").getText());
		assertEquals("pinned:" + pin.getPinId(), field(fields, "variantField").getText());
		assertTrue(draft.getVariants().isEmpty());
		assertEquals(stored, storedFiles());
		editor.buttonPressed("toggle_member:1");
		field(((List<?>) getField(editor, "memberFields")).get(1), "variantField").setText("parent_variant");
		editor.buttonPressed("format_ids");
		assertTrue(editor.getSaveErrors().isEmpty(), editor.getStatusText());
		var corrected = (BattleNext_FleetModel) getField(editor, "draft");
		assertEquals(List.of("ctb_parent_1", "ctb_parent_2"), corrected.getMembers().stream().map(BattleNext_MemberModel::getVariantId).toList());
		assertEquals("ctb_child_1", corrected.getVariant("ctb_parent_1").build().getModuleVariant("module").getHullVariantId());
		assertEquals("ctb_child_1", corrected.getVariant("ctb_parent_2").build().getModuleVariant("module").getHullVariantId());
		assertEquals("parent_variant", parent.getHullVariantId());
		assertEquals(stored, storedFiles());
		editor.close();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void moduleOnlyChangesTriggerConfirmationAndTheFooterSharesLocalizedFormatFeedback(boolean cnText) throws Exception {

		BattleNext_Settings.save(cnText, true, false, false, 500f, 1f, 3f, 50, 0);
		var fleet = fleet(List.of("ctb_parent_custom"));
		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson().put("variantId", "ctb_parent_custom");
		fleet.putVariant(BattleNext_VariantBuilder.fromJson(json, ""));
		var store = new BattleNext_FleetStore();
		store.reload();
		store.save(fleet);
		AtomicBoolean closed = new AtomicBoolean();
		var manager = new BattleNext_UiManager(() -> closed.set(true));
		manager.attach(panel(1400f, 900f, manager));
		Object fleets = Arrays.stream(Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants())
				.filter(tab -> tab.toString().equals("FLEETS")).findFirst().orElseThrow();
		manager.buttonPressed(fleets);
		var page = (BattleNext_FleetLibraryPage) getField(manager, "fleetPage");
		manager.buttonPressed("fleet:format_fleet");
		var editor = (BattleNext_FleetEditor) getField(page, "editor");
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_variant_id_prefix")));
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_format_ids"), buttonLabels.get("format_ids"));
		field(editor, "variantPrefixField").setText("ctb");
		assertFalse(page.hasUnsavedChanges());
		assertTrue(page.save());
		field(editor, "variantPrefixField").setText("bad?");
		manager.buttonPressed("format_ids");
		assertEquals(editor.getStatusText(), page.getStatusText());
		assertEquals(page.getStatusText(), ((LabelAPI) getField(page, "statusLabel")).getText());
		assertEquals(1, page.getSaveErrors().size());
		TooltipMakerAPI hover = tooltip(480f, 0f, false);
		attachedTooltips.get(getField(page, "statusLabel")).createTooltip(hover, false, null);
		assertEquals("1. " + page.getSaveErrors().get(0), ((LabelAPI) tooltips.get(hover).getComponents().get(0)).getText());
		field(editor, "variantPrefixField").setText("ctb");
		manager.buttonPressed("format_ids");
		assertTrue(page.getSaveErrors().isEmpty());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_format_success"), 1, 1), page.getStatusText());
		assertEquals("ctb_parent_custom", ((BattleNext_FleetModel) getField(editor, "draft")).getMembers().get(0).getVariantId());
		assertTrue(page.hasUnsavedChanges());
		manager.buttonPressed("format_ids");
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_format_success"), 0, 0), page.getStatusText());
		manager.buttonPressed("close");
		var confirmation = (BattleNext_ConfirmationDialog) getField(manager, "confirmationDialog");
		assertTrue(confirmation.isActive());
		assertFalse(closed.get());
		manager.buttonPressed("confirm_cancel");
		assertTrue(page.hasUnsavedChanges());
		page.discard();
		assertFalse(page.hasUnsavedChanges());
		assertEquals("", field(editor, "variantPrefixField").getText());
		assertEquals("child_variant", ((BattleNext_FleetModel) getField(editor, "draft")).getVariant("ctb_parent_custom").toJson()
				.getJSONObject("modules").getJSONObject("module").getString("variantId"));
		field(editor, "variantPrefixField").setText("ctb");
		manager.buttonPressed("format_ids");
		manager.buttonPressed("close");
		manager.buttonPressed("confirm_save");
		assertTrue(closed.get());
		store.reload();
		assertEquals("ctb_child_1", store.get("format_fleet").getVariant("ctb_parent_custom").build().getModuleVariant("module").getHullVariantId());
	}

	private TextFieldAPI field(Object editor, String name) throws Exception {
		return (TextFieldAPI) getField(editor, name);
	}

	private BattleNext_FleetModel fleet(List<String> ids) {
		return new BattleNext_FleetModel("format_fleet", "Fleet", List.of(), List.of(), ids.stream()
				.map(id -> new BattleNext_MemberModel(id, id, "", List.of(), "", "", List.of())).toList());
	}

	private Map<Path, String> storedFiles() throws IOException {
		Map<Path, String> stored = new LinkedHashMap<>();
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				stored.put(path, Files.readString(path, StandardCharsets.UTF_8));
			}
		}
		return stored;
	}
}
