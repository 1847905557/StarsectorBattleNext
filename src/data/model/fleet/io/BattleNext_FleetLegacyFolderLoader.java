package data.model.fleet.io;

import data.model.fleet.BattleNext_FleetModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_FleetLegacyFolderLoader extends BattleNext_FleetLegacyLoader {

	@Override
	public List<BattleNext_FleetModel> load() throws IOException, JSONException {

		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_ModFileManager.list("tournament", true);
		for (String path : paths) {
			String[] segments = path.split("/");
			if (segments.length != 3) continue;
			String folder = segments[1];
			if (BattleNext_EntryId.isReservedFolder(folder)) continue;
			if (!segments[2].matches("player\\d+_data\\.csv")) continue;
			String directory = "tournament/" + folder;
			if (paths.contains(directory + "/fleet_data.json")) continue;
			String number = segments[2].replaceAll("^player(\\d+)_data\\.csv$", "$1");
			String fleetPath = directory + "/player" + number + "_fleet.csv";
			if (!paths.contains(fleetPath)) continue;
			BattleNext_FleetModel fleet = readFleet(number, path, fleetPath);
			fleet.setFolderName(folder);
			BattleNext_FleetVariantLoader.load(fleet, paths);
			result.add(fleet);
		}
		return result;
	}
}
