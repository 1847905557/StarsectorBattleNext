package data.scripts.ui.widget;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.*;

public class BattleNext_VariantPreview {

	private final List<Part> parts;
	private final List<String> errors;
	private final Bounds bounds;

	public BattleNext_VariantPreview(JSONObject json, ShipHullSpecAPI hull, SettingsAPI settings) {
		Builder builder = new Builder(settings, json.optString("variantId", ""));
		builder.addVariant(new Node(json, hull, new Vector2f(), 90f, "", false));
		parts = List.copyOf(builder.parts);
		errors = List.copyOf(builder.errors);
		bounds = measure(parts);
	}

	public List<String> getErrors() {
		return errors;
	}

	public boolean hasContent() {
		return !parts.isEmpty();
	}

	public void render(float centerX, float centerY, float width, float height, float alpha) {
		if (parts.isEmpty()) return;
		float scale = BattleNext_PageLayout.spriteScaleToFit(bounds.right - bounds.left, bounds.top - bounds.bottom,
				width, height);
		float originX = centerX - (bounds.left + bounds.right) * 0.5f * scale;
		float originY = centerY - (bounds.bottom + bounds.top) * 0.5f * scale;
		for (Part part : parts) {
			part.render(originX, originY, scale, alpha);
		}
	}

	private static Bounds measure(List<Part> parts) {

		float left = Float.POSITIVE_INFINITY;
		float bottom = Float.POSITIVE_INFINITY;
		float right = Float.NEGATIVE_INFINITY;
		float top = Float.NEGATIVE_INFINITY;
		for (Part part : parts) {
			for (float horizontal : new float[]{-part.pivotX, part.width - part.pivotX}) {
				for (float vertical : new float[]{-part.pivotY, part.height - part.pivotY}) {
					Vector2f corner = rotate(new Vector2f(horizontal, vertical), part.angle);
					left = Math.min(left, part.position.x + corner.x);
					right = Math.max(right, part.position.x + corner.x);
					bottom = Math.min(bottom, part.position.y + corner.y);
					top = Math.max(top, part.position.y + corner.y);
				}
			}
		}
		return new Bounds(left, bottom, right, top);
	}

	private static Vector2f rotate(Vector2f vector, float angle) {
		double radians = Math.toRadians(angle);
		float cosine = (float) Math.cos(radians);
		float sine = (float) Math.sin(radians);
		return new Vector2f(vector.x * cosine - vector.y * sine, vector.x * sine + vector.y * cosine);
	}

	private record Bounds(float left, float bottom, float right, float top) {

	}

	private record Node(JSONObject json, ShipHullSpecAPI hull, Vector2f position, float facing, String path, boolean underParent) {

	}

	private record Part(SpriteAPI sprite, float width, float height, float pivotX, float pivotY,
			Vector2f position, float angle) {

		private void render(float originX, float originY, float scale, float alpha) {

			float previousWidth = sprite.getWidth();
			float previousHeight = sprite.getHeight();
			float previousCenterX = sprite.getCenterX();
			float previousCenterY = sprite.getCenterY();
			float previousAngle = sprite.getAngle();
			float previousAlpha = sprite.getAlphaMult();
			Color previousColor = sprite.getColor();
			int previousBlendSrc = sprite.getBlendSrc();
			int previousBlendDest = sprite.getBlendDest();
			try {
				sprite.setSize(width * scale, height * scale);
				sprite.setCenter(pivotX * scale, pivotY * scale);
				sprite.setAngle(angle);
				sprite.setColor(Color.WHITE);
				sprite.setNormalBlend();
				sprite.setAlphaMult(previousAlpha * alpha);
				sprite.renderAtCenter(originX + position.x * scale, originY + position.y * scale);
			} finally {
				sprite.setSize(previousWidth, previousHeight);
				sprite.setCenter(previousCenterX, previousCenterY);
				sprite.setAngle(previousAngle);
				sprite.setAlphaMult(previousAlpha);
				sprite.setColor(previousColor);
				sprite.setBlendFunc(previousBlendSrc, previousBlendDest);
			}
		}
	}

	private static class Builder {
		private final SettingsAPI settings;
		private final String variantId;
		private final List<Part> parts = new ArrayList<>();
		private final List<String> errors = new ArrayList<>();
		private final Set<JSONObject> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

		private Builder(SettingsAPI settings, String variantId) {
			this.settings = settings;
			this.variantId = variantId;
		}

		private void addVariant(Node node) {
			if (!visiting.add(node.json)) throw new IllegalArgumentException("Cyclic preview modules: " + node.path);
			try {
				List<Node> modules = modules(node);
				for (Node module : modules) {
					if (!module.underParent) continue;
					addVariant(module);
				}
				addHullSprite(node);
				addWeapons(node);
				for (Node module : modules) {
					if (module.underParent) continue;
					addVariant(module);
				}
			} finally {
				visiting.remove(node.json);
			}
		}

		private void addHullSprite(Node node) {
			String path = "";
			try {
				path = node.hull.getSpriteName();
				BattleNext_SpriteSpec spec = BattleNext_SpriteSpec.read(node.hull);
				addSprite(path, node.position, node.facing - 90f, spec,
						node, "", node.hull.getHullId(), "hull.sprite");
			} catch (RuntimeException exception) {
				failed(node, "", node.hull.getHullId(), "hull.resolve", path, exception);
			}
		}

		private void addWeapons(Node node) {

			Map<String, String> weapons = new TreeMap<>(node.hull.getBuiltInWeapons());
			JSONArray groups = node.json.optJSONArray("weaponGroups");
			if (groups != null) {
				for (int index = 0; index < groups.length(); index++) {
					try {
						JSONObject entries = groups.getJSONObject(index).getJSONObject("weapons");
						for (String slot : keys(entries)) {
							weapons.put(slot, entries.getString(slot));
						}
					} catch (Exception exception) {
						failed(node, "", "", "weapon_group." + index, "", exception);
					}
				}
			}
			for (var entry : weapons.entrySet()) {
				String slotId = entry.getKey();
				String id = entry.getValue();
				if (id.isEmpty()) continue;
				try {
					WeaponSlotAPI slot = node.hull.getWeaponSlot(slotId);
					if (slot == null) throw new IllegalArgumentException("Unknown weapon slot: " + slotId);
					if (slot.isHidden()) continue;
					if (!slot.isTurret() && !slot.isHardpoint()) continue;
					WeaponSpecAPI spec = settings.getWeaponSpec(id);
					if (spec == null) throw new IllegalArgumentException("Unknown weapon: " + id);
					Vector2f position = Vector2f.add(node.position, rotate(slot.getLocation(), node.facing), new Vector2f());
					float facing = node.facing + slot.getAngle();
					addWeaponSprite(spec, slot, position, facing, node, false);
					addWeaponSprite(spec, slot, position, facing, node, true);
					addMissiles(spec, slot, position, facing, node);
				} catch (Exception exception) {
					failed(node, slotId, id, "weapon.resolve", "", exception);
				}
			}
		}

		private void addWeaponSprite(WeaponSpecAPI spec, WeaponSlotAPI slot, Vector2f position,
				float facing, Node node, boolean barrel) {
			String stage = barrel ? "weapon.barrel" : "weapon.sprite";
			String path = "";
			try {
				if (barrel) {
					if (!(spec instanceof ProjectileWeaponSpecAPI projectile)) return;
					path = slot.isHardpoint() ? projectile.getHardpointGunSpriteName() : projectile.getTurretGunSpriteName();
				} else {
					path = slot.isHardpoint() ? spec.getHardpointSpriteName() : spec.getTurretSpriteName();
				}
				if (path == null || path.isEmpty()) return;
				SpriteAPI sprite = settings.getSprite(path);
				parts.add(new Part(sprite, sprite.getWidth(), sprite.getHeight(), sprite.getWidth() * 0.5f,
						sprite.getHeight() * (slot.isHardpoint() ? 0.25f : 0.5f), position, facing - 90f));
			} catch (RuntimeException exception) {
				failed(node, slot.getId(), spec.getWeaponId(), stage, path, exception);
			}
		}

		private void addMissiles(WeaponSpecAPI spec, WeaponSlotAPI slot, Vector2f position, float facing, Node node) {
			String path = "";
			try {
				if (!(spec.getProjectileSpec() instanceof MissileSpecAPI missile)) return;
				path = missile.getHullSpec().getSpriteName();
				BattleNext_SpriteSpec spriteSpec = BattleNext_SpriteSpec.read(missile.getHullSpec());
				List<Vector2f> offsets = slot.isHardpoint() ? spec.getHardpointFireOffsets() : spec.getTurretFireOffsets();
				List<Float> angles = slot.isHardpoint() ? spec.getHardpointAngleOffsets() : spec.getTurretAngleOffsets();
				for (int index = 0; index < offsets.size(); index++) {
					float angle = facing + (angles.isEmpty() ? 0f : angles.get(index));
					Vector2f location = Vector2f.add(position, rotate(offsets.get(index), facing), new Vector2f());
					addSprite(path, location, angle - 90f, spriteSpec, node, slot.getId(), spec.getWeaponId(), "missile.sprite");
				}
			} catch (Exception exception) {
				failed(node, slot.getId(), spec.getWeaponId(), "missile.resolve", path, exception);
			}
		}

		private List<Node> modules(Node node) {
			List<Node> result = new ArrayList<>();
			Object raw = node.json.opt("modules");
			try {
				if (raw instanceof JSONObject object) {
					addModules(result, object, node);
				} else if (raw instanceof JSONArray array) {
					for (int index = 0; index < array.length(); index++) {
						try {
							addModules(result, array.getJSONObject(index), node);
						} catch (Exception exception) {
							failed(node, String.valueOf(index), "", "module.array", "", exception);
						}
					}
				} else if (raw != null) {
					throw new IllegalArgumentException("Invalid modules: " + raw);
				}
			} catch (Exception exception) {
				failed(node, "", "", "module.resolve", "", exception);
			}
			return result;
		}

		private void addModules(List<Node> result, JSONObject modules, Node node) {

			for (String slotId : keys(modules)) {
				String id = "";
				try {
					Object raw = modules.get(slotId);
					JSONObject json;
					if (raw instanceof JSONObject object) {
						json = object;
						id = object.optString("variantId", "");
					} else if (raw instanceof String reference) {
						id = reference;
						json = BattleNext_VariantBuilder.fromRegistered(reference).toJson();
					} else {
						throw new IllegalArgumentException("Invalid module: " + slotId);
					}
					ShipHullSpecAPI hull = settings.getHullSpec(json.getString("hullId"));
					if (hull == null) throw new IllegalArgumentException("Unknown module hull: " + json.getString("hullId"));
					WeaponSlotAPI slot = node.hull.getWeaponSlot(slotId);
					if (slot == null || !slot.isStationModule()) throw new IllegalArgumentException("Unknown module slot: " + slotId);
					if (visiting.contains(json)) throw new IllegalArgumentException("Cyclic preview modules: " + slotId);
					float facing = node.facing + slot.getAngle();
					Vector2f position = Vector2f.add(node.position, rotate(slot.getLocation(), node.facing), new Vector2f());
					Vector2f anchor = hull.getModuleAnchor();
					if (anchor != null) {
						Vector2f.sub(position, rotate(anchor, facing), position);
					}
					String path = node.path.isEmpty() ? slotId : node.path + "/" + slotId;
					result.add(new Node(json, hull, position, facing, path, hull.getHints().contains(ShipTypeHints.UNDER_PARENT)));
				} catch (Exception exception) {
					failed(node, slotId, id, "module.resolve", "", exception);
				}
			}
		}

		private void addSprite(String path, Vector2f position, float angle, BattleNext_SpriteSpec spec,
				Node node, String slot, String id, String stage) {
			try {
				SpriteAPI sprite = settings.getSprite(path);
				parts.add(new Part(sprite, spec.width(), spec.height(), spec.centerX(), spec.centerY(), position, angle));
			} catch (RuntimeException exception) {
				failed(node, slot, id, stage, path, exception);
			}
		}

		private void failed(Node node, String slot, String id, String stage, String path, Exception exception) {
			errors.add(stage + " [" + node.json.optString("variantId", "") + "/" + slot + "/" + id + "]: " + exception);
			BattleNext_Logger.warn(BattleNext_VariantPreview.class, "variant.preview.degraded",
					BattleNext_Logger.fields().put("variant_id", variantId).put("module_path", node.path)
							.put("context_variant_id", node.json.optString("variantId", "")).put("hull_id", node.hull.getHullId())
							.put("slot_id", slot).put("object_id", id).put("stage", stage).put("resource", path).build(), exception);
		}

		private static List<String> keys(JSONObject object) {
			if (object.length() == 0) return List.of();
			String[] keys = JSONObject.getNames(object);
			Arrays.sort(keys);
			return Arrays.asList(keys);
		}
	}
}
