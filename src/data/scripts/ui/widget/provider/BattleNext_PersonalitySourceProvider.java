package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleNext_PersonalitySourceProvider implements BattleNext_OptionPicker.OptionChoiceSource {

	private static final String PERSONALITIES_PATH = "data/characters/personalities.csv";
	private static final float PERSONALITY_ID_TOOLTIP_GAP = 5f;
	private static final float PERSONALITY_DESCRIPTION_TOOLTIP_GAP = 8f;

	@Override
	public int getMaxCount(String id) {
		return 1;
	}

	@Override
	public List<OptionChoice> getOptions() {
		return toOptions(rows());
	}

	static List<OptionChoice> toOptions(List<JSONObject> rows) {
		List<OptionChoice> options = new ArrayList<>();
		for (JSONObject row : rows) {
			OptionChoice option = toOption(row);
			if (option != null) options.add(option);
		}
		options.sort(Comparator.comparing(OptionChoice::getId));
		return options;
	}

	@Override
	public String getIconPath(String id) {
		return "";
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
		JSONObject row = findRow(option.getId());
		tooltip.addTitle(option.getLabel());
		tooltip.addPara(option.getId(), PERSONALITY_ID_TOOLTIP_GAP, BattleNext_UiTheme.MUTED);
		if (row != null) {
			String description = row.optString("desc", "").trim();
			if (!description.isEmpty()) {
				tooltip.addPara(description, PERSONALITY_DESCRIPTION_TOOLTIP_GAP, BattleNext_UiTheme.TEXT);
			}
		}
	}

	private List<JSONObject> rows() {
		// Starsector exposes no API for enumerating all personalities; read the merged vanilla CSV instead.
		List<JSONObject> result = new ArrayList<>();
		try {
			JSONArray rows = Global.getSettings().getMergedSpreadsheetData("id", PERSONALITIES_PATH);
			for (int index = 0; index < rows.length(); index++) {
				JSONObject row = rows.optJSONObject(index);
				if (row != null) result.add(row);
			}
		} catch (IOException | RuntimeException | JSONException exception) {
			throw new IllegalStateException("Unable to load personalities: " + PERSONALITIES_PATH, exception);
		}
		return result;
	}

	private JSONObject findRow(String id) {
		for (JSONObject row : rows()) {
			if (id.equals(row.optString("id", "").trim())) return row;
		}
		return null;
	}

	static OptionChoice toOption(JSONObject row) {
		if (row == null) return null;
		String id = row.optString("id", "").trim();
		if (id.isEmpty()) return null;
		String name = row.optString("name", "").trim();
		return new OptionChoice(id, name.isEmpty() ? id : name);
	}
}
