package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_SettingsEditor;
import data.simulator.BattleNext_SimulatorWidget;
import data.simulator.BattleNext_SimulatorBrowser;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.function.Consumer;
import java.util.List;

public class BattleNext_SettingsPage extends BattleNext_PageView {

	private static final float SETTINGS_WIDTH_SHARE = 0.28f;

	private final Consumer<Runnable> confirmation;
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_SettingsEditor editor = new BattleNext_SettingsEditor();
	private final BattleNext_SimulatorBrowser simulatorBrowser = new BattleNext_SimulatorBrowser();
	private final BattleNext_BattleSourceProvider simulatorSources = new BattleNext_BattleSourceProvider();

	private UIRect contentRect;
	private UIRect simulatorRect;
	private BattleNext_SimulatorWidget simulatorWidget;
	private boolean simulatorUnlocked;

	public BattleNext_SettingsPage(Consumer<Runnable> confirmation) {
		this.confirmation = confirmation;
	}

	@Override
	protected void loadPageState() {
		BattleNext_Settings.load();
		editor.open();
		if (simulatorUnlocked) {
			simulatorSources.reloadCatalogs();
		}
	}

	@Override
	protected BattleNext_EditorView editorView() {
		return editor;
	}

	@Override
	protected void buildPage() {

		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), BattleNext_PageLayout.HEADER_HEIGHT), panel, managementStrings.get("page_settings"));
		UIRect area = BattleNext_PageLayout.pageContentRect(new UIRect(0f, BattleNext_PageLayout.CONTENT_TOP,
				root.getWidth(), root.getHeight() - BattleNext_PageLayout.CONTENT_TOP - 4f));
		float columnWidth = (float) Math.floor((area.getWidth() - BattleNext_PageLayout.EDITOR_COLUMN_GAP) * SETTINGS_WIDTH_SHARE);
		UIRect footer = new UIRect(area.getX(), area.getY() + area.getHeight() - BattleNext_PageLayout.FOOTER_HEIGHT,
				columnWidth, BattleNext_PageLayout.FOOTER_HEIGHT);
		contentRect = new UIRect(area.getX(), area.getY(), columnWidth,
				area.getHeight() - BattleNext_PageLayout.FOOTER_HEIGHT - BattleNext_PageLayout.FOOTER_MARGIN);
		simulatorRect = new UIRect(area.getX() + columnWidth + BattleNext_PageLayout.EDITOR_COLUMN_GAP, area.getY(),
				area.getWidth() - columnWidth - BattleNext_PageLayout.EDITOR_COLUMN_GAP, area.getHeight());
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
		simulatorRect.removeLatestSubPanel();
		attachEditor(contentRect.createSubPanel(panel, panel.getPlugin()).getPanel());
		simulatorWidget = new BattleNext_SimulatorWidget(inputRouter, overlayHost == null ? panel : overlayHost,
				() -> simulatorUnlocked, simulatorBrowser, simulatorSources);
		simulatorWidget.attach(simulatorRect.createSubPanel(panel, panel.getPlugin()).getPanel());
	}

	@Override
	protected void clearPageResources() {
		if (simulatorWidget != null) {
			simulatorWidget.close();
			simulatorWidget = null;
		}
	}

	@Override
	protected void handlePageAction(Object action) {
		if ("settings_save".equals(action)) {
			save();
		}
		if ("simulator_enable".equals(action) && !simulatorUnlocked) {
			confirmation.accept(() -> {
				simulatorUnlocked = true;
				simulatorSources.reloadCatalogs();
				simulatorWidget.refreshContent();
				BattleNext_Logger.info(BattleNext_SettingsPage.class, "simulator.ui.unlocked",
						BattleNext_Logger.fields().put("scope", "management_window").build());
			});
		}
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

	@Override
	protected void clearPageOverlays() {
		editor.closeOverlays();
		if (simulatorWidget != null) {
			simulatorWidget.closeOverlays();
		}
	}

	@Override
	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return simulatorWidget != null && simulatorWidget.handleGlobalInput(events);
	}

	@Override
	public void advance(float amount) {
		if (simulatorWidget != null) {
			simulatorWidget.refreshStatus();
		}
	}
}
