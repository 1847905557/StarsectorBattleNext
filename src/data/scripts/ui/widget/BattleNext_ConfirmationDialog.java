package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;

public class BattleNext_ConfirmationDialog {

	public interface Host {

		boolean saveCurrentPage();

		String getSaveError();

		void discardCurrentPage();
	}

	private final Host host;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI parent;
	private CustomPanelAPI confirmation;
	private TooltipMakerAPI confirmationStatusContent;
	private LabelAPI confirmationStatus;
	private Runnable onConfirm;
	private String deleteDescription;

	public BattleNext_ConfirmationDialog(Host host) {
		this.host = host;
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
					confirmationStatusContent.getPosition().getWidth() - 10f));
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
		confirmation = null;
		confirmationStatusContent = null;
		confirmationStatus = null;
		deleteDescription = null;
	}

	private void showConfirmation() {
		if (confirmation != null) return;
		float width = Math.min(520f, parent.getPosition().getWidth() - 40f);
		confirmation = Global.getSettings().createCustom(width, 160f, new ConfirmationPlugin(width));
		UIRect dialogRect = new UIRect(12f, 8f, width - 24f, 75f);
		TooltipMakerAPI dialog = dialogRect.beginTooltip(confirmation, false);
		BattleNext_UiTheme.configure(dialog);
		dialog.addTitle(strings.get(deleteDescription == null ? "unsaved_title" : "delete_title"));
		dialog.addPara(deleteDescription == null ? strings.get("unsaved_message")
				: strings.get("delete_message") + " " + deleteDescription, 12f, BattleNext_UiTheme.TEXT);
		dialogRect.addTooltip();
		UIRect statusRect = new UIRect(12f, 88f, width - 24f, 25f);
		confirmationStatusContent = statusRect.beginTooltip(confirmation, false);
		confirmationStatus = confirmationStatusContent.addPara("", 0f, BattleNext_UiTheme.MUTED);
		confirmationStatusContent.setHeightSoFar(statusRect.getHeight());
		statusRect.addTooltip();
		String[] actions = deleteDescription == null
				? new String[]{"save", "discard", "cancel"} : new String[]{"confirm", "cancel"};
		float buttonWidth = Math.min(145f, (width - 40f) / actions.length);
		float firstX = (width - (buttonWidth * actions.length + 8f * (actions.length - 1))) * 0.5f;
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(confirmation,
					new UIRect(firstX + index * (buttonWidth + 8f), 118f, buttonWidth, 30f),
					strings.get((deleteDescription == null ? "unsaved_" : "delete_") + actions[index]),
					(deleteDescription == null ? "confirm_" : "delete_") + actions[index], CutStyle.ALL, false);
		}
		parent.addComponent(confirmation).inMid();
	}

	private class ConfirmationPlugin extends BaseCustomUIPanelPlugin {

		private final UIBorderObject border = new UIBorderObject(false, true);
		private final float borderX;
		private final float borderY;

		private ConfirmationPlugin(float width) {
			border.setSize(width + 16f, 176f);
			borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - 8f;
			borderY = (ShaderCore.getScreenHeight() - 160f) * 0.5f - 8f;
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
