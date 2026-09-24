package data.scripts.combat;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;

public final class BattleNext_BattleMap {

	public static final int MAP_COUNT = 12;
	private static final float MIN_MAP_SCALE = 0.1f;

	private BattleNext_BattleMap() { }

	public static int resolve(int map, int seed) {
		return map < 0 ? Math.floorMod(seed, MAP_COUNT) : Math.floorMod(map, MAP_COUNT);
	}

	public static void apply(MissionDefinitionAPI api, int map, float size) {
		float scale = Math.max(MIN_MAP_SCALE, size);
		float width;
		float height;
		switch (Math.floorMod(map, MAP_COUNT)) {
			case 0 -> {
				width = 8000f;
				height = 8000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(0f, 0f, "comm_relay");
				nebulaCorners(api, scaledWidth, scaledHeight, 500f * scale);
				addAsteroidField(api, 0f, 0f, 0f, 500f * scale, 10f, 15f, Math.max(1, Math.round(15 * scale)));
			}
			case 1 -> {
				width = 12000f;
				height = 8000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				objectives(api, scaledWidth, scaledHeight);
				addAsteroidField(api, -0.4f * scaledWidth, 0f, 90f, 1000f * scale, 20f, 25f, Math.max(1, Math.round(40 * scale)));
				addAsteroidField(api, 0.4f * scaledWidth, 0f, -90f, 1000f * scale, 20f, 25f, Math.max(1, Math.round(40 * scale)));
			}
			case 2 -> {
				width = 12000f;
				height = 9000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				objectives(api, scaledWidth, scaledHeight);
				nebulaColumns(api, scaledWidth, scaledHeight, 1000f * scale);
			}
			case 3 -> {
				width = 11000f;
				height = 11000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				objectives(api, scaledWidth, scaledHeight);
				for (int index = 0; index < 10; index++) {
					float x = (index % 5 - 2) * 600f * scale;
					float y = (index / 5 == 0 ? 1f : -1f) * 1800f * scale;
					float radius = (300f + index % 3 * 150f) * scale;
					api.addNebula(x, y, radius);
				}
			}
			case 4 -> {
				width = 10000f;
				height = 12000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(0f, -0.25f * scaledHeight, "comm_relay");
				api.addObjective(0f, 0.25f * scaledHeight, "comm_relay");
				nebulaRows(api, scaledWidth, scaledHeight, 500f * scale);
			}
			case 5 -> {
				width = 14000f;
				height = 6000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(-0.25f * scaledWidth, 0f, "sensor_array");
				api.addObjective(0.25f * scaledWidth, 0f, "sensor_array");
				api.addNebula(0f, 0f, 500f * scale);
			}
			case 6 -> {
				width = 12000f;
				height = 12000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(0f, 0f, "sensor_array");
				api.addObjective(-0.25f * scaledWidth, -0.25f * scaledHeight, "nav_buoy");
				api.addObjective(0.25f * scaledWidth, 0.25f * scaledHeight, "nav_buoy");
				nebulaCorners(api, scaledWidth, scaledHeight, 500f * scale);
			}
			case 7 -> {
				width = 10000f;
				height = 16000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(0f, 0f, "nav_buoy");
				api.addObjective(-0.2f * scaledWidth, -0.35f * scaledHeight, "comm_relay");
				api.addObjective(0.2f * scaledWidth, 0.35f * scaledHeight, "comm_relay");
				addAsteroidField(api, 0f, 0f, 30f, 1600f * scale, 20f, 25f, Math.max(1, Math.round(40 * scale)));
			}
			case 8 -> {
				width = 16000f;
				height = 12000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(0f, 0f, "comm_relay");
				api.addObjective(-0.15f * scaledWidth, 0f, "nav_buoy");
				api.addObjective(0.15f * scaledWidth, 0f, "nav_buoy");
			}
			case 9 -> {
				width = 16000f;
				height = 14000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(-0.25f * scaledWidth, 0.15f * scaledHeight, "nav_buoy");
				api.addObjective(0.25f * scaledWidth, -0.15f * scaledHeight, "nav_buoy");
				api.addObjective(-0.25f * scaledWidth, -0.15f * scaledHeight, "sensor_array");
				api.addObjective(0.25f * scaledWidth, 0.15f * scaledHeight, "sensor_array");
			}
			case 10 -> {
				width = 16000f;
				height = 16000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(0f, 0.35f * scaledHeight, "nav_buoy");
				api.addObjective(0f, -0.35f * scaledHeight, "nav_buoy");
				addAsteroidField(api, 0f, 0f, 60f, 2000f * scale, 20f, 25f, Math.max(1, Math.round(40 * scale)));
				api.addNebula(0f, 0f, 3200f * scale);
			}
			default -> {
				width = 18000f;
				height = 14000f;
				float scaledWidth = width * scale;
				float scaledHeight = height * scale;
				initMap(api, scaledWidth, scaledHeight);
				api.addObjective(-0.05f * scaledWidth, 0.15f * scaledHeight, "nav_buoy");
				api.addObjective(0.05f * scaledWidth, 0.15f * scaledHeight, "sensor_array");
				api.addObjective(-0.05f * scaledWidth, -0.15f * scaledHeight, "sensor_array");
				api.addObjective(0.05f * scaledWidth, -0.15f * scaledHeight, "nav_buoy");
			}
		}
	}

	private static void initMap(MissionDefinitionAPI api, float width, float height) {
		api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);
	}

	private static void objectives(MissionDefinitionAPI api, float width, float height) {
		api.addObjective(-0.25f * width, 0.05f * height, "sensor_array");
		api.addObjective(0.25f * width, -0.05f * height, "nav_buoy");
		api.addObjective(-0.25f * width, -0.05f * height, "nav_buoy");
		api.addObjective(0.25f * width, 0.05f * height, "sensor_array");
	}

	private static void nebulaCorners(MissionDefinitionAPI api, float width, float height, float radius) {
		api.addNebula(0.25f * width, 0.25f * height, radius);
		api.addNebula(-0.25f * width, 0.25f * height, radius);
		api.addNebula(0.25f * width, -0.25f * height, radius);
		api.addNebula(-0.25f * width, -0.25f * height, radius);
	}

	private static void nebulaColumns(MissionDefinitionAPI api, float width, float height, float radius) {
		for (int index = -1; index <= 1; index++) api.addNebula(index * 0.3f * width, 0.2f * height, radius);
	}

	private static void nebulaRows(MissionDefinitionAPI api, float width, float height, float radius) {
		api.addNebula(0.05f * width, 0.3f * height, radius);
		api.addNebula(-0.05f * width, 0.3f * height, radius);
		api.addNebula(0.05f * width, -0.3f * height, radius);
		api.addNebula(-0.05f * width, -0.3f * height, radius);
	}

	private static void addAsteroidField(MissionDefinitionAPI api, float x, float y, float angle,
			float width, float minSize, float maxSize, int count) {
		api.addAsteroidField(x, y, angle, width, minSize, maxSize, count);
	}
}
