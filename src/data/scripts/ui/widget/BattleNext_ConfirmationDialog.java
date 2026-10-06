package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class BattleNext_ConfirmationDialog extends BattleNext_PanelBasedWidget {

	private static final float DIALOG_MAX_WIDTH = 520f;
	private static final float DIALOG_PARENT_WIDTH_RESERVE = 40f;
	private static final float DIALOG_HEIGHT = 160f;
	private static final float DIALOG_SIDE_INSET = 12f;
	private static final float DIALOG_MESSAGE_TOP = 8f;
	private static final float DIALOG_MESSAGE_HEIGHT = 75f;
	private static final float DIALOG_MESSAGE_GAP = 12f;
	private static final float DIALOG_STATUS_TOP = 88f;
	private static final float DIALOG_STATUS_HEIGHT = 25f;
	private static final float DIALOG_STATUS_TEXT_RESERVE = 10f;
	private static final float DIALOG_BUTTON_TOP = 118f;
	private static final float DIALOG_BUTTON_HEIGHT = 30f;
	private static final float DIALOG_BUTTON_MAX_WIDTH = 145f;
	private static final float DIALOG_BUTTON_GAP = 8f;
	private static final float DIALOG_BUTTON_WIDTH_RESERVE = 40f;
	private static final float DIALOG_BORDER_INSET = 8f;

	public interface ConfirmationDialogHost {

		boolean saveCurrentPage();

		String getSaveError();

		void discardCurrentPage();
	}

	private final ConfirmationDialogHost host;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");

	private CustomPanelAPI parent;
	private CustomPanelAPI confirmation;
	private TooltipMakerAPI confirmationStatusContent;
	private LabelAPI confirmationStatus;
	private ConfirmationRequest request;
	private DialogOverlay overlay;

	private record ConfirmationRequest(String title, String message, String description,
			String confirmId, String confirmLabel, String cancelId, Runnable onConfirmed) { }

	public BattleNext_ConfirmationDialog(ConfirmationDialogHost host, BattleNext_UiInputRouter inputRouter) {
		super(inputRouter);
		this.host = host;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {
		parent = panel;
	}

	@Override
	protected void clearPageState() {
		parent = null;
	}

	@Override
	protected void clearOverlays() {
		closeDialog();
	}

	public boolean isActive() {
		return confirmation != null;
	}

	public void request(Runnable confirmAction) {
		if (confirmation != null) return;
		request = new ConfirmationRequest(strings.get("unsaved_title"), strings.get("unsaved_message"), "",
				"", "", "confirm_cancel", confirmAction);
		showConfirmation();
	}

	public void requestDelete(String description, Runnable deleteAction) {
		if (confirmation != null) return;
		request = new ConfirmationRequest(strings.get("delete_title"), strings.get("delete_message"), description,
				"delete_confirm", strings.get("delete"), "delete_cancel", deleteAction);
		showConfirmation();
	}

	public void requestAction(String title, String message, Runnable confirmAction) {
		if (confirmation != null) return;
		request = new ConfirmationRequest(title, message, "", "action_confirm", strings.get("confirm"), "action_cancel", confirmAction);
		showConfirmation();
	}

	public void handle(Object action) {

		if (confirmation == null) return;
		ConfirmationRequest current = request;
		if ("confirm_cancel".equals(action) || current.cancelId().equals(action)) {
			closeDialog();
			return;
		}
		if (!current.confirmId().isEmpty()) {
			if (current.confirmId().equals(action)) {
				closeDialog();
				current.onConfirmed().run();
			}
			return;
		}
		if (!"confirm_save".equals(action) && !"confirm_discard".equals(action)) return;
		if ("confirm_save".equals(action) && !host.saveCurrentPage()) {
			confirmationStatus.setText(confirmationStatusContent.shortenString(host.getSaveError(),
					confirmationStatusContent.getPosition().getWidth() - DIALOG_STATUS_TEXT_RESERVE));
			return;
		}
		if ("confirm_discard".equals(action)) {
			host.discardCurrentPage();
		}
		closeDialog();
		current.onConfirmed().run();
	}

	private void closeDialog() {
		if (confirmation != null) releasePanel(confirmation);
		if (overlay != null) inputRouter.pop(overlay);
		confirmation = null;
		overlay = null;
		confirmationStatusContent = null;
		confirmationStatus = null;
		request = null;
	}

	private void showConfirmation() {
		if (confirmation != null) return;
		float width = Math.min(DIALOG_MAX_WIDTH, parent.getPosition().getWidth() - DIALOG_PARENT_WIDTH_RESERVE);
		confirmation = ownPanel(parent, parent.createCustomPanel(width, DIALOG_HEIGHT, new ConfirmationDialogPlugin(width)));
		UIRect dialogRect = new UIRect(DIALOG_SIDE_INSET, DIALOG_MESSAGE_TOP,
				width - DIALOG_SIDE_INSET * 2f, DIALOG_MESSAGE_HEIGHT);
		TooltipMakerAPI dialog = dialogRect.beginTooltip(confirmation, false);
		BattleNext_UiTheme.configure(dialog);
		dialog.addTitle(request.title());
		dialog.addPara(request.message(), DIALOG_MESSAGE_GAP);
		if (!request.description().isEmpty()) {
			dialog.addPara(request.description(), DIALOG_MESSAGE_GAP);
		}
		dialogRect.addTooltip();
		UIRect statusRect = new UIRect(DIALOG_SIDE_INSET, DIALOG_STATUS_TOP,
				width - DIALOG_SIDE_INSET * 2f, DIALOG_STATUS_HEIGHT);
		confirmationStatusContent = statusRect.beginTooltip(confirmation, false);
		confirmationStatus = confirmationStatusContent.addPara("", 0f);
		confirmationStatusContent.setHeightSoFar(statusRect.getHeight());
		statusRect.addTooltip();
		String[] actions = request.confirmId().isEmpty()
				? new String[]{"confirm_save", "confirm_discard", "confirm_cancel"} : new String[]{request.confirmId(), request.cancelId()};
		String[] labels = request.confirmId().isEmpty()
				? new String[]{strings.get("save"), strings.get("discard"), strings.get("cancel")}
				: new String[]{request.confirmLabel(), strings.get("cancel")};
		float buttonWidth = Math.min(DIALOG_BUTTON_MAX_WIDTH,
				(width - DIALOG_BUTTON_WIDTH_RESERVE) / actions.length);
		float firstX = (width - (buttonWidth * actions.length
				+ DIALOG_BUTTON_GAP * (actions.length - 1))) * 0.5f;
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(confirmation,
					new UIRect(firstX + index * (buttonWidth + DIALOG_BUTTON_GAP), DIALOG_BUTTON_TOP,
							buttonWidth, DIALOG_BUTTON_HEIGHT),
					labels[index],
					actions[index], CutStyle.ALL, false);
		}
		parent.addComponent(confirmation).inMid();
		overlay = new DialogOverlay(confirmation);
		inputRouter.push(overlay);
	}

	private final class DialogOverlay implements BattleNext_UiInputRouter.Overlay {
		private final CustomPanelAPI panel;

		private DialogOverlay(CustomPanelAPI panel) {
			this.panel = panel;
		}

		@Override
		public PositionAPI getBounds() {
			return panel.getPosition();
		}

		@Override
		public boolean handleGlobalInput(List<InputEventAPI> events) {
			for (InputEventAPI event : events) {
				if (event.isConsumed()) continue;
				if (event.isKeyboardEvent() && event.isKeyDownEvent()
						&& event.getEventValue() == Keyboard.KEY_ESCAPE) {
					handle("confirm_cancel");
					event.consume();
					return true;
				}
				if (event.isMouseDownEvent() && !panel.getPosition().containsEvent(event)) {
					event.consume();
					return true;
				}
			}
			return false;
		}

		@Override
		public void close() {
			if (confirmation != null) BattleNext_ConfirmationDialog.this.closeDialog();
		}
	}

	private class ConfirmationDialogPlugin extends BaseCustomUIPanelPlugin {

		private final UIBorderObject border = new UIBorderObject(false, true);
		private final float borderX;
		private final float borderY;

		private ConfirmationDialogPlugin(float width) {
			border.setSize(width + DIALOG_BORDER_INSET * 2f, DIALOG_HEIGHT + DIALOG_BORDER_INSET * 2f);
			borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - DIALOG_BORDER_INSET;
			borderY = (ShaderCore.getScreenHeight() - DIALOG_HEIGHT) * 0.5f - DIALOG_BORDER_INSET;
		}

		@Override
		public void renderBelow(float alphaMult) {
			border.setAlpha(alphaMult);
			border.render(borderX, borderY);
		}

		@Override
		public void buttonPressed(Object buttonId) {
			handle(buttonId);
		}
	}
}
