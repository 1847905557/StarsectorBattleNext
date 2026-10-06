package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import com.fs.starfarer.api.ui.LabelAPI;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_ConfirmationDialogTest extends BattleNext_TestPageFixture {

	@Test
	void unsavedAndDeleteRequestsOpenAndCancelTheirOverlay() throws Exception {
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
		assertTrue(((List<?>) getField(dialog, "ownedPanels")).isEmpty());
		for (int cycle = 0; cycle < 4; cycle++) {
			dialog.request(() -> {});
			assertEquals(1, children.get(root).size());
			dialog.handle("confirm_cancel");
			assertTrue(children.get(root).isEmpty());
			assertTrue(((List<?>) getField(dialog, "ownedPanels")).isEmpty());
		}
		dialog.close();
	}

	@Test
	void actionRequestsConfirmOnceCancelOnEscapeAndReleaseTheirModalOwnership() throws Exception {

		AtomicInteger confirmations = new AtomicInteger();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		BattleNext_ConfirmationDialog dialog = new BattleNext_ConfirmationDialog(
				new BattleNext_ConfirmationDialog.ConfirmationDialogHost() {
					@Override
					public boolean saveCurrentPage() {
						throw new AssertionError("Unexpected settings commit");
					}

					@Override
					public String getSaveError() {
						throw new AssertionError("Unexpected save error query");
					}

					@Override
					public void discardCurrentPage() {
						throw new AssertionError("Unexpected settings discard");
					}
				}, router);
		CustomPanelAPI root = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		dialog.attach(root);
		dialog.requestAction("Enable area", "Confirm enabling this window's area", confirmations::incrementAndGet);
		CustomPanelAPI popup = (CustomPanelAPI) getField(dialog, "confirmation");
		assertEquals(root.getPosition().getCenterX(), popup.getPosition().getCenterX());
		assertEquals(root.getPosition().getCenterY(), popup.getPosition().getCenterY());
		assertTrue(children.get(popup).stream().flatMap(child -> tooltips.get(child).getComponents().stream())
				.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast)
				.anyMatch(label -> label.getText().equals("Confirm enabling this window's area")));
		assertEquals("BattleNext_common_confirm", buttonLabels.get("action_confirm"));
		assertEquals("BattleNext_common_cancel", buttonLabels.get("action_cancel"));
		AtomicBoolean outside = new AtomicBoolean();
		assertTrue(router.handleGlobalInput(List.of(event("outside", outside))));
		assertTrue(outside.get());
		assertTrue(dialog.isActive());
		AtomicBoolean escape = new AtomicBoolean();
		assertTrue(router.handleGlobalInput(List.of(event("escape", escape))));
		assertTrue(escape.get());
		assertEquals(0, confirmations.get());
		assertTrue(router.isEmpty());
		assertTrue(children.get(root).isEmpty());
		dialog.requestAction("Enable area", "Confirm enabling this window's area", confirmations::incrementAndGet);
		popup = (CustomPanelAPI) getField(dialog, "confirmation");
		dialog.requestAction("Ignored action", "Ignored message", () -> {
			throw new AssertionError("Replaced active confirmation");
		});
		assertSame(popup, getField(dialog, "confirmation"));
		plugins.get(popup).buttonPressed("action_cancel");
		assertEquals(0, confirmations.get());
		dialog.requestAction("Enable area", "Confirm enabling this window's area", confirmations::incrementAndGet);
		popup = (CustomPanelAPI) getField(dialog, "confirmation");
		plugins.get(popup).buttonPressed("action_confirm");
		dialog.handle("action_confirm");
		assertEquals(1, confirmations.get());
		assertTrue(router.isEmpty());
		assertTrue(children.get(root).isEmpty());
		dialog.close();
	}
}
