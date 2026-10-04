package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.provider.BattleNext_WeaponDisplay;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.util.*;
import java.util.function.BooleanSupplier;

public class BattleNext_VariantDetails extends BattleNext_PanelBasedWidget {

	private static final float GAP = BattleNext_PageLayout.MANAGEMENT_CONTENT_INSET;
	private static final float SECTION_HEADER_HEIGHT = BattleNext_PageLayout.TABLE_HEADER_HEIGHT;
	private static final float ROW_GAP = BattleNext_ExpandablePanel.ROW_GAP;
	private static final float ICON_SIZE = BattleNext_ExpandablePanel.ICON_SIZE;
	private static final float WEAPON_ROW_HEIGHT = 40f;
	private static final float TILE_TARGET_SIZE = 120f;
	private static final float TILE_NAME_HEIGHT = 24f;
	private static final float TILE_BORDER_WIDTH = 2f;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
	private final Set<Integer> expanded = new HashSet<>();
	private final Map<String, TooltipMakerAPI> lists = new LinkedHashMap<>();
	private final Runnable refresh;
	private final BooleanSupplier interactive;
	private JSONObject variantJson;

	public BattleNext_VariantDetails(Runnable refresh) {
		this(refresh, () -> true);
	}

	public BattleNext_VariantDetails(Runnable refresh, BooleanSupplier interactive) {
		this.refresh = refresh;
		this.interactive = interactive;
	}

	public void clear() {
		expanded.clear();
		lists.clear();
	}

	public void setVariantJson(JSONObject json) {
		variantJson = json;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {
		if (variantJson != null) {
			buildDetails(panel, panel.getPosition().getWidth(), panel.getPosition().getHeight(), variantJson);
		}
	}

	private void buildDetails(CustomPanelAPI owner, float width, float height, JSONObject json) {

		Map<String, Float> offsets = new LinkedHashMap<>();
		for (var entry : lists.entrySet()) {
			if (entry.getValue().getExternalScroller() != null) {
				offsets.put(entry.getKey(), entry.getValue().getExternalScroller().getYOffset());
			}
		}
		lists.clear();
		String hullId = json.optString("hullId", "");
		ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
		if (hull == null) throw new IllegalArgumentException("Unknown hull: " + hullId);

		float usableWidth = width - GAP * 2f;
		float leftWidth = (float) Math.floor(usableWidth * 0.32f);
		float centerWidth = (float) Math.floor(usableWidth * 0.28f);
		float rightWidth = usableWidth - leftWidth - centerWidth;
		float upperHeight = (float) Math.floor((height - GAP) * 0.5f);
		float lowerHeight = height - GAP - upperHeight;
		CustomPanelAPI preview = frame(owner, new UIRect(0f, 0f, leftWidth, height), hull.getHullName());
		buildPreview(preview, hull, json, offsets.getOrDefault("identity", 0f));
		Zone plugins = zone(owner, new UIRect(leftWidth + GAP, 0f, centerWidth, upperHeight),
				variantStrings.get("hullmods"));
		Zone modules = zone(owner, new UIRect(leftWidth + GAP, upperHeight + GAP, centerWidth, lowerHeight),
				variantStrings.get("modules"));
		float rightX = leftWidth + centerWidth + GAP * 2f;
		Zone groups = zone(owner, new UIRect(rightX, 0f, rightWidth, upperHeight),
				variantStrings.get("weapon_groups"));
		Zone wings = zone(owner, new UIRect(rightX, upperHeight + GAP, rightWidth, lowerHeight),
				variantStrings.get("fighter_wings"));

		buildPlugins(plugins.list(), plugins.panel(), plugins.width(), hull, json);
		buildModulesSummary(modules.list(), modules.panel(), modules.width(), json);
		buildWeaponGroups(groups.list(), groups.panel(), groups.width(), json);
		buildWings(wings.list(), wings.panel(), wings.width(), json);
		Map<String, Zone> zones = new LinkedHashMap<>();
		zones.put("plugins", plugins);
		zones.put("modules", modules);
		zones.put("groups", groups);
		zones.put("wings", wings);
		for (var entry : zones.entrySet()) {
			Zone zone = entry.getValue();
			zone.list().addSpacer(GAP);
			zone.panel().addUIElement(zone.list()).inTL(GAP - BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET,
					SECTION_HEADER_HEIGHT);
			lists.put(entry.getKey(), zone.list());
			if (zone.list().getExternalScroller() != null) {
				float limit = Math.max(0f, zone.list().getHeightSoFar() - zone.height());
				zone.list().getExternalScroller().setYOffset(Math.min(limit, offsets.getOrDefault(entry.getKey(), 0f)));
			}
		}
	}

	private CustomPanelAPI frame(CustomPanelAPI owner, UIRect bounds, String title) {
		CustomPanelAPI panel = ownPanel(owner, owner.createCustomPanel(bounds.getWidth(), bounds.getHeight(), new FramePlugin()));
		owner.addComponent(panel).inTL(bounds.getX(), bounds.getY());
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, bounds.getWidth(), SECTION_HEADER_HEIGHT),
				panel, title);
		return panel;
	}

	private Zone zone(CustomPanelAPI owner, UIRect bounds, String title) {
		CustomPanelAPI panel = frame(owner, bounds, title);
		float width = Math.max(1f, bounds.getWidth() - GAP * 2f);
		float height = Math.max(1f, bounds.getHeight() - SECTION_HEADER_HEIGHT - GAP);
		TooltipMakerAPI list = panel.createUIElement(width + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, height, true);
		BattleNext_UiTheme.configure(list);
		list.setTextWidthOverride(width);
		return new Zone(panel, list, width, height);
	}

	private void buildPreview(CustomPanelAPI frame, ShipHullSpecAPI hull, JSONObject json, float offset) {

		float width = Math.max(1f, frame.getPosition().getWidth() - GAP * 2f);
		float height = Math.max(1f, frame.getPosition().getHeight() - SECTION_HEADER_HEIGHT - GAP * 2f);
		float identityLimit = Math.max(1f, (float) Math.floor(height * 0.5f) - GAP);
		TooltipMakerAPI identity = frame.createUIElement(width + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH,
				identityLimit, true);
		BattleNext_UiTheme.configure(identity);
		identity.setParaFontVictor14();
		identity.setTextWidthOverride(width);
		String variantId = json.optString("variantId", "");
		String variantName = json.optString("displayName", variantId);
		identity.addPara(variantStrings.get("label") + ": " + variantName, GAP * 0.5f);
		BattleNext_PageLayout.addIdTooltip(identity, variantName, variantId);
		identity.addPara(variantStrings.get("flux_capacitors") + ": " + json.optInt("fluxCapacitors", 0), GAP * 0.5f);
		identity.addPara(variantStrings.get("flux_vents") + ": " + json.optInt("fluxVents", 0), GAP * 0.5f);
		identity.setParaSmallInsignia();
		SpriteAPI sprite = null;
		try {
			sprite = Global.getSettings().getSprite(hull.getSpriteName());
		} catch (RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.preview.degraded",
				BattleNext_Logger.fields().put("variant", json).put("hull_id", hull.getHullId()).put("sprite", hull.getSpriteName()).build(), exception);
			identity.addPara(variantStrings.get("preview_error") + ": " + exception, ROW_GAP);
		}
		float identityHeight = Math.min(identityLimit, identity.getHeightSoFar());
		identity.getPosition().setSize(width + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, identityHeight);
		if (sprite != null) {
			PreviewPlugin plugin = new PreviewPlugin(sprite, Math.max(0f, height - identityHeight * 2f));
			CustomPanelAPI image = frame.createCustomPanel(width, height, plugin);
			plugin.position = image.getPosition();
			frame.addComponent(image).inTL(GAP, SECTION_HEADER_HEIGHT + GAP);
		}
		frame.addUIElement(identity).inTL(GAP - BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET,
				SECTION_HEADER_HEIGHT);
		lists.put("identity", identity);
		if (identity.getExternalScroller() != null) {
			identity.getExternalScroller().setYOffset(Math.min(Math.max(0f, identity.getHeightSoFar() - identityHeight), offset));
		}
	}

	private void buildPlugins(TooltipMakerAPI list, CustomPanelAPI owner, float width,
			ShipHullSpecAPI hull, JSONObject json) {

		Set<String> ids = new LinkedHashSet<>();
		for (String key : List.of("hullMods", "permaMods", "sMods", "sModdedBuiltIns", "suppressedMods")) {
			ids.addAll(values(json.optJSONArray(key)));
		}
		List<String> sorted = ids.stream().sorted((left, right) -> pluginName(left).compareToIgnoreCase(pluginName(right))).toList();
		if (sorted.isEmpty()) {
			list.addPara(variantStrings.get("none"), ROW_GAP);
			return;
		}
		Set<String> sMods = new HashSet<>(values(json.optJSONArray("sMods")));
		Set<String> builtInSMods = new HashSet<>(values(json.optJSONArray("sModdedBuiltIns")));
		for (String id : sorted) {
			HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
			if (spec == null) {
				BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.hullmod.missing",
				BattleNext_Logger.fields().put("variant", json).put("hullmod_id", id).build());
				list.addPara(strings.get("unavailable") + ": " + id, ROW_GAP);
				continue;
			}
			String label = spec.hasTag(Tags.HULLMOD_DMOD) ? variantStrings.get("d_mod")
					: sMods.contains(id) || builtInSMods.contains(id) ? variantStrings.get("s_mod")
					: hull.isBuiltInMod(id) ? variantStrings.get("built_in") : "";
			CustomPanelAPI card = card(owner, width, spec.getSpriteName(), spec.getDisplayName(), label, null);
			list.addCustom(card, ROW_GAP);
			list.addTooltipToPrevious(new BaseTooltipCreator() {
				@Override
				public float getTooltipWidth(Object ignored) {
					return BattleNext_EquipmentTooltip.WIDTH;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
					BattleNext_EquipmentTooltip.renderHullmod(tooltip, spec.getDisplayName(), id, spec,
							hull.getHullSize(), json.optString("variantId", ""), List::of);
				}
			}, TooltipMakerAPI.TooltipLocation.RIGHT);
		}
	}

	private void buildModulesSummary(TooltipMakerAPI list, CustomPanelAPI owner, float width, JSONObject json) {

		Object raw = json.opt("modules");
		List<DisplayTile> tiles = new ArrayList<>();
		if (raw instanceof JSONObject object) {
			addModuleTiles(tiles, object);
		} else if (raw instanceof JSONArray array) {
			for (int index = 0; index < array.length(); index++) {
				JSONObject object = array.optJSONObject(index);
				if (object == null) {
					tiles.add(new DisplayTile(String.valueOf(index), "", "", null,
							variantStrings.get("module_error") + ": " + index));
				} else {
					addModuleTiles(tiles, object);
				}
			}
		} else if (raw != null) {
			tiles.add(new DisplayTile(strings.get("unavailable"), "", "", null,
					variantStrings.get("module_error") + ": " + raw));
		}
		buildTileGrid(list, owner, width, tiles);
	}

	private void addModuleTiles(List<DisplayTile> tiles, JSONObject modules) {

		for (String slot : keys(modules)) {
			String hullId = "";
			try {
				Object raw = modules.opt(slot);
				JSONObject module = raw instanceof JSONObject object ? object
						: raw instanceof String id ? BattleNext_VariantBuilder.fromRegistered(id).toJson() : null;
				if (module == null) throw new IllegalArgumentException("Invalid module: " + slot);
				hullId = module.optString("hullId", "");
				ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
				if (hull == null) throw new IllegalArgumentException("Unknown module hull: " + hullId);
				tiles.add(new DisplayTile(hull.getHullName(), hull.getSpriteName(), hullId, hull, ""));
			} catch (Exception exception) {
				BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.module.degraded",
				BattleNext_Logger.fields().put("variant", variantJson).put("slot", slot).put("hull_id", hullId).put("module", modules.opt(slot)).build(), exception);
				tiles.add(new DisplayTile(slot, "", hullId, null,
						variantStrings.get("module_error") + ": " + exception));
			}
		}
	}

	private void buildWeaponGroups(TooltipMakerAPI list, CustomPanelAPI owner, float width, JSONObject json) {

		JSONArray groups = json.optJSONArray("weaponGroups");
		if (groups == null || groups.length() == 0) {
			list.addPara(variantStrings.get("none"), ROW_GAP);
			return;
		}
		for (int index = 0; index < groups.length(); index++) {
			JSONObject group = groups.optJSONObject(index);
			if (group == null) {
				BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.weapon_group.rejected",
				BattleNext_Logger.fields().put("variant", json).put("group_index", index).put("input", groups.opt(index)).build());
				list.addPara(variantStrings.get("weapon_group_error") + ": " + index, ROW_GAP);
				continue;
			}
			JSONObject weapons = group.optJSONObject("weapons");
			int count = weapons == null ? 0 : weapons.length();
			int key = index;
			boolean open = expanded.contains(key);
			CustomPanelAPI body = owner.createCustomPanel(width, 0f, new BaseCustomUIPanelPlugin());
			float bodyHeight = 0f;
			if (open) {
				float contentWidth = Math.max(1f, width - GAP * 2f);
				TooltipMakerAPI text = body.createUIElement(contentWidth
						+ BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, 0f, false);
				BattleNext_UiTheme.configure(text);
				text.setTextWidthOverride(contentWidth);
				if (count == 0) {
					text.addPara(variantStrings.get("none"), 0f);
				}
				List<String> slots = keys(weapons);
				for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
					String slot = slots.get(slotIndex);
					String id = weapons.optString(slot, "");
					addWeaponRow(body, text, slot, id, contentWidth, slotIndex == 0 ? 0f : ROW_GAP);
				}
				bodyHeight = text.getHeightSoFar() + GAP * 2f;
				body.getPosition().setSize(width, bodyHeight);
				body.addUIElement(text).inTL(GAP - BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET, GAP);
			}
			String status = variantStrings.get(group.optBoolean("autofire", false) ? "autofire_enabled" : "autofire_disabled");
			String mode = group.optString("mode", "");
			String icon = switch (mode) {
				case "LINKED" -> Global.getSettings().getSpriteName("icon", "BattleNext_weapon_group_linked");
				case "ALTERNATING" -> Global.getSettings().getSpriteName("systemMap", "icon_jump_point");
				default -> "";
			};
			BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(icon, false, "",
					variantStrings.format("weapon_group_summary", index + 1, count),
					mode + " / " + status, "", "", BattleNext_UiTheme.ACCENT);
			BattleNext_ExpandablePanel.add(list, owner, width, open, bodyHeight, header,
					() -> {
						if (!interactive.getAsBoolean()) return;
						if (!expanded.add(key)) {
							expanded.remove(key);
						}
						refresh.run();
					}, ignored -> {

					}, container -> container.addComponent(body).inTL(0f, 0f));
		}
	}

	private void addWeaponRow(CustomPanelAPI body, TooltipMakerAPI list,
			String slot, String id, float width, float pad) {

		BattleNext_WeaponDisplay.Visual visual = BattleNext_WeaponDisplay.resolve(id, Global.getSettings());
		String weaponName = id.isEmpty() ? variantStrings.get("empty_slot")
				: visual.resolved() ? visual.name() : strings.get("unavailable") + ": " + id;
		String title = slot + ": " + weaponName;
		WeaponRowPlugin plugin = new WeaponRowPlugin();
		CustomPanelAPI row = body.createCustomPanel(width, WEAPON_ROW_HEIGHT, plugin);
		plugin.position = row.getPosition();
		TooltipMakerAPI icon = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
		plugin.missingIcon = !addWeaponImage(icon, visual);
		if (!plugin.missingIcon) {
			icon.getPrev().getPosition().inTL(0f, 0f);
			icon.setHeightSoFar(ICON_SIZE);
			row.addUIElement(icon).inTL(0f, (WEAPON_ROW_HEIGHT - ICON_SIZE) * 0.5f);
		}
		float textX = ICON_SIZE + GAP;
		float textWidth = Math.max(1f, width - textX);
		TooltipMakerAPI text = row.createUIElement(textWidth, WEAPON_ROW_HEIGHT, false);
		BattleNext_UiTheme.configure(text);
		text.setTextWidthOverride(textWidth);
		LabelAPI label = text.addPara(text.shortenString(title, Math.max(1f, textWidth - 4f)), 0f);
		label.getPosition().inTL(0f, (WEAPON_ROW_HEIGHT - label.getPosition().getHeight()) * 0.5f);
		text.setHeightSoFar(WEAPON_ROW_HEIGHT);
		row.addUIElement(text).inTL(textX, 0f);
		list.addCustom(row, pad);
		list.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return 360f;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				BattleNext_EquipmentTooltip.render(tooltip, BattleNext_EquipmentTooltip.description(
						weaponName, id, id, Description.Type.WEAPON, Global.getSettings(), List.of()));
			}
		}, TooltipMakerAPI.TooltipLocation.RIGHT);
	}

	private boolean addWeaponImage(TooltipMakerAPI icon, BattleNext_WeaponDisplay.Visual visual) {
		for (String path : List.of(visual.primarySprite(), visual.secondarySprite())) {
			if (path.isEmpty()) continue;
			try {
				Global.getSettings().getSprite(path);
				icon.addImage(path, ICON_SIZE, ICON_SIZE, 0f);
				return true;
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.weapon.sprite.degraded",
				BattleNext_Logger.fields().put("variant", variantJson).put("weapon_name", visual.name()).put("path", path).put("primary", visual.primarySprite()).put("secondary", visual.secondarySprite()).build(), exception);
			}
		}
		return false;
	}

	private void buildWings(TooltipMakerAPI list, CustomPanelAPI owner, float width, JSONObject json) {

		List<DisplayTile> tiles = new ArrayList<>();
		for (String id : values(json.optJSONArray("wings"))) {
			if (id.isEmpty()) {
				tiles.add(new DisplayTile(variantStrings.get("empty_slot"), "", "", null, ""));
				continue;
			}
			String name = id;
			try {
				FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(id);
				if (wing == null) throw new IllegalArgumentException("Unknown fighter wing: " + id);
				name = wing.getWingName();
				var variant = wing.getVariant();
				if (variant == null) throw new IllegalArgumentException("Missing fighter variant: " + id);
				ShipHullSpecAPI hull = variant.getHullSpec();
				if (hull == null) throw new IllegalArgumentException("Missing fighter hull: " + id);
				tiles.add(new DisplayTile(name, hull.getSpriteName(), id, hull, ""));
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.wing.degraded",
				BattleNext_Logger.fields().put("variant", json).put("wing_id", id).put("name", name).build(), exception);
				tiles.add(new DisplayTile(name, "", id, null, strings.get("unavailable") + ": " + exception));
			}
		}
		buildTileGrid(list, owner, width, tiles);
	}

	private void buildTileGrid(TooltipMakerAPI list, CustomPanelAPI owner, float width, List<DisplayTile> tiles) {

		if (tiles.isEmpty()) {
			list.addPara(variantStrings.get("none"), ROW_GAP);
			return;
		}
		int columns = Math.max(1, (int) ((width + ROW_GAP) / (TILE_TARGET_SIZE + ROW_GAP)));
		float size = (width - ROW_GAP * (columns - 1)) / columns;
		for (int start = 0; start < tiles.size(); start += columns) {
			CustomPanelAPI row = owner.createCustomPanel(width, size, new BaseCustomUIPanelPlugin());
			int count = Math.min(columns, tiles.size() - start);
			for (int index = 0; index < count; index++) {
				DisplayTile tile = tiles.get(start + index);
				CustomPanelAPI card = squareCard(row, size, tile);
				row.addComponent(card).inTL(index * (size + ROW_GAP), 0f);
			}
			list.addCustom(row, ROW_GAP);
		}
	}

	private CustomPanelAPI squareCard(CustomPanelAPI owner, float size, DisplayTile tile) {

		SpriteAPI sprite = null;
		String error = tile.error();
		if (tile.spriteName() != null && !tile.spriteName().isBlank()) {
			try {
				sprite = Global.getSettings().getSprite(tile.spriteName());
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.tile.sprite.degraded",
				BattleNext_Logger.fields().put("id", tile.id()).put("name", tile.name()).put("path", tile.spriteName()).build(), exception);
				error = variantStrings.get("preview_error") + ": " + exception;
			}
		}
		if (sprite == null && error.isEmpty()) {
			error = variantStrings.get("preview_error");
		}
		SquareCardPlugin plugin = new SquareCardPlugin(sprite);
		CustomPanelAPI card = owner.createCustomPanel(size, size, plugin);
		plugin.position = card.getPosition();
		float nameWidth = Math.max(1f, size - GAP * 2f);
		TooltipMakerAPI caption = card.createUIElement(size, size, false);
		BattleNext_UiTheme.configure(caption);
		caption.setTextWidthOverride(nameWidth);
		caption.addSpacer(size).getPosition().setSize(size, size).inTL(0f, 0f);
		String displayError = error;
		caption.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return 360f;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				List<String> notes = displayError.isEmpty() ? List.of() : List.of(displayError);
				BattleNext_EquipmentTooltip.Content info = tile.hull() == null
						? new BattleNext_EquipmentTooltip.Content(tile.name(), tile.id(), "", notes)
						: BattleNext_EquipmentTooltip.hull(tile.name(), tile.id(), tile.hull(), Global.getSettings(), notes);
				BattleNext_EquipmentTooltip.render(tooltip, info);
			}
		}, TooltipMakerAPI.TooltipLocation.RIGHT);
		LabelAPI name = caption.addPara(caption.shortenString(tile.name(), nameWidth), 0f);
		name.setAlignment(Alignment.MID);
		name.getPosition().setSize(nameWidth, name.getPosition().getHeight());
		name.getPosition().inTL(GAP, size - GAP - TILE_NAME_HEIGHT
				+ (TILE_NAME_HEIGHT - name.getPosition().getHeight()) * 0.5f);
		caption.setHeightSoFar(size);
		card.addUIElement(caption).inTL(0f, 0f);
		return card;
	}

	private CustomPanelAPI card(CustomPanelAPI owner, float width, String spriteName, String title,
			String subtitle, String id) {

		SpriteAPI sprite = null;
		boolean icon = spriteName != null && !spriteName.isBlank();
		if (icon) {
			try {
				sprite = Global.getSettings().getSprite(spriteName);
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_VariantDetails.class, "variant.card.sprite.degraded",
				BattleNext_Logger.fields().put("variant", variantJson).put("id", id).put("title", title).put("path", spriteName).build(), exception);
			}
		}
		CardPlugin plugin = new CardPlugin(sprite);
		CustomPanelAPI card = owner.createCustomPanel(width, 0f, plugin);
		plugin.position = card.getPosition();
		float textX = icon ? ICON_SIZE + GAP * 2f : GAP;
		float textWidth = Math.max(1f, width - textX - GAP);
		TooltipMakerAPI text = card.createUIElement(textWidth, 0f, false);
		BattleNext_UiTheme.configure(text);
		text.setTextWidthOverride(textWidth);
		text.addPara(title, 0f).getPosition().inTL(0f, 0f);
		if (id != null && !id.isEmpty()) BattleNext_PageLayout.addIdTooltip(text, title, id);
		if (!subtitle.isBlank()) {
			float top = text.getHeightSoFar() + ROW_GAP;
			text.addPara(subtitle, ROW_GAP).getPosition().inTL(0f, top);
		}
		float height = Math.max(ICON_SIZE + GAP * 2f, text.getHeightSoFar() + GAP * 2f);
		card.getPosition().setSize(width, height);
		card.addUIElement(text).inTL(textX, GAP);
		return card;
	}

	private String pluginName(String id) {
		HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
		String name = id;
		if (spec != null) {
			name = spec.getDisplayName();
		}
		return name;
	}

	private record DisplayTile(String name, String spriteName, String id, ShipHullSpecAPI hull, String error) {

	}

	private record Zone(CustomPanelAPI panel, TooltipMakerAPI list, float width, float height) {

	}

	private static class FramePlugin extends BaseCustomUIPanelPlugin {
		private PositionAPI position;

		@Override
		public void positionChanged(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.fillRect(position, Color.BLACK, alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()},
					BattleNext_UiTheme.PANEL, alphaMult, BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		}
	}

	private static class PreviewPlugin extends BaseCustomUIPanelPlugin {
		private final SpriteAPI sprite;
		private final float imageHeight;
		private PositionAPI position;

		private PreviewPlugin(SpriteAPI sprite, float imageHeight) {
			this.sprite = sprite;
			this.imageHeight = imageHeight;
		}

		@Override
		public void positionChanged(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void render(float alphaMult) {
			if (position == null || imageHeight <= 0f) return;
			BattleNext_PageLayout.renderFittedSprite(sprite, position.getCenterX(), position.getCenterY(),
					position.getWidth(), imageHeight, alphaMult);
		}
	}

	private static class CardPlugin extends BaseCustomUIPanelPlugin {
		private final SpriteAPI sprite;
		private PositionAPI position;

		private CardPlugin(SpriteAPI sprite) {
			this.sprite = sprite;
		}

		@Override
		public void positionChanged(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.fillRect(position, BattleNext_UiTheme.PANEL.darker(), alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()},
					BattleNext_UiTheme.PANEL, alphaMult, 1f);
		}

		@Override
		public void render(float alphaMult) {
			if (position == null || sprite == null) return;
			BattleNext_PageLayout.renderFittedSprite(sprite, position.getX() + GAP + ICON_SIZE * 0.5f,
					position.getY() + position.getHeight() - GAP - ICON_SIZE * 0.5f,
					ICON_SIZE, ICON_SIZE, alphaMult);
		}
	}

	private static class SquareCardPlugin extends BaseCustomUIPanelPlugin {
		private final SpriteAPI sprite;
		private PositionAPI position;

		private SquareCardPlugin(SpriteAPI sprite) {
			this.sprite = sprite;
		}

		@Override
		public void renderBelow(float alphaMult) {
			BattleNext_GeometryRenderer.lineLoop(new float[]{
					position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()
			}, BattleNext_UiTheme.PANEL, alphaMult, TILE_BORDER_WIDTH);
		}

		@Override
		public void render(float alphaMult) {
			float imageWidth = Math.max(1f, position.getWidth() - GAP * 2f);
			float imageHeight = Math.max(1f, position.getHeight() - GAP * 2f - TILE_NAME_HEIGHT - ROW_GAP);
			float centerX = position.getCenterX();
			float centerY = position.getY() + position.getHeight() - GAP - imageHeight * 0.5f;
			if (sprite != null) {
				BattleNext_PageLayout.renderFittedSprite(sprite, centerX, centerY, imageWidth, imageHeight, alphaMult);
			} else {
				float size = Math.min(ICON_SIZE * 0.75f, Math.min(imageWidth, imageHeight));
				BattleNext_PageLayout.renderMissingIcon(BattleNext_PageLayout.missingIcon(),
						centerX - size * 0.5f, centerY - size * 0.5f, size, alphaMult);
			}
		}
	}

	private static class WeaponRowPlugin extends BaseCustomUIPanelPlugin {
		private PositionAPI position;
		private boolean missingIcon;

		@Override
		public void renderBelow(float alphaMult) {
			BattleNext_GeometryRenderer.lineStrip(new float[]{
					position.getX(), position.getY() + position.getHeight(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight()
			}, BattleNext_UiTheme.PANEL, alphaMult * 0.6f, 1f);
		}

		@Override
		public void render(float alphaMult) {
			if (!missingIcon) return;
			float size = ICON_SIZE * 0.75f;
			BattleNext_PageLayout.renderMissingIcon(BattleNext_PageLayout.missingIcon(),
					position.getX() + (ICON_SIZE - size) * 0.5f,
					position.getY() + (WEAPON_ROW_HEIGHT - size) * 0.5f, size, alphaMult);
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
		String[] names = JSONObject.getNames(object);
		Arrays.sort(names);
		List<String> keys = Arrays.asList(names);
		return keys;
	}
}
