package data.scripts.ui.integration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

class BattleNext_I18nUiIntegrationTest extends BattleNext_TestVariantFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void fleetMetadataAndMemberEditorsUseTheirOwnSkillAndIdentityLabels(boolean cnText) throws Exception {
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.text(args[0], args[1]);
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_Settings.save(cnText, true, false, false, 500f, 1f, 3f, 50);
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		var root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		attachEditor(editor, root);
		editor.buttonPressed("add_member");
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_commander_skills")));
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Member", "BattleNext_member_officer_skills")));
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Member", "BattleNext_member_officer_personality")));
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Member", "BattleNext_member_officer_portrait")));
		editor.closeOverlays();
		router.clear();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void weaponGroupsDisplayTheirAutofireState(boolean enabled) throws Exception {
		var json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		json.getJSONArray("weaponGroups").getJSONObject(0).put("autofire", enabled);
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		details.setVariantJson(json);
		details.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		String key = enabled ? BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_autofire_enabled") : BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_autofire_disabled");
		assertTrue(labels.stream().anyMatch(label -> label.contains(key)));
		details.close();
	}

	@Test
	void invalidTournamentConfigurationUsesTheConfigurationError() throws Exception {

		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		BattleNext_FleetStore fleets = new BattleNext_FleetStore();
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		tournaments.reload();
		fleets.reload();
		rules.reload();
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournaments, rules);
		BattleNext_TournamentModel missing = new BattleNext_TournamentModel("tournament", "Tournament", TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		editor.open(missing);
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		editor.setInputRouter(router);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(editor, "idField")).setText("tournament");
		((TextFieldAPI) getField(editor, "nameField")).setText("Tournament");
		((TextFieldAPI) getField(editor, "maxFleetsField")).setText("1");
		assertFalse(editor.serialize());
		assertEquals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_invalid_configuration"), editor.getStatusText());
		editor.closeOverlays();
		router.clear();
	}

	@Test
	void confirmationActionsKeepTheirEventIdsAndShareCommonLabels() throws Exception {

		BattleNext_ConfirmationDialog dialog = new BattleNext_ConfirmationDialog(
				new BattleNext_ConfirmationDialog.ConfirmationDialogHost() {
					@Override
					public boolean saveCurrentPage() {
						return true;
					}

					@Override
					public String getSaveError() {
						return "";
					}

					@Override
					public void discardCurrentPage() {

					}
				}, new BattleNext_UiInputRouter());
		dialog.attach(panel(800f, 600f, new BaseCustomUIPanelPlugin()));
		dialog.request(() -> {});
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_save"), buttonLabels.get("confirm_save"));
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_discard"), buttonLabels.get("confirm_discard"));
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_cancel"), buttonLabels.get("confirm_cancel"));
		dialog.handle("confirm_cancel");
		dialog.requestDelete("Fleet", () -> {});
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_delete"), buttonLabels.get("delete_confirm"));
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_cancel"), buttonLabels.get("delete_cancel"));
		dialog.handle("delete_cancel");
		dialog.close();
	}
}
