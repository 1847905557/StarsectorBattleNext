package data.scripts.ui.page;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.report.BattleNext_BattleReport;
import data.scripts.report.BattleNext_BattleReportStore;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_BattleReportPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final BattleNext_BattleReportStore reportStore = new BattleNext_BattleReportStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private final List<Entry> entries = new ArrayList<>();
	private Entry selected;

	public BattleNext_BattleReportPage(Runnable backAction) {
		this.backAction = backAction;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		load();
		selected = null;
		createContents();
	}

	@Override
	public void createContents() {
		// left list / right details / bottom back button
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] bottom = rest[1].splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		BattleNext_PageLayout.heading(header[0], panel, text("page_reports"));

		float listWidth = BattleNext_PageLayout.listWidth(panel);
		UIRect[] columns = bottom[0].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f)
				.splitHorizontally(listWidth + 10f);
		listRect = columns[0];
		detailRect = columns[1];

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX(), footerRect.getY(), Math.min(160f, footerRect.getWidth()), 30f),
				text("back"), "back", CutStyle.ALL, true);

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI listContent = listPanel.createUIElement(listRect.getWidth(), listRect.getHeight(), true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(0f, 0f);

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI detailContent = detailPanel.createUIElement(detailRect.getWidth(), detailRect.getHeight(), true);
		addDetails(detailContent);
		detailPanel.addUIElement(detailContent).inTL(0f, 0f);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String)buttonId).startsWith("report:")) {
			int index = Integer.parseInt(((String)buttonId).substring("report:".length()));
			if (index >= 0 && index < entries.size()) {
				selected = entries.get(index);
				refreshContent();
			}
		} else if ("back".equals(buttonId)) {
			if (selected == null) backAction.run();
			else {
				selected = null;
				refreshContent();
			}
		}
	}

	private void load() {
		entries.clear();
		try {
			reportStore.reload();
		} catch (Exception ignored) {
		}
		for (BattleNext_BattleReport report : reportStore.getRecent()) entries.add(new Entry(report, ""));
		try {
			tournamentStore.reload();
			for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
				for (MatchWithRound match : rounds(tournament)) {
					if (match.round.getReport() != null) {
						entries.add(new Entry(match.round.getReport(), text("tournament_context") + " "
								+ tournament.getId() + " / " + text("match") + " " + match.matchId
								+ " / " + text("round") + " " + match.round.getNumber()
								+ " / " + text("attempt") + " " + match.round.getAttempt()));
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private List<MatchWithRound> rounds(BattleNext_Tournament tournament) {
		List<MatchWithRound> result = new ArrayList<>();
		for (BattleNext_Match match : tournament.getMatches()) {
			for (BattleNext_Round round : match.getRounds()) result.add(new MatchWithRound(match.getId(), round));
		}
		return result;
	}

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_reports"));
		for (int index = 0; index < entries.size(); index++) {
			Entry entry = entries.get(index);
			BattleNext_PageLayout.button(listContent, entry.report.getId() + " / " + entry.report.getWinner()
					+ " / " + entry.report.getMembers().size(), "report:" + index,
					listContent.getPosition().getWidth() - 20f);
		}
		if (entries.isEmpty()) listContent.addPara(text("no_reports"), 8f, BattleNext_UiTheme.MUTED);
	}

	private void addDetails(TooltipMakerAPI detailContent) {
		if (selected == null) {
			detailContent.addTitle(text("select_report"));
			return;
		}
		BattleNext_BattleReport report = selected.report;
		detailContent.addTitle(report.getId());
		detailContent.addPara(text("winner") + ": " + report.getWinner() + " / "
				+ text("duration") + ": " + report.getDurationMillis() + " ms", 8f, BattleNext_UiTheme.TEXT);
		if (!selected.context.isEmpty()) detailContent.addPara(selected.context, 6f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("damage_dealt"), 10f, BattleNext_UiTheme.ACCENT);
		appendMembers(detailContent, sorted(report, true));
		detailContent.addPara(text("damage_taken"), 10f, BattleNext_UiTheme.ACCENT);
		appendMembers(detailContent, sorted(report, false));
	}

	private void appendMembers(TooltipMakerAPI detailContent, List<BattleNext_MemberReport> members) {
		for (BattleNext_MemberReport member : members) {
			detailContent.addPara(member.getFleetId() + "[" + member.getMemberIndex() + "] "
					+ member.getVariantId() + " " + member.getName(), 5f, BattleNext_UiTheme.TEXT);
			detailContent.addPara(text("damage_dealt") + ": " + member.getDamageDealt()
					+ " / " + text("damage_taken") + ": " + member.getDamageTaken()
					+ " / " + text("status") + ": " + member.getStatus(), 2f,
					BattleNext_UiTheme.MUTED);
			detailContent.addPara(text("hull") + ": " + member.getHullDamageDealt()
					+ " / " + member.getHullDamageTaken() + "   " + text("shield")
					+ ": " + member.getShieldDamageDealt() + " / " + member.getShieldDamageTaken(),
					2f, BattleNext_UiTheme.MUTED);
			detailContent.addPara(text("emp") + ": " + member.getEmpDamageDealt()
					+ " / " + member.getEmpDamageTaken() + "   " + text("final_hull")
					+ ": " + member.getFinalHullFraction() + "   " + text("final_cr")
					+ ": " + member.getFinalCr(), 2f, BattleNext_UiTheme.MUTED);
		}
	}

	private List<BattleNext_MemberReport> sorted(BattleNext_BattleReport report, boolean dealt) {
		List<BattleNext_MemberReport> result = new ArrayList<>(report.getMembers());
		result.sort(BattleNext_MemberReport.damageOrder(dealt));
		return result;
	}

	private String text(String key) {
		return strings.get(key);
	}

	private static final class Entry {
		private final BattleNext_BattleReport report;
		private final String context;

		private Entry(BattleNext_BattleReport report, String context) {
			this.report = report;
			this.context = context;
		}
	}

	private static final class MatchWithRound {
		private final String matchId;
		private final BattleNext_Round round;

		private MatchWithRound(String matchId, BattleNext_Round round) {
			this.matchId = matchId;
			this.round = round;
		}
	}
}
