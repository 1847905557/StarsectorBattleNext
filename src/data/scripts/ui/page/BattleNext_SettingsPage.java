package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_SettingsEditor;

public class BattleNext_SettingsPage extends BattleNext_PageView {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_SettingsEditor editor = new BattleNext_SettingsEditor();
	private UIRect contentRect;

	@Override
	protected void loadPageState() {
		BattleNext_Settings.load();
		editor.open();
	}

	@Override
	protected BattleNext_EditorView editorView() {
		return editor;
	}

	@Override
	protected void buildPage() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), BattleNext_PageLayout.HEADER_HEIGHT), panel, managementStrings.get("page_settings"));
		UIRect footer = BattleNext_PageLayout.pageContentRect(new UIRect(0f,
				root.getHeight() - BattleNext_PageLayout.FOOTER_HEIGHT - 4f,
				root.getWidth(), BattleNext_PageLayout.FOOTER_HEIGHT));
		contentRect = BattleNext_PageLayout.pageContentRect(new UIRect(0f, BattleNext_PageLayout.CONTENT_TOP, root.getWidth(),
				footer.getY() - BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.FOOTER_MARGIN));
		float width = BattleNext_PageLayout.managementFooterButtonWidth(footer, 1);
		BattleNext_PageLayout.fixedButton(panel, BattleNext_PageLayout.managementFooterButtonRect(footer, width, 0),
				strings.get("save"), "settings_save", CutStyle.ALL, true);
		TooltipMakerAPI status = panel.createUIElement(footer.getWidth() - width - 10f, footer.getHeight(), false);
		statusLabel = status.addPara("", 0f);
		panel.addUIElement(status).inTL(footer.getX() + width + 10f, footer.getY());
	}

	@Override
	protected void refreshPage() {
		contentRect.removeLatestSubPanel();
		attachEditor(contentRect.createSubPanel(panel, panel.getPlugin()).getPanel());
	}

	@Override
	protected void handlePageAction(Object action) {
		if ("settings_save".equals(action)) save();
	}

	@Override
	protected boolean isPageDirty() {
		return editor.hasUnsavedChanges();
	}

	@Override
	protected boolean savePage() {
		boolean cnText = BattleNext_Settings.isCnTextEnabled();
		boolean result = editor.save();
		setStatus(editor.getStatusText());
		if (result && cnText != BattleNext_Settings.isCnTextEnabled()) {
			createContents();
		}
		return result;
	}

	@Override
	protected void discardPage() {
		editor.discard();
	}
}
