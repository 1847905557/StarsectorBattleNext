package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_ConfirmationDialogTest extends BattleNext_TestPageFixture {

	@Test
	void unsavedAndDeleteRequestsOpenAndCancelTheirOverlay() {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
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
				}, router);
		CustomPanelAPI root = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		dialog.attach(root);

		dialog.request(() -> {});
		assertTrue(dialog.isActive());
		dialog.handle("confirm_cancel");
		assertFalse(dialog.isActive());

		dialog.requestDelete("Fleet", () -> {});
		assertTrue(dialog.isActive());
		dialog.handle("delete_cancel");
		assertFalse(dialog.isActive());
		assertTrue(router.isEmpty());
	}
}
