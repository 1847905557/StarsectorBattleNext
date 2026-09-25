package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.report.BattleNext_BattleReport;
import data.scripts.report.BattleNext_BattleReportStore;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.battle.BattleNext_Round;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleNext_BattleReportPage {

	private final Runnable backAction;
	private final BattleNext_BattleReportStore reportStore = new BattleNext_BattleReportStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "");
	private final List<Entry> entries = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI heading;
	private TooltipMakerAPI listContent;
	private TooltipMakerAPI detailContent;
	private TooltipMakerAPI footer;
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
						entries.add(new Entry(match.round.getReport(), text("BattleNext_ui_tournament_context") + " "
								+ tournament.getSlot() + " / " + text("BattleNext_ui_match") + " " + match.matchId
								+ " / " + text("BattleNext_ui_round") + " " + match.round.getNumber()
								+ " / " + text("BattleNext_ui_attempt") + " " + match.round.getAttempt()));
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
		if (listContent != null) panel.removeComponent(listContent);
		if (detailContent != null) panel.removeComponent(detailContent);
		if (footer != null) panel.removeComponent(footer);

		heading = BattleNext_PageLayout.heading(panel, text("BattleNext_ui_page_reports"));
		float listWidth = BattleNext_PageLayout.listWidth(panel);
		listContent = BattleNext_PageLayout.column(panel, 0f, listWidth);
		detailContent = BattleNext_PageLayout.column(panel, listWidth + 10f,
				panel.getPosition().getWidth() - listWidth - 10f);
		footer = BattleNext_PageLayout.footer(panel);
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_back"), "back", 160f)
				.getPosition().inBL(0f, 0f);
		addList();
		addDetails();
	}

	private void addList() {
		listContent.addTitle(text("BattleNext_ui_page_reports"));
		for (int index = 0; index < entries.size(); index++) {
			Entry entry = entries.get(index);
			BattleNext_PageLayout.button(listContent, entry.report.getId() + " / " + entry.report.getWinner()
					+ " / " + entry.report.getMembers().size(), "report:" + index,
					listContent.getPosition().getWidth() - 20f);
		}
		if (entries.isEmpty()) listContent.addPara(text("BattleNext_ui_no_reports"), 8f, BattleNext_UiTheme.MUTED);
	}

	private void addDetails() {
		if (selected == null) {
			detailContent.addTitle(text("BattleNext_ui_select_report"));
			return;
		}
		BattleNext_BattleReport report = selected.report;
		detailContent.addTitle(report.getId());
		detailContent.addPara(text("BattleNext_ui_winner") + ": " + report.getWinner() + " / "
				+ text("BattleNext_ui_duration") + ": " + report.getDurationMillis() + " ms", 8f, BattleNext_UiTheme.TEXT);
		if (!selected.context.isEmpty()) detailContent.addPara(selected.context, 6f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("BattleNext_ui_damage_dealt"), 10f, BattleNext_UiTheme.ACCENT);
		appendMembers(sorted(report, true));
		detailContent.addPara(text("BattleNext_ui_damage_taken"), 10f, BattleNext_UiTheme.ACCENT);
		appendMembers(sorted(report, false));
	}

	private void appendMembers(List<BattleNext_MemberReport> members) {
		for (BattleNext_MemberReport member : members) {
			detailContent.addPara(member.getFleetId() + "[" + member.getMemberIndex() + "] "
					+ member.getVariantId() + " " + member.getName(), 5f, BattleNext_UiTheme.TEXT);
			detailContent.addPara(text("BattleNext_ui_damage_dealt") + ": " + member.getDamageDealt()
					+ " / " + text("BattleNext_ui_damage_taken") + ": " + member.getDamageTaken()
					+ " / " + text("BattleNext_ui_status") + ": " + member.getStatus(), 2f,
					BattleNext_UiTheme.MUTED);
			detailContent.addPara(text("BattleNext_ui_hull") + ": " + member.getHullDamageDealt()
					+ " / " + member.getHullDamageTaken() + "   " + text("BattleNext_ui_shield")
					+ ": " + member.getShieldDamageDealt() + " / " + member.getShieldDamageTaken(),
					2f, BattleNext_UiTheme.MUTED);
			detailContent.addPara(text("BattleNext_ui_emp") + ": " + member.getEmpDamageDealt()
					+ " / " + member.getEmpDamageTaken() + "   " + text("BattleNext_ui_final_hull")
					+ ": " + member.getFinalHullFraction() + "   " + text("BattleNext_ui_final_cr")
					+ ": " + member.getFinalCr(), 2f, BattleNext_UiTheme.MUTED);
		}
	}

	private List<BattleNext_MemberReport> sorted(BattleNext_BattleReport report, boolean dealt) {
		List<BattleNext_MemberReport> result = new ArrayList<>(report.getMembers());
		result.sort(Comparator.comparingDouble((BattleNext_MemberReport member) -> dealt ? member.getDamageDealt() : member.getDamageTaken())
				.reversed().thenComparing(BattleNext_MemberReport::getFleetId).thenComparingInt(BattleNext_MemberReport::getMemberIndex));
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
