package data.scripts.battle;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_BattleSignals;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.tools.BattleNext_Settings;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

public class BattleNext_BattleResultRecorder extends BaseEveryFrameCombatPlugin {
	private static final int MESSAGE_PRIORITY = 0;

	enum SaveOutcome { RETAINED, NOT_RETAINED }

	@FunctionalInterface
	interface ReportWriter {
		SaveOutcome save(BattleNext_BattleReportModel report) throws IOException, org.json.JSONException;
	}

	private final BattleNext_ScorekeeperPlugin scorekeeper;
	private final BattleNext_Match match;
	private final BattleNext_BattleSourceProvider provider;
	private final String playerName;
	private final String enemyName;
	private final String marker;
	private final ReportWriter writer;
	private final Function<String, String> messages;
	private CombatEngineAPI combatEngine;
	private boolean recorded;

	public BattleNext_BattleResultRecorder(BattleNext_ScorekeeperPlugin scorekeeper, BattleNext_Match match,
			BattleNext_BattleSourceProvider provider, String playerName, String enemyName, String marker) {
		this(scorekeeper, match, provider, playerName, enemyName, marker, null, null);
	}

	BattleNext_BattleResultRecorder(BattleNext_ScorekeeperPlugin scorekeeper, BattleNext_Match match,
			BattleNext_BattleSourceProvider provider, String playerName, String enemyName, String marker,
			ReportWriter writer, Function<String, String> messages) {
		this.scorekeeper = scorekeeper;
		this.match = match;
		this.provider = provider;
		this.playerName = playerName;
		this.enemyName = enemyName;
		this.marker = marker;
		this.writer = writer == null ? this::record : writer;
		this.messages = messages == null
				? BattleNext_I18nSection.getInstance("Report", "BattleNext_report_")::get : messages;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		combatEngine = engine;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (recorded || !scorekeeper.isFrozen()) return;
		recorded = true;
		BattleNext_BattleReportModel report = scorekeeper.getFrozenReport();
		SaveOutcome outcome;
		try {
			outcome = writer.save(report);
		} catch (IOException | org.json.JSONException | RuntimeException exception) {
			com.fs.starfarer.api.Global.getLogger(BattleNext_BattleResultRecorder.class).error("Unable to save battle report", exception);
			combatEngine.getCombatUI().addMessage(MESSAGE_PRIORITY, messages.apply("save_failed"));
			return;
		}
		String message = outcome == SaveOutcome.RETAINED
				? String.format(messages.apply("generated"), report.getName()) : messages.apply("not_retained");
		combatEngine.getCombatUI().addMessage(MESSAGE_PRIORITY, message);
	}

	private SaveOutcome record(BattleNext_BattleReportModel report) throws IOException, org.json.JSONException {
		if (report.getId().isEmpty()) report.setId(UUID.randomUUID().toString());
		if (report.getCreatedAt() <= 0L) report.setCreatedAt(System.currentTimeMillis());
		String playerLabel = playerName == null || playerName.isBlank() ? match.getPlayer() : playerName;
		String enemyLabel = enemyName == null || enemyName.isBlank() ? match.getEnemy() : enemyName;
		if (report.getName().isEmpty()) report.setName(defaultName(playerLabel, enemyLabel, marker, report.getCreatedAt()));
		BattleNext_TournamentModel tournament = provider == null ? null : provider.getActiveTournament();
		int keepCount = -1;
		if (tournament == null) {
			BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
			store.reload();
			store.save(report);
			keepCount = BattleNext_Settings.getReportKeepCount();
			List<BattleNext_BattleReportModel> reports = new java.util.ArrayList<>(store.getAll());
			reports.sort(java.util.Comparator.comparingLong(BattleNext_BattleReportModel::getCreatedAt).reversed());
			for (int index = keepCount; index < reports.size(); index++) {
				store.delete(reports.get(index).getId());
			}
		} else {
			BattleNext_TournamentEngine engine = provider.getTournamentEngine();
			engine.recordRound(tournament, match.getId(), resolveWinner(match, report.getWinner()), report);
			BattleNext_TournamentStore store = provider.getTournamentStore();
			store.save(tournament);
		}
		BattleNext_BattleSignals.markPendingResult();
		return outcomeFor(tournament != null, keepCount);
	}

	static SaveOutcome outcomeFor(boolean tournament, int keepCount) {
		return tournament || keepCount > 0 ? SaveOutcome.RETAINED : SaveOutcome.NOT_RETAINED;
	}

	public static String defaultName(String playerName, String enemyName, String marker, long createdAt) {
		String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(createdAt));
		return playerName + " vs " + enemyName + " - " + marker + " - " + time;
	}

	private String resolveWinner(BattleNext_Match match, String winner) {
		if ("player".equals(winner)) {
			return match.getPlayer();
		}
		if ("enemy".equals(winner)) {
			return match.getEnemy();
		}
		return winner;
	}
}
