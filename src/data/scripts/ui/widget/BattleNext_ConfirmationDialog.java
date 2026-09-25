package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
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
	private LabelAPI confirmationStatus;
	private Runnable onConfirm;

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
		onConfirm = confirmAction;
		showConfirmation();
	}

	public void handle(Object action) {
		if ("confirm_cancel".equals(action)) {
			close();
			return;
		}
		if (!"confirm_save".equals(action) && !"confirm_discard".equals(action)) return;
		if ("confirm_save".equals(action) && !host.saveCurrentPage()) {
			confirmationStatus.setText(host.getSaveError());
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
		confirmationStatus = null;
	}

	private void showConfirmation() {
		if (confirmation != null) return;
		float width = Math.min(520f, parent.getPosition().getWidth() - 40f);
		confirmation = Global.getSettings().createCustom(width, 160f, new ConfirmationPlugin(width));
		UIRect dialogRect = new UIRect(0f, 0f, width, 160f);
		TooltipMakerAPI dialog = dialogRect.beginTooltip(confirmation, false);
		BattleNext_UiTheme.configure(dialog);
		dialog.addTitle(strings.get("unsaved_title"));
		dialog.addPara(strings.get("unsaved_message"), 12f, BattleNext_UiTheme.TEXT);
		confirmationStatus = dialog.addPara("", 6f, BattleNext_UiTheme.MUTED);
		String[] actions = {"save", "discard", "cancel"};
		for (int index = 0; index < actions.length; index++) {
			ButtonAPI button = dialog.addButton(strings.get("unsaved_" + actions[index]), "confirm_" + actions[index],
					BattleNext_UiTheme.TEXT, BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL, 145f, 30f, 0f);
			button.getPosition().inBL(22f + index * 165f, 12f);
			button.setMouseOverSound("BUtil_button_in");
			button.setButtonPressedSound("BUtil_button_down");
		}
		dialogRect.addTooltip();
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
