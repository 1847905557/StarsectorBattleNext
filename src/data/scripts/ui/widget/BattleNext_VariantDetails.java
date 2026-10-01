package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.BattleNext_PageLayout;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

public class BattleNext_PinnedVariantDetails {

	private static final float GAP = 8f;
	private static final float PREVIEW_HEIGHT = 180f;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");
	private final Set<String> expanded = new HashSet<>();
	private final Runnable refresh;
	private final BooleanSupplier interactive;

	public BattleNext_PinnedVariantDetails(Runnable refresh) {
		this(refresh, () -> true);
	}

	public BattleNext_PinnedVariantDetails(Runnable refresh, BooleanSupplier interactive) {
		this.refresh = refresh;
		this.interactive = interactive;
	}

	public void clear() {
		expanded.clear();
	}

	public void addTo(TooltipMakerAPI content, CustomPanelAPI owner, float width, JSONObject json) {
		addTo(content, owner, width, json, true);
	}

	public void addTo(TooltipMakerAPI content, CustomPanelAPI owner, float width, JSONObject json,
			boolean showPreviewAndIdentity) {
		addVariant(content, owner, width, json, "root", new HashSet<>(), showPreviewAndIdentity);
	}

	private void addVariant(TooltipMakerAPI content, CustomPanelAPI owner, float width,
			JSONObject json, String path, Set<String> parents, boolean showPreviewAndIdentity) {

		String hullId = json.optString("hullId", "");
		ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
		if (hull == null) throw new IllegalArgumentException("Unknown hull: " + hullId);
		String variantId = json.optString("variantId", "");
		Set<String> ancestors = new HashSet<>(parents);
		if (!ancestors.add(variantId)) throw new IllegalArgumentException("Cyclic module variant: " + variantId);
		if (showPreviewAndIdentity) {
			try {
				SpriteAPI sprite = Global.getSettings().getSprite(hull.getSpriteName());
				float scale = BattleNext_PageLayout.spriteScaleToFit(sprite.getWidth(), sprite.getHeight(), width, PREVIEW_HEIGHT);
				content.addImage(hull.getSpriteName(), sprite.getWidth() * scale, sprite.getHeight() * scale, GAP);
			} catch (RuntimeException exception) {
				content.addPara(strings.get("variant_preview_error") + ": " + exception, GAP);
			}
			content.addPara(hull.getHullName() + " [" + hullId + "]", GAP);
			content.addPara(strings.get("variant") + ": " + json.optString("displayName", variantId) + " [" + variantId + "]", GAP);
		}
		content.addPara(strings.get("variant_flux") + ": " + json.optInt("fluxCapacitors", 0)
				+ " / " + json.optInt("fluxVents", 0), GAP);
		addGroups(content, json.optJSONArray("weaponGroups"));
		addEquipment(content, strings.get("variant_wings"), json.optJSONArray("wings"), "wing");
		Set<String> special = new HashSet<>();
		for (String key : List.of("permaMods", "sMods", "sModdedBuiltIns", "suppressedMods")) {
			special.addAll(values(json.optJSONArray(key)));
		}
		JSONArray ordinary = new JSONArray();
		for (String id : values(json.optJSONArray("hullMods"))) {
			if (!special.contains(id)) {
				ordinary.put(id);
			}
		}
		addEquipment(content, strings.get("variant_normal_mods"), ordinary, "mod");
		JSONArray permanent = new JSONArray();
		Set<String> sMods = new HashSet<>(values(json.optJSONArray("sMods")));
		for (String id : values(json.optJSONArray("permaMods"))) {
			if (!sMods.contains(id)) {
				permanent.put(id);
			}
		}
		addEquipment(content, strings.get("variant_permanent_mods"), permanent, "mod");
		addEquipment(content, strings.get("refit_smods"), json.optJSONArray("sMods"), "mod");
		addEquipment(content, strings.get("variant_builtin_smods"), json.optJSONArray("sModdedBuiltIns"), "mod");
		addEquipment(content, strings.get("variant_suppressed_mods"), json.optJSONArray("suppressedMods"), "mod");
		Object modules = json.opt("modules");
		if (modules instanceof JSONObject object) {
			addModules(content, owner, width, object, path, ancestors);
		} else if (modules instanceof JSONArray array) {
			for (int index = 0; index < array.length(); index++) {
				JSONObject module = array.optJSONObject(index);
				if (module == null) {
					content.addPara(path + "/modules/" + index + ": " + strings.get("variant_module_error"), GAP);
					continue;
				}
				addModules(content, owner, width, module, path, ancestors);
			}
		}
	}

	private void addGroups(TooltipMakerAPI content, JSONArray groups) {

		content.addSectionHeading(strings.get("variant_weapon_groups"), com.fs.starfarer.api.ui.Alignment.LMID, GAP);
		if (groups == null || groups.length() == 0) {
			content.addPara(strings.get("variant_none"), GAP);
			return;
		}
		for (int index = 0; index < groups.length(); index++) {
			JSONObject group = groups.optJSONObject(index);
			if (group == null) {
				content.addPara(strings.get("variant_group_error") + ": " + index, GAP);
				continue;
			}
			content.addPara(strings.format("variant_group", index + 1, group.optString("mode", ""),
					strings.get(group.optBoolean("autofire", false) ? "enabled" : "variant_disabled")), GAP);
			JSONObject weapons = group.optJSONObject("weapons");
			for (String slot : keys(weapons)) {
				String id = weapons.optString(slot, "");
				content.addPara(slot + ": " + equipmentName(id, "weapon") + " [" + id + "]", GAP);
			}
		}
	}

	private void addEquipment(TooltipMakerAPI content, String heading, JSONArray entries, String kind) {
		content.addSectionHeading(heading, com.fs.starfarer.api.ui.Alignment.LMID, GAP);
		List<String> ids = values(entries);
		if (ids.isEmpty()) {
			content.addPara(strings.get("variant_none"), GAP);
		}
		for (int index = 0; index < ids.size(); index++) {
			String id = ids.get(index);
			content.addPara((kind.equals("wing") ? (index + 1) + ": " : "")
					+ equipmentName(id, kind) + " [" + id + "]", GAP);
		}
	}

	private String equipmentName(String id, String kind) {
		try {
			if (id.isEmpty()) return strings.get("variant_empty_slot");
			if (kind.equals("weapon")) return Global.getSettings().getWeaponSpec(id).getWeaponName();
			if (kind.equals("wing")) return Global.getSettings().getFighterWingSpec(id).getWingName();
			return Global.getSettings().getHullModSpec(id).getDisplayName();
		} catch (RuntimeException exception) {
			return strings.get("unavailable") + ": " + id;
		}
	}

	private void addModules(TooltipMakerAPI content, CustomPanelAPI owner, float width,
			JSONObject modules, String path, Set<String> ancestors) {

		for (String slot : keys(modules)) {
			String key = path + "/" + slot;
			try {
				Object value = modules.opt(slot);
				JSONObject json;
				if (value instanceof JSONObject object) {
					json = object;
				} else if (value instanceof String id) {
					json = BattleNext_VariantBuilder.fromRegistered(id).toJson();
				} else {
					throw new IllegalArgumentException("Invalid module: " + key);
				}
				String hullId = json.optString("hullId", "");
				ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
				if (hull == null) throw new IllegalArgumentException("Unknown module hull: " + hullId);
				boolean open = expanded.contains(key);
				CustomPanelAPI body = owner.createCustomPanel(width - GAP * 2f, 0f, new BaseCustomUIPanelPlugin());
				TooltipMakerAPI details = body.createUIElement(width - GAP * 2f, 0f, false);
				if (open) {
					try {
						addVariant(details, body, width - GAP * 2f, json, key, ancestors, true);
					} catch (Exception exception) {
						details.addPara(key + ": " + exception, GAP);
					}
				}
				float height = details.getHeightSoFar();
				body.getPosition().setSize(width - GAP * 2f, height);
				body.addUIElement(details).inTL(0f, 0f);
				BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
						hull.getSpriteName(), false, "", slot + ": " + hull.getHullName(),
						json.optString("displayName", "") + " [" + json.optString("variantId", "") + "]", "", "");
				BattleNext_ExpandablePanel.addTo(content, owner, width, open, height + GAP * 2f, header,
						() -> {
							if (!interactive.getAsBoolean()) return;
							if (!expanded.add(key)) {
								expanded.remove(key);
							}
							refresh.run();
						}, ignored -> {

						}, container -> container.addComponent(body).inTL(GAP, GAP));
			} catch (Exception exception) {
				content.addPara(strings.get("variant_module_error") + " " + key + ": " + exception, GAP);
			}
		}
	}

	private static List<String> values(JSONArray array) {
		List<String> result = new ArrayList<>();
		if (array == null) return result;
		for (int index = 0; index < array.length(); index++) {
			result.add(array.optString(index, ""));
		}
		return result;
	}

	private static List<String> keys(JSONObject object) {
		if (object == null || object.length() == 0) return List.of();
		return Arrays.stream(JSONObject.getNames(object)).sorted().toList();
	}
}
