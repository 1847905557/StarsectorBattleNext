package data.simulator;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_SimulatorBrowser {

	private final Map<String, BattleNext_TournamentDisplay.ViewState> tournamentStates = new LinkedHashMap<>();
	private String selectedId = "";
	private final Map<String, String> selectedInstances = new LinkedHashMap<>();
	private final Map<String, BattleNext_TournamentTemplate> templates = new LinkedHashMap<>();
	private BattleNext_QuickBattleDisplay.ViewState quickBattleState;
	private String statusError = "";
	private BattleNext_BattleValidation.Result checkResult;

	public BattleNext_BattleValidation.Result getCheckResult() {
		return checkResult;
	}

	public void setCheckResult(BattleNext_BattleValidation.Result result) {
		checkResult = result;
	}

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
		checkResult = null;
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

	public String getSelectedInstanceId() {
		return selectedInstances.get(selectedId);
	}

	public void selectInstance(String id) {
		checkResult = null;
		selectedInstances.put(selectedId, id);
	}

	public BattleNext_TournamentDisplay.ViewState getTournamentState() {
		return tournamentStates.get(selectedId + "/" + getSelectedInstanceId());
	}

	public void rememberTournament(BattleNext_TournamentDisplay.ViewState state) {
		tournamentStates.put(selectedId + "/" + getSelectedInstanceId(), state);
	}

	public BattleNext_TournamentTemplate template(BattleNext_TournamentModel configuration) {
		BattleNext_TournamentTemplate template = templates.computeIfAbsent(configuration.getId(),
				id -> new BattleNext_TournamentTemplate(configuration.getMaxFleet()));
		template.resize(configuration.getMaxFleet());
		return template;
	}
}
