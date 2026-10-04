package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.report.BattleNext_ReportTarget;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_ReportEditor;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_MatchProgress;
import data.scripts.battle.BattleNext_Round;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_PageLayout;
import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_BattleReportPage extends BattleNext_ManagementPage {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_BattleReportStore reportStore = new BattleNext_BattleReportStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_I18nSection reportStrings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private final List<BattleNext_ReportTarget> entries = new ArrayList<>();
	private String selectedId = "";
	private final BattleNext_ReportEditor editor = new BattleNext_ReportEditor(reportStore, tournamentStore);
	private boolean detailOpen;

	public BattleNext_BattleReportPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	protected void loadPageState() {
		selectedId = "";
		detailOpen = false;
		status = "";
		try {
			reportStore.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			status = exception.getMessage();
		}
		try {
			tournamentStore.reload();
		} catch (Exception exception) {
			status += (status.isEmpty() ? "" : " | ") + exception.getMessage();
		}
		load();
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_reports");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"save", "back"};
	}

	@Override
	protected String promptText() {
		return reportStrings.get("select_prompt");
	}

	@Override
	protected boolean isListLoaded() {
		return reportStore.isLoaded();
	}

	@Override
	protected String listError() {
		return reportStore.getLoadError();
	}

	@Override
	protected boolean isListEmpty() {
		return entries.isEmpty();
	}

	@Override
	protected String emptyListText() {
		return reportStrings.get("empty");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		for (BattleNext_ReportTarget entry : entries) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, listLabel(entry),
					"report:" + entry.identity(), BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {
		attachEditor(contentPanel);
	}

	@Override
	protected BattleNext_EditorView editorView() {
		return editor;
	}

	@Override
	protected boolean isDetailOpen() {
		return detailOpen && entry(selectedId) != null;
	}

	@Override
	protected boolean hasSelection() {
		return entry(selectedId) != null;
	}

	@Override
	protected boolean isSaveDataLoaded() {
		return entry(selectedId).tournament() == null ? reportStore.isLoaded() : tournamentStore.isLoaded();
	}

	@Override
	protected boolean canDelete() {
		return entry(selectedId).tournament() == null ? reportStore.isLoaded() : tournamentStore.isLoaded();
	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("report:")) {
			String id = ((String) buttonId).substring("report:".length());
			navigation.accept(() -> {
				selectedId = id;
				detailOpen = true;
				editor.open(entry(id));
				refreshContent();
			});
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty()
				&& entry(selectedId) != null) {
			BattleNext_ReportTarget selected = entry(selectedId);
			Runnable action = () -> deletion.accept(listLabel(selected), () -> {
				try {
					deleteReport(selected);
					load();
					selectedId = "";
					detailOpen = false;
					status = strings.get("deleted");
					refreshContent();
				} catch (Exception exception) {
					setStatus(strings.get("save_failed") + ": " + exception.getMessage());
				}
			});
			if (hasUnsavedChanges()) navigation.accept(action);
			else action.run();
		} else if ("back".equals(buttonId)) {
			if (detailOpen) navigation.accept(() -> {
				selectedId = "";
				detailOpen = false;
				refreshContent();
			});
			else backAction.run();
		} else if (detailOpen) {
			editor.buttonPressed(buttonId);
		}
	}

	@Override
	protected boolean isPageDirty() {
		return detailOpen && editor.hasUnsavedChanges();
	}

	@Override
	protected boolean savePage() {
		boolean result = editor.save();
		setStatus(editor.getStatusText());
		if (result) {
			try {
				refreshContent();
			} catch (RuntimeException exception) {
				setStatus(strings.get("saved") + " / " + strings.get("load_failed") + ": " + exception);
			}
		}
		return result;
	}

	@Override
	protected void discardPage() {
		editor.discard();
	}

	private void load() {
		entries.clear();
		for (BattleNext_BattleReportModel report : reportStore.getAll()) {
			entries.add(new BattleNext_ReportTarget(report, "", null, "", -1));
		}
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			for (BattleNext_Match match : tournament.getMatches()) {
				for (int index = 0; index < match.getRounds().size(); index++) {
					BattleNext_Round round = match.getRounds().get(index);
					if (!round.getReport().getId().isEmpty()) {
						entries.add(new BattleNext_ReportTarget(round.getReport(),
								context(tournament, match.getId(), index + 1), tournament, match.getId(), index));
					}
				}
			}
		}
		entries.sort(Comparator.comparingLong((BattleNext_ReportTarget entry) -> entry.report().getCreatedAt()).reversed());
	}

	private String context(BattleNext_TournamentModel tournament, String matchId, int roundNumber) {
		BattleNext_Match match = tournament.findMatch(matchId);
		if (match == null || match.getEnemy().isEmpty()) {
			return tournamentStrings.get("context_label") + " " + tournament.getId() + " / " + matchId;
		}
		return tournamentStrings.get("context_label") + " " + tournament.getId() + " / "
				+ BattleNext_MatchProgress.tournament(tournament, match, roundNumber).format(battleStrings);
	}

	private String listLabel(BattleNext_ReportTarget entry) {
		String time = timeText(entry.report());
		return entry.report().getName() + " / " + (time.isEmpty() ? "" : time + " / ") + entry.report().getWinner()
				+ " / " + (entry.report().getPlayerMembers().size() + entry.report().getEnemyMembers().size());
	}

	private String timeText(BattleNext_BattleReportModel report) {
		if (report.getCreatedAt() <= 0L) return "";
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(report.getCreatedAt()));
	}

	private BattleNext_ReportTarget entry(String id) {
		for (BattleNext_ReportTarget entry : entries) {
			if (entry.identity().equals(id)) return entry;
		}
		return null;
	}

	private void deleteReport(BattleNext_ReportTarget entry) throws IOException, JSONException {
		entry.delete(reportStore, tournamentStore);
	}
}
