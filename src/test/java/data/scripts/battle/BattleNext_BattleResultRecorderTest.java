package data.scripts.battle;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BattleNext_BattleResultRecorderTest {

	@Test
	void noticeAppearsImmediatelyAfterFirstSuccessfulSave() {
		FakeScorekeeper scorekeeper = new FakeScorekeeper();
		List<String> events = new ArrayList<>();
		BattleNext_BattleResultRecorder recorder = recorder(scorekeeper, null, report -> {
			events.add("save");
			assertSame(scorekeeper.report, report);
			return BattleNext_BattleResultRecorder.SaveOutcome.RETAINED;
		});
		recorder.init(engine(events));
		recorder.advance(0f, List.of());
		assertEquals(List.of(), events);

		scorekeeper.frozen = true;
		recorder.advance(0f, List.of());
		recorder.advance(0f, List.of());

		assertEquals(List.of("save", "Battle report generated: Alpha vs Beta"), events);
	}

	@Test
	void zeroRetentionAndTournamentUseTheirResolvedSaveOutcome() {
		assertEquals(BattleNext_BattleResultRecorder.SaveOutcome.NOT_RETAINED,
				BattleNext_BattleResultRecorder.outcomeFor(false, 0));
		assertEquals(BattleNext_BattleResultRecorder.SaveOutcome.RETAINED,
				BattleNext_BattleResultRecorder.outcomeFor(false, 50));
		assertEquals(BattleNext_BattleResultRecorder.SaveOutcome.RETAINED,
				BattleNext_BattleResultRecorder.outcomeFor(true, 0));
		FakeScorekeeper scorekeeper = new FakeScorekeeper();
		scorekeeper.frozen = true;
		List<String> messages = new ArrayList<>();
		BattleNext_BattleResultRecorder recorder = recorder(scorekeeper, null,
				report -> BattleNext_BattleResultRecorder.SaveOutcome.NOT_RETAINED);
		recorder.init(engine(messages));

		recorder.advance(0f, List.of());

		assertEquals(List.of("Battle report was not retained."), messages);
	}

	@Test
	void tournamentReportNotifiesOnItsFirstFrozenFrame() {
		FakeScorekeeper scorekeeper = new FakeScorekeeper();
		scorekeeper.frozen = true;
		List<String> messages = new ArrayList<>();
		AtomicInteger writes = new AtomicInteger();
		BattleNext_BattleResultRecorder recorder = recorder(scorekeeper, new BattleNext_BattleSourceProvider(),
				report -> {
					writes.incrementAndGet();
					return BattleNext_BattleResultRecorder.SaveOutcome.RETAINED;
				});
		recorder.init(engine(messages));

		recorder.advance(0f, List.of());
		recorder.advance(0f, List.of());

		assertEquals(1, writes.get());
		assertEquals(List.of("Battle report generated: Alpha vs Beta"), messages);
	}

	@Test
	void failedSaveEmitsOneFailureNotice() {
		FakeScorekeeper scorekeeper = new FakeScorekeeper();
		scorekeeper.frozen = true;
		AtomicInteger writes = new AtomicInteger();
		List<String> messages = new ArrayList<>();
		BattleNext_BattleResultRecorder recorder = recorder(scorekeeper, null, report -> {
			writes.incrementAndGet();
			throw new IOException("disk unavailable");
		});
		recorder.init(engine(messages));

		recorder.advance(0f, List.of());
		recorder.advance(0f, List.of());

		assertEquals(1, writes.get());
		assertEquals(List.of("Battle report could not be saved."), messages);
	}

	private BattleNext_BattleResultRecorder recorder(FakeScorekeeper scorekeeper,
			BattleNext_BattleSourceProvider provider, BattleNext_BattleResultRecorder.ReportWriter writer) {
		return new BattleNext_BattleResultRecorder(scorekeeper,
				new BattleNext_Match("match", "alpha", "beta", 0, 1f), provider,
				"Alpha", "Beta", "Quick Match 1 Round 1", writer,
				key -> switch (key) {
					case "generated" -> "Battle report generated: %s";
					case "not_retained" -> "Battle report was not retained.";
					case "save_failed" -> "Battle report could not be saved.";
					default -> throw new IllegalArgumentException(key);
				});
	}

	private CombatEngineAPI engine(List<String> notices) {
		CombatUIAPI ui = (CombatUIAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CombatUIAPI.class}, (instance, method, args) -> {
					if ("addMessage".equals(method.getName())) {
						notices.add((String) ((Object[]) args[1])[0]);
					}
					return null;
				});
		return (CombatEngineAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CombatEngineAPI.class}, (instance, method, args) ->
					"getCombatUI".equals(method.getName()) ? ui : null);
	}

	private static class FakeScorekeeper extends BattleNext_ScorekeeperPlugin {
		private final BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		private boolean frozen;

		private FakeScorekeeper() {
			super(List.of(), List.of());
			report.setName("Alpha vs Beta");
		}

		@Override
		public boolean isFrozen() {
			return frozen;
		}

		@Override
		public BattleNext_BattleReportModel getFrozenReport() {
			return report;
		}
	}
}
