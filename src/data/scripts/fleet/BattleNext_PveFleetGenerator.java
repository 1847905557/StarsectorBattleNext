package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.IOException;

public class BattleNext_PveFleetGenerator {

	public static final String FACTIONS_PATH = "data/config/BattleNext_pveFactions.csv";

	public List<FactionSpecAPI> loadFactions() throws IOException, JSONException {

		JSONArray rows = BattleNext_ModFileManager.readMergedCsv(BattleNext_PveFleetGenerator.class, "id", FACTIONS_PATH);
		List<FactionSpecAPI> specs = Global.getSettings().getAllFactionSpecs();
		Set<String> known = specs.stream().map(FactionSpecAPI::getId).collect(Collectors.toSet());
		Set<String> allowed = new LinkedHashSet<>();
		for (int index = 0; index < rows.length(); index++) {
			String id = rows.getJSONObject(index).getString("id").trim();
			if (!known.contains(id)) {
				BattleNext_Logger.warn(BattleNext_PveFleetGenerator.class, "pve.faction.override.rejected",
						BattleNext_Logger.fields().put("path", FACTIONS_PATH).put("row", index + 1).put("faction_id", id)
								.put("criterion", "Faction must exist in the loaded faction catalog").build());
				continue;
			}
			allowed.add(id);
		}
		List<FactionSpecAPI> factions = specs.stream()
				.filter(spec -> spec.isShowInIntelTab() || allowed.contains(spec.getId())).toList();
		BattleNext_Logger.info(BattleNext_PveFleetGenerator.class, "pve.factions.loaded",
				BattleNext_Logger.fields().put("path", FACTIONS_PATH).put("allowed_ids", allowed)
						.put("faction_ids", factions.stream().map(FactionSpecAPI::getId).toList()).build());
		return factions;
	}

	public BattleNext_AssembledFleet generate(String factionId, int budget) {

		FleetParamsV3 params = new FleetParamsV3(null, new Vector2f(), factionId, null,
				FleetTypes.PATROL_LARGE, budget, 0f, 0f, 0f, 0f, 0f, 0f);
		BattleNext_Logger.Fields input = parameters(params);
		BattleNext_Logger.operationStarted(BattleNext_PveFleetGenerator.class, "pve.fleet.generate", input);
		try {
			CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
			if (fleet == null) throw new IllegalStateException("Fleet generator returned no fleet for " + factionId);
			fleet.inflateIfNeeded();
			List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
			if (members.stream().noneMatch(member -> !member.isFighterWing())) {
				throw new IllegalStateException("Fleet generator returned no ships for " + factionId);
			}
			PersonAPI commander = fleet.getCommander();
			List<BattleNext_MemberModel> definitions = new ArrayList<>();
			for (FleetMemberAPI member : members) {
				PersonAPI captain = member.getCaptain();
				boolean assigned = !captain.isDefault();
				definitions.add(new BattleNext_MemberModel(member.getVariant().getHullVariantId(), member.getShipName(),
						assigned ? captain.getPersonalityAPI().getId() : "",
						assigned ? BattleNext_PersonSkills.capture(captain) : List.of(),
						assigned ? captain.getPortraitSprite() : "", "", List.of()));
				member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
			}
			BattleNext_FleetModel definition = new BattleNext_FleetModel(fleet.getId(), fleet.getNameWithFaction(),
					BattleNext_PersonSkills.capture(commander), List.of("default_ai"), definitions);
			BattleNext_AssembledFleet result = new BattleNext_AssembledFleet(definition, commander, members, List.of());
			BattleNext_Logger.operationSucceeded(BattleNext_PveFleetGenerator.class, "pve.fleet.generate",
					BattleNext_Logger.fields().put("parameters", parameters(params)).put("definition", definition)
							.put("commander", commander).put("members", members).put("actual_points", fleet.getFleetPoints()).build());
			return result;
		} catch (RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_PveFleetGenerator.class, "pve.fleet.generate", input, exception);
			throw exception;
		}
	}

	private BattleNext_Logger.Fields parameters(FleetParamsV3 params) {

		return BattleNext_Logger.fields().put("faction_id", params.factionId).put("fleet_type", params.fleetType)
				.put("source_id", params.source == null ? null : params.source.getId()).put("location", params.locInHyper)
				.put("combat_points", params.combatPts).put("freighter_points", params.freighterPts)
				.put("tanker_points", params.tankerPts).put("transport_points", params.transportPts)
				.put("liner_points", params.linerPts).put("utility_points", params.utilityPts)
				.put("quality", params.quality).put("quality_override", params.qualityOverride).put("quality_mod", params.qualityMod)
				.put("faction_override", params.factionOverride).put("doctrine_override", params.doctrineOverride)
				.put("min_ship_size", params.minShipSize).put("max_ship_size", params.maxShipSize)
				.put("average_smods", params.averageSMods).put("with_officers", params.withOfficers)
				.put("ignore_market_fleet_size_mult", params.ignoreMarketFleetSizeMult)
				.put("only_apply_fleet_size_to_combat_ships", params.onlyApplyFleetSizeToCombatShips)
				.put("do_not_prune", params.doNotPrune).put("do_not_add_ships_before_pruning", params.doNotAddShipsBeforePruning)
				.put("mode_override", params.modeOverride).put("officer_level_bonus", params.officerLevelBonus)
				.put("officer_number_bonus", params.officerNumberBonus).put("max_officers_to_add", params.maxOfficersToAdd)
				.put("officer_number_mult", params.officerNumberMult).put("officer_level_limit", params.officerLevelLimit)
				.put("commander_level_limit", params.commanderLevelLimit).put("commander", params.commander)
				.put("no_commander_skills", params.noCommanderSkills).put("force_allow_phase_ships", params.forceAllowPhaseShipsEtc)
				.put("combat_freighter_fraction", params.treatCombatFreighterSettingAsFraction).put("timestamp", params.timestamp)
				.put("max_ships", params.maxNumShips).put("only_retain_flagship", params.onlyRetainFlagship)
				.put("flagship_variant_id", params.flagshipVariantId).put("flagship_variant", params.flagshipVariant)
				.put("ai_cores", params.aiCores).put("do_not_integrate_ai_cores", params.doNotIntegrateAICores)
				.put("all_weapons", params.allWeapons).put("add_ships", params.addShips).build();
	}
}
