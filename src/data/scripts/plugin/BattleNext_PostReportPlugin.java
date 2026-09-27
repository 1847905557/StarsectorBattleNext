package data.scripts.plugin;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_PostReportPlugin extends BaseEveryFrameCombatPlugin {

	private static final int MESSAGE_PRIORITY = 0;
	private final BattleNext_ScorekeeperPlugin scorekeeper;
	private final BattleNext_PageantPlugin pageant;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");
	private CombatEngineAPI engine;
	private boolean shown;
	public BattleNext_PostReportPlugin(BattleNext_ScorekeeperPlugin scorekeeper, BattleNext_PageantPlugin pageant) {
		this.scorekeeper = scorekeeper;
		this.pageant = pageant;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null) return;
		BattleNext_BattleReportModel report = scorekeeper.getFrozenReport();
		if (report == null) return;
		engine.setDoNotEndCombat(true);
		lockCamera();
		if (!shown && pageant.isEndingComplete()) {
			shown = true;
			showReport(report);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
			pageant.cleanup();
			engine.setDoNotEndCombat(false);
			engine.removePlugin(this);
		}
	}

	private void showReport(BattleNext_BattleReportModel report) {
		engine.getCombatUI().addMessage(MESSAGE_PRIORITY, strings.format("battle_report", report.getWinner()));
		engine.getCombatUI().addMessage(MESSAGE_PRIORITY, strings.format("duration_seconds", report.getDurationMillis() / 1000f));
		List<BattleNext_MemberReport> members = new ArrayList<>(report.getPlayerMembers());
		members.addAll(report.getEnemyMembers());
		showLeaderboard(strings.get("damage_dealt"), members, true);
		showLeaderboard(strings.get("damage_taken"), members, false);
		for (BattleNext_MemberReport member : members) {
			engine.getCombatUI().addMessage(MESSAGE_PRIORITY, strings.format("member_state", member.getVariantId(),
					member.getStatus(), member.getFinalHullFraction(), member.getFinalCr()));
		}
		engine.getCombatUI().addMessage(MESSAGE_PRIORITY, strings.get("exit_hint"));
	}

	private void showLeaderboard(String title, List<BattleNext_MemberReport> source, boolean dealt) {
		List<BattleNext_MemberReport> members = new ArrayList<>(source);
		members.sort(BattleNext_MemberReport.damageOrder(dealt));
		engine.getCombatUI().addMessage(MESSAGE_PRIORITY, title + ":");
		for (BattleNext_MemberReport member : members) {
			double value = dealt ? member.getDamageDealt() : member.getDamageTaken();
			double hull = dealt ? member.getHullDamageDealt() : member.getHullDamageTaken();
			double shields = dealt ? member.getShieldDamageDealt() : member.getShieldDamageTaken();
			double emp = dealt ? member.getEmpDamageDealt() : member.getEmpDamageTaken();
			engine.getCombatUI().addMessage(MESSAGE_PRIORITY, strings.format("member_damage", member.getVariantId(),
					value, hull, shields, emp));
		}
	}

	private void lockCamera() {
		Vector2f center = new Vector2f(engine.getViewport().getCenter());
		engine.getViewport().setExternalControl(true);
		engine.getViewport().setCenter(center);
	}
}
