package data.scripts.ui.page;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;

public class BattleNext_SettingsPage implements BattleNext_PageView {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private ButtonAPI fullGraphicEffect;
	private TextFieldAPI battleTimeLimit;
	private TextFieldAPI defaultTimeMult;
	private TextFieldAPI boostTimeMult;
	private TextFieldAPI reportKeepCount;
	private String savedState = "";
	private String status = "";
	private LabelAPI statusLabel;

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		load();
		createContents();
		savedState = serializeState();
	}

	@Override
	public void createContents() {
		BattleNext_TextField.clearRegisteredFields();
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		BattleNext_PageLayout.heading(header[0], panel, strings.get("page_settings"));
		UIRect contentRect = rest[1];
		TooltipMakerAPI content = BattleNext_PageLayout.column(contentRect, panel);
		content.addTitle(strings.get("settings_heading"));
		content.addPara(strings.get("settings_description"), 10f, BattleNext_UiTheme.TEXT);
		fullGraphicEffect = content.addCheckbox(contentRect.getWidth() - 20f, 30f,
				strings.get("settings_full_graphic_effect"), "fullGraphicEffect", ButtonAPI.UICheckboxSize.SMALL, 6f);
		fullGraphicEffect.setChecked(BattleNext_Settings.isFullGraphicEffect());
		battleTimeLimit = field(content, "settings_battle_time_limit", Float.toString(BattleNext_Settings.getBattleTimeLimit()));
		defaultTimeMult = field(content, "settings_default_time_mult", Float.toString(BattleNext_Settings.getDefaultTimeMult()));
		boostTimeMult = field(content, "settings_boost_time_mult", Float.toString(BattleNext_Settings.getBoostTimeMult()));
		reportKeepCount = field(content, "settings_report_keep_count", Integer.toString(BattleNext_Settings.getReportKeepCount()));
		UIRect footer = new UIRect(contentRect.getX(), contentRect.getY() + contentRect.getHeight() - 34f,
				contentRect.getWidth(), 30f);
		BattleNext_PageLayout.fixedButton(panel, footer, strings.get("save"), "settings_save",
				com.fs.starfarer.api.ui.CutStyle.ALL, true);
		statusLabel = content.addPara(status, 8f, BattleNext_UiTheme.MUTED);
	}

	private TextFieldAPI field(TooltipMakerAPI content, String labelKey, String value) {
		content.addPara(strings.get(labelKey), 6f, BattleNext_UiTheme.TEXT);
		TextFieldAPI field = BattleNext_TextField.addTo(content, panel,
				Math.min(260f, content.getPosition().getWidth() - 20f), 28f, value, content);
		field.setMaxChars(16);
		return field;
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("settings_save".equals(buttonId)) save();
	}

	@Override
	public boolean hasUnsavedChanges() {
		return !serializeState().equals(savedState);
	}

	@Override
	public boolean save() {
		try {
			float timeLimit = parseNonNegative(battleTimeLimit.getText());
			float defaultMult = parsePositive(defaultTimeMult.getText());
			float boostMult = parsePositive(boostTimeMult.getText());
			int keepCount = parseCount(reportKeepCount.getText());
			BattleNext_Settings.save(fullGraphicEffect.isChecked(), timeLimit, defaultMult, boostMult, keepCount);
			savedState = serializeState();
			status = strings.get("saved");
			statusLabel.setText(status);
			return true;
		} catch (RuntimeException exception) {
			status = strings.get("save_failed") + ": " + exception.getMessage();
			statusLabel.setText(status);
			return false;
		}
	}

	@Override
	public void discard() {
		load();
		fullGraphicEffect.setChecked(BattleNext_Settings.isFullGraphicEffect());
		battleTimeLimit.setText(Float.toString(BattleNext_Settings.getBattleTimeLimit()));
		defaultTimeMult.setText(Float.toString(BattleNext_Settings.getDefaultTimeMult()));
		boostTimeMult.setText(Float.toString(BattleNext_Settings.getBoostTimeMult()));
		reportKeepCount.setText(Integer.toString(BattleNext_Settings.getReportKeepCount()));
		savedState = serializeState();
	}

	@Override
	public String getStatusText() {
		return status;
	}

	@Override
	public void refreshContent() {
		if (statusLabel != null) statusLabel.setText(status);
	}

	private void load() {
		BattleNext_Settings.load();
	}

	private String serializeState() {
		if (fullGraphicEffect == null) return "";
		return fullGraphicEffect.isChecked() + "|" + battleTimeLimit.getText() + "|"
				+ defaultTimeMult.getText() + "|" + boostTimeMult.getText() + "|" + reportKeepCount.getText();
	}

	private float parseNonNegative(String value) {
		float result = Float.parseFloat(value.trim());
		if (!Float.isFinite(result) || result < 0f) throw new IllegalArgumentException(strings.get("settings_invalid_number"));
		return result;
	}

	private float parsePositive(String value) {
		float result = Float.parseFloat(value.trim());
		if (!Float.isFinite(result) || result <= 0f) throw new IllegalArgumentException(strings.get("settings_invalid_number"));
		return result;
	}

	private int parseCount(String value) {
		int result = Integer.parseInt(value.trim());
		if (result < 0) throw new IllegalArgumentException(strings.get("settings_invalid_number"));
		return result;
	}
}
