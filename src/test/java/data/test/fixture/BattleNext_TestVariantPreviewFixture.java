package data.test.fixture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.specs.Q;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_VariantPreview;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;

public class BattleNext_TestVariantPreviewFixture extends BattleNext_TestVariantFixture {
	protected final Map<String, WeaponSpecAPI> previewWeapons = new HashMap<>();
	protected final Map<String, float[]> imageSizes = new HashMap<>();
	protected final Map<String, SpriteAPI> sharedSprites = new HashMap<>();
	protected final Set<String> failedImages = new HashSet<>();
	protected final Map<String, String> failedWeaponQueries = new HashMap<>();
	protected final List<String> weaponReads = new ArrayList<>();
	protected final List<String> imageReads = new ArrayList<>();
	protected final Map<String, Q> spriteSpecs = new HashMap<>();
	protected final Set<String> failedSpriteSpecs = new HashSet<>();
	protected final List<String> spriteSpecReads = new ArrayList<>();
	private final RecordingAppender appender = new RecordingAppender();
	private Logger logger;

	@BeforeEach
	protected void configurePreviewServices() {

		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getWeaponSpec")) {
				String id = (String) arguments[0];
				weaponReads.add(id);
				if (previewWeapons.containsKey(id)) return previewWeapons.get(id);
			}
			if (method.getName().equals("getSprite") && arguments.length == 1) {
				String path = (String) arguments[0];
				imageReads.add(path);
				if (failedImages.contains(path)) throw new IllegalArgumentException("Missing image: " + path);
				if (sharedSprites.containsKey(path)) return sharedSprites.get(path);
				SpriteAPI sprite = previous.getSprite(path);
				float[] size = imageSizes.getOrDefault(path, new float[]{16f, 24f});
				sprite.setSize(size[0], size[1]);
				sharedSprites.put(path, sprite);
				return sprite;
			}
			try {
				return method.invoke(previous, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_Logger.fields();
		logger = Global.getLogger(BattleNext_Logger.class);
		logger.addAppender(appender);
	}

	@AfterEach
	protected void releasePreviewLogging() {
		logger.removeAppender(appender);
	}

	protected ShipHullSpecAPI previewHull(String id, Map<String, WeaponSlotAPI> slots,
			Map<String, String> builtIns, Vector2f anchor, boolean underParent) {
		spriteSpecs.put(id, new Q("graphics/" + id + ".png", 80f, 100f, 40f, 50f, 0f, null));
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, arguments) -> switch (method) {
			case "getHullId", "getHullName" -> id;
			case "getSpriteName" -> "graphics/" + id + ".png";
			case "getSpriteSpec" -> spriteSpec(id);
			case "getBuiltInWeapons" -> new HashMap<>(builtIns);
			case "getBuiltInMods", "getBuiltInWings" -> List.of();
			case "getModuleAnchor" -> anchor;
			case "getHints" -> underParent ? EnumSet.of(ShipTypeHints.UNDER_PARENT) : EnumSet.noneOf(ShipTypeHints.class);
			case "getWeaponSlot", "getWeaponSlotAPI" -> slots.get(arguments[0]);
			case "getAllWeaponSlotsCopy" -> new ArrayList<>(slots.values());
			default -> null;
		});
		hulls.put(id, hull);
		moduleSlots.put(hull, slots.entrySet().stream().filter(entry -> entry.getValue().isStationModule())
				.map(Map.Entry::getKey).toList());
		imageSizes.put("graphics/" + id + ".png", new float[]{80f, 100f});
		return hull;
	}

	protected WeaponSlotAPI previewSlot(String id, String type, Vector2f location, float angle) {
		return proxy(WeaponSlotAPI.class, (method, arguments) -> switch (method) {
			case "getId" -> id;
			case "isTurret" -> type.equals("turret") || type.equals("decorative");
			case "isHardpoint" -> type.equals("hardpoint");
			case "isHidden" -> type.equals("hidden");
			case "isDecorative" -> type.equals("decorative");
			case "isStationModule" -> type.equals("module");
			case "getLocation" -> location;
			case "getAngle" -> angle;
			default -> null;
		});
	}

	protected WeaponSpecAPI previewWeapon(String id, boolean missile, List<Vector2f> offsets, List<Float> angles) {
		spriteSpecs.put(id + "_missile", new Q("graphics/" + id + "_missile.png", 10f, 14f, 5f, 7f, 0f, null));
		ShipHullSpecAPI missileHull = proxy(ShipHullSpecAPI.class, (method, arguments) -> switch (method) {
			case "getHullId" -> id + "_missile";
			case "getSpriteName" -> "graphics/" + id + "_missile.png";
			case "getSpriteSpec" -> spriteSpec(id + "_missile");
			default -> null;
		});
		MissileSpecAPI projectile = proxy(MissileSpecAPI.class, (method, arguments) ->
				method.equals("getHullSpec") ? missileHull : null);
		WeaponSpecAPI spec = proxy(ProjectileWeaponSpecAPI.class, (method, arguments) -> {
			if (method.equals(failedWeaponQueries.get(id))) throw new IllegalArgumentException("Weapon query failure: " + method);
			return switch (method) {
			case "getWeaponId", "getWeaponName" -> id;
			case "getTurretSpriteName" -> "graphics/" + id + "_turret.png";
			case "getHardpointSpriteName" -> "graphics/" + id + "_hardpoint.png";
			case "getTurretGunSpriteName" -> "graphics/" + id + "_turret_gun.png";
			case "getHardpointGunSpriteName" -> "graphics/" + id + "_hardpoint_gun.png";
			case "getProjectileSpec" -> missile ? projectile : null;
			case "getTurretFireOffsets", "getHardpointFireOffsets" -> offsets;
			case "getTurretAngleOffsets", "getHardpointAngleOffsets" -> angles;
			case "getHiddenFireOffsets", "getHiddenAngleOffsets" -> throw new AssertionError("Hidden weapon queried");
			default -> null;
			};
		});
		previewWeapons.put(id, spec);
		imageSizes.put("graphics/" + id + "_missile.png", new float[]{10f, 14f});
		return spec;
	}

	private Q spriteSpec(String id) {
		spriteSpecReads.add(id);
		if (failedSpriteSpecs.contains(id)) throw new IllegalStateException("Sprite spec failure: " + id);
		return spriteSpecs.get(id);
	}

	protected JSONObject previewJson(String id, String hullId, Map<String, String> weapons) throws JSONException {
		return new JSONObject().put("variantId", id).put("hullId", hullId)
				.put("weaponGroups", new JSONArray().put(new JSONObject().put("mode", "LINKED")
						.put("weapons", new JSONObject(weapons))));
	}

	protected void renderPreview(BattleNext_VariantPreview preview, float width, float height) {
		spriteCenters.clear();
		spriteRenderStates.clear();
		spriteRenderTransforms.clear();
		spriteRenderColors.clear();
		renderedSprites.clear();
		preview.render(100f, 200f, width, height, 0.5f);
	}

	protected List<String> renderedPaths() {
		return renderedSprites.stream().map(spritePaths::get).toList();
	}

	protected int renderIndex(String path, int occurrence) {
		List<String> paths = renderedPaths();
		for (int index = 0; index < paths.size(); index++) {
			if (!paths.get(index).equals(path)) continue;
			if (occurrence-- == 0) return index;
		}
		throw new AssertionError("Missing render: " + path);
	}

	protected List<JSONObject> previewLogs() throws JSONException {
		List<JSONObject> entries = new ArrayList<>();
		for (LoggingEvent event : appender.events) {
			JSONObject entry = new JSONObject(event.getRenderedMessage());
			if (entry.getString("source").equals(BattleNext_VariantPreview.class.getName())) {
				entries.add(entry);
			}
		}
		return entries;
	}

	private static class RecordingAppender extends AppenderSkeleton {
		private final List<LoggingEvent> events = new ArrayList<>();

		@Override
		protected void append(LoggingEvent event) {
			events.add(event);
		}

		@Override
		public void close() {

		}

		@Override
		public boolean requiresLayout() {
			return false;
		}
	}
}
