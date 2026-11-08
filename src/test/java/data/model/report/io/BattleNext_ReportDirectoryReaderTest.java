package data.model.report.io;

import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tools.BattleNext_Settings;
import org.json.JSONObject;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_ReportDirectoryReaderTest extends BattleNext_TestFleetIoFixture {

	@Test
	void enabledInformationLogsKeepTheReportDirectoryLazy() throws Exception {
		Field values = BattleNext_Settings.class.getDeclaredField("values");
		values.setAccessible(true);
		values.set(null, new JSONObject().put("infoLog", true));

		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report");
		report.setName("Report");
		report.addPlayerMember(new BattleNext_MemberReport("variant", "hull", "Member"));
		write("tournament/_reports/report.json", report.toJson().toString());
		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		Field source = BattleNext_BattleReportModel.class.getDeclaredField("detailSource");
		source.setAccessible(true);
		assertNotNull(source.get(store.get("report")));
		assertTrue(dataReads.isEmpty());
	}

	@Test
	void directoryReadsHeaderAndCountsMembersWithoutLoadingTheirDamageRecords() throws Exception {

		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report");
		report.setName("Report ] }, quoted \" name");
		report.addPlayerMember(new BattleNext_MemberReport("variant", "hull", "Member ] \""));
		report.addEnemyMember(new BattleNext_MemberReport("other", "other", "Other"));
		write("tournament/_reports/report.json", report.toJson().toString(4));
		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		BattleNext_BattleReportModel loaded = store.get("report");
		assertEquals(report.getName(), loaded.getName());
		assertEquals(2, loaded.getMemberCount());
		assertTrue(dataReads.isEmpty());

		BattleNext_BattleReportModel external = new BattleNext_BattleReportModel();
		external.setId("report");
		external.setName("External");
		write("tournament/_reports/report.json", external.toJson().toString());
		loaded.prepareDetails();
		assertTrue(dataReads.isEmpty());

		store.reload();
		assertEquals("External", store.get("report").getName());
		assertEquals(0, store.get("report").getMemberCount());
		assertEquals(report.toJson().toString(), loaded.toJson().toString());

		loaded.prepareDetails();
		assertTrue(dataReads.isEmpty());
	}
}
