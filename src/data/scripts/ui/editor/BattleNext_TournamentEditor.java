package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_TournamentModel;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_TournamentEditor implements BattleNext_EditorView {

	private static final float FIELD_HEIGHT = 28f;

	private final BattleNext_TournamentStore store;
	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_RulesStore rulesStore;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final Set<String> teams = new LinkedHashSet<>();
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private BattleNext_TournamentModel baseline;
	private BattleNext_TournamentModel draft;
	private String savedSnapshot = "";
	private String statusText = "";
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private TextFieldAPI bracketField;
	private TextFieldAPI formatField;
	private TextFieldAPI rulesField;
	private TextFieldAPI mapField;

	public BattleNext_TournamentEditor(BattleNext_TournamentStore store, BattleNext_FleetStore fleetStore,
			BattleNext_RulesStore rulesStore) {
		this.store = store;
		this.fleetStore = fleetStore;
		this.rulesStore = rulesStore;
	}

	public boolean open(BattleNext_TournamentModel source) {
		try {
			BattleNext_TournamentModel next = source == null
					? new BattleNext_TournamentModel("", "", "single_elim", "BO1", 0)
					: BattleNext_TournamentModel.fromJson(source.toJson());
			baseline = source;
			draft = next;
			teams.clear();
			teams.addAll(next.getTeams());
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
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI content = contentPanel.createUIElement(contentRect.getWidth(), contentRect.getHeight(), true);
		addDetails(content);
		contentPanel.addUIElement(content).inTL(0f, 0f);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (!(buttonId instanceof String action) || !action.startsWith("team:") || isReadOnly()) return;
		String team = action.substring("team:".length());
		String mapText = mapField.getText();
		captureFields();
		if (!teams.remove(team) && teams.size() < 32) teams.add(team);
		draft.setTeams(new ArrayList<>(teams));
		refreshContent();
		mapField.setText(mapText);
	}

	private void addDetails(TooltipMakerAPI content) {
		content.addTitle(isReadOnly() ? text("tournament_locked") : text("tournament_editable"));
		idField = field(content, text("tournament_id"), draft.getId());
		nameField = field(content, text("tournament_name"), draft.getName());
		bracketField = field(content, text("tournament_bracket"), draft.getBracketType());
		formatField = field(content, text("tournament_bo"), draft.getMatchFormat());
		rulesField = field(content, text("tournament_rules"), draft.getRulesId());
		mapField = field(content, text("battle_map"), String.valueOf(draft.getMapId()));
		content.addPara(text("tournament_size") + ": " + teams.size(), 8f, BattleNext_UiTheme.MUTED);
		content.addPara(text("teams"), 10f, BattleNext_UiTheme.ACCENT);
		if (isReadOnly()) {
			for (String team : teams) {
				content.addPara(team, 5f, BattleNext_UiTheme.TEXT);
			}
		} else {
			for (BattleNext_FleetModel fleet : fleetStore.getAll()) {
				String label = (teams.contains(fleet.getId()) ? "[x] " : "[ ] ") + fleet.getName() + " [" + fleet.getId() + "]";
				BattleNext_PageLayout.buttonWithTooltip(content, label, "team:" + fleet.getId(),
						Math.max(40f, contentRect.getWidth() - 18f));
			}
		}
		if (isReadOnly()) {
			content.addPara(text("matches"), 10f, BattleNext_UiTheme.ACCENT);
			for (BattleNext_Match match : draft.getMatches()) {
				content.addPara(match.getId() + " " + match.getPlayer() + " / " + match.getEnemy()
						+ " [" + match.getStatus() + "]", 6f, BattleNext_UiTheme.TEXT);
			}
		}
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value) {
		host.addPara(label, 5f, BattleNext_UiTheme.MUTED);
		if (isReadOnly()) {
			host.addPara(value == null ? "" : value, 2f, BattleNext_UiTheme.TEXT);
			return null;
		}
		TextFieldAPI field = BattleNext_TextField.addTo(host, panel, Math.min(360f, contentRect.getWidth() - 18f), FIELD_HEIGHT, value);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		return field;
	}

	private void captureFields() {
		if (idField == null || isReadOnly()) return;
		draft.setId(idField.getText());
		draft.setName(nameField.getText());
		draft.setBracketType(bracketField.getText());
		draft.setMatchFormat(formatField.getText());
		draft.setRulesId(rulesField.getText());
	}

	@Override
	public String serializeState() {
		if (idField == null) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, nameField, bracketField, formatField, rulesField, mapField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		for (String team : teams) {
			value.append(team.length()).append(':').append(team);
		}
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
			statusText = text("load_failed") + ": " + (!store.isLoaded() ? store.getLoadError()
					: !fleetStore.isLoaded() ? fleetStore.getLoadError() : rulesStore.getLoadError());
			return false;
		}
		captureFields();
		if (!List.of("single_elim", "double_elim", "round_robin", "kof").contains(draft.getBracketType())
				|| !List.of("BO1", "BO3", "BO5").contains(draft.getMatchFormat())) {
			statusText = text("invalid_tournament_format");
			return false;
		}
		int map;
		try {
			map = Integer.parseInt(mapField.getText().trim());
		} catch (NumberFormatException exception) {
			statusText = text("invalid_map");
			return false;
		}
		if (map < -1 || map > 11) {
			statusText = text("invalid_map");
			return false;
		}
		draft.setMapId(map);
		draft.setTeams(new ArrayList<>(teams));
		try {
			BattleNext_TournamentModel nextBaseline = BattleNext_TournamentModel.fromJson(draft.toJson());
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
}
