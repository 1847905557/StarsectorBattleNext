package data.simulator;

import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.BattleNext_MissionBattleMap;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.order.BattleNext_FleetOrderBehavior;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.plugin.BattleNext_EnvironmentPlugin;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_SimulatorMission {

	private BattleNext_SimulatorMission() {
	}

	public static BattleNext_ScorekeeperPlugin define(MissionDefinitionAPI api, BattleNext_SimulationRequest request) {

		if (request.kind() == BattleNext_SimulationRequest.Kind.VARIANTS) return define(api, request.player(), request.enemy(), request.config());
		if (request.kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) throw new UnsupportedOperationException("Tournament simulation is not supported");
		try {
			JSONObject source = request.source();
			BattleNext_AssembledFleet player = assemble(source.getJSONObject("player"));
			BattleNext_AssembledFleet enemy = assemble(source.getJSONObject("enemy"));
			BattleNext_FleetBuilder builder = new BattleNext_FleetBuilder();
			builder.addToMission(api, FleetSide.PLAYER, player, player.getDefinition().getName());
			builder.addToMission(api, FleetSide.ENEMY, enemy, enemy.getDefinition().getName());
			BattleNext_MissionBattleMap.apply(api, source.getInt("actualMap"), (float) source.getJSONObject("match").getDouble("mapScale"));
			api.addPlugin(new BattleNext_DeploymentPlugin());
			api.addPlugin(new BattleNext_EnvironmentPlugin(request.config().assaultAfterSeconds()));
			BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(player, enemy);
			api.addPlugin(scorekeeper);
			api.addPlugin(new BattleNext_FleetOrderBehavior(BattleNext_FleetOrderBehavior.fromTags(player.getDefinition().getTags()),
					BattleNext_FleetOrderBehavior.fromTags(enemy.getDefinition().getTags()), player, enemy));
			return scorekeeper;
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Invalid match simulation snapshot: " + request.id(), exception);
		}
	}

	private static BattleNext_AssembledFleet assemble(JSONObject snapshot) throws JSONException {

		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.fromJson(snapshot.getJSONObject("definition"));
		fleet.setFolderName(snapshot.getString("folderName"));
		fleet.setSourceType(BattleNext_LoadSource.valueOf(snapshot.getString("sourceType")));
		fleet.setSourcePath(snapshot.getString("sourcePath"));
		JSONObject variants = snapshot.getJSONObject("variants");
		for (int index = 0; index < fleet.getMembers().size(); index++) {
			String id = fleet.getMembers().get(index).getVariantId();
			JSONObject variant = variants.getJSONObject(Integer.toString(index));
			variant.put("variantId", id);
			fleet.putVariant(BattleNext_VariantBuilder.fromJson(variant, id));
		}
		BattleNext_AssembledFleet assembled = new BattleNext_FleetBuilder().build(fleet);
		if (!assembled.isComplete() || assembled.getMembers().isEmpty()) throw new IllegalArgumentException("Incomplete simulation fleet: " + fleet.getId() + ": " + assembled.getMemberErrors());
		return assembled;
	}

	private static BattleNext_ScorekeeperPlugin define(MissionDefinitionAPI api, List<String> player,
			List<String> enemy, BattleNext_SimulationConfig config) {
		List<FleetMemberAPI> players = buildSide(api, FleetSide.PLAYER, player, config.playerAdmiral());
		List<FleetMemberAPI> enemies = buildSide(api, FleetSide.ENEMY, enemy, config.enemyAdmiral());
		api.initMap(-config.mapWidth() * 0.5f, config.mapWidth() * 0.5f, -config.mapHeight() * 0.5f, config.mapHeight() * 0.5f);
		api.addPlugin(new BattleNext_DeploymentPlugin());
		api.addPlugin(new BattleNext_EnvironmentPlugin(config.assaultAfterSeconds()));
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(players, enemies);
		api.addPlugin(scorekeeper);
		return scorekeeper;
	}

	private static List<FleetMemberAPI> buildSide(MissionDefinitionAPI api, FleetSide side, List<String> ids, boolean admiral) {
		api.initFleet(side, "", FleetGoal.ATTACK, admiral, 999);
		api.setFleetTagline(side, String.join(", ", ids));
		BattleNext_MemberBuilder builder = new BattleNext_MemberBuilder();
		List<FleetMemberAPI> members = new ArrayList<>();
		for (String id : ids) {
			ShipVariantAPI variant;
			try {
				variant = BattleNext_VariantBuilder.fromRegistered(id).build();
			} catch (Exception exception) {
				throw new IllegalArgumentException("Unable to assemble simulation variant " + id, exception);
			}
			if (variant.getHullSpec().getHullSize() == HullSize.FIGHTER) throw new IllegalArgumentException("Expected a registered root ship variant: " + id);
			BattleNext_MemberModel definition = new BattleNext_MemberModel(id, variant.getHullSpec().getHullName(), "", List.of(), "", "", List.of());
			FleetMemberAPI member = builder.build(definition, variant);
			api.addFleetMember(side, member);
			members.add(member);
		}
		return members;
	}
}
