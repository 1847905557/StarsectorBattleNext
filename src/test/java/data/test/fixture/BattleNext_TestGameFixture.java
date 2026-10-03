package data.test.fixture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.*;
import com.fs.starfarer.api.ui.*;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.scripts.campaign.BattleNext_LunaPinnedVariantPanel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.page.BattleNext_PageView;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.input.Keyboard;
import static org.junit.jupiter.api.Assertions.*;

public class BattleNext_TestGameFixture {

	@TempDir
	protected Path directory;
	protected SettingsAPI previousSettings;
	protected SectorAPI previousSector;
	protected final Map<String, ShipHullSpecAPI> hulls = new LinkedHashMap<>();
	protected final Map<ShipHullSpecAPI, List<String>> moduleSlots = new IdentityHashMap<>();
	protected final List<float[]> spriteCenters = new ArrayList<>();
	protected final List<float[]> spriteSizes = new ArrayList<>();
	protected final List<Color> spriteRenderColors = new ArrayList<>();
	protected final List<float[]> spriteRenderStates = new ArrayList<>();
	protected final Map<SpriteAPI, float[]> sprites = new IdentityHashMap<>();
	protected final Map<SpriteAPI, String> spritePaths = new IdentityHashMap<>();
	protected float spriteWidth = 120f;
	protected float spriteHeight = 120f;
	protected boolean failBuild;
	protected boolean failSprites;
	protected boolean lunaEnabled;

	@BeforeEach
	protected void configureGameServices() {

		previousSettings = Global.getSettings();
		previousSector = Global.getSector();
		ModSpecAPI mod = proxy(ModSpecAPI.class, (method, args) -> "getPath".equals(method) ? directory.toString() : null);
		ModManagerAPI mods = proxy(ModManagerAPI.class, (method, args) -> switch (method) {
			case "getModSpec" -> mod;
			case "isModEnabled" -> lunaEnabled && "lunalib".equals(args[0]);
			default -> null;
		});
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> switch (method) {
			case "getModManager" -> mods;
			case "getString" -> BattleNext_TestTextCatalog.key(args[0], args[1]);
			case "getSpriteName" -> BattleNext_TestSpriteCatalog.path(args[0], args[1]);
			case "getColor" -> Color.GRAY;
			case "getFloat" -> 1f;
			case "getScreenWidth" -> 1600f;
			case "getScreenHeight" -> 900f;
			case "getInt" -> 1;
			case "getAllVariantIds", "getAllSkillIds", "getAllFactionSpecs" -> List.of();
			case "getMergedSpreadsheetData" -> new JSONArray();
			case "loadJSON" -> new JSONObject(Files.readString(directory.resolve((String) args[0]), StandardCharsets.UTF_8));
			case "loadText" -> "player,enemy,-1,1";
			case "createCustom" -> panel((Float) args[0], (Float) args[1], (CustomUIPanelPlugin) args[2]);
			case "getHullSpec" -> failBuild ? null : hulls.get(args[0]);
			case "getHullModSpec" -> ((String) args[0]).endsWith("_missing_hullmod") ? null
					: proxy(HullModSpecAPI.class, (name, values) -> "getDisplayName".equals(name) ? "Hullmod " + args[0] : null);
			case "getFighterWingSpec" -> ((String) args[0]).endsWith("_missing_wing") ? null
					: proxy(FighterWingSpecAPI.class, (name, values) -> "getWingName".equals(name) ? "Wing " + args[0] : null);
			case "getWeaponSpec" -> proxy(WeaponSpecAPI.class, (name, values) -> "getWeaponName".equals(name) ? "Weapon " + args[0] : null);
			case "createEmptyVariant" -> {
				Variant variant = new Variant((String) args[0], (ShipHullSpecAPI) args[1]);
				yield variant.api;
			}
			case "getSprite" -> {
				if (failSprites) throw new IllegalArgumentException("Missing sprite");
				float[] state = {spriteWidth, spriteHeight, 11f, 17f, 0.8f};
				Color[] color = {Color.WHITE};
				SpriteAPI sprite = proxy(SpriteAPI.class, (name, values) -> switch (name) {
					case "getColor" -> color[0];
					case "setColor" -> {
						color[0] = (Color) values[0];
						yield null;
					}
					case "getWidth" -> state[0];
					case "getHeight" -> state[1];
					case "getCenterX" -> state[2];
					case "getCenterY" -> state[3];
					case "getAlphaMult" -> state[4];
					case "setCenter" -> {
						state[2] = (Float) values[0];
						state[3] = (Float) values[1];
						yield null;
					}
					case "setAlphaMult" -> {
						state[4] = (Float) values[0];
						yield null;
					}
					case "renderAtCenter" -> {
						spriteCenters.add(new float[]{(Float) values[0], (Float) values[1]});
						spriteRenderColors.add(color[0]);
						spriteRenderStates.add(state.clone());
						yield null;
					}
					case "setSize" -> {
						state[0] = (Float) values[0];
						state[1] = (Float) values[1];
						spriteSizes.add(new float[]{state[0], state[1]});
						yield null;
					}
					default -> null;
				});
				sprites.put(sprite, state);
				spritePaths.put(sprite, args.length == 1 ? (String) args[0]
						: args[0] + "/" + args[1]);
				yield sprite;
			}
			default -> null;
		}));
	}

	@AfterEach
	protected void restoreGameServices() {
		Global.setSettings(previousSettings);
		Global.setSector(previousSector);
	}

	protected ShipHullSpecAPI hull(String id, String name, List<String> slots) {
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> id;
			case "getHullName" -> name;
			case "getSpriteName" -> "graphics/" + id + ".png";
			case "getBuiltInWings" -> List.of();
			case "getWeaponSlot" -> proxy(WeaponSlotAPI.class, (key, values) -> null);
			default -> null;
		});
		hulls.put(id, hull);
		moduleSlots.put(hull, slots);
		return hull;
	}

	protected Variant source() throws JSONException {

		ShipHullSpecAPI parentHull = hull("parent", "Parent", List.of("module"));
		ShipHullSpecAPI moduleHull = hull("child", "Child", List.of());
		Variant child = new Variant("child_variant", moduleHull);
		child.api.setNumFluxVents(7);
		Variant parent = new Variant("parent_variant", parentHull);
		parent.api.setVariantDisplayName("Unsubmitted");
		parent.api.setModuleVariant("module", child.api);
		parent.api.getSModdedBuiltIns().add("built");
		parent.api.getSuppressedMods().add("d");
		parent.api.addPermaMod("sm", true);
		parent.json.put("wings", new JSONArray().put("wing"));
		parent.json.put("weaponGroups", new JSONArray().put(new JSONObject()
				.put("mode", "LINKED").put("autofire", true)
				.put("weapons", new JSONObject().put("weapon", "laser"))));
		return parent;
	}

	protected class Variant {
		private final JSONObject json = new JSONObject();
		private final Map<String, ShipVariantAPI> modules = new LinkedHashMap<>();
		private final LinkedHashSet<String> builtIns = new LinkedHashSet<>();
		private final LinkedHashSet<String> suppressed = new LinkedHashSet<>();
		private final LinkedHashSet<String> sMods = new LinkedHashSet<>();
		private final LinkedHashSet<String> permaMods = new LinkedHashSet<>();
		private final LinkedHashSet<String> mods = new LinkedHashSet<>();
		private final ShipVariantAPI api;

		protected Variant(String id, ShipHullSpecAPI hull) throws JSONException {

			json.put("variantId", id).put("hullId", hull.getHullId()).put("displayName", id);
			api = proxy(ShipVariantAPI.class, (method, args) -> switch (method) {
				case "getHullSpec" -> hull;
				case "getHullVariantId" -> json.getString("variantId");
				case "getDisplayName" -> json.getString("displayName");
				case "clone" -> args[0];
				case "toJSONObject" -> new JSONObject(json.toString())
						.put("hullMods", new JSONArray(mods)).put("permaMods", new JSONArray(permaMods)).put("sMods", new JSONArray(sMods));
				case "getSModdedBuiltIns" -> builtIns;
				case "getSuppressedMods" -> suppressed;
				case "getSMods" -> sMods;
				case "getModuleSlots" -> moduleSlots.get(hull);
				case "getStationModules" -> {
					Map<String, String> ids = new LinkedHashMap<>();
					modules.forEach((slot, variant) -> ids.put(slot, variant == null ? "missing" : variant.getHullVariantId()));
					yield ids;
				}
				case "getModuleVariant" -> modules.get(args[0]);
				case "setModuleVariant" -> modules.put((String) args[0], (ShipVariantAPI) args[1]);
				case "setVariantDisplayName" -> json.put("displayName", args[0]);
				case "setNumFluxVents" -> json.put("fluxVents", args[0]);
				case "setNumFluxCapacitors" -> json.put("fluxCapacitors", args[0]);
				case "setGoalVariant" -> json.put("goalVariant", args[0]);
				case "addMod" -> mods.add((String) args[0]);
				case "addSuppressedMod" -> suppressed.add((String) args[0]);
				case "addPermaMod" -> {
					permaMods.add((String) args[0]);
					if (args.length > 1 && (Boolean) args[1]) {
						sMods.add((String) args[0]);
					}
					yield null;
				}
				case "setWingId" -> {
					JSONArray wings = json.optJSONArray("wings");
					if (wings == null) {
						wings = new JSONArray();
					}
					wings.put((Integer) args[0], args[1]);
					json.put("wings", wings);
					yield null;
				}
				case "addWeaponGroup" -> {
					WeaponGroupSpec group = (WeaponGroupSpec) args[0];
					JSONArray groups = json.optJSONArray("weaponGroups");
					if (groups == null) {
						groups = new JSONArray();
					}
					groups.put(new JSONObject().put("mode", group.getType().name())
							.put("autofire", group.isAutofireOnByDefault())
							.put("weapons", new JSONObject().put("weapon", "laser")));
					json.put("weaponGroups", groups);
					yield null;
				}
				default -> null;
			});
		}
		public JSONObject getJson() {
			return json;
		}

		public Map<String, ShipVariantAPI> getModules() {
			return modules;
		}

		public ShipVariantAPI getApi() {
			return api;
		}
	}

	protected InputEventAPI event(String type, AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			case "isKeyboardEvent", "isKeyDownEvent" -> "escape".equals(type);
			case "getEventValue" -> Keyboard.KEY_ESCAPE;
			case "isMouseDownEvent" -> "outside".equals(type);
			case "getX", "getY" -> "outside".equals(type) ? -1 : 0;
			default -> null;
		});
	}

	protected interface Handler {
		Object apply(String method, Object[] args) throws Throwable;
	}

	protected static <T> T proxy(Class<T> type, Handler handler) {

		Class<?>[] interfaces = type == LabelAPI.class ? new Class<?>[]{type, UIComponentAPI.class} : new Class<?>[]{type};
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), interfaces, (instance, method, args) -> {
			if ("equals".equals(method.getName())) return instance == args[0];
			if ("hashCode".equals(method.getName())) return System.identityHashCode(instance);
			if ("clone".equals(method.getName())) return instance;
			Object result = handler.apply(method.getName(), args == null ? new Object[0] : args);
			if (result != null && method.getReturnType() == void.class) return null;
			if (result != null) return result;
			if (type == PositionAPI.class && method.getReturnType() == PositionAPI.class) return instance;
			if (method.getReturnType() == boolean.class) return false;
			if (method.getReturnType() == float.class) return 0f;
			if (method.getReturnType() == int.class) return 0;
			return null;
		}));
	}

	protected CustomPanelAPI panel(float width, float height, CustomUIPanelPlugin plugin) {
		throw new UnsupportedOperationException("UI fixture required");
	}

}
