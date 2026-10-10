package data.scripts.campaign.luna;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.SoundPlayerAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestCampaignFleetFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONObject;
import org.json.JSONArray;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_LunaFleetSavePanelTest extends BattleNext_TestCampaignFleetFixture {

	@Test
	void expandingMembersRetainsNameFocusScrollAndTheSavedSnapshot() throws Exception {

		BattleNext_FleetStore store = new BattleNext_FleetStore();
		BattleNext_LunaFleetSavePanel display = open(store, 560);
		BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
		name.setText("Frozen fleet");
		name.grabFocus(false);
		List<CustomPanelAPI> collapsed = memberRows(display);
		assertEquals(List.of("Officer ship", "Player ship", "Core ship", "Civilian mothballed", "Unassigned"),
				collapsed.stream().map(row -> {
					CustomPanelAPI header = (CustomPanelAPI) children.get(row).get(0);
					TooltipMakerAPI text = (TooltipMakerAPI) children.get(header).get(0);
					return ((LabelAPI) tooltips.get(text).getComponents().get(0)).getText();
				}).toList());
		assertTrue(collapsed.stream().allMatch(row -> children.get(row).size() == 1));
		TooltipMakerAPI previous = membersContent(display);
		CustomPanelAPI previousPanel = (CustomPanelAPI) getField(display, "membersPanel");
		SoundPlayerAPI sound = Global.getSoundPlayer();
		try {
			Global.setSoundPlayer(proxy(SoundPlayerAPI.class, (method, args) -> null));
			CustomPanelAPI header = (CustomPanelAPI) children.get(collapsed.get(0)).get(0);
			PositionAPI bounds = header.getPosition();
			AtomicBoolean consumed = new AtomicBoolean();
			header.getPlugin().processInput(List.of(proxy(InputEventAPI.class, (method, args) -> switch (method) {
				case "isConsumed" -> consumed.get();
				case "isLMBDownEvent" -> true;
				case "getX" -> (int) bounds.getCenterX();
				case "getY" -> (int) bounds.getCenterY();
				case "consume" -> { consumed.set(true); yield null; }
				default -> null;
			})));
			assertTrue(consumed.get());
		} finally {
			Global.setSoundPlayer(sound);
		}
		assertTrue(removedComponents.contains(previousPanel));
		assertNotSame(previous, membersContent(display));
		assertEquals(Set.of(0), getField(display, "expandedMembers"));
		assertTrue(bodyText(display, 0).contains(text("Member", "ship_name") + ": Officer ship"));
		assertTrue(bodyText(display, 0).contains(text("Member", "officer_personality") + ": reckless"));
		activeVariant.getApi().setNumFluxVents(99);
		membersContent(display).getExternalScroller().setYOffset(80f);
		display.buttonPressed(1);
		assertEquals(80f, membersContent(display).getExternalScroller().getYOffset());
		assertEquals(Set.of(0, 1), getField(display, "expandedMembers"));
		assertTrue(bodyText(display, 0).contains(text("Variant", "flux_capacitors") + ": 0 / " + text("Variant", "flux_vents") + ": 42"));
		assertTrue(bodyText(display, 1).contains(text("Variant", "flux_capacitors") + ": 0 / " + text("Variant", "flux_vents") + ": 13"));
		display.buttonPressed(0);
		assertEquals(Set.of(1), getField(display, "expandedMembers"));
		assertEquals(1, children.get(memberRows(display).get(0)).size());
		membersContent(display).getExternalScroller().setYOffset(120f);
		display.buttonPressed("fleet_capture_save");
		assertEquals(120f, membersContent(display).getExternalScroller().getYOffset());
		assertSame(name, getField(display, "name"));
		assertTrue(name.hasFocus());
		assertEquals("Frozen fleet", name.getText());
		assertEquals(Set.of(1), getField(display, "expandedMembers"));
		assertEquals(42, store.getAll().get(0).getVariants().get(0).toJson().getInt("fluxVents"));
		membersContent(display).getExternalScroller().setYOffset(Float.MAX_VALUE);
		display.buttonPressed(1);
		TooltipMakerAPI list = membersContent(display);
		assertEquals(Math.max(0f, list.getHeightSoFar() - tooltips.get(list).getViewport().getHeight()),
				list.getExternalScroller().getYOffset());
		display.close();
		assertNull(getField(display, "membersContent"));
		assertNull(getField(display, "membersPanel"));
		assertEquals(Set.of(), getField(display, "expandedMembers"));
	}

	@Test
	void summariesCountBuiltInEquipmentAndLabelOfficerLevelsHullmodStatesAndNestedModules() throws Exception {

		ShipHullSpecAPI parent = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> "parent";
			case "getHullName" -> "Parent";
			case "getSpriteName" -> "graphics/parent.png";
			case "getBuiltInMods" -> List.of("built", "d");
			case "getBuiltInWeapons" -> new HashMap<>(Map.of("builtin", "laser"));
			case "getBuiltInWings" -> List.of("wing");
			case "getWeaponSlot" -> proxy(WeaponSlotAPI.class, (key, values) -> switch (key) {
				case "isStationModule" -> args[0].equals("module");
				default -> null;
			});
			default -> null;
		});
		hulls.put("parent", parent);
		activeVariant = variant("active", parent);
		activeVariant.getApi().setVariantDisplayName("Active loadout");
		activeVariant.getApi().setNumFluxCapacitors(11);
		activeVariant.getApi().setNumFluxVents(42);
		activeVariant.getApi().addMod("ordinary");
		activeVariant.getApi().addPermaMod("sm", true);
		activeVariant.getApi().addPermaMod("active_dmod");
		activeVariant.getApi().getSModdedBuiltIns().add("built");
		activeVariant.getApi().getSuppressedMods().add("d");
		activeVariant.getJson().put("wings", new JSONArray().put("wing").put("wing").put("wing"));
		activeVariant.getJson().put("weaponGroups", new JSONArray().put(new JSONObject().put("mode", "LINKED")
				.put("weapons", new JSONObject().put("one", "laser").put("two", "laser").put("three", "ballistic"))));
		Variant child = variant("child_variant", hull("child", "Child", List.of("nested")));
		child.getApi().setModuleVariant("nested", variant("leaf_variant", hull("leaf", "Leaf", List.of())).getApi());
		activeVariant.getApi().setModuleVariant("module", child.getApi());
		fleetMembers.set(4, member("Unassigned", variant("plain_variant", hull("plain", "Plain", List.of())).getApi(), null, false));
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getHullModSpec")) {
				String id = (String) args[0];
				return proxy(HullModSpecAPI.class, (name, values) -> switch (name) {
					case "getDisplayName" -> "Hullmod " + id;
					case "hasTag" -> values[0].equals(Tags.HULLMOD_DMOD) && (id.equals("active_dmod") || id.equals("d"));
					default -> null;
				});
			}
			if (method.getName().equals("getWeaponSpec")) {
				return proxy(WeaponSpecAPI.class, (name, values) -> name.equals("getWeaponName")
						? args[0].equals("laser") ? "Alpha gun" : "Zulu gun" : null);
			}
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_LunaFleetSavePanel display = open(new BattleNext_FleetStore(), 560);
		display.buttonPressed(0);
		List<String> details = bodyText(display, 0);
		assertTrue(details.contains(text("Member", "officer_skills") + ": Skill gunnery / "
				+ String.format(text("Common", "skill_effect_level"), "2") + ", Skill helmsmanship / "
				+ String.format(text("Common", "skill_effect_level"), "1")));
		assertTrue(details.contains(text("Variant", "flux_capacitors") + ": 11 / " + text("Variant", "flux_vents") + ": 42"));
		assertTrue(details.contains(text("Variant", "weapons") + ": Alpha gun × 3, Zulu gun × 1"));
		assertTrue(details.contains(text("Variant", "fighter_wings") + ": Wing wing × 3"));
		assertTrue(details.contains(text("Variant", "hullmods") + ": Hullmod active_dmod [" + text("Variant", "d_mod")
				+ "], Hullmod built [" + text("Variant", "s_mod") + ", " + text("Variant", "built_in")
				+ "], Hullmod d [" + text("Variant", "d_mod") + ", " + text("Variant", "built_in") + ", " + text("Variant", "suppressed")
				+ "], Hullmod ordinary, Hullmod sm [" + text("Variant", "s_mod") + "]"));
		assertTrue(details.contains("• Child / module"));
		assertTrue(details.contains("    • Leaf / nested"));
		display.buttonPressed(4);
		assertTrue(bodyText(display, 4).contains(text("Member", "officer_personality") + ": " + text("Fleet", "officer_none")));
		assertTrue(bodyText(display, 4).contains(text("Member", "officer_skills") + ": " + text("Variant", "none")));
		for (String section : List.of("weapons", "hullmods", "fighter_wings")) {
			assertTrue(bodyText(display, 4).contains(text("Variant", section) + ": " + text("Variant", "none")));
		}
		assertTrue(bodyText(display, 4).contains(text("Variant", "none")));
		display.close();
	}

	@Test
	void nameAndFooterFitAndCorrectedNamesPublishDistinctFrozenSnapshots() throws Exception {

		int width = 560;
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		BattleNext_LunaFleetSavePanel display = open(store, width);
		CustomPanelAPI root = (CustomPanelAPI) getField(display, "panel");
		BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
		assertTrue(name.getText().matches("Player-\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
		assertEquals(((BattleNext_FleetModel) getField(display, "snapshot")).getName(), name.getText());
		assertTrue(labels.contains(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_commander"), "Player")));
		assertTrue(labels.contains(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_members"), 5)));
		assertEquals(1, children.get(root).size());
		assertTrue(name.getPosition().getX() >= root.getPosition().getX());
		assertTrue(name.getPosition().getX() + name.getPosition().getWidth() <= root.getPosition().getX() + width);
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_save").getY());
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_close").getY());
		assertTrue(buttonPositions.get("fleet_capture_save").getX() + buttonPositions.get("fleet_capture_save").getWidth()
				< buttonPositions.get("fleet_capture_close").getX());
		String id = ((BattleNext_FleetModel) getField(display, "snapshot")).getId();
		name.setText("   ");
		display.buttonPressed("fleet_capture_save");
		assertEquals("   ", ((BattleNext_TextField) getField(display, "name")).getText());
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_name_required"), getField(display, "status"));
		assertFalse(Files.exists(directory.resolve("tournament")));
		activeVariant.getApi().setNumFluxVents(99);
		String fullName = "舰队😀50% " + "Full name ".repeat(40).stripTrailing();
		((BattleNext_TextField) getField(display, "name")).setText("  " + fullName + "  ");
		display.buttonPressed("fleet_capture_save");
		display.buttonPressed("fleet_capture_save");
		assertEquals(1, store.getAll().size());
		assertEquals(id, store.getAll().get(0).getId());
		assertEquals(fullName, store.getAll().get(0).getName());
		assertEquals("Player", store.getAll().get(0).getAuthor());
		assertEquals(42, store.getAll().get(0).getVariants().get(0).toJson().getInt("fluxVents"));
		assertFalse(buttons.get("fleet_capture_save").get());
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_saved"), buttonLabels.get("fleet_capture_save"));
		assertEquals("  " + fullName + "  ", ((BattleNext_TextField) getField(display, "name")).getText());
		assertTrue(((String) getField(display, "status")).contains("tournament/" + id + "/fleet_data.json"));
		assertEquals(1, children.get(root).size());
		assertEquals(1, registrations(display).size());
		display.close();
		assertTrue(children.get(root).isEmpty());
		assertTrue(registrations(display).isEmpty());
		BattleNext_LunaFleetSavePanel second = open(store, width);
		((BattleNext_TextField) getField(second, "name")).setText(fullName);
		second.buttonPressed("fleet_capture_save");
		assertEquals(2, store.getAll().size());
		assertTrue(store.getAll().stream().allMatch(snapshot -> fullName.equals(snapshot.getName())));
		assertNotEquals(id, ((BattleNext_FleetModel) getField(second, "snapshot")).getId());
		second.close();
	}

	@Test
	void narrowPanelKeepsNameAndFooterWithinBounds() throws Exception {
		for (int index = 0; index < 20; index++) {
			fleetMembers.add(member("Long ship name ".repeat(12) + index, otherVariant.getApi(), null, false));
		}
		shortenLabels = true;
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		BattleNext_LunaFleetSavePanel display = open(store, 320);
		CustomPanelAPI root = (CustomPanelAPI) getField(display, "panel");
		BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
		assertTrue(name.getPosition().getX() >= root.getPosition().getX());
		assertTrue(name.getPosition().getX() + name.getPosition().getWidth() <= root.getPosition().getX() + 320f);
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_save").getY());
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_close").getY());
		assertTrue(buttonPositions.get("fleet_capture_save").getX() + buttonPositions.get("fleet_capture_save").getWidth()
				< buttonPositions.get("fleet_capture_close").getX());
		PositionAPI field = name.getPosition();
		float fieldY = field.getY();
		float buttonY = buttonPositions.get("fleet_capture_save").getY();
		display.buttonPressed(5);
		TooltipMakerAPI list = membersContent(display);
		TooltipState state = tooltips.get(list);
		assertTrue(state.getUsedHeight() > state.getViewport().getHeight());
		assertTrue(state.isScrollable());
		assertEquals(25, memberRows(display).size());
		list.getExternalScroller().setYOffset(400f);
		assertEquals(400f, list.getExternalScroller().getYOffset());
		assertEquals(fieldY, field.getY());
		assertEquals(buttonY, buttonPositions.get("fleet_capture_save").getY());
		assertTrue(state.getViewport().getY() > buttonPositions.get("fleet_capture_save").getY() + 30f);
		assertTrue(state.getViewport().getY() + state.getViewport().getHeight() < field.getY());
		CustomPanelAPI expanded = memberRows(display).get(5);
		CustomPanelAPI body = (CustomPanelAPI) children.get(expanded).get(1);
		TooltipMakerAPI detail = (TooltipMakerAPI) children.get(body).get(0);
		assertTrue(tooltips.get(detail).getComponents().get(0).getPosition().getHeight() > paragraphLineHeight);
		assertEquals(detail.getHeightSoFar() + 20f, body.getPosition().getHeight());
		for (CustomPanelAPI row : memberRows(display)) {
			assertEquals(300f, row.getPosition().getWidth());
			assertEquals(root.getPosition().getX() + 10f, row.getPosition().getX());
		}
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"load", "write"})
	void saveFailureRetainsNameSnapshotAndIdentityForRetry(String failure) throws Exception {
		fleetMembers.subList(1, fleetMembers.size()).clear();
		FailingFleetStore store = new FailingFleetStore();
		store.setFailLoad("load".equals(failure));
		store.setFailWrite("write".equals(failure));
		BattleNext_LunaFleetSavePanel display = open(store, 560);
		BattleNext_FleetModel snapshot = (BattleNext_FleetModel) getField(display, "snapshot");
		String id = snapshot.getId();
		((BattleNext_TextField) getField(display, "name")).setText("Retry fleet");
		display.buttonPressed(0);
		membersContent(display).getExternalScroller().setYOffset(80f);
		display.buttonPressed("fleet_capture_save");
		assertTrue(store.getAll().isEmpty());
		assertSame(snapshot, getField(display, "snapshot"));
		assertEquals("Retry fleet", ((BattleNext_TextField) getField(display, "name")).getText());
		assertTrue(buttons.get("fleet_capture_save").get());
		assertEquals(Set.of(0), getField(display, "expandedMembers"));
		assertEquals(80f, membersContent(display).getExternalScroller().getYOffset());
		assertTrue(((String) getField(display, "status")).contains("Injected fleet " + failure + " failure"));
		assertFalse(Files.exists(directory.resolve("tournament/" + id + "/fleet_data.json")));
		store.setFailLoad(false);
		store.setFailWrite(false);
		display.buttonPressed("fleet_capture_save");
		assertEquals(1, store.getAll().size());
		assertEquals(id, store.getAll().get(0).getId());
		assertEquals("Retry fleet", store.getAll().get(0).getName());
		assertEquals(Set.of(0), getField(display, "expandedMembers"));
		assertEquals(80f, membersContent(display).getExternalScroller().getYOffset());
		assertEquals(42, store.getAll().get(0).getVariants().get(0).toJson().getInt("fluxVents"));
		assertTrue(Files.exists(directory.resolve("tournament/" + id + "/fleet_data.json")));
		assertJsonEquals(snapshot.toJson(), new JSONObject(Files.readString(directory.resolve(snapshot.getSourcePath()))));
		assertJsonEquals(snapshot.getVariants().get(0).toJson(), new JSONObject(Files.readString(
				directory.resolve("tournament/" + id + "/" + snapshot.getVariants().get(0).getId() + ".variant"))));
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"context", "cycle", "missing", "hull"})
	void captureFailureReportsErrorAndDisablesSaving(String failure) throws Exception {
		if ("cycle".equals(failure) || "missing".equals(failure)) {
			activeVariant.getApi().setModuleVariant("module", "cycle".equals(failure) ? activeVariant.getApi() : null);
		}
		failBuild = "hull".equals(failure);
		BattleNext_LunaFleetSavePanel display = new BattleNext_LunaFleetSavePanel(() -> {});
		display.open(selected, activeVariant.getApi(), "context".equals(failure) ? "Specific context error" : "");
		display.attach(panel(560f, 300f, display), 560f, 300f);
		assertNull(getField(display, "snapshot"));
		assertFalse(buttons.get("fleet_capture_save").get());
		assertTrue(((String) getField(display, "status")).contains("context".equals(failure)
				? "Specific context error" : BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_snapshot_error")));
		String reason = switch (failure) {
			case "context" -> "Specific context error";
			case "cycle" -> "Cyclic variant modules";
			case "missing" -> "Missing module: module";
			default -> "Unknown hull: parent";
		};
		assertTrue(((String) getField(display, "status")).contains(reason));
		display.buttonPressed("fleet_capture_save");
		assertFalse(Files.exists(directory.resolve("tournament")));
		display.close();
	}

	@Test
	void closingReleasesFocusRegistrationsAndMountedContentsAcrossReopenings() throws Exception {

		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_LunaFleetSavePanel[] holder = new BattleNext_LunaFleetSavePanel[1];
		holder[0] = new BattleNext_LunaFleetSavePanel(() -> {
			closed.set(true);
			holder[0].close();
		});
		BattleNext_LunaFleetSavePanel display = holder[0];
		CustomPanelAPI root = panel(560f, 300f, display);
		for (String path : List.of("button", "escape", "outside")) {
			closed.set(false);
			display.open(selected, activeVariant.getApi(), "");
			display.attach(root, 560f, 300f);
			BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
			name.grabFocus(false);
			display.buttonPressed(0);
			assertTrue(name.hasFocus());
			assertEquals(1, registrations(display).size());
			if ("button".equals(path)) {
				display.buttonPressed("fleet_capture_close");
			} else {
				AtomicBoolean consumed = new AtomicBoolean();
				display.processInput(List.of(event(path, consumed)));
				assertTrue(consumed.get());
			}
			assertTrue(closed.get());
			assertFalse(name.hasFocus());
			assertTrue(registrations(display).isEmpty());
			assertTrue(((List<?>) getField(name, "ownedPanels")).isEmpty());
			assertTrue(children.get(root).isEmpty());
			assertNull(getField(display, "snapshot"));
			assertNull(getField(display, "membersContent"));
			assertEquals(Set.of(), getField(display, "expandedMembers"));
			display.buttonPressed("fleet_capture_save");
			assertFalse(Files.exists(directory.resolve("tournament")));
		}
	}

	private BattleNext_LunaFleetSavePanel open(BattleNext_FleetStore store, int width) {
		BattleNext_LunaFleetSavePanel display = new BattleNext_LunaFleetSavePanel(() -> {}, store);
		display.open(selected, activeVariant.getApi(), "");
		display.attach(panel(width, 400f, display), width, 400f);
		return display;
	}

	private TooltipMakerAPI membersContent(BattleNext_LunaFleetSavePanel display) throws Exception {
		return (TooltipMakerAPI) getField(display, "membersContent");
	}

	private List<CustomPanelAPI> memberRows(BattleNext_LunaFleetSavePanel display) throws Exception {
		return tooltips.get(membersContent(display)).getComponents().stream()
				.filter(CustomPanelAPI.class::isInstance).map(CustomPanelAPI.class::cast).toList();
	}

	private List<String> bodyText(BattleNext_LunaFleetSavePanel display, int index) throws Exception {
		CustomPanelAPI row = memberRows(display).get(index);
		CustomPanelAPI body = (CustomPanelAPI) children.get(row).get(1);
		TooltipMakerAPI content = (TooltipMakerAPI) children.get(body).get(0);
		return tooltips.get(content).getComponents().stream().map(LabelAPI.class::cast).map(LabelAPI::getText).toList();
	}

	private String text(String category, String id) {
		return BattleNext_TestTextCatalog.text(category, "BattleNext_" + category.toLowerCase() + "_" + id);
	}

	private Map<?, ?> registrations(BattleNext_LunaFleetSavePanel display) throws Exception {
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(display, "inputRouter");
		return (Map<?, ?>) getField(router.getSession(), "registrations");
	}
}
