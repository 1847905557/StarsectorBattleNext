package data.scripts.battle;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tournament.BattleNext_TournamentEngine;
import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class BattleNext_BattleResultRecorder extends BaseEveryFrameCombatPlugin {
	private static final int MESSAGE_PRIORITY = 0;

	enum SaveOutcome { RETAINED, NOT_RETAINED }

	private final BattleNext_ScorekeeperPlugin scorekeeper;
	private final BattleNext_Match match;
	private final BattleNext_BattleSourceProvider provider;
	private final String playerName;
	private final String enemyName;
	private final String marker;
	private final BattleNext_I18nSection reportStrings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");
	private CombatEngineAPI combatEngine;
	private boolean recorded;

	public BattleNext_BattleResultRecorder(BattleNext_ScorekeeperPlugin scorekeeper, BattleNext_Match match,
			BattleNext_BattleSourceProvider provider, String playerName, String enemyName, String marker) {
		this.scorekeeper = scorekeeper;
		this.match = match;
		this.provider = provider;
		this.playerName = playerName;
		this.enemyName = enemyName;
		this.marker = marker;
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
		BattleNext_Logger.operationStarted(BattleNext_BattleResultRecorder.class, "battle.report.record",
				BattleNext_Logger.fields().put("report", report).put("match", match).build());
		try {
			outcome = record(report);
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_BattleResultRecorder.class, "battle.report.record",
				BattleNext_Logger.fields().put("report", report).put("match", match).build(), exception);
			combatEngine.getCombatUI().addMessage(MESSAGE_PRIORITY, reportStrings.get("save_failed"));
			return;
		}
		BattleNext_Logger.operationSucceeded(BattleNext_BattleResultRecorder.class, "battle.report.record",
				BattleNext_Logger.fields().put("report", report).put("match", match).put("outcome", outcome).build());
		String message = outcome == SaveOutcome.RETAINED
				? reportStrings.format("generated", report.getName()) : reportStrings.get("not_retained");
		combatEngine.getCombatUI().addMessage(MESSAGE_PRIORITY, message);
	}

	private SaveOutcome record(BattleNext_BattleReportModel report) throws IOException, JSONException {
		if (report.getId().isEmpty()) {
			report.setId(UUID.randomUUID().toString());
			BattleNext_Logger.warn(BattleNext_BattleResultRecorder.class, "report.identity.completed",
				BattleNext_Logger.fields().put("report", report).put("match", match).build());
		}
		if (report.getCreatedAt() <= 0L) report.setCreatedAt(System.currentTimeMillis());
		String playerLabel = playerName == null || playerName.isBlank() ? match.getPlayer() : playerName;
		String enemyLabel = enemyName == null || enemyName.isBlank() ? match.getEnemy() : enemyName;
		if (report.getName().isEmpty()) report.setName(defaultName(playerLabel, enemyLabel, marker, report.getCreatedAt()));
		BattleNext_TournamentModel tournament = provider == null ? null : provider.getActiveTournament();
		if (tournament == null) {
			return saveStandalone(report) ? SaveOutcome.RETAINED : SaveOutcome.NOT_RETAINED;
		}
		BattleNext_TournamentEngine engine = provider.getTournamentEngine();
		engine.recordRound(tournament, match.getId(), resolveWinner(match, report.getWinner()), report);
		BattleNext_TournamentStore store = provider.getTournamentStore();
		store.save(tournament);
		BattleNext_BattleSignals.markPendingResult();
		return SaveOutcome.RETAINED;
	}

	public static boolean saveStandalone(BattleNext_BattleReportModel report) throws IOException, JSONException {
		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		store.save(report);
		int keepCount = BattleNext_Settings.getReportKeepCount();
		List<BattleNext_BattleReportModel> reports = new ArrayList<>(store.getAll());
		reports.sort(Comparator.comparingLong(BattleNext_BattleReportModel::getCreatedAt).reversed());
		BattleNext_Logger.info(BattleNext_BattleResultRecorder.class, "report.retention.applied",
				BattleNext_Logger.fields().put("keep_count", keepCount).put("ordered_reports", reports).put("report_id", report.getId()).build());
		for (int index = keepCount; index < reports.size(); index++) {
			store.delete(reports.get(index).getId());
		}
		BattleNext_BattleSignals.markPendingResult();
		return reports.subList(0, Math.min(keepCount, reports.size())).stream().anyMatch(retained -> retained.getId().equals(report.getId()));
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
