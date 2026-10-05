package data.test.fixture;

import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class BattleNext_TestFleetIoFixture extends BattleNext_TestCampaignFleetFixture {

	@BeforeEach
	protected void registerFleetVariants() {
		registeredVariants.put("parent_variant", selectedVariant.getApi());
		registeredVariants.put("child_variant", selectedVariant.getModules().get("module"));
	}

	protected void write(String path, String text) throws Exception {
		Files.createDirectories(directory.resolve(path).getParent());
		Files.writeString(directory.resolve(path), text, StandardCharsets.UTF_8);
	}

	protected void csvFleet(String folder, String number, String members) throws Exception {
		String prefix = folder.isEmpty() ? "tournament/" : "tournament/" + folder + "/";
		write(prefix + "player" + number + "_data.csv", "name,author,skills,tags\nTeam,Author,,\n");
		write(prefix + "player" + number + "_fleet.csv", "rowNumber,variant,personality,skills,name,portrait,orders,tags\n" + members);
	}

	protected void standard(String folder, String id, String... variants) throws Exception {
		List<BattleNext_MemberModel> members = Arrays.stream(variants)
				.map(variant -> new BattleNext_MemberModel(variant, "", "", List.of(), "", "", List.of())).toList();
		BattleNext_FleetModel fleet = new BattleNext_FleetModel(id, folder, "Standard", List.of(), List.of(), members);
		write("tournament/" + folder + "/fleet_data.json", fleet.toJson().toString());
	}

	protected void variant(String folder, String id, String hull, int vents, Object modules) throws Exception {
		JSONObject json = new JSONObject().put("variantId", id).put("hullId", hull).put("fluxVents", vents);
		if (modules != null) {
			json.put("modules", modules);
		}
		write("tournament/" + folder + "/" + id + ".variant", json.toString());
	}
}
