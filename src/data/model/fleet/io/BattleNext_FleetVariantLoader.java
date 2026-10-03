package data.model.fleet.io;

import com.fs.starfarer.api.Global;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class BattleNext_FleetVariantLoader {

	private BattleNext_FleetVariantLoader() {

	}

	public static void load(BattleNext_FleetModel fleet, List<String> paths) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("fleet", fleet).put("paths", paths).build();
		BattleNext_Logger.operationStarted(BattleNext_FleetVariantLoader.class, "fleet.variants.load", input);
		try {

			String source = fleet.getSourcePath().replace('\\', '/');
			String prefix = source.substring(0, source.lastIndexOf('/') + 1);
			Map<String, JSONObject> variants = new LinkedHashMap<>();
			Map<String, String> sources = new LinkedHashMap<>();
			for (String path : paths) {
				if (!path.startsWith(prefix) || !path.endsWith(".variant")) continue;
				if (path.indexOf('/', prefix.length()) >= 0) continue;
				String fileId = path.substring(prefix.length(), path.length() - ".variant".length());
				try {
					BattleNext_VariantBuilder variant = BattleNext_VariantBuilder.fromJson(
							BattleNext_ModFileManager.readJson(BattleNext_FleetVariantLoader.class, path, "AI_Battles_Next"), fileId);
					String previous = sources.put(variant.getId(), path);
					if (previous != null) {
						throw new IOException("Duplicate variant ID " + variant.getId() + ": " + previous + " and " + path);
					}
					variants.put(variant.getId(), variant.toJson().put("variantId", variant.getId()));
				} catch (IOException | JSONException exception) {
					throw new IOException("Unable to load variant " + path + ": " + exception.getMessage(), exception);
				}
			}
			for (Map.Entry<String, JSONObject> entry : variants.entrySet()) {
				try {
					JSONObject json = new JSONObject(entry.getValue().toString());
					resolve(json, variants, new LinkedHashSet<>());
					fleet.putVariant(BattleNext_VariantBuilder.fromJson(json, entry.getKey()));
				} catch (IOException | JSONException exception) {
					throw new IOException("Unable to resolve variant " + sources.get(entry.getKey())
							+ ": " + exception.getMessage(), exception);
				}
			}
			if (fleet.hasLocalVariants()) {
				for (String variantId : fleet.getMembers().stream().map(BattleNext_MemberModel::getVariantId).toList()) {
					if (fleet.getVariant(variantId) == null && !Global.getSettings().doesVariantExist(variantId)) {
						BattleNext_Logger.warn(BattleNext_FleetVariantLoader.class, "fleet.variant.missing",
					BattleNext_Logger.fields().put("fleet_id", fleet.getId()).put("variant_id", variantId).put("path", source).build());
						fleet.addLoadError("Missing variant " + variantId + " in " + source);
					}
				}
			}
			BattleNext_Logger.operationSucceeded(BattleNext_FleetVariantLoader.class, "fleet.variants.load",
					BattleNext_Logger.fields().put("fleet", fleet).build());
		} catch (IOException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_FleetVariantLoader.class, "fleet.variants.load", input, exception);
			throw exception;
		}
	}

	private static void resolve(JSONObject json, Map<String, JSONObject> variants, Set<String> visiting)
			throws IOException, JSONException {
		String id = json.getString("variantId");
		if (!visiting.add(id)) throw new IOException("Cyclic module variant: " + String.join(" -> ", visiting) + " -> " + id);
		Object modules = json.opt("modules");
		if (modules instanceof JSONObject object) {
			resolveModules(object, variants, visiting);
		} else if (modules instanceof JSONArray array) {
			for (int index = 0; index < array.length(); index++) {
				resolveModules(array.getJSONObject(index), variants, visiting);
			}
		}
		visiting.remove(id);
	}

	private static void resolveModules(JSONObject modules, Map<String, JSONObject> variants, Set<String> visiting)
			throws IOException, JSONException {
		if (modules.length() == 0) return;
		for (String slot : JSONObject.getNames(modules)) {
			Object module = modules.get(slot);
			if (module instanceof String reference && variants.containsKey(reference)) {
				JSONObject local = new JSONObject(variants.get(reference).toString());
				resolve(local, variants, visiting);
				modules.put(slot, local);
			} else if (module instanceof JSONObject object) {
				resolve(object, variants, visiting);
			}
		}
	}
}
