package data.scripts.order;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderParser;
import data.order.BattleNext_OrderRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BattleNext_OrderProcessor {

	private final List<BattleNext_OrderThread> threads = new ArrayList<>();
	private final String shipLabel;
	private final Consumer<String> report;
	private BattleNext_OrderThread lastThread;

	public BattleNext_OrderProcessor(String script, String shipLabel, BattleNext_OrderRegistry registry, Consumer<String> report) {

		this.shipLabel = shipLabel;
		this.report = report;
		List<BattleNext_OrderParser.OrderParseResult> parsed = BattleNext_OrderParser.parseScript(script, registry);
		for (int index = 0; index < parsed.size(); index++) {
			BattleNext_OrderParser.OrderParseResult result = parsed.get(index);
			if (!result.isValid()) {
				report.accept(shipLabel + " instruction " + (index + 1) + ": " + result.getError());
				continue;
			}
			try {
				threads.add(new BattleNext_OrderThread(result.getCommand(), registry));
			} catch (RuntimeException exception) {
				report.accept(shipLabel + " instruction " + (index + 1) + ": " + exception.getMessage());
			}
		}
	}

	public boolean isEmpty() {
		return threads.isEmpty();
	}

	public void advance(ShipAPI ship, CombatEngineAPI engine, BattleNext_OrderField field) {

		if (field.hasPlayerForcedAssignment(ship)) return;
		for (int index = 0; index < threads.size(); index++) {
			BattleNext_OrderThread thread = threads.get(index);
			try {
				if (!thread.advance(ship, engine, field, lastThread == thread)) continue;
				lastThread = thread;
				return;
			} catch (BattleNext_OrderThread.OrderScriptExecutionException exception) {
				report.accept(shipLabel + " instruction " + (index + 1) + " script " + exception.getScriptId()
						+ ": " + exception.getCause());
			}
		}
		lastThread = null;
	}
}
