package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_OptionField extends BattleNext_TooltipBasedWidget {

	public enum OptionSelectionMode {
		SINGLE, MULTIPLE
	}

	private static final float PICKER_BUTTON_WIDTH = 32f;
	private static final float PICKER_ROW_TOP_GAP = 2f;

	private final BattleNext_OptionPicker.OptionChoiceSource source;
	private final OptionSelectionMode mode;
	private final float fieldWidth;
	private final CustomPanelAPI overlayHost;
	private final TooltipMakerAPI[] viewports;

	private BattleNext_OptionPicker picker;
	private BattleNext_TextField field;
	private CustomPanelAPI row;

	@Override
	protected void buildContent(TooltipMakerAPI host, CustomPanelAPI owner) {
		float buttonWidth = Math.min(PICKER_BUTTON_WIDTH, Math.max(0f, fieldWidth - 1f));
		float inputWidth = fieldWidth - buttonWidth;
		row = ownPanel(host, owner.createCustomPanel(fieldWidth, BattleNext_PageLayout.FORM_FIELD_HEIGHT,
				new BaseCustomUIPanelPlugin()));
		TooltipMakerAPI input = row.createUIElement(inputWidth, BattleNext_PageLayout.FORM_FIELD_HEIGHT, false);
		BattleNext_TextField textField = new BattleNext_TextField(inputRouter, row, inputWidth,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, viewports);
		textField.attach(input, row);
		field = textField;
		field.getPosition().inTL(0f, 0f);
		field.setLimitByStringWidth(false);
		field.setVerticalCursor(true);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		row.addUIElement(input).inTL(0f, 0f);
		TooltipMakerAPI pickerHost = row.createUIElement(buttonWidth,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, false);
		picker = new BattleNext_OptionPicker(inputRouter, overlayHost, buttonWidth,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, "...", 0f, source, toPickerMode(mode),
				() -> parse(field.getText()), ids -> field.setText(expand(ids)), viewports);
		picker.attach(pickerHost, row);
		row.addUIElement(pickerHost).inTL(inputWidth, 0f);
		host.addCustom(row, PICKER_ROW_TOP_GAP);
	}

	public TextFieldAPI getField() {
		return field;
	}

	public BattleNext_OptionField(BattleNext_UiInputRouter inputRouter,
			CustomPanelAPI overlayHost, float fieldWidth,
			BattleNext_OptionPicker.OptionChoiceSource source, OptionSelectionMode mode,
			TooltipMakerAPI... viewports) {
		super(inputRouter);
		this.source = source;
		this.mode = mode;
		this.fieldWidth = fieldWidth;
		this.overlayHost = overlayHost;
		this.viewports = viewports;
	}

	@Override
	protected void clearOverlays() {
		if (picker != null) picker.closeOverlays();
	}

	@Override
	protected void clearContent() {
		clearOverlays();
		if (picker != null) picker.close();
		if (field != null) field.close();
		row = null;
		field = null;
		picker = null;
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		return picker != null && picker.handleGlobalInput(events);
	}

	private BattleNext_OptionPicker.OptionSelectionMode toPickerMode(OptionSelectionMode value) {
		return value == OptionSelectionMode.SINGLE
				? BattleNext_OptionPicker.OptionSelectionMode.SINGLE
				: BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE;
	}

	private List<String> parse(String value) {
		List<String> result = new ArrayList<>();
		if (value == null || value.isBlank()) return result;
		if (mode == OptionSelectionMode.SINGLE) {
			result.add(value.trim());
			return result;
		}
		for (String part : value.split(",")) {
			String id = part.trim();
			if (!id.isEmpty()) result.add(id);
		}
		return result;
	}

	private String expand(List<String> values) {
		return String.join(",", values);
	}
}
