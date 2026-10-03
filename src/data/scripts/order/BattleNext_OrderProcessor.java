package data.scripts.order;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderParser;
import data.order.BattleNext_OrderRegistry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class BattleNext_OrderProcessor {

	private final List<Instruction> instructions = new ArrayList<>();
	private final String shipLabel;
	private final Consumer<String> report;

	public BattleNext_OrderProcessor(String script, String shipLabel, BattleNext_OrderRegistry registry, Consumer<String> report) {

		this.shipLabel = shipLabel;
		this.report = report;
		for (BattleNext_OrderParser.OrderParseResult parsed : BattleNext_OrderParser.parseScript(script, registry)) {
			String location = shipLabel + " instruction " + parsed.getInstructionNumber() + " line " + parsed.getLineNumber();
			if (!parsed.isValid()) {
				report.accept(location + ": " + parsed.getError());
				continue;
			}
			try {
				instructions.add(new Instruction(parsed.getInstructionNumber(), parsed.getLineNumber(),
						new BattleNext_OrderThread(parsed.getCommand(), registry)));
			} catch (BattleNext_OrderThread.OrderScriptExecutionException exception) {
				report.accept(location + " script " + exception.getScriptId() + ": " + exception.getCause());
			}
		}
	}

	public boolean isEmpty() {
		return instructions.isEmpty();
	}

	public void advance(ShipAPI ship, CombatEngineAPI engine, BattleNext_OrderField field) {

		if (field.hasExternalAssignment(ship)) return;
		Iterator<Instruction> iterator = instructions.iterator();
		while (iterator.hasNext()) {
			Instruction instruction = iterator.next();
			try {
				if (instruction.thread.advance(ship, engine, field)) return;
			} catch (BattleNext_OrderThread.OrderScriptExecutionException exception) {
				report.accept(shipLabel + " instruction " + instruction.number + " line " + instruction.line
						+ " script " + exception.getScriptId() + ": " + exception.getCause());
				iterator.remove();
				field.releaseShip(ship);
			}
		}
		field.releaseShip(ship);
	}

	private record Instruction(int number, int line, BattleNext_OrderThread thread) { }
}
