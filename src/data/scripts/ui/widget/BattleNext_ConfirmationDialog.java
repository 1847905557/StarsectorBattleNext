package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.BattleNext_UiInputRouter;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.lwjgl.input.Keyboard;

public class BattleNext_ConfirmationDialog {

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
	private final BattleNext_UiInputRouter inputRouter;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI parent;
	private CustomPanelAPI confirmation;
	private TooltipMakerAPI confirmationStatusContent;
	private LabelAPI confirmationStatus;
	private Runnable onConfirm;
	private String deleteDescription;
	private DialogOverlay overlay;

	public BattleNext_ConfirmationDialog(ConfirmationDialogHost host) {
		this(host, BattleNext_UiInputRouter.current());
	}

	public BattleNext_ConfirmationDialog(ConfirmationDialogHost host, BattleNext_UiInputRouter inputRouter) {
		this.host = host;
		this.inputRouter = inputRouter;
	}

	public void attach(CustomPanelAPI parent) {
		this.parent = parent;
	}

	public boolean isActive() {
		return confirmation != null;
	}

	public void request(Runnable confirmAction) {
		if (confirmation != null) return;
		deleteDescription = null;
		onConfirm = confirmAction;
		showConfirmation();
	}

	public void requestDelete(String description, Runnable deleteAction) {
		if (confirmation != null) return;
		deleteDescription = description;
		onConfirm = deleteAction;
		showConfirmation();
	}

	public void handle(Object action) {
		if ("confirm_cancel".equals(action) || "delete_cancel".equals(action)) {
			close();
			return;
		}
		if (deleteDescription != null) {
			if ("delete_confirm".equals(action)) {
				close();
				Runnable pending = onConfirm;
				onConfirm = null;
				if (pending != null) pending.run();
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
		close();
		Runnable pending = onConfirm;
		onConfirm = null;
		if (pending != null) pending.run();
	}

	private void close() {
		if (parent != null && confirmation != null) parent.removeComponent(confirmation);
		if (overlay != null) inputRouter.pop(overlay);
		confirmation = null;
		overlay = null;
		confirmationStatusContent = null;
		confirmationStatus = null;
		deleteDescription = null;
	}

	private void showConfirmation() {
		if (confirmation != null) return;
		float width = Math.min(DIALOG_MAX_WIDTH, parent.getPosition().getWidth() - DIALOG_PARENT_WIDTH_RESERVE);
		confirmation = Global.getSettings().createCustom(width, DIALOG_HEIGHT, new ConfirmationDialogPlugin(width));
		UIRect dialogRect = new UIRect(DIALOG_SIDE_INSET, DIALOG_MESSAGE_TOP,
				width - DIALOG_SIDE_INSET * 2f, DIALOG_MESSAGE_HEIGHT);
		TooltipMakerAPI dialog = dialogRect.beginTooltip(confirmation, false);
		BattleNext_UiTheme.configure(dialog);
		dialog.addTitle(strings.get(deleteDescription == null ? "unsaved_title" : "delete_title"));
		dialog.addPara(deleteDescription == null ? strings.get("unsaved_message")
				: strings.get("delete_message") + " " + deleteDescription, DIALOG_MESSAGE_GAP);
		dialogRect.addTooltip();
		UIRect statusRect = new UIRect(DIALOG_SIDE_INSET, DIALOG_STATUS_TOP,
				width - DIALOG_SIDE_INSET * 2f, DIALOG_STATUS_HEIGHT);
		confirmationStatusContent = statusRect.beginTooltip(confirmation, false);
		confirmationStatus = confirmationStatusContent.addPara("", 0f);
		confirmationStatusContent.setHeightSoFar(statusRect.getHeight());
		statusRect.addTooltip();
		String[] actions = deleteDescription == null
				? new String[]{"save", "discard", "cancel"} : new String[]{"confirm", "cancel"};
		float buttonWidth = Math.min(DIALOG_BUTTON_MAX_WIDTH,
				(width - DIALOG_BUTTON_WIDTH_RESERVE) / actions.length);
		float firstX = (width - (buttonWidth * actions.length
				+ DIALOG_BUTTON_GAP * (actions.length - 1))) * 0.5f;
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(confirmation,
					new UIRect(firstX + index * (buttonWidth + DIALOG_BUTTON_GAP), DIALOG_BUTTON_TOP,
							buttonWidth, DIALOG_BUTTON_HEIGHT),
					strings.get((deleteDescription == null ? "unsaved_" : "delete_") + actions[index]),
					(deleteDescription == null ? "confirm_" : "delete_") + actions[index], CutStyle.ALL, false);
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
		public boolean handleGlobalInput(java.util.List<com.fs.starfarer.api.input.InputEventAPI> events) {
			for (com.fs.starfarer.api.input.InputEventAPI event : events) {
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
			if (confirmation != null) BattleNext_ConfirmationDialog.this.close();
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
