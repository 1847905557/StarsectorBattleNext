package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.match.BattleNext_BattleReport;
import data.scripts.match.BattleNext_BattleReportStore;
import data.scripts.match.BattleNext_MemberReport;
import data.scripts.match.BattleNext_Round;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.util.BattleNext_I18nSection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleNext_BattleReportPage {

	private final Runnable backAction;
	private final BattleNext_BattleReportStore reportStore = new BattleNext_BattleReportStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("AI_Battles_Next", "");
	private final List<Entry> entries = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI content;
	private Entry selected;

	public BattleNext_BattleReportPage(Runnable backAction) {
		this.backAction = backAction;
	}

	public void attach(CustomPanelAPI panel) {
		if (this.panel != panel) content = null;
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
					entries.add(new Entry(match.round.getReport(), "Tournament " + tournament.getSlot() + " Match " + match.matchId + " Round " + match.round.getNumber() + " attempt " + match.round.getAttempt()));
				}
			}
		} catch (Exception ignored) {
		}
	}

	private List<BattleNext_MatchWithRound> rounds(BattleNext_Tournament tournament) {
		List<BattleNext_MatchWithRound> result = new ArrayList<>();
		for (data.scripts.match.BattleNext_Match match : tournament.getMatches()) {
			for (BattleNext_Round round : match.getRounds()) result.add(new BattleNext_MatchWithRound(match.getId(), round));
		}
		return result;
	}

	private void rebuild() {
		if (content != null) panel.removeComponent(content);
		content = panel.createUIElement(panel.getPosition().getWidth() - 20f,
				panel.getPosition().getHeight() - 8f, true);
		BattleNext_UiTheme.configure(content);
		content.addTitle(text("BattleNext_ui_page_reports"));
		if (selected == null) addList();
		else addDetails();
		content.addButton(text("BattleNext_ui_back"), "back", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 160f, 30f, 6f);
		panel.addUIElement(content).inTL(10f, 0f);
	}

	private void addList() {
		for (int index = 0; index < entries.size(); index++) {
			Entry entry = entries.get(index);
			content.addButton(entry.report.getId() + " / " + entry.report.getWinner() + " / " + entry.report.getMembers().size(),
					"report:" + index, BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 500f, 30f, 5f);
		}
		if (entries.isEmpty()) content.addPara(text("BattleNext_ui_no_reports"), 8f, BattleNext_UiTheme.MUTED);
	}

	private void addDetails() {
		BattleNext_BattleReport report = selected.report;
		content.addPara(report.getId() + " / " + report.getWinner() + " / " + report.getDurationMillis() + " ms", 8f, BattleNext_UiTheme.TEXT);
		if (!selected.context.isEmpty()) content.addPara(selected.context, 6f, BattleNext_UiTheme.MUTED);
		content.addPara(text("BattleNext_ui_damage_dealt"), 6f, BattleNext_UiTheme.ACCENT);
		appendMembers(sorted(report, true));
		content.addPara(text("BattleNext_ui_damage_taken"), 6f, BattleNext_UiTheme.ACCENT);
		appendMembers(sorted(report, false));
	}

	private void appendMembers(List<BattleNext_MemberReport> members) {
		for (BattleNext_MemberReport member : members) {
			content.addPara(member.getFleetId() + "[" + member.getMemberIndex() + "] " + member.getVariantId() + " " + member.getName()
					+ " dealt=" + member.getDamageDealt() + " taken=" + member.getDamageTaken()
					+ " hull=" + member.getHullDamageDealt() + "/" + member.getHullDamageTaken()
					+ " shield=" + member.getShieldDamageDealt() + "/" + member.getShieldDamageTaken()
					+ " EMP=" + member.getEmpDamageDealt() + "/" + member.getEmpDamageTaken()
					+ " hull%=" + member.getFinalHullFraction() + " CR=" + member.getFinalCr() + " " + member.getStatus(), 4f,
					BattleNext_UiTheme.TEXT);
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
