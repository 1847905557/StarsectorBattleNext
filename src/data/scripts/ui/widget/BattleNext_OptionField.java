package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.ui.UIRect;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_OptionField {

	public enum OptionSelectionMode {
		SINGLE, MULTIPLE
	}

	private static final float FIELD_HEIGHT = 28f;
	private static final float BUTTON_WIDTH = 32f;

	private final CustomPanelAPI overlayHost;
	private final BattleNext_OptionPicker.OptionChoiceSource source;
	private final OptionSelectionMode mode;
	private BattleNext_OptionPicker picker;
	private TextFieldAPI field;

	public BattleNext_OptionField(CustomPanelAPI overlayHost, BattleNext_OptionPicker.OptionChoiceSource source,
			OptionSelectionMode mode) {
		this.overlayHost = overlayHost;
		this.source = source;
		this.mode = mode;
	}

	public TextFieldAPI addTo(TooltipMakerAPI host, CustomPanelAPI owner, float width, String value) {
		CustomPanelAPI row = owner.createCustomPanel(width, FIELD_HEIGHT, owner.getPlugin());
		TooltipMakerAPI input = row.createUIElement(width - BUTTON_WIDTH, FIELD_HEIGHT, false);
		field = BattleNext_TextField.addTo(input, row, width - BUTTON_WIDTH, FIELD_HEIGHT,
				value == null ? "" : value);
		field.getPosition().inTL(0f, 0f);
		field.setLimitByStringWidth(false);
		field.setVerticalCursor(true);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		row.addUIElement(input).inTL(0f, 0f);
		picker = new BattleNext_OptionPicker(overlayHost, source,
				toPickerMode(mode), () -> parse(field.getText()), ids -> field.setText(expand(ids)));
		picker.addButton(row, new UIRect(width - BUTTON_WIDTH, 0f, BUTTON_WIDTH, FIELD_HEIGHT), "...");
		host.addCustom(row, 2f);
		return field;
	}

	public void close() {
		BattleNext_TextField.releaseFocusedField();
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
