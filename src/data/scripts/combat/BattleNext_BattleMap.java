package data.scripts.combat;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;

public final class BattleNext_BattleMap {
	public static final int MAP_COUNT = 12;

	private BattleNext_BattleMap() { }

	public static int resolve(int map, int seed) {
		return map < 0 ? Math.floorMod(seed, MAP_COUNT) : Math.floorMod(map, MAP_COUNT);
	}

	public static void apply(MissionDefinitionAPI api, int map, float size) {
		float scale = Math.max(0.1f, size);
		float width;
		float height;
		switch (Math.floorMod(map, MAP_COUNT)) {
			case 0 -> { width = 8000f; height = 8000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(0f, 0f, "comm_relay"); nebulaCorners(api, width * scale, height * scale, 500f); api.addAsteroidField(0f, 0f, 0f, 500f, 10f, 15f, 15); }
			case 1 -> { width = 12000f; height = 8000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); objectives(api, width * scale, height * scale); api.addAsteroidField(-0.4f * width * scale, 0f, 90f, 1000f, 20f, 25f, 40); api.addAsteroidField(0.4f * width * scale, 0f, -90f, 1000f, 20f, 25f, 40); }
			case 2 -> { width = 12000f; height = 9000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); objectives(api, width * scale, height * scale); nebulaColumns(api, width * scale, height * scale); }
			case 3 -> { width = 11000f; height = 11000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); objectives(api, width * scale, height * scale); for (int index = 0; index < 10; index++) api.addNebula((index % 5 - 2) * 600f * scale, (index / 5 == 0 ? 1f : -1f) * 1800f * scale, 300f + index % 3 * 150f); }
			case 4 -> { width = 10000f; height = 12000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(0f, -0.25f * height * scale, "comm_relay"); api.addObjective(0f, 0.25f * height * scale, "comm_relay"); nebulaRows(api, width * scale, height * scale); }
			case 5 -> { width = 14000f; height = 6000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(-0.25f * width * scale, 0f, "sensor_array"); api.addObjective(0.25f * width * scale, 0f, "sensor_array"); api.addNebula(0f, 0f, 500f * scale); }
			case 6 -> { width = 12000f; height = 12000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(0f, 0f, "sensor_array"); api.addObjective(-0.25f * width * scale, -0.25f * height * scale, "nav_buoy"); api.addObjective(0.25f * width * scale, 0.25f * height * scale, "nav_buoy"); nebulaCorners(api, width * scale, height * scale, 500f); }
			case 7 -> { width = 10000f; height = 16000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(0f, 0f, "nav_buoy"); api.addObjective(-0.2f * width * scale, -0.35f * height * scale, "comm_relay"); api.addObjective(0.2f * width * scale, 0.35f * height * scale, "comm_relay"); api.addAsteroidField(0f, 0f, 30f, 1600f, 20f, 25f, 40); }
			case 8 -> { width = 16000f; height = 12000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(0f, 0f, "comm_relay"); api.addObjective(-0.15f * width * scale, 0f, "nav_buoy"); api.addObjective(0.15f * width * scale, 0f, "nav_buoy"); }
			case 9 -> { width = 16000f; height = 14000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(-0.25f * width * scale, 0.15f * height * scale, "nav_buoy"); api.addObjective(0.25f * width * scale, -0.15f * height * scale, "nav_buoy"); api.addObjective(-0.25f * width * scale, -0.15f * height * scale, "sensor_array"); api.addObjective(0.25f * width * scale, 0.15f * height * scale, "sensor_array"); }
			case 10 -> { width = 16000f; height = 16000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(0f, 0.35f * height * scale, "nav_buoy"); api.addObjective(0f, -0.35f * height * scale, "nav_buoy"); api.addAsteroidField(0f, 0f, 60f, 2000f, 20f, 25f, 40); api.addNebula(0f, 0f, 3200f * scale); }
			default -> { width = 18000f; height = 14000f; api.initMap(-width * scale / 2f, width * scale / 2f, -height * scale / 2f, height * scale / 2f); api.addObjective(-0.05f * width * scale, 0.15f * height * scale, "nav_buoy"); api.addObjective(0.05f * width * scale, 0.15f * height * scale, "sensor_array"); api.addObjective(-0.05f * width * scale, -0.15f * height * scale, "sensor_array"); api.addObjective(0.05f * width * scale, -0.15f * height * scale, "nav_buoy"); }
		}
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

	private static void nebulaColumns(MissionDefinitionAPI api, float width, float height) {
		for (int index = -1; index <= 1; index++) api.addNebula(index * 0.3f * width, 0.2f * height, 1000f);
	}

	private static void nebulaRows(MissionDefinitionAPI api, float width, float height) {
		api.addNebula(0.05f * width, 0.3f * height, 500f);
		api.addNebula(-0.05f * width, 0.3f * height, 500f);
		api.addNebula(0.05f * width, -0.3f * height, 500f);
		api.addNebula(-0.05f * width, -0.3f * height, 500f);
	}
}
