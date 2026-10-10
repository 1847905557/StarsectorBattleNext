package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

public class BattleNext_SettingsEditor extends BattleNext_EditorView {

	private final BattleNext_I18nSection settingsStrings = BattleNext_I18nSection.getInstance("Settings", "BattleNext_settings_");

	private ButtonAPI cnTextCheckbox;
	private ButtonAPI fullGraphicEffectCheckbox;
	private ButtonAPI infoLogCheckbox;
	private ButtonAPI rttInBattleCheckbox;
	private TextFieldAPI battleTimeLimitField;
	private TextFieldAPI defaultTimeMultField;
	private TextFieldAPI boostTimeMultField;
	private TextFieldAPI reportKeepCountField;
	private TextFieldAPI deploymentPointLimitField;

	public void open() {
		beginSession();
		cnTextCheckbox = null;
		fullGraphicEffectCheckbox = null;
		infoLogCheckbox = null;
		rttInBattleCheckbox = null;
	}

	@Override
	protected void buildEditorContent() {

		boolean cnText = cnTextCheckbox == null ? BattleNext_Settings.isCnTextEnabled() : cnTextCheckbox.isChecked();
		boolean graphics = fullGraphicEffectCheckbox == null ? BattleNext_Settings.isFullGraphicEffect() : fullGraphicEffectCheckbox.isChecked();
		boolean logging = infoLogCheckbox == null ? BattleNext_Settings.isInfoLogEnabled() : infoLogCheckbox.isChecked();
		boolean rtt = rttInBattleCheckbox == null ? BattleNext_Settings.isRttInBattle() : rttInBattleCheckbox.isChecked();
		String[] values = fullGraphicEffectCheckbox == null
				? new String[]{Float.toString(BattleNext_Settings.getBattleTimeLimit()),
						Float.toString(BattleNext_Settings.getDefaultTimeMult()),
						Float.toString(BattleNext_Settings.getBoostTimeMult()),
						Integer.toString(BattleNext_Settings.getReportKeepCount()),
						Integer.toString(BattleNext_Settings.getDeploymentPointLimit())}
				: new String[]{battleTimeLimitField.getText(), defaultTimeMultField.getText(),
						boostTimeMultField.getText(), reportKeepCountField.getText(), deploymentPointLimitField.getText()};
		float areaWidth = panel.getPosition().getWidth();
		TooltipMakerAPI fieldContent = panel.createUIElement(areaWidth, panel.getPosition().getHeight(), true);
		BattleNext_UiTheme.configure(fieldContent);
		UIRect fieldArea = new UIRect(0f, 0f, areaWidth, BattleNext_PageLayout.FORM_FIELD_HEIGHT);
		fieldContent.setTextWidthOverride(areaWidth);
		BattleNext_PageLayout.contentTitle(fieldContent, settingsStrings.get("heading"), areaWidth, 0f);
		fieldContent.addPara(settingsStrings.get("description"), 10f);
		cnTextCheckbox = createCheckbox("cn_text", fieldContent, fieldArea, settingsStrings.get("cn_text"), "cnText", cnText, true);
		fullGraphicEffectCheckbox = createCheckbox("graphics", fieldContent, fieldArea, settingsStrings.get("full_graphic_effect"), "fullGraphicEffect", graphics, true);
		infoLogCheckbox = createCheckbox("logging", fieldContent, fieldArea, settingsStrings.get("info_log"), "infoLog", logging, true);
		rttInBattleCheckbox = createCheckbox("rtt", fieldContent, fieldArea, settingsStrings.get("rtt_in_battle"), "rttInBattle", rtt, true);
		battleTimeLimitField = createTextField("battle_time_limit", panel, fieldContent, fieldArea, settingsStrings.get("battle_time_limit"), values[0], true, fieldContent);
		deploymentPointLimitField = createTextField("deployment_point_limit", panel, fieldContent, fieldArea, settingsStrings.get("deployment_point_limit"), values[4], true, fieldContent);
		defaultTimeMultField = createTextField("default_time_mult", panel, fieldContent, fieldArea, settingsStrings.get("default_time_mult"), values[1], true, fieldContent);
		boostTimeMultField = createTextField("boost_time_mult", panel, fieldContent, fieldArea, settingsStrings.get("boost_time_mult"), values[2], true, fieldContent);
		reportKeepCountField = createTextField("report_keep_count", panel, fieldContent, fieldArea, settingsStrings.get("report_keep_count"), values[3], true, fieldContent);
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
		return cnTextCheckbox.isChecked() + "|" + fullGraphicEffectCheckbox.isChecked() + "|" + infoLogCheckbox.isChecked() + "|" + rttInBattleCheckbox.isChecked() + "|" + battleTimeLimitField.getText() + "|"
				+ defaultTimeMultField.getText() + "|" + boostTimeMultField.getText() + "|" + reportKeepCountField.getText() + "|" + deploymentPointLimitField.getText();
	}

	@Override
	protected boolean submitDraft() {
		try {
			float timeLimit = parseNonNegative(battleTimeLimitField.getText());
			float defaultMult = parsePositive(defaultTimeMultField.getText());
			float boostMult = parsePositive(boostTimeMultField.getText());
			int keepCount = parseCount(reportKeepCountField.getText());
			int deploymentPointLimit = Integer.parseInt(deploymentPointLimitField.getText().trim());
			BattleNext_Settings.save(cnTextCheckbox.isChecked(), fullGraphicEffectCheckbox.isChecked(), infoLogCheckbox.isChecked(), rttInBattleCheckbox.isChecked(),
					timeLimit, defaultMult, boostMult, keepCount, deploymentPointLimit);
			return true;
		} catch (RuntimeException exception) {
			statusText = settingsStrings.get("invalid_number") + ": " + exception.getMessage();
			return false;
		}
	}

	@Override
	protected void restoreDraft() {
		BattleNext_Settings.load();
		cnTextCheckbox = null;
		fullGraphicEffectCheckbox = null;
		infoLogCheckbox = null;
		rttInBattleCheckbox = null;
	}
}
