package data.scripts.ui.editor;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rules.BattleNext_RulesStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.BracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.CheckBoxGroup;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_TextField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_TournamentEditor implements BattleNext_EditorView {

	private static final float FIELD_HEIGHT = 28f;

	private final BattleNext_TournamentStore store;
	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_RulesStore rulesStore;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private BattleNext_TournamentModel baseline;
	private BattleNext_TournamentModel draft;
	private String savedSnapshot = "";
	private String statusText = "";
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private TextFieldAPI boField;
	private TextFieldAPI maxFleetField;
	private TextFieldAPI rulesField;
	private TextFieldAPI mapField;
	private TextFieldAPI fleetField;
	private BattleNext_OptionField fleetPicker;
	private CheckBoxGroup bracketGroup;

	public BattleNext_TournamentEditor(BattleNext_TournamentStore store, BattleNext_FleetStore fleetStore,
			BattleNext_RulesStore rulesStore) {
		this.store = store;
		this.fleetStore = fleetStore;
		this.rulesStore = rulesStore;
	}

	public boolean open(BattleNext_TournamentModel source) {
		try {
			BattleNext_TournamentModel next = new BattleNext_TournamentModel();
			if (source != null) next.fromJson(source.toJson());
			baseline = source;
			draft = next;
			idField = null;
			statusText = "";
			return true;
		} catch (Exception exception) {
			statusText = text("load_failed") + ": " + exception.getMessage();
			return false;
		}
	}

	public String getDraftId() {
		return idField == null ? "" : idField.getText().trim();
	}

	public boolean isReadOnly() {
		return draft != null && draft.isStarted();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return fleetPicker != null && fleetPicker.handleGlobalInput(events);
	}

	public void closePicker() {
		if (fleetPicker != null) fleetPicker.close();
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		createContents();
		savedSnapshot = serializeState();
	}

	@Override
	public void createContents() {
		contentRect = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		refreshContent();
	}

	@Override
	public void refreshContent() {
		closePicker();
		BattleNext_TextField.clearRegisteredFields();
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI content = contentPanel.createUIElement(contentRect.getWidth(), contentRect.getHeight(), true);
		addDetails(content);
		contentPanel.addUIElement(content).inTL(0f, 0f);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (isReadOnly() || !(buttonId instanceof String action)) return;
		if (action.startsWith("bracket:")) {
			bracketGroup.updateCheck();
			return;
		}
		if (!action.startsWith("fleet:")) return;
		String[] fields = {idField.getText(), nameField.getText(), boField.getText(),
				maxFleetField.getText(), rulesField.getText(), mapField.getText()};
		List<String> fleets = selectedFleets();
		if (!"fleet:apply".equals(action)) {
			int index = Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
			int target = action.startsWith("fleet:up:") ? index - 1 : index + 1;
			if (index < 0 || target < 0 || index >= fleets.size() || target >= fleets.size()) return;
			String moved = fleets.remove(index);
			fleets.add(target, moved);
		}
		captureTextFields();
		draft.setFleetIds(fleets);
		refreshContent();
		TextFieldAPI[] current = {idField, nameField, boField, maxFleetField, rulesField, mapField};
		for (int field = 0; field < current.length; field++) current[field].setText(fields[field]);
	}

	private void addDetails(TooltipMakerAPI content) {
		content.addTitle(isReadOnly() ? text("tournament_locked") : text("tournament_editable"));
		idField = field(content, text("tournament_id"), draft.getId());
		nameField = field(content, text("tournament_name"), draft.getName());
		content.addPara(text("tournament_bracket"), 8f, BattleNext_UiTheme.MUTED);
		bracketGroup = new CheckBoxGroup();
		if (isReadOnly()) {
			content.addPara(bracketLabel(draft.getBracketType()), 2f, BattleNext_UiTheme.TEXT);
		} else {
			for (BracketType type : BracketType.values()) {
				ButtonAPI button = content.addCheckbox(Math.max(100f, contentRect.getWidth() - 18f), 28f,
						bracketLabel(type), "bracket:" + type.name(), ButtonAPI.UICheckboxSize.SMALL, 2f);
				bracketGroup.addCheckBox(button, type);
			}
			bracketGroup.tryCheckValue(draft.getBracketType());
			bracketGroup.updateCheck();
		}
		boField = field(content, text("tournament_bo"), Integer.toString(draft.getBoCount()));
		maxFleetField = field(content, text("tournament_max_fleet"), Integer.toString(draft.getMaxFleet()));
		rulesField = field(content, text("tournament_rules"), draft.getRulesId());
		mapField = field(content, text("battle_map"), Integer.toString(draft.getMapId()));
		content.addPara(text("fleet_ids"), 10f, BattleNext_UiTheme.ACCENT);
		if (isReadOnly()) {
			for (String fleet : draft.getFleetIds()) content.addPara(fleet, 5f, BattleNext_UiTheme.TEXT);
		} else {
			fleetPicker = new BattleNext_OptionField(panel, new FleetOptions(), BattleNext_OptionField.Mode.MULTIPLE);
			fleetField = fleetPicker.addTo(content, panel, Math.min(360f, contentRect.getWidth() - 18f),
					String.join(",", draft.getFleetIds()));
			fleetField.setMaxChars(8192);
			BattleNext_PageLayout.button(content, text("apply_fleet_selection"), "fleet:apply", 180f);
			for (int index = 0; index < draft.getFleetIds().size(); index++) {
				String fleetId = draft.getFleetIds().get(index);
				BattleNext_FleetModel fleet = fleetStore.get(fleetId);
				String label = (index + 1) + ". " + (fleet == null ? fleetId : fleet.getName() + " [" + fleetId + "]");
				content.addPara(label, 5f, BattleNext_UiTheme.TEXT);
				if (index > 0) BattleNext_PageLayout.button(content, text("move_up"), "fleet:up:" + index, 90f);
				if (index + 1 < draft.getFleetIds().size()) {
					BattleNext_PageLayout.button(content, text("move_down"), "fleet:down:" + index, 90f);
				}
			}
		}
		if (isReadOnly()) {
			content.addPara(text("matches"), 10f, BattleNext_UiTheme.ACCENT);
			for (BattleNext_Match match : draft.getMatches()) {
				content.addPara(match.getId() + " " + match.getPlayer() + " / " + match.getEnemy()
						+ " [" + text("match_status_" + match.getStatus().name().toLowerCase(java.util.Locale.ROOT))
						+ "]", 6f, BattleNext_UiTheme.TEXT);
			}
		}
	}

	private String bracketLabel(BracketType type) {
		return text("bracket_" + type.name().toLowerCase(java.util.Locale.ROOT));
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value) {
		host.addPara(label, 5f, BattleNext_UiTheme.MUTED);
		if (isReadOnly()) {
			host.addPara(value, 2f, BattleNext_UiTheme.TEXT);
			return null;
		}
		TextFieldAPI result = BattleNext_TextField.addTo(host, panel,
				Math.min(360f, contentRect.getWidth() - 18f), FIELD_HEIGHT, value);
		result.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		return result;
	}

	private void captureTextFields() {
		draft.setId(idField.getText());
		draft.setName(nameField.getText());
		draft.setBracketType((BracketType) bracketGroup.getCheckedValue());
		draft.setRulesId(rulesField.getText());
	}

	private List<String> selectedFleets() {
		List<String> result = new ArrayList<>();
		for (String value : fleetField.getText().split(",")) {
			if (!value.trim().isEmpty()) result.add(value.trim());
		}
		return result;
	}

	@Override
	public String serializeState() {
		if (idField == null || isReadOnly()) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, nameField, boField,
				maxFleetField, rulesField, mapField, fleetField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		value.append(bracketGroup.getCheckedValue());
		return value.toString();
	}

	@Override
	public boolean hasUnsavedChanges() {
		return !isReadOnly() && !serializeState().equals(savedSnapshot);
	}

	@Override
	public boolean serialize() {
		if (isReadOnly()) return true;
		if (!store.isLoaded() || !fleetStore.isLoaded() || !rulesStore.isLoaded()) {
			statusText = text("load_failed");
			return false;
		}
		int boCount;
		int maxFleet;
		int map;
		try {
			boCount = Integer.parseInt(boField.getText().trim());
			maxFleet = Integer.parseInt(maxFleetField.getText().trim());
			map = Integer.parseInt(mapField.getText().trim());
		} catch (NumberFormatException exception) {
			statusText = text("invalid_tournament_format");
			return false;
		}
		List<String> fleets = selectedFleets();
		if (bracketGroup.getCheckedValue() == null || boCount < 1 || maxFleet < 2 || maxFleet > 32
				|| fleets.size() < 2 || fleets.size() > maxFleet || new HashSet<>(fleets).size() != fleets.size()
				|| map < -1 || map > 11) {
			statusText = text("invalid_tournament_format");
			return false;
		}
		for (String fleet : fleets) {
			if (fleetStore.get(fleet) == null) {
				statusText = text("invalid_player") + ": " + fleet;
				return false;
			}
		}
		captureTextFields();
		draft.setBoCount(boCount);
		draft.setMaxFleet(maxFleet);
		draft.setMapId(map);
		draft.setFleetIds(fleets);
		try {
			BattleNext_TournamentModel nextBaseline = new BattleNext_TournamentModel();
			nextBaseline.fromJson(draft.toJson());
			store.save(draft);
			baseline = nextBaseline;
			savedSnapshot = serializeState();
			statusText = text("saved");
			return true;
		} catch (Exception exception) {
			statusText = text("save_failed") + ": " + exception.getMessage();
			return false;
		}
	}

	@Override
	public void deserialize() {
		if (!open(baseline)) return;
		refreshContent();
		savedSnapshot = serializeState();
	}

	@Override
	public String getStatusText() {
		return statusText;
	}

	private String text(String key) {
		return strings.get(key);
	}

	private class FleetOptions implements BattleNext_OptionField.Source {
		@Override
		public List<BattleNext_OptionField.Option> getOptions() {
			List<BattleNext_OptionField.Option> options = new ArrayList<>();
			for (BattleNext_FleetModel fleet : fleetStore.getAll()) {
				options.add(new BattleNext_OptionField.Option(fleet.getId(), fleet.getName()));
			}
			return options;
		}

		@Override
		public int getMaxCount(String id) {
			return 1;
		}

		@Override
		public String getIconPath(String id) {
			return null;
		}

		@Override
		public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionField.Option option) {
			tooltip.addPara(option.getLabel() + " [" + option.getId() + "]", 0f, BattleNext_UiTheme.TEXT);
		}
	}
}
