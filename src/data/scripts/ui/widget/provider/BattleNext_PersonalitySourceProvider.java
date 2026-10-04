package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleNext_PersonalitySourceProvider extends BattleNext_BaseSourceProvider {

	private static final String PERSONALITIES_PATH = "data/characters/personalities.csv";

	@Override
	public int getMaxStack(String id) {
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
	public boolean expectsIcon() {
		return false;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
		JSONObject row = findRow(option.getId());
		addHeader(tooltip, option.getLabel(), option.getId());
		if (row != null) {
			String description = row.optString("desc", "").trim();
			addDescription(tooltip, description);
		}
	}

	private List<JSONObject> rows() {
		// Starsector exposes no API for enumerating all personalities; read the merged vanilla CSV instead.
		List<JSONObject> result = new ArrayList<>();
		try {
			JSONArray rows = BattleNext_ModFileManager.readMergedCsv(BattleNext_PersonalitySourceProvider.class, "id", PERSONALITIES_PATH);
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
		if (id.isEmpty()) {
			BattleNext_Logger.warn(BattleNext_PersonalitySourceProvider.class, "personality.identity.rejected",
				BattleNext_Logger.fields().put("row", row).build());
			return null;
		}
		String name = row.optString("name", "").trim();
		if (name.isEmpty()) {
			BattleNext_Logger.warn(BattleNext_PersonalitySourceProvider.class, "personality.name.fallback",
				BattleNext_Logger.fields().put("row", row).put("name", id).build());
		}
		return new OptionChoice(id, name.isEmpty() ? id : name);
	}
}
