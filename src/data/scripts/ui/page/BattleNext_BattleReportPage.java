package data.scripts.ui.page;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_MatchProgress;
import data.scripts.battle.BattleNext_Round;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.scripts.report.BattleNext_MemberReport;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_BattleReportPage implements BattleNext_PageView {

	private static final float REPORT_SUMMARY_TOP_GAP = 8f;
	private static final float REPORT_CONTEXT_TOP_GAP = 6f;
	private static final float REPORT_SECTION_TOP_GAP = 10f;
	private static final float MEMBER_TITLE_TOP_GAP = 5f;
	private static final float MEMBER_DETAIL_TOP_GAP = 2f;

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_BattleReportStore reportStore = new BattleNext_BattleReportStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final BattleNext_I18nSection reportStrings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");

	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private final List<BattleReportListItem> entries = new ArrayList<>();
	private String selectedId = "";
	private boolean detailOpen;
	private LabelAPI statusLabel;
	private String status = "";

	public BattleNext_BattleReportPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
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
		createContents();
	}

	@Override
	public void createContents() {
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), BattleNext_PageLayout.HEADER_HEIGHT),
				panel, text("page_reports"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.HEADER,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		detailRect.addBorder(panel, BattleNext_UiTheme.HEADER,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		UIRect footerRect = regions[2];
		String[] actions = {"back"};
		float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(footerRect, actions.length);
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(panel,
					BattleNext_PageLayout.managementFooterButtonRect(footerRect, buttonWidth, index),
					text(actions[index]), actions[index], CutStyle.ALL, true);
		}
		UIRect statusRect = BattleNext_PageLayout.managementFooterStatusRect(footerRect, buttonWidth, actions.length);
		if (statusRect.getWidth() >= BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_MIN_WIDTH) {
			TooltipMakerAPI footerStatus = panel.createUIElement(statusRect.getWidth(), statusRect.getHeight(), false);
			statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
			statusLabel.getPosition().inTL(0f, BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_TOP_INSET);
			footerStatus.setHeightSoFar(statusRect.getHeight());
			panel.addUIElement(footerStatus).inTL(statusRect.getX(), statusRect.getY());
		}

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		UIRect listContentRect = BattleNext_PageLayout.managementContentRect(listRect);
		TooltipMakerAPI listContent = listPanel.createUIElement(listContentRect.getWidth(), listContentRect.getHeight(), true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(listContentRect.getX() - listRect.getX(),
				listContentRect.getY() - listRect.getY());

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		if (!detailOpen) {
			UIRect promptRect = BattleNext_PageLayout.managementContentRect(detailRect);
			TooltipMakerAPI prompt = detailPanel.createUIElement(promptRect.getWidth(), promptRect.getHeight(), false);
			prompt.addTitle(text("select_report"));
			detailPanel.addUIElement(prompt).inTL(promptRect.getX() - detailRect.getX(),
					promptRect.getY() - detailRect.getY());
			return;
		}
		UIRect contentRect = BattleNext_PageLayout.managementEditorRect(detailRect);
		TooltipMakerAPI detailContent = detailPanel.createUIElement(contentRect.getWidth(), contentRect.getHeight(), true);
		addDetails(detailContent);
		detailPanel.addUIElement(detailContent).inTL(contentRect.getX() - detailRect.getX(),
				contentRect.getY() - detailRect.getY());
		BattleReportListItem selected = entry(selectedId);
		ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
				BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect),
				text("delete"), "delete", CutStyle.ALL, true);
		delete.setEnabled(selected != null && (selected.tournament == null
				? reportStore.isLoaded() : tournamentStore.isLoaded()));
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("report:")) {
			String id = ((String) buttonId).substring("report:".length());
			navigation.accept(() -> { selectedId = id; detailOpen = true; refreshContent(); });
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty()
				&& entry(selectedId) != null) {
			BattleReportListItem selected = entry(selectedId);
			deletion.accept(listLabel(selected), () -> {
				try {
					deleteReport(selected);
					selectedId = "";
					detailOpen = false;
					status = text("deleted");
					refreshContent();
				} catch (Exception exception) {
					setStatus(text("save_failed") + ": " + exception.getMessage());
				}
			});
		} else if ("back".equals(buttonId)) {
			if (detailOpen) navigation.accept(() -> { selectedId = ""; detailOpen = false; refreshContent(); });
			else backAction.run();
		}
	}

	private void load() {
		entries.clear();
		for (BattleNext_BattleReportModel report : reportStore.getAll()) {
			entries.add(new BattleReportListItem(report, "", null, null));
		}
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			for (BattleNext_Match match : tournament.getMatches()) {
				for (int index = 0; index < match.getRounds().size(); index++) {
					BattleNext_Round round = match.getRounds().get(index);
					if (!round.getReport().getId().isEmpty()) {
						entries.add(new BattleReportListItem(round.getReport(),
								context(tournament, match.getId(), index + 1), tournament, round));
					}
				}
			}
		}
		entries.sort(Comparator.comparingLong((BattleReportListItem entry) -> entry.report.getCreatedAt()).reversed());
	}

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_reports"));
		if (!reportStore.isLoaded()) listContent.addPara(text("load_failed") + ": "
				+ reportStore.getLoadError(), BattleNext_PageLayout.MANAGEMENT_LIST_ERROR_TOP_GAP,
				BattleNext_UiTheme.MUTED);
		if (entries.isEmpty()) listContent.addPara(text("no_reports"),
				BattleNext_PageLayout.MANAGEMENT_LIST_EMPTY_TOP_GAP, BattleNext_UiTheme.MUTED);
		for (BattleReportListItem entry : entries) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, listLabel(entry),
					"report:" + entry.report.getId(), BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	private void addDetails(TooltipMakerAPI detailContent) {
		BattleReportListItem selected = entry(selectedId);
		if (selected == null) return;
		BattleNext_BattleReportModel report = selected.report;
		detailContent.addTitle(report.getId());
		String time = timeText(report);
		detailContent.addPara((time.isEmpty() ? "" : time + " / ") + text("winner") + ": " + report.getWinner()
				+ " / " + text("duration") + ": " + report.getDurationMillis() + " ms",
				REPORT_SUMMARY_TOP_GAP, BattleNext_UiTheme.TEXT);
		if (!selected.context.isEmpty()) detailContent.addPara(selected.context,
				REPORT_CONTEXT_TOP_GAP, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("damage_dealt"), REPORT_SECTION_TOP_GAP, BattleNext_UiTheme.ACCENT);
		appendMembers(detailContent, sorted(report, true));
		detailContent.addPara(text("damage_taken"), REPORT_SECTION_TOP_GAP, BattleNext_UiTheme.ACCENT);
		appendMembers(detailContent, sorted(report, false));
	}

	private void appendMembers(TooltipMakerAPI detailContent, List<BattleNext_MemberReport> members) {
		for (BattleNext_MemberReport member : members) {
			detailContent.addPara(member.getVariantId() + " " + member.getName(),
					MEMBER_TITLE_TOP_GAP, BattleNext_UiTheme.TEXT);
			detailContent.addPara(text("damage_dealt") + ": " + member.getDamageDealt()
					+ " / " + text("damage_taken") + ": " + member.getDamageTaken()
					+ " / " + text("status") + ": " + member.getStatus(), MEMBER_DETAIL_TOP_GAP,
					BattleNext_UiTheme.MUTED);
			detailContent.addPara(text("hull") + ": " + member.getHullDamageDealt()
					+ " / " + member.getHullDamageTaken() + "   " + text("shield")
					+ ": " + member.getShieldDamageDealt() + " / " + member.getShieldDamageTaken(),
					MEMBER_DETAIL_TOP_GAP, BattleNext_UiTheme.MUTED);
			detailContent.addPara(text("emp") + ": " + member.getEmpDamageDealt()
					+ " / " + member.getEmpDamageTaken() + "   " + text("final_hull")
					+ ": " + member.getFinalHullFraction() + "   " + text("final_cr")
					+ ": " + member.getFinalCr(), MEMBER_DETAIL_TOP_GAP, BattleNext_UiTheme.MUTED);
		}
	}

	private List<BattleNext_MemberReport> sorted(BattleNext_BattleReportModel report, boolean dealt) {
		List<BattleNext_MemberReport> result = new ArrayList<>(report.getPlayerMembers());
		result.addAll(report.getEnemyMembers());
		result.sort(BattleNext_MemberReport.damageOrder(dealt));
		return result;
	}

	private String context(BattleNext_TournamentModel tournament, String matchId, int roundNumber) {
		BattleNext_Match match = tournament.findMatch(matchId);
		if (match == null || match.getEnemy().isEmpty()) {
			return text("tournament_context") + " " + tournament.getId() + " / " + matchId;
		}
		return text("tournament_context") + " " + tournament.getId() + " / "
				+ BattleNext_MatchProgress.tournament(tournament, match, roundNumber).format(strings);
	}

	private String listLabel(BattleReportListItem entry) {
		String time = timeText(entry.report);
		return (time.isEmpty() ? "" : time + " / ") + entry.report.getWinner()
				+ " / " + (entry.report.getPlayerMembers().size() + entry.report.getEnemyMembers().size());
	}

	private String timeText(BattleNext_BattleReportModel report) {
		if (report.getCreatedAt() <= 0L) return "";
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(report.getCreatedAt()));
	}

	private BattleReportListItem entry(String id) {
		for (BattleReportListItem entry : entries) {
			if (entry.report.getId().equals(id)) return entry;
		}
		return null;
	}

	private void deleteReport(BattleReportListItem entry) throws IOException, JSONException {
		if (entry.tournament == null) {
			reportStore.delete(entry.report.getId());
			return;
		}
		entry.round.setReport(new BattleNext_BattleReportModel());
		tournamentStore.save(entry.tournament);
	}

	@Override
	public String getStatusText() {
		return status;
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		if (key.equals("damage_dealt") || key.equals("damage_taken") || key.equals("winner")
				|| key.equals("duration") || key.equals("status") || key.equals("hull") || key.equals("shield")
				|| key.equals("emp") || key.equals("final_hull") || key.equals("final_cr")) return reportStrings.get(key);
		return strings.get(key);
	}

	private static final class BattleReportListItem {
		private final BattleNext_BattleReportModel report;
		private final String context;
		private final BattleNext_TournamentModel tournament;
		private final BattleNext_Round round;

		private BattleReportListItem(BattleNext_BattleReportModel report, String context,
				BattleNext_TournamentModel tournament, BattleNext_Round round) {
			this.report = report;
			this.context = context;
			this.tournament = tournament;
			this.round = round;
		}
	}
}
