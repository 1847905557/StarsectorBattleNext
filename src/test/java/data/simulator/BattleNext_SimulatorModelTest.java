package data.simulator;

import data.model.report.BattleNext_BattleReportModel;
import data.scripts.tools.BattleNext_Logger;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorModelTest {

	@TempDir
	private Path directory;

	@Test
	void requestRetainsDuplicatesAndCopiesCallerLists() throws Exception {
		List<String> ids = new ArrayList<>(List.of("wolf_CS", "wolf_CS"));
		BattleNext_SimulationRequest request = new BattleNext_SimulationRequest(UUID.randomUUID().toString(), ids, ids, BattleNext_SimulationConfig.lab());
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
		assertThrows(IllegalArgumentException.class, () -> new BattleNext_SimulationRequest(UUID.randomUUID().toString(), List.of(), List.of("wolf_CS"), BattleNext_SimulationConfig.lab()));
	}

	@Test
	void journalPreservesCompleteRequestReportAndOwnershipDuringRecoveryAndAcknowledgment() throws Exception {
		Logger logger = Logger.getLogger(BattleNext_Logger.class);
		Level previous = logger.getLevel();
		logger.setLevel(Level.OFF);
		try {
			BattleNext_SimulatorStorage storage = new BattleNext_SimulatorStorage(directory);
			BattleNext_SimulationRequest request = new BattleNext_SimulationRequest(UUID.randomUUID().toString(), List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.lab());
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
			assertThrows(Exception.class, () -> storage.cleanup(directory.resolve("pending")));
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
}
