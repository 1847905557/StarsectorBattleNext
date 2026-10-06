package data.simulator;

import data.model.report.BattleNext_BattleReportModel;
import data.scripts.tools.BattleNext_Logger;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorModelTest {

	@TempDir
	private Path directory;

	@Test
	void requestRetainsDuplicatesAndCopiesCallerLists() throws Exception {
		List<String> ids = new ArrayList<>(List.of("wolf_CS", "wolf_CS"));
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromVariants(ids, ids, BattleNext_SimulationConfig.defaults());
		ids.clear();
		assertEquals(List.of("wolf_CS", "wolf_CS"), request.player());
		assertEquals(request, BattleNext_SimulationRequest.fromJson(request.toJson()));
		assertThrows(UnsupportedOperationException.class, () -> request.player().add("other"));
	}

	@Test
	void configurationValidatesExternalBudgetsAndRoundTripsBothStrategies() throws Exception {
		for (BattleNext_SimulationConfig.Strategy strategy : BattleNext_SimulationConfig.Strategy.values()) {
			BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(strategy, 12000f, 9000f, 500f, 3600f, 120f, 32, true, false);
			assertEquals(config, BattleNext_SimulationConfig.fromJson(config.toJson()));
		}
		assertThrows(IllegalArgumentException.class, () -> new BattleNext_SimulationConfig(BattleNext_SimulationConfig.Strategy.AGGRESSIVE,
				Float.NaN, 9000f, 500f, 3600f, 120f, 32, true, false));
		assertThrows(IllegalArgumentException.class, () -> BattleNext_SimulationRequest.fromVariants(List.of(), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults()));
	}

	@Test
	void journalPreservesCompleteRequestReportAndOwnershipDuringRecoveryAndAcknowledgment() throws Exception {
		Logger logger = Logger.getLogger(BattleNext_Logger.class);
		Level previous = logger.getLevel();
		logger.setLevel(Level.OFF);
		try {
			BattleNext_SimulatorStorage storage = new BattleNext_SimulatorStorage(directory);
			BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults());
			BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
			report.setId(request.id());
			report.setName("中文 50% report");
			BattleNext_SimulationResult result = new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
					report.toJson().toString(), 100f, 500000000L, 6000L, 1024L, "");
			storage.write(request, result);
			BattleNext_SimulatorStorage.Stored recovered = storage.read(storage.pending().get(0));
			assertEquals(request, recovered.request());
			assertEquals(result, recovered.result());
			assertEquals("中文 50% report", recovered.result().report().getName());
			Path runtime = storage.directory("runtime/session");
			storage.cleanup(runtime);
			assertEquals(1, storage.pending().size());
			IOException failure = assertThrows(IOException.class, () -> storage.cleanup(directory.resolve("pending")));
			assertTrue(failure.getMessage().contains(directory.resolve("pending").toString()));
			storage.acknowledge(request.id());
			assertTrue(storage.pending().isEmpty());
		} finally {
			logger.setLevel(previous);
		}
	}

	@Test
	void terminalOutcomesPreserveMetricsAndCompleteResultsRequireReports() throws Exception {
		for (BattleNext_SimulationResult.Outcome outcome : BattleNext_SimulationResult.Outcome.values()) {
			String json = outcome == BattleNext_SimulationResult.Outcome.COMPLETED ? new BattleNext_BattleReportModel().toJson().put("name", "Report").toString() : "";
			BattleNext_SimulationResult result = new BattleNext_SimulationResult(UUID.randomUUID().toString(), outcome, json, 10f, 200L, 600L, 500L, "reason");
			assertEquals(result, BattleNext_SimulationResult.fromJson(result.toJson()));
		}
		assertThrows(IllegalArgumentException.class, () -> new BattleNext_SimulationResult(UUID.randomUUID().toString(),
				BattleNext_SimulationResult.Outcome.COMPLETED, "", 0f, 0L, 0L, 0L, ""));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void graphicsOverridesPreserveSourceAndMechanismSettingsAndRemainStableAcrossReload(boolean stored) throws Exception {

		BattleNext_SimulatorStorage storage = new BattleNext_SimulatorStorage(directory.resolve("owned"));
		Path source = directory.resolve("source/common/LunaSettings/shaderLib.json.data");
		JSONObject settings = new JSONObject().put("enableExplosionEffects", "启用").put("enableExplosionShockwave", true)
				.put("explosionTrailScale", 100).put("enableShaders", true).put("custom_value", "中文 50%")
				.put("custom_nested", new JSONObject().put("preserved", 42));
		List<String> options = List.of("enableLights", "enableBloom", "enableNormal", "autoGenNormals", "loadMaterial",
				"loadSurface", "enableDistortion", "enablePostProcess", "preloadAllMaps");
		for (String option : options) {
			settings.put(option, true);
		}
		byte[] original = settings.toString(2).getBytes(StandardCharsets.UTF_8);
		if (stored) {
			Files.createDirectories(source.getParent());
			Files.write(source, original);
		}
		Path worker = storage.directory("runtime/session").resolve("saves");
		storage.snapshotCommon(directory.resolve("source"), worker);
		storage.configureGraphics(worker);
		Path target = worker.resolve("common/LunaSettings/shaderLib.json.data");
		JSONObject result = new JSONObject(Files.readString(target, StandardCharsets.UTF_8));
		for (String option : options) {
			assertFalse(result.getBoolean(option), option);
		}
		if (stored) {
			assertArrayEquals(original, Files.readAllBytes(source));
			for (String key : JSONObject.getNames(settings)) {
				if (options.contains(key)) continue;
				assertEquals(settings.get(key).toString(), result.get(key).toString(), key);
			}
		} else {
			assertFalse(Files.exists(source));
			assertEquals(options.size(), result.length());
		}
		byte[] configured = Files.readAllBytes(target);
		assertFalse(configured.length >= 3 && configured[0] == (byte) 0xef && configured[1] == (byte) 0xbb && configured[2] == (byte) 0xbf);
		storage.configureGraphics(worker);
		assertArrayEquals(configured, Files.readAllBytes(target));
	}

	@Test
	void malformedWorkerGraphicsSettingsRejectPreparationAndKeepTheCopiedInput() throws Exception {
		BattleNext_SimulatorStorage storage = new BattleNext_SimulatorStorage(directory.resolve("owned"));
		Path worker = storage.directory("runtime/session").resolve("saves");
		Path target = worker.resolve("common/LunaSettings/shaderLib.json.data");
		Files.createDirectories(target.getParent());
		byte[] invalid = "{broken".getBytes(StandardCharsets.UTF_8);
		Files.write(target, invalid);
		assertThrows(JSONException.class, () -> storage.configureGraphics(worker));
		assertArrayEquals(invalid, Files.readAllBytes(target));
	}

	@Test
	void boxConfigurationPreservesBusinessContextAndUsesCompleteDefaultsInTheWorkerCopy() throws Exception {

		BattleNext_SimulatorStorage storage = new BattleNext_SimulatorStorage(directory.resolve("owned"));
		Path source = directory.resolve("source/common");
		Files.createDirectories(source.resolve("shaderpacks"));
		JSONObject configuration = new JSONObject().put("BUtil_EnableShader", true).put("BUtil_EnableDistortion", true)
				.put("BUtil_EnableDebug", true).put("BUtil_EnableCL", true).put("BUtil_InstanceClamp", 4096)
				.put("custom", "中文 50%");
		JSONObject effects = new JSONObject().put("BUtil_EnableBloom", true).put("BUtil_EnableAA", true)
				.put("BUtil_DepthAA", true).put("BUtil_AAConsole", true);
		byte[] original = configuration.toString().getBytes(StandardCharsets.UTF_8);
		byte[] selection = "{\"id\":\"business_shader_context\"}".getBytes(StandardCharsets.UTF_8);
		Files.write(source.resolve("BUtil_Configs.json.data"), original);
		Files.writeString(source.resolve("shaderpacks/BUtil_NotSelectedShaderPacks.json.data"), effects.toString(), StandardCharsets.UTF_8);
		Files.write(source.resolve("shaderpacks/BUtil_SelectedShaderPacks.json.data"), selection);
		Path worker = storage.directory("runtime/session").resolve("saves");
		storage.snapshotCommon(directory.resolve("source"), worker);
		storage.configureGraphics(worker);
		JSONObject configured = new JSONObject(Files.readString(worker.resolve("common/BUtil_Configs.json.data"), StandardCharsets.UTF_8));
		for (String key : List.of("BUtil_EnableShader", "BUtil_EnableDistortion", "BUtil_EnableDebug", "BUtil_EnableCL")) {
			assertFalse(configured.getBoolean(key), key);
		}
		assertEquals(4096, configured.getInt("BUtil_InstanceClamp"));
		assertEquals(32, configured.getInt("BUtil_CurveNode"));
		assertEquals(32, configured.getInt("BUtil_CurveInterpolation"));
		assertEquals("中文 50%", configured.getString("custom"));
		JSONObject configuredEffects = new JSONObject(Files.readString(worker.resolve("common/shaderpacks/BUtil_NotSelectedShaderPacks.json.data"), StandardCharsets.UTF_8));
		for (String key : List.of("BUtil_EnableBloom", "BUtil_EnableAA", "BUtil_DepthAA")) {
			assertFalse(configuredEffects.getBoolean(key), key);
		}
		assertTrue(configuredEffects.getBoolean("BUtil_AAConsole"));
		assertArrayEquals(selection, Files.readAllBytes(worker.resolve("common/shaderpacks/BUtil_SelectedShaderPacks.json.data")));
		assertArrayEquals(original, Files.readAllBytes(source.resolve("BUtil_Configs.json.data")));
	}
}
