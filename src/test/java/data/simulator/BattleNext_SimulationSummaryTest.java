package data.simulator;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.test.fixture.BattleNext_TestPerformance;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulationSummaryTest {

	@Test
	void deletionDuringACommittingRoundKeepsTheCancelledResultOutOfTaskMetrics() {
		String id = UUID.randomUUID().toString();
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(new BattleNext_SimulationRequest(id,
				BattleNext_SimulationRequest.Kind.TOURNAMENT, "{}", BattleNext_SimulationConfig.defaults()), null);
		task.receiveRound(new BattleNext_SimulationResult(id, BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED,
				"", 1f, 1L, 1L, 1L, ""));
		task.requestDeletion();
		assertDoesNotThrow(task::committedRound);
		assertTrue(task.getRoundResultsCopy().isEmpty());
		assertEquals(new BattleNext_SimulationTask.Metrics(0f, 0L, 0L, 0L), task.getSummary().metrics());
	}

	@Test
	void unchanged32FleetTournamentReusesItsSummaryAndPublishesStateChangesByVersion() throws Exception {

		List<String> fleets = IntStream.range(0, 32).mapToObj(index -> "fleet" + index).toList();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.ROUND_ROBIN, 1, 32);
		BattleNext_StartedTournament started = new BattleNext_TournamentEngine().start(configuration, fleets,
				BattleNext_StartedTournament.ExecutionKind.SIMULATION);
		assertEquals(496, started.getMatchesReadonly().size());
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(new BattleNext_SimulationRequest(UUID.randomUUID().toString(),
				BattleNext_SimulationRequest.Kind.TOURNAMENT, "{}", BattleNext_SimulationConfig.defaults()), null);
		task.publishStarted(started);
		BattleNext_SimulationTask.Summary first = task.getSummary();
		JSONObject measurements = BattleNext_TestPerformance.measure(20, () -> {
			for (int index = 0; index < 6000; index++) assertSame(first, task.getSummary());
		});
		BattleNext_TestPerformance.write("tournament-summary-32", measurements.put("fleets", 32).put("matches", 496)
				.put("summary_reads_per_sample", 6000).put("summary_instances", 1));

		task.transition(BattleNext_SimulationTask.State.RUNNING);
		assertNotSame(first, task.getSummary());
		assertEquals(BattleNext_SimulationTask.State.RUNNING, task.getSummary().state());
		assertEquals(task.getVersion(), task.getSummary().version());
		assertEquals("cup", task.getSummary().sourceTournamentId());
		assertEquals(new BattleNext_SimulationTask.Metrics(0f, 0L, 0L, 0L), task.getSummary().metrics());
	}
}
