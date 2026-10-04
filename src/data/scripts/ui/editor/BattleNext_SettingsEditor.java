package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_UiTheme;

public class BattleNext_SettingsEditor extends BattleNext_EditorView {

	private final BattleNext_I18nSection settingsStrings = BattleNext_I18nSection.getInstance("Settings", "BattleNext_settings_");

	private ButtonAPI fullGraphicEffectCheckbox;
	private ButtonAPI infoLogCheckbox;
	private ButtonAPI rttInBattleCheckbox;
	private TextFieldAPI battleTimeLimitField;
	private TextFieldAPI defaultTimeMultField;
	private TextFieldAPI boostTimeMultField;
	private TextFieldAPI reportKeepCountField;

	public void open() {
		beginSession();
		fullGraphicEffectCheckbox = null;
		infoLogCheckbox = null;
		rttInBattleCheckbox = null;
	}

	@Override
	protected void buildEditorContent() {
		boolean graphics = fullGraphicEffectCheckbox == null ? BattleNext_Settings.isFullGraphicEffect() : fullGraphicEffectCheckbox.isChecked();
		boolean logging = infoLogCheckbox == null ? BattleNext_Settings.isInfoLogEnabled() : infoLogCheckbox.isChecked();
		boolean rtt = rttInBattleCheckbox == null ? BattleNext_Settings.isRttInBattle() : rttInBattleCheckbox.isChecked();
		String[] values = fullGraphicEffectCheckbox == null
				? new String[]{Float.toString(BattleNext_Settings.getBattleTimeLimit()),
						Float.toString(BattleNext_Settings.getDefaultTimeMult()),
						Float.toString(BattleNext_Settings.getBoostTimeMult()),
						Integer.toString(BattleNext_Settings.getReportKeepCount())}
				: new String[]{battleTimeLimitField.getText(), defaultTimeMultField.getText(),
						boostTimeMultField.getText(), reportKeepCountField.getText()};
		float areaWidth = panel.getPosition().getWidth();
		TooltipMakerAPI fieldContent = panel.createUIElement(areaWidth, panel.getPosition().getHeight(), true);
		BattleNext_UiTheme.configure(fieldContent);
		fieldContent.addTitle(settingsStrings.get("heading"));
		fieldContent.addPara(settingsStrings.get("description"), 10f);
		fullGraphicEffectCheckbox = createCheckbox("graphics", fieldContent, areaWidth, settingsStrings.get("full_graphic_effect"), "fullGraphicEffect", graphics, true);
		infoLogCheckbox = createCheckbox("logging", fieldContent, areaWidth, settingsStrings.get("info_log"), "infoLog", logging, true);
		rttInBattleCheckbox = createCheckbox("rtt", fieldContent, areaWidth, settingsStrings.get("rtt_in_battle"), "rttInBattle", rtt, true);
		battleTimeLimitField = createTextField("battle_time_limit", panel, fieldContent, areaWidth, settingsStrings.get("battle_time_limit"), values[0], true, fieldContent);
		defaultTimeMultField = createTextField("default_time_mult", panel, fieldContent, areaWidth, settingsStrings.get("default_time_mult"), values[1], true, fieldContent);
		boostTimeMultField = createTextField("boost_time_mult", panel, fieldContent, areaWidth, settingsStrings.get("boost_time_mult"), values[2], true, fieldContent);
		reportKeepCountField = createTextField("report_keep_count", panel, fieldContent, areaWidth, settingsStrings.get("report_keep_count"), values[3], true, fieldContent);
		panel.addUIElement(fieldContent).inTL(0f, 0f);
		bindViewport("settings", fieldContent);
	}

	private float parseNonNegative(String value) {
		float result = Float.parseFloat(value.trim());
		if (!Float.isFinite(result) || result < 0f) throw new IllegalArgumentException("Expected a finite nonnegative number: " + value);
		return result;
	}

	private float parsePositive(String value) {
		float result = Float.parseFloat(value.trim());
		if (!Float.isFinite(result) || result <= 0f) throw new IllegalArgumentException("Expected a finite positive number: " + value);
		return result;
	}

	private int parseCount(String value) {
		int result = Integer.parseInt(value.trim());
		if (result < 0) throw new IllegalArgumentException("Expected a nonnegative integer: " + value);
		return result;
	}

	@Override
	public void buttonPressed(Object action) {

	}

	@Override
	protected String draftState() {
		if (fullGraphicEffectCheckbox == null) return "";
		return fullGraphicEffectCheckbox.isChecked() + "|" + infoLogCheckbox.isChecked() + "|" + rttInBattleCheckbox.isChecked() + "|" + battleTimeLimitField.getText() + "|"
				+ defaultTimeMultField.getText() + "|" + boostTimeMultField.getText() + "|" + reportKeepCountField.getText();
	}

	@Override
	protected boolean submitDraft() {
		try {
			float timeLimit = parseNonNegative(battleTimeLimitField.getText());
			float defaultMult = parsePositive(defaultTimeMultField.getText());
			float boostMult = parsePositive(boostTimeMultField.getText());
			int keepCount = parseCount(reportKeepCountField.getText());
			BattleNext_Settings.save(fullGraphicEffectCheckbox.isChecked(), infoLogCheckbox.isChecked(), rttInBattleCheckbox.isChecked(),
					timeLimit, defaultMult, boostMult, keepCount);
			return true;
		} catch (RuntimeException exception) {
			statusText = settingsStrings.get("invalid_number") + ": " + exception.getMessage();
			return false;
		}
	}

	@Override
	protected void restoreDraft() {
		BattleNext_Settings.load();
		fullGraphicEffectCheckbox = null;
		infoLogCheckbox = null;
		rttInBattleCheckbox = null;
	}
}
