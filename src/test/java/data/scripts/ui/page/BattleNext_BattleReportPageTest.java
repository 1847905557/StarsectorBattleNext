package data.scripts.ui.page;

import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_BattleReportPageTest {

	@Test
	void sourceRowsSortByHullArmorShieldTotalAndExpansionToggles() {
		BattleNext_DamageSourceReport high = new BattleNext_DamageSourceReport("weapon", "high");
		high.add(5d, 3d, 2d, 0d);
		BattleNext_DamageSourceReport empOnly = new BattleNext_DamageSourceReport("weapon", "emp");
		empOnly.add(0d, 0d, 0d, 100d);
		BattleNext_DamageSourceReport low = new BattleNext_DamageSourceReport("weapon", "low");
		low.add(1d, 1d, 1d, 0d);

		assertEquals(List.of(high, low, empOnly),
				BattleNext_BattleReportPage.sortedSources(List.of(empOnly, low, high)));
		BattleNext_MemberReport first = new BattleNext_MemberReport("first", "");
		BattleNext_MemberReport second = new BattleNext_MemberReport("second", "");
		BattleNext_BattleReportPage.MemberRow firstDealt = new BattleNext_BattleReportPage.MemberRow(first, true);
		BattleNext_BattleReportPage.MemberRow firstTaken = new BattleNext_BattleReportPage.MemberRow(first, false);
		BattleNext_BattleReportPage.MemberRow secondDealt = new BattleNext_BattleReportPage.MemberRow(second, true);
		Set<BattleNext_BattleReportPage.MemberRow> expanded = new HashSet<>();
		BattleNext_BattleReportPage.toggleExpanded(expanded, firstDealt);
		BattleNext_BattleReportPage.toggleExpanded(expanded, secondDealt);
		assertTrue(expanded.contains(firstDealt));
		assertTrue(expanded.contains(secondDealt));
		assertFalse(expanded.contains(firstTaken));
		BattleNext_BattleReportPage.toggleExpanded(expanded, firstTaken);
		assertTrue(expanded.contains(firstDealt));
		assertTrue(expanded.contains(firstTaken));
		BattleNext_BattleReportPage.toggleExpanded(expanded, firstDealt);
		assertFalse(expanded.contains(firstDealt));
		assertTrue(expanded.contains(firstTaken));
	}

	@Test
	void detailControlsUseKnownContentWidthAndNameSurvivesRefresh() {
		BattleNext_BattleReportModel report = report();
		BattleNext_MemberReport member = new BattleNext_MemberReport("wolf_CS", "Alpha Wolf");
		member.addDamageDealt("weapon", "lightmg", 2d, 3d, 4d, 5d);
		member.addDamageTaken("weapon", "lightmg", 6d, 7d, 8d, 9d);

		assertEquals(988f, BattleNext_BattleReportPage.detailControlWidth(1000f));
		assertEquals(1f, BattleNext_BattleReportPage.detailControlWidth(8f));
		assertEquals("Old name", BattleNext_BattleReportPage.nameForRefresh(report, null));
		assertEquals("Unsaved edit", BattleNext_BattleReportPage.nameForRefresh(report, "Unsaved edit"));
		assertEquals(9d, BattleNext_BattleReportPage.sourcesFor(
				new BattleNext_BattleReportPage.MemberRow(member, true)).get(0).getTotal());
		assertEquals(21d, BattleNext_BattleReportPage.sourcesFor(
				new BattleNext_BattleReportPage.MemberRow(member, false)).get(0).getTotal());
		assertEquals(84f, BattleNext_BattleReportPage.memberBodyHeight(1));
		assertEquals(172f, BattleNext_BattleReportPage.memberBodyHeight(3));
		assertEquals(84f, BattleNext_BattleReportPage.memberBodyHeight(0));
		assertEquals(44f, BattleNext_BattleReportPage.memberBodyHeight(2)
				- BattleNext_BattleReportPage.memberBodyHeight(1));
		assertEquals(352f, BattleNext_BattleReportPage.sourceTextWidth(400f));
		assertEquals(1f, BattleNext_BattleReportPage.sourceTextWidth(40f));
		assertArrayEquals(new double[]{2d, 3d, 4d, 5d}, BattleNext_BattleReportPage.damageValues(
				new BattleNext_BattleReportPage.MemberRow(member, true)));
		assertArrayEquals(new double[]{6d, 7d, 8d, 9d}, BattleNext_BattleReportPage.damageValues(
				new BattleNext_BattleReportPage.MemberRow(member, false)));
		assertEquals("1,234.6", BattleNext_BattleReportPage.formatDamage(1234.56d));
	}

	@Test
	void hullSummaryUsesRegisteredVariantAndFallsBackToId() {
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, name -> switch (name) {
			case "getHullName" -> "Wolf";
			case "getSpriteName" -> "graphics/ships/wolf.png";
			default -> null;
		});
		ShipVariantAPI variant = proxy(ShipVariantAPI.class, name ->
				"getHullSpec".equals(name) ? hull : null);

		assertEquals(new BattleNext_BattleReportPage.HullSummary("Wolf", "graphics/ships/wolf.png"),
				BattleNext_BattleReportPage.resolveHull("wolf_CS", id -> variant));
		assertEquals(new BattleNext_BattleReportPage.HullSummary("missing", ""),
				BattleNext_BattleReportPage.resolveHull("missing", id -> null));
		assertEquals(155f, BattleNext_ExpandablePanel.headerLayout(400f, false, true).metricsWidth());
		assertEquals(260f, BattleNext_ExpandablePanel.headerLayout(1200f, false, true).metricsWidth());
		assertEquals("status_alive", BattleNext_BattleReportPage.statusKey("alive"));
		assertEquals("status_destroyed", BattleNext_BattleReportPage.statusKey("destroyed"));
		assertEquals("status_retreated", BattleNext_BattleReportPage.statusKey("retreated"));
	}

	@Test
	void independentReportNamePersistsAndFailureRestoresModel() throws Exception {
		BattleNext_BattleReportModel report = report();
		RecordingReports reports = new RecordingReports();
		RecordingTournaments tournaments = new RecordingTournaments();

		BattleNext_BattleReportPage.persistName(report, null, reports, tournaments, " New name ");
		assertEquals("New name", report.getName());
		assertSame(report, reports.saved);
		assertEquals(0, tournaments.saves);
		assertThrows(IllegalArgumentException.class, () ->
				BattleNext_BattleReportPage.persistName(report, null, reports, tournaments, "  "));
		assertEquals("New name", report.getName());
		reports.fail = true;
		assertThrows(IOException.class, () ->
				BattleNext_BattleReportPage.persistName(report, null, reports, tournaments, "Failed name"));
		assertEquals("New name", report.getName());
	}

	@Test
	void tournamentReportNameIsSavedInsideRound() throws Exception {
		BattleNext_BattleReportModel report = report();
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.setId("cup");
		BattleNext_Match match = new BattleNext_Match("match", "a", "b", 0, 1f);
		BattleNext_Round round = new BattleNext_Round("a", "b");
		round.setReport(report);
		match.addRound(round);
		tournament.addGeneratedMatch(match);
		RecordingReports reports = new RecordingReports();
		RecordingTournaments tournaments = new RecordingTournaments();

		BattleNext_BattleReportPage.persistName(report, tournament, reports, tournaments, "Cup final");
		assertSame(tournament, tournaments.saved);
		assertEquals("Cup final", tournament.toJson().getJSONArray("matches").getJSONObject(0)
				.getJSONArray("rounds").getJSONObject(0).getJSONObject("report").getString("name"));
		assertEquals(null, reports.saved);
	}

	private BattleNext_BattleReportModel report() {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report-1");
		report.setName("Old name");
		return report;
	}

	private interface Call {
		Object invoke(String name);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> call.invoke(method.getName())));
	}

	private static class RecordingReports extends BattleNext_BattleReportStore {
		private BattleNext_BattleReportModel saved;
		private boolean fail;

		@Override
		public void save(BattleNext_BattleReportModel model) throws IOException {
			if (fail) throw new IOException("write failed");
			saved = model;
		}
	}

	private static class RecordingTournaments extends BattleNext_TournamentStore {
		private BattleNext_TournamentModel saved;
		private int saves;

		@Override
		public void save(BattleNext_TournamentModel model) {
			saved = model;
			saves++;
		}
	}
}
