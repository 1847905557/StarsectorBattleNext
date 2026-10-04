package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.member.BattleNext_MemberModel;
import data.test.fixture.BattleNext_TestVariantFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_FleetEditorIntegrationTest extends BattleNext_TestVariantFixture {

	@Test
	void fleetEditorEmbedsCollectionThroughItsFormalCapturePath() throws Exception {

		BattleNext_PinnedVariantStore pins = new BattleNext_PinnedVariantStore();
		pins.reload();
		BattleNext_PinnedVariantModel pin = pins.saveVariant("Fleet input", source().getApi());
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(),
				List.of(new BattleNext_MemberModel("pinned:" + pin.getPinId(), "Ship", "", List.of(), "", "", List.of())));
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(store);
		editor.open(fleet, false);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		assertEquals(true, editor.save(), editor.getStatusText());
		store.reload();
		BattleNext_FleetModel saved = store.get("fleet");
		assertEquals(pin.getPinId(), saved.getMembers().get(0).getVariantId());
		pins.delete(pin.getPinId());
		assertEquals("Unsubmitted", saved.getVariant(pin.getPinId()).build().getDisplayName());
		assertNotNull(saved.getVariant(pin.getPinId()).build().getModuleVariant("module"));
		editor.close();
	}
}
