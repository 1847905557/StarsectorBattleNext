package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battle.BattleNext_Round;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.report.BattleNext_BattleReport;
import data.scripts.report.BattleNext_BattleReportStore;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_PageView;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_BattleReportPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final BattleNext_BattleReportStore reportStore = new BattleNext_BattleReportStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final List<Entry> entries = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI heading;
	private TooltipMakerAPI listContent;
	private TooltipMakerAPI detailContent;
	private TooltipMakerAPI footer;
	private UIRect listRect;
	private UIRect detailRect;
	private Entry selected;

	public BattleNext_BattleReportPage(Runnable backAction) {
		this.backAction = backAction;
	}

	public void attach(CustomPanelAPI panel) {
		if (this.panel != panel) {
			heading = null;
			listContent = null;
			detailContent = null;
			footer = null;
		}
		this.panel = panel;
		load();
		selected = null;
		rebuild();
	}

	public void advance() {
	}

	public void processInput(List<InputEventAPI> events) {
	}

	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String)buttonId).startsWith("report:")) {
			int index = Integer.parseInt(((String)buttonId).substring("report:".length()));
			if (index >= 0 && index < entries.size()) {
				selected = entries.get(index);
				rebuild();
			}
		} else if ("back".equals(buttonId)) {
			if (selected == null) backAction.run();
			else {
				selected = null;
				rebuild();
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
				for (BattleNext_MatchWithRound match : rounds(tournament)) {
					if (match.round.getReport() != null) {
						entries.add(new Entry(match.round.getReport(), text("tournament_context") + " "
								+ tournament.getSlot() + " / " + text("match") + " " + match.matchId
								+ " / " + text("round") + " " + match.round.getNumber()
								+ " / " + text("attempt") + " " + match.round.getAttempt()));
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private List<BattleNext_MatchWithRound> rounds(BattleNext_Tournament tournament) {
		List<BattleNext_MatchWithRound> result = new ArrayList<>();
		for (data.scripts.battle.BattleNext_Match match : tournament.getMatches()) {
			for (BattleNext_Round round : match.getRounds()) result.add(new BattleNext_MatchWithRound(match.getId(), round));
		}
		return result;
	}

	private void rebuild() {
		if (heading != null) panel.removeComponent(heading);
		if (footer != null) panel.removeComponent(footer);

		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] bottom = rest[1].splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		float listWidth = BattleNext_PageLayout.listWidth(panel);
		UIRect[] columns = bottom[0].splitHorizontally(listWidth + 10f);
		listRect = columns[0];
		detailRect = columns[1];
		heading = BattleNext_PageLayout.heading(header[0], panel, text("page_reports"));
		listContent = BattleNext_PageLayout.column(listRect, panel);
		detailContent = BattleNext_PageLayout.column(detailRect, panel);
		footer = BattleNext_PageLayout.footer(bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f), panel);
		BattleNext_PageLayout.button(footer, text("back"), "back", 160f)
				.getPosition().inBL(0f, 0f);
		addList();
		addDetails();
	}

	private void addList() {
		listContent.addTitle(text("page_reports"));
		for (int index = 0; index < entries.size(); index++) {
			Entry entry = entries.get(index);
			BattleNext_PageLayout.button(listContent, entry.report.getId() + " / " + entry.report.getWinner()
					+ " / " + entry.report.getMembers().size(), "report:" + index,
					listContent.getPosition().getWidth() - 20f);
		}
		if (entries.isEmpty()) listContent.addPara(text("no_reports"), 8f, BattleNext_UiTheme.MUTED);
	}

	private void addDetails() {
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
		appendMembers(sorted(report, true));
		detailContent.addPara(text("damage_taken"), 10f, BattleNext_UiTheme.ACCENT);
		appendMembers(sorted(report, false));
	}

	private void appendMembers(List<BattleNext_MemberReport> members) {
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

	private static final class BattleNext_MatchWithRound {
		private final String matchId;
		private final BattleNext_Round round;

		private BattleNext_MatchWithRound(String matchId, BattleNext_Round round) {
			this.matchId = matchId;
			this.round = round;
		}
	}
}
