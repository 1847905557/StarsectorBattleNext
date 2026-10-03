package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;

public class BattleNext_SettingsPage extends BattleNext_PageView {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection settingsStrings = BattleNext_I18nSection.getInstance("Settings", "BattleNext_settings_");

	private static final float DESCRIPTION_TOP_GAP = 10f;
	private static final float GRAPHICS_CHECKBOX_WIDTH_RESERVE = 20f;
	private static final float GRAPHICS_CHECKBOX_HEIGHT = 30f;
	private static final float GRAPHICS_CHECKBOX_TOP_GAP = 6f;
	private static final float SETTINGS_FOOTER_BOTTOM_INSET = 4f;
	private static final float STATUS_TOP_GAP = 8f;
	private static final float FIELD_LABEL_TOP_GAP = 6f;
	private static final float FIELD_MAX_WIDTH = 260f;
	private static final float FIELD_WIDTH_RESERVE = 20f;
	private static final int FIELD_MAX_CHARS = 16;

	private UIRect contentRect;
	private CustomPanelAPI contentPanel;
	private ButtonAPI saveButton;
	private ButtonAPI fullGraphicEffect;
	private ButtonAPI infoLog;
	private ButtonAPI rttInBattle;
	private TextFieldAPI battleTimeLimit;
	private TextFieldAPI defaultTimeMult;
	private TextFieldAPI boostTimeMult;
	private TextFieldAPI reportKeepCount;
	private String savedState = "";

	@Override
	protected void loadPageState() {
		fullGraphicEffect = null;
		infoLog = null;
		rttInBattle = null;
		status = "";
		BattleNext_Settings.load();
	}

	@Override
	protected void establishPageState() {
		savedState = serializeState();
	}

	@Override
	protected void buildPage() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), BattleNext_PageLayout.HEADER_HEIGHT),
				panel, managementStrings.get("page_settings"));
		UIRect footerRect = new UIRect(0f,
				root.getHeight() - BattleNext_PageLayout.FOOTER_HEIGHT
						- SETTINGS_FOOTER_BOTTOM_INSET,
				root.getWidth(), BattleNext_PageLayout.FOOTER_HEIGHT);
		contentRect = new UIRect(0f, BattleNext_PageLayout.CONTENT_TOP, root.getWidth(),
				footerRect.getY() - BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.FOOTER_MARGIN);
		float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(footerRect, 1);
		saveButton = BattleNext_PageLayout.fixedButton(panel,
				BattleNext_PageLayout.managementFooterButtonRect(footerRect, buttonWidth, 0),
				strings.get("save"), "settings_save", CutStyle.ALL, true);
	}

	@Override
	protected void refreshPage() {

		boolean graphics = fullGraphicEffect == null ? BattleNext_Settings.isFullGraphicEffect() : fullGraphicEffect.isChecked();
		boolean logging = infoLog == null ? BattleNext_Settings.isInfoLogEnabled() : infoLog.isChecked();
		boolean rtt = rttInBattle == null ? BattleNext_Settings.isRttInBattle() : rttInBattle.isChecked();
		String[] values = fullGraphicEffect == null
				? new String[]{Float.toString(BattleNext_Settings.getBattleTimeLimit()),
						Float.toString(BattleNext_Settings.getDefaultTimeMult()),
						Float.toString(BattleNext_Settings.getBoostTimeMult()),
						Integer.toString(BattleNext_Settings.getReportKeepCount())}
				: new String[]{battleTimeLimit.getText(), defaultTimeMult.getText(),
						boostTimeMult.getText(), reportKeepCount.getText()};
		contentRect.removeLatestSubPanel();
		contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI content = contentPanel.createUIElement(contentRect.getWidth(), contentRect.getHeight(), true);
		BattleNext_UiTheme.configure(content);
		content.addTitle(settingsStrings.get("heading"));
		content.addPara(settingsStrings.get("description"), DESCRIPTION_TOP_GAP);
		fullGraphicEffect = content.addCheckbox(contentRect.getWidth() - GRAPHICS_CHECKBOX_WIDTH_RESERVE,
				GRAPHICS_CHECKBOX_HEIGHT,
				settingsStrings.get("full_graphic_effect"), "fullGraphicEffect", ButtonAPI.UICheckboxSize.SMALL,
				GRAPHICS_CHECKBOX_TOP_GAP);
		fullGraphicEffect.setChecked(graphics);
		infoLog = content.addCheckbox(contentRect.getWidth() - GRAPHICS_CHECKBOX_WIDTH_RESERVE,
				GRAPHICS_CHECKBOX_HEIGHT,
				settingsStrings.get("info_log"), "infoLog", ButtonAPI.UICheckboxSize.SMALL,
				GRAPHICS_CHECKBOX_TOP_GAP);
		infoLog.setChecked(logging);
		rttInBattle = content.addCheckbox(contentRect.getWidth() - GRAPHICS_CHECKBOX_WIDTH_RESERVE,
				GRAPHICS_CHECKBOX_HEIGHT,
				settingsStrings.get("rtt_in_battle"), "rttInBattle", ButtonAPI.UICheckboxSize.SMALL,
				GRAPHICS_CHECKBOX_TOP_GAP);
		rttInBattle.setChecked(rtt);
		battleTimeLimit = field(content, "battle_time_limit", values[0]);
		defaultTimeMult = field(content, "default_time_mult", values[1]);
		boostTimeMult = field(content, "boost_time_mult", values[2]);
		reportKeepCount = field(content, "report_keep_count", values[3]);
		statusLabel = content.addPara(status, STATUS_TOP_GAP);
		contentPanel.addUIElement(content).inTL(0f, 0f);
	}

	@Override
	protected void updatePageControls() {
		saveButton.setEnabled(true);
	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if ("settings_save".equals(buttonId)) save();
	}

	@Override
	protected boolean isPageDirty() {
		return !serializeState().equals(savedState);
	}

	@Override
	protected boolean savePage() {
		try {
			float timeLimit = parseNonNegative(battleTimeLimit.getText());
			float defaultMult = parsePositive(defaultTimeMult.getText());
			float boostMult = parsePositive(boostTimeMult.getText());
			int keepCount = parseCount(reportKeepCount.getText());
			BattleNext_Settings.save(fullGraphicEffect.isChecked(), infoLog.isChecked(), rttInBattle.isChecked(),
					timeLimit, defaultMult, boostMult, keepCount);
			savedState = serializeState();
			status = strings.get("saved");
			return true;
		} catch (RuntimeException exception) {
			status = strings.get("save_failed") + ": " + exception.getMessage();
			return false;
		}
	}

	@Override
	protected void discardPage() {
		BattleNext_Settings.load();
		fullGraphicEffect.setChecked(BattleNext_Settings.isFullGraphicEffect());
		infoLog.setChecked(BattleNext_Settings.isInfoLogEnabled());
		rttInBattle.setChecked(BattleNext_Settings.isRttInBattle());
		battleTimeLimit.setText(Float.toString(BattleNext_Settings.getBattleTimeLimit()));
		defaultTimeMult.setText(Float.toString(BattleNext_Settings.getDefaultTimeMult()));
		boostTimeMult.setText(Float.toString(BattleNext_Settings.getBoostTimeMult()));
		reportKeepCount.setText(Integer.toString(BattleNext_Settings.getReportKeepCount()));
		savedState = serializeState();
	}	private TextFieldAPI field(TooltipMakerAPI content, String labelKey, String value) {
		content.addPara(settingsStrings.get(labelKey), FIELD_LABEL_TOP_GAP);
		float width = Math.min(FIELD_MAX_WIDTH, contentRect.getWidth() - FIELD_WIDTH_RESERVE);
		BattleNext_TextField widget = new BattleNext_TextField(inputRouter, contentPanel, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, content);
		widget.attach(content, contentPanel);
		widget.setText(value);
		TextFieldAPI field = widget;
		field.setMaxChars(FIELD_MAX_CHARS);
		return field;
	}

	private String serializeState() {
		if (fullGraphicEffect == null) return "";
		return fullGraphicEffect.isChecked() + "|" + infoLog.isChecked() + "|" + rttInBattle.isChecked() + "|" + battleTimeLimit.getText() + "|"
				+ defaultTimeMult.getText() + "|" + boostTimeMult.getText() + "|" + reportKeepCount.getText();
	}

	private float parseNonNegative(String value) {
		float result = Float.parseFloat(value.trim());
		if (!Float.isFinite(result) || result < 0f) throw new IllegalArgumentException(settingsStrings.get("invalid_number"));
		return result;
	}

	private float parsePositive(String value) {
		float result = Float.parseFloat(value.trim());
		if (!Float.isFinite(result) || result <= 0f) throw new IllegalArgumentException(settingsStrings.get("invalid_number"));
		return result;
	}

	private int parseCount(String value) {
		int result = Integer.parseInt(value.trim());
		if (result < 0) throw new IllegalArgumentException(settingsStrings.get("invalid_number"));
		return result;
	}
}
