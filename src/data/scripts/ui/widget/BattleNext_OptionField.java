package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_OptionField {

	public enum OptionSelectionMode {
		SINGLE, MULTIPLE
	}

	private static final float PICKER_BUTTON_WIDTH = 32f;
	private static final float PICKER_ROW_TOP_GAP = 2f;

	private final BattleNext_UiInputRouter inputRouter;
	private final CustomPanelAPI overlayHost;
	private final BattleNext_OptionPicker.OptionChoiceSource source;
	private final OptionSelectionMode mode;
	private BattleNext_OptionPicker picker;
	private TextFieldAPI field;

	public BattleNext_OptionField(CustomPanelAPI overlayHost, BattleNext_OptionPicker.OptionChoiceSource source,
			OptionSelectionMode mode) {
		this(BattleNext_UiInputRouter.current(), overlayHost, source, mode);
	}

	public BattleNext_OptionField(BattleNext_UiInputRouter inputRouter, CustomPanelAPI overlayHost,
			BattleNext_OptionPicker.OptionChoiceSource source, OptionSelectionMode mode) {
		this.inputRouter = inputRouter;
		this.overlayHost = overlayHost;
		this.source = source;
		this.mode = mode;
	}

	public TextFieldAPI addTo(TooltipMakerAPI host, CustomPanelAPI owner, float width, String value) {
		return addTo(host, owner, width, value, new TooltipMakerAPI[0]);
	}

	public TextFieldAPI addTo(TooltipMakerAPI host, CustomPanelAPI owner, float width, String value,
			TooltipMakerAPI... viewports) {
		float inputWidth = width - PICKER_BUTTON_WIDTH;
		CustomPanelAPI row = owner.createCustomPanel(width, BattleNext_PageLayout.FORM_FIELD_HEIGHT,
				owner.getPlugin());
		TooltipMakerAPI input = row.createUIElement(inputWidth, BattleNext_PageLayout.FORM_FIELD_HEIGHT, false);
		field = BattleNext_TextField.addTo(inputRouter, input, row, inputWidth, BattleNext_PageLayout.FORM_FIELD_HEIGHT,
				value == null ? "" : value, viewports);
		field.getPosition().inTL(0f, 0f);
		field.setLimitByStringWidth(false);
		field.setVerticalCursor(true);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		row.addUIElement(input).inTL(0f, 0f);
		picker = new BattleNext_OptionPicker(inputRouter, overlayHost, source,
				toPickerMode(mode), () -> parse(field.getText()), ids -> field.setText(expand(ids)));
		picker.addButton(row, new UIRect(inputWidth, 0f, PICKER_BUTTON_WIDTH,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT), "...", viewports);
		host.addCustom(row, PICKER_ROW_TOP_GAP);
		return field;
	}

	public void close() {
		inputRouter.releaseFocus();
		if (picker != null) picker.close();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
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
