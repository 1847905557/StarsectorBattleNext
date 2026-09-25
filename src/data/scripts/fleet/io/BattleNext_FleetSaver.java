package data.scripts.fleet.io;

import com.fs.starfarer.api.Global;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class BattleNext_FleetSaver {

	public String save(BattleNext_FleetModel fleet) throws IOException, JSONException {
		String directory = "AI_Battles_Next/" + fleet.getId() + "/";
		String path = directory + "fleet_data.json";
		if (Global.getSettings().fileExistsInCommon(path)) {
			JSONObject previous = Global.getSettings().readJSONFromCommon(path, false);
			Global.getSettings().writeJSONToCommon(directory + "fleet_data.json.bak", previous, false);
		}
		Global.getSettings().writeJSONToCommon(path, fleet.toJson(), false);
		for (BattleNext_VariantModel variant : fleet.getVariants()) {
			Global.getSettings().writeTextFileToCommon(directory + variant.getId() + ".variant", variant.toJson().toString(2));
		}
		return path;
	}

	public void delete(BattleNext_FleetModel fleet) throws IOException {
		String directory = "AI_Battles_Next/" + fleet.getId() + "/";
		Global.getSettings().deleteTextFileFromCommon(directory + "fleet_data.json");
		if (Global.getSettings().fileExistsInCommon(directory + "fleet_data.json")) {
			throw new IOException("Fleet file remains after deletion: " + fleet.getId());
		}
		Global.getSettings().deleteTextFileFromCommon(directory + "fleet_data.json.bak");
		for (BattleNext_VariantModel variant : fleet.getVariants()) {
			Global.getSettings().deleteTextFileFromCommon(directory + variant.getId() + ".variant");
		}
	}
}
