package data.scripts.ai.rtt;

import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

public class BattleNext_RttGeometry {

	private static final float MIN_SPACING = 100f;
	private static final float SHIP_SPACING = 1.25f;

	private BattleNext_RttGeometry() {
	}

	public static boolean inside(float x, float y, float firstX, float firstY, float secondX, float secondY) {
		return x >= Math.min(firstX, secondX) && x <= Math.max(firstX, secondX)
				&& y >= Math.min(firstY, secondY) && y <= Math.max(firstY, secondY);
	}

	public static ShipAPI hit(List<ShipAPI> ships, float x, float y) {
		ShipAPI best = null;
		float bestDistance = Float.MAX_VALUE;
		for (ShipAPI ship : ships) {
			float dx = ship.getLocation().x - x;
			float dy = ship.getLocation().y - y;
			float distance = dx * dx + dy * dy;
			if (distance > ship.getCollisionRadius() * ship.getCollisionRadius()) continue;
			if (distance >= bestDistance) continue;
			best = ship;
			bestDistance = distance;
		}
		return best;
	}

	public static Set<ShipAPI> select(Set<ShipAPI> current, List<ShipAPI> candidates,
			float firstX, float firstY, float secondX, float secondY, boolean dragging, boolean additive) {
		Set<ShipAPI> selected = additive ? new LinkedHashSet<>(current) : new LinkedHashSet<>();
		if (!dragging) {
			ShipAPI ship = hit(candidates, secondX, secondY);
			if (ship != null) selected.add(ship);
			return selected;
		}
		for (ShipAPI ship : candidates) {
			Vector2f location = ship.getLocation();
			if (inside(location.x, location.y, firstX, firstY, secondX, secondY)) selected.add(ship);
		}
		return selected;
	}

	public static Map<ShipAPI, Vector2f> formation(List<ShipAPI> ships, Vector2f center,
			float mapWidth, float mapHeight) {
		Map<ShipAPI, Vector2f> result = new IdentityHashMap<>();
		if (ships.isEmpty()) return result;
		List<ShipAPI> ordered = new ArrayList<>(ships);
		ordered.sort(Comparator.comparingDouble((ShipAPI ship) -> ship.getLocation().x)
				.thenComparingDouble(ship -> ship.getLocation().y));
		float averageX = 0f;
		float averageY = 0f;
		float maxRadius = 0f;
		for (ShipAPI ship : ordered) {
			averageX += ship.getLocation().x;
			averageY += ship.getLocation().y;
			maxRadius = Math.max(maxRadius, ship.getCollisionRadius());
		}
		averageX /= ordered.size();
		averageY /= ordered.size();
		float sourceSpan = 0f;
		for (ShipAPI ship : ordered) {
			sourceSpan = Math.max(sourceSpan, Vector2f.sub(ship.getLocation(),
					new Vector2f(averageX, averageY), null).length());
		}
		float desiredSpan = Math.max(maxRadius * SHIP_SPACING, MIN_SPACING) * (float) Math.sqrt(ordered.size());
		float scale = sourceSpan < 1f ? 0f : Math.min(1f, desiredSpan / sourceSpan);
		for (int index = 0; index < ordered.size(); index++) {
			ShipAPI ship = ordered.get(index);
			float x = (ship.getLocation().x - averageX) * scale;
			float y = (ship.getLocation().y - averageY) * scale;
			if (sourceSpan < 1f) x = (index - (ordered.size() - 1) * 0.5f) * maxRadius * 2.5f;
			result.put(ship, new Vector2f(x, y));
		}
		for (int second = 1; second < ordered.size(); second++) {
			ShipAPI right = ordered.get(second);
			Vector2f b = result.get(right);
			for (int first = 0; first < second; first++) {
				ShipAPI left = ordered.get(first);
				Vector2f a = result.get(left);
				float dy = b.y - a.y;
				float minimum = (left.getCollisionRadius() + right.getCollisionRadius()) * SHIP_SPACING;
				if (Math.abs(dy) >= minimum) continue;
				float requiredX = a.x + (float) Math.sqrt(minimum * minimum - dy * dy);
				b.x = Math.max(b.x, requiredX);
			}
		}
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (Vector2f point : result.values()) {
			minX = Math.min(minX, point.x);
			maxX = Math.max(maxX, point.x);
			minY = Math.min(minY, point.y);
			maxY = Math.max(maxY, point.y);
		}
		float targetX = Math.max(-mapWidth * 0.5f - minX,
				Math.min(center.x, mapWidth * 0.5f - maxX));
		float targetY = Math.max(-mapHeight * 0.5f - minY,
				Math.min(center.y, mapHeight * 0.5f - maxY));
		for (Vector2f point : result.values()) {
			point.x += targetX;
			point.y += targetY;
		}
		return result;
	}
}
