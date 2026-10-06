package data.simulator;

import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;

import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_SimulatorBrowser {

	private final Map<String, BattleNext_TournamentDisplay.ViewState> tournamentStates = new LinkedHashMap<>();
	private String selectedId = "";
	private BattleNext_QuickBattleDisplay.ViewState quickBattleState;
	private String statusError = "";

	public String getStatusError() {
		return statusError;
	}

	public void setStatusError(String error) {
		statusError = error;
	}

	public String getSelectedId() {
		return selectedId;
	}

	public void select(String id) {
		String previous = selectedId;
		selectedId = id;
		BattleNext_Logger.info(BattleNext_SimulatorBrowser.class, "simulator.source.selected",
				BattleNext_Logger.fields().put("previous_id", previous).put("selected_id", id).build());
	}

	public BattleNext_QuickBattleDisplay.ViewState getQuickBattleState() {
		return quickBattleState;
	}

	public void rememberQuickBattle(BattleNext_QuickBattleDisplay.ViewState state) {
		quickBattleState = state;
	}

	public BattleNext_TournamentDisplay.ViewState getTournamentState() {
		return tournamentStates.get(selectedId);
	}

	public void rememberTournament(BattleNext_TournamentDisplay.ViewState state) {
		tournamentStates.put(selectedId, state);
	}
}
