package data.scripts.combat;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.report.BattleNext_MemberReport;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_PostReport extends BaseEveryFrameCombatPlugin {

	private static final int MESSAGE_PRIORITY = 0;
	private final BattleNext_Scorekeeper scorekeeper;
	private CombatEngineAPI engine;
	private boolean shown;

	public BattleNext_PostReport(BattleNext_Scorekeeper scorekeeper) {
		this.scorekeeper = scorekeeper;
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
		if (!shown) {
			shown = true;
			showReport(report);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
			engine.setDoNotEndCombat(false);
			engine.removePlugin(this);
		}
	}

	private void showReport(BattleNext_BattleReportModel report) {
		engine.getCombatUI().addMessage(MESSAGE_PRIORITY, "Battle report: " + report.getWinner());
		engine.getCombatUI().addMessage(MESSAGE_PRIORITY, "Duration: " + report.getDurationMillis() / 1000f + " seconds");
		List<BattleNext_MemberReport> members = new ArrayList<>(report.getPlayerMembers());
		members.addAll(report.getEnemyMembers());
		showLeaderboard("Damage Dealt", members, true);
		showLeaderboard("Damage Taken", members, false);
		for (BattleNext_MemberReport member : members) {
			engine.getCombatUI().addMessage(MESSAGE_PRIORITY, member.getVariantId() + " " + member.getStatus()
					+ " hull=" + member.getFinalHullFraction()
					+ " cr=" + member.getFinalCr());
		}
		engine.getCombatUI().addMessage(MESSAGE_PRIORITY, "Press ESC to exit the battle report.");
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
			engine.getCombatUI().addMessage(MESSAGE_PRIORITY, member.getVariantId() + " " + value
					+ " hull=" + hull + " shields=" + shields + " emp=" + emp);
		}
	}

	private void lockCamera() {
		Vector2f center = new Vector2f(engine.getViewport().getCenter());
		engine.getViewport().setExternalControl(true);
		engine.getViewport().setCenter(center);
	}
}
