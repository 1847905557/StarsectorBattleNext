package data.scripts.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestGameFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_BattleResultRecorderTest extends BattleNext_TestGameFixture {

	private final List<String> notices = new ArrayList<>();

	@BeforeEach
	void useTheCurrentReportTextCatalog() {
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.text(args[0], args[1]);
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_BattleSignals.consumePendingResult();
	}

	@AfterEach
	void releaseReportNotificationState() {
		BattleNext_BattleSignals.consumePendingResult();
		BattleNext_BattleSignals.clearAuthorization();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void frozenReportPersistsOnceAndUsesTheCurrentRetentionBeforeNotifying(int keep) throws Exception {

		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, keep);
		FrozenScorekeeper scorekeeper = new FrozenScorekeeper();
		scorekeeper.report.setId("");
		scorekeeper.report.setName("");
		scorekeeper.report.setCreatedAt(0L);
		BattleNext_BattleResultRecorder recorder = recorder(scorekeeper, null, new BattleNext_Match("match", "alpha", "beta", 0, 1f));
		recorder.advance(0f, List.of());
		assertTrue(notices.isEmpty());
		assertFalse(Files.exists(directory.resolve("tournament/_reports")));
		scorekeeper.frozen = true;
		long recordingStarted = System.currentTimeMillis();
		recorder.advance(0f, List.of());
		recorder.advance(0f, List.of());
		assertDoesNotThrow(() -> UUID.fromString(scorekeeper.report.getId()));
		assertTrue(scorekeeper.report.getCreatedAt() >= recordingStarted);
		assertTrue(scorekeeper.report.getCreatedAt() <= System.currentTimeMillis());
		assertTrue(scorekeeper.report.getName().startsWith("Alpha vs Beta - Quick Match 1 Round 1 - "));
		assertTrue(scorekeeper.report.getName().matches(".*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"));
		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		assertEquals(keep, store.getAll().size());
		if (keep > 0) {
			assertEquals(scorekeeper.report.toJson().toString(), store.get(scorekeeper.report.getId()).toJson().toString());
		}
		assertEquals(List.of(keep == 0 ? BattleNext_TestTextCatalog.text("Report", "BattleNext_report_not_retained")
				: String.format(BattleNext_TestTextCatalog.text("Report", "BattleNext_report_generated"), scorekeeper.report.getName())), notices);
		assertTrue(BattleNext_BattleSignals.consumePendingResult());
		assertFalse(BattleNext_BattleSignals.consumePendingResult());
	}

	@Test
	void tournamentReportCommitsItsRoundAndSurvivesZeroStandaloneRetention() throws Exception {

		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 0);
		BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
		provider.getFleetStore().reload();
		for (String id : List.of("alpha", "beta")) {
			provider.getFleetStore().save(new BattleNext_FleetModel(id, id, List.of(), List.of(), List.of()));
		}
		BattleNext_TournamentStore tournaments = provider.getTournamentStore();
		tournaments.reload();
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		tournament.setFleetIds(List.of("alpha", "beta"));
		provider.getTournamentEngine().start(tournament);
		tournaments.save(tournament);
		tournaments.setActiveId(tournament.getId());
		BattleNext_Match match = provider.load().get(0);
		FrozenScorekeeper scorekeeper = new FrozenScorekeeper();
		scorekeeper.frozen = true;
		BattleNext_BattleResultRecorder recorder = recorder(scorekeeper, provider, match);
		recorder.advance(0f, List.of());
		recorder.advance(0f, List.of());
		tournaments.reload();
		BattleNext_TournamentModel saved = tournaments.get("cup");
		assertTrue(saved.isCompleted());
		assertEquals(match.getPlayer(), saved.getChampionFleetId());
		assertEquals(1, saved.findMatch(match.getId()).getRounds().size());
		assertEquals("result", saved.findMatch(match.getId()).getRounds().get(0).getReport().getId());
		assertEquals(List.of("Battle report generated: Alpha vs Beta"), notices);
		assertFalse(Files.exists(directory.resolve("tournament/_reports")));
	}

	@Test
	void actualFilesystemFailureKeepsTheReportAndEmitsOneFailureNotice() throws Exception {
		FrozenScorekeeper scorekeeper = new FrozenScorekeeper();
		scorekeeper.frozen = true;
		Files.createDirectories(directory.resolve("tournament/_reports/result.json"));
		String snapshot = scorekeeper.report.toJson().toString();
		BattleNext_BattleResultRecorder recorder = recorder(scorekeeper, null, new BattleNext_Match("match", "alpha", "beta", 0, 1f));
		recorder.advance(0f, List.of());
		recorder.advance(0f, List.of());
		assertEquals(List.of("Battle report could not be saved."), notices);
		assertEquals(snapshot, scorekeeper.report.toJson().toString());
		assertFalse(BattleNext_BattleSignals.consumePendingResult());
	}

	private BattleNext_BattleResultRecorder recorder(FrozenScorekeeper scorekeeper,
			BattleNext_BattleSourceProvider provider, BattleNext_Match match) {
		CombatUIAPI ui = proxy(CombatUIAPI.class, (method, args) -> {
			if (method.equals("addMessage")) {
				notices.add(String.valueOf(((Object[]) args[1])[0]));
			}
			return null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> method.equals("getCombatUI") ? ui : null);
		BattleNext_BattleResultRecorder recorder = new BattleNext_BattleResultRecorder(scorekeeper, match, provider,
				"Alpha", "Beta", "Quick Match 1 Round 1");
		recorder.init(engine);
		return recorder;
	}

	private static class FrozenScorekeeper extends BattleNext_ScorekeeperPlugin {
		private final BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		private boolean frozen;

		private FrozenScorekeeper() {
			super(List.of(), List.of());
			report.setId("result");
			report.setName("Alpha vs Beta");
			report.setWinner("player");
			report.setCreatedAt(1L);
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
