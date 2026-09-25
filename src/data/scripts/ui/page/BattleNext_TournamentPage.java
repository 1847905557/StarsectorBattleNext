package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_TournamentPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RulesStore rulesStore = new BattleNext_RulesStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private String selectedId = "";
	private BattleNext_Tournament draft;
	private boolean draftOpen;
	private String savedSnapshot = "";
	private final Set<String> teams = new LinkedHashSet<>();
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private TextFieldAPI bracketField;
	private TextFieldAPI formatField;
	private TextFieldAPI rulesField;
	private TextFieldAPI mapField;
	private LabelAPI statusLabel;
	private String status = "";

	public BattleNext_TournamentPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		selectedId = "";
		draftOpen = false;
		fleetStore.reload();
		try {
			tournamentStore.reload();
			rulesStore.reload();
		} catch (Exception exception) {
			status = exception.getMessage();
		}
		createContents();
	}

	@Override
	public void createContents() {
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), 30f),
				panel, text("page_tournaments"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.HEADER, 1f);
		detailRect.addBorder(panel, BattleNext_UiTheme.HEADER, 1f);
		UIRect footerRect = regions[2];
		String[] actions = {"add", "save", "back"};
		float buttonWidth = Math.min(160f, (footerRect.getWidth() - 20f) / 3f);
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(panel,
					new UIRect(footerRect.getX() + index * (buttonWidth + 10f), footerRect.getY(), buttonWidth, 30f),
					text(actions[index]), actions[index], CutStyle.ALL, true);
		}
		float statusX = 3f * buttonWidth + 30f;
		if (footerRect.getWidth() - statusX >= 60f) {
			TooltipMakerAPI footerStatus = panel.createUIElement(footerRect.getWidth() - statusX, 30f, false);
			statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
			statusLabel.getPosition().inTL(0f, 5f);
			footerStatus.setHeightSoFar(30f);
			panel.addUIElement(footerStatus).inTL(footerRect.getX() + statusX, footerRect.getY());
		}

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI listContent = listPanel.createUIElement(listRect.getWidth() - 18f, listRect.getHeight() - 16f, true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(8f, 8f);

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, null).getPanel();
		if (!draftOpen) {
			TooltipMakerAPI prompt = detailPanel.createUIElement(detailRect.getWidth() - 18f, detailRect.getHeight() - 16f, false);
			prompt.addTitle(text("select_tournament"));
			detailPanel.addUIElement(prompt).inTL(8f, 8f);
			return;
		}
		BattleNext_Tournament selected = selectedId.isEmpty() ? null : tournamentStore.get(selectedId);
		if (draft == null) openDraft(selected);
		TooltipMakerAPI detailContent = detailPanel.createUIElement(detailRect.getWidth() - 18f,
				detailRect.getHeight() - 50f, true);
		addDetails(detailContent);
		detailPanel.addUIElement(detailContent).inTL(8f, 8f);
		if (selected != null) {
			ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
					new UIRect(8f, detailRect.getHeight() - 36f, 145f, 28f),
					text("delete"), "delete", CutStyle.ALL, true);
			delete.setEnabled(tournamentStore.isManaged(selectedId));
			if (!tournamentStore.isManaged(selectedId)) {
				TooltipMakerAPI note = detailPanel.createUIElement(detailRect.getWidth() - 170f, 28f, false);
				note.addPara(note.shortenString(text("source_unavailable_short"), detailRect.getWidth() - 180f),
						0f, BattleNext_UiTheme.MUTED);
				detailPanel.addUIElement(note).inTL(165f, detailRect.getHeight() - 34f);
			}
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("tournament:select:")) {
			String id = ((String) buttonId).substring("tournament:select:".length());
			navigation.accept(() -> { selectedId = id; openDraft(tournamentStore.get(id)); refreshContent(); savedSnapshot = snapshot(); });
		} else if ("add".equals(buttonId)) {
			navigation.accept(() -> { selectedId = ""; openDraft(null); refreshContent(); savedSnapshot = snapshot(); });
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty()
				&& tournamentStore.isManaged(selectedId)) {
			BattleNext_Tournament selected = tournamentStore.get(selectedId);
			deletion.accept(selected.getName() + " [" + selectedId + "]", () -> {
				try {
					tournamentStore.delete(selectedId);
					selectedId = "";
					draftOpen = false;
					draft = null;
					setStatus(text("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(text("save_failed") + ": " + exception.getMessage());
				}
			});
		} else if ("back".equals(buttonId)) {
			if (draftOpen) navigation.accept(() -> { selectedId = ""; draftOpen = false; draft = null; refreshContent(); });
			else backAction.run();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("team:")
				&& draftOpen && !draft.isStarted()) {
			String team = ((String) buttonId).substring("team:".length());
			String mapText = mapField.getText();
			captureFields();
			if (!teams.remove(team) && teams.size() < 32) teams.add(team);
			draft.setTeams(new ArrayList<>(teams));
			refreshContent();
			mapField.setText(mapText);
		}
	}

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_tournaments"));
		if (tournamentStore.getAll().isEmpty()) listContent.addPara(text("no_tournaments"), 10f, BattleNext_UiTheme.MUTED);
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			String label = tournament.getName() + " [" + tournament.getId() + "]";
			BattleNext_PageLayout.buttonWithTooltip(listContent, label, "tournament:select:" + tournament.getId(),
					Math.max(80f, listRect.getWidth() - 30f));
		}
	}

	private void addDetails(TooltipMakerAPI detailContent) {
		detailContent.addTitle(draft.isStarted() ? text("tournament_locked") : text("tournament_editable"));
		idField = field(detailContent, text("tournament_id"), draft.getId(), draft.isStarted());
		nameField = field(detailContent, text("tournament_name"), draft.getName(), draft.isStarted());
		bracketField = field(detailContent, text("tournament_bracket"), draft.getBracketType(), draft.isStarted());
		formatField = field(detailContent, text("tournament_bo"), draft.getMatchFormat(), draft.isStarted());
		rulesField = field(detailContent, text("tournament_rules"), draft.getRulesId(), draft.isStarted());
		mapField = field(detailContent, text("battle_map"), String.valueOf(draft.getMapId()), draft.isStarted());
		detailContent.addPara(text("tournament_size") + ": " + teams.size(), 8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("teams"), 10f, BattleNext_UiTheme.ACCENT);
		if (draft.isStarted()) {
			for (String team : teams) detailContent.addPara(team, 5f, BattleNext_UiTheme.TEXT);
		} else {
			for (BattleNext_FleetModel fleet : fleetStore.getAll()) {
				String label = (teams.contains(fleet.getId()) ? "[x] " : "[ ] ") + fleet.getName() + " [" + fleet.getId() + "]";
				BattleNext_PageLayout.buttonWithTooltip(detailContent, label, "team:" + fleet.getId(),
						Math.max(100f, detailRect.getWidth() - 38f));
			}
		}
		if (draft.isStarted()) {
			detailContent.addPara(text("matches"), 10f, BattleNext_UiTheme.ACCENT);
			for (BattleNext_Match match : draft.getMatches()) {
				detailContent.addPara(match.getId() + " " + match.getPlayer() + " / " + match.getEnemy()
						+ " [" + match.getStatus() + "]", 6f, BattleNext_UiTheme.TEXT);
			}
		}
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value, boolean readOnly) {
		host.addPara(label, 5f, BattleNext_UiTheme.MUTED);
		if (readOnly) {
			host.addPara(value == null ? "" : value, 2f, BattleNext_UiTheme.TEXT);
			return null;
		}
		TextFieldAPI field = host.addTextField(Math.min(360f, detailRect.getWidth() - 30f), 2f);
		field.setText(value == null ? "" : value);
		field.setMaxChars(240);
		return field;
	}

	private void openDraft(BattleNext_Tournament source) {
		draftOpen = true;
		try {
			draft = source == null ? new BattleNext_Tournament("", "", "single_elim", "BO1", 0)
					: BattleNext_Tournament.fromJson(source.toJson());
		} catch (JSONException exception) {
			setStatus(text("load_failed") + ": " + exception.getMessage());
			return;
		}
		teams.clear();
		teams.addAll(draft.getTeams());
		idField = null;
	}

	private String snapshot() {
		if (idField == null) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, nameField, bracketField, formatField, rulesField, mapField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		for (String team : teams) value.append(team.length()).append(':').append(team);
		return value.toString();
	}

	private void captureFields() {
		if (idField == null || draft == null || draft.isStarted()) return;
		draft.setId(idField.getText());
		draft.setName(nameField.getText());
		draft.setBracketType(bracketField.getText());
		draft.setMatchFormat(formatField.getText());
		draft.setRulesId(rulesField.getText());
	}

	@Override public boolean hasUnsavedChanges() { return draftOpen && draft != null && !draft.isStarted()
			&& !snapshot().equals(savedSnapshot); }

	@Override public boolean save() {
		if (!draftOpen || draft == null || draft.isStarted()) return true;
		captureFields();
		String id = draft.getId();
		if (!BattleNext_EntryId.isValid(id)) { setStatus(text("invalid_tournament_id")); return false; }
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			if (BattleNext_EntryId.sameFileName(id, tournament.getId()) && !tournament.getId().equals(selectedId)) {
				setStatus(text("duplicate_id")); return false;
			}
		}
		if (!selectedId.isEmpty() && BattleNext_EntryId.sameFileName(id, selectedId)
				&& !tournamentStore.isManaged(selectedId)) {
			setStatus(text("source_unavailable")); return false;
		}
		if (!List.of("single_elim", "double_elim", "round_robin", "kof").contains(draft.getBracketType())
				|| !List.of("BO1", "BO3", "BO5").contains(draft.getMatchFormat())) {
			setStatus(text("invalid_tournament_format")); return false;
		}
		int map;
		try { map = Integer.parseInt(mapField.getText().trim()); }
		catch (NumberFormatException exception) { setStatus(text("invalid_map")); return false; }
		if (map < -1 || map > 11) { setStatus(text("invalid_map")); return false; }
		draft.setMapId(map);
		draft.setTeams(new ArrayList<>(teams));
		try {
			tournamentStore.save(draft);
			tournamentStore.reload();
			selectedId = id;
			openDraft(tournamentStore.get(id));
			setStatus(text("saved"));
			refreshContent();
			savedSnapshot = snapshot();
			return true;
		} catch (Exception exception) {
			setStatus(text("save_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	@Override public void discard() {
		if (selectedId.isEmpty()) { draftOpen = false; draft = null; refreshContent(); }
		else { openDraft(tournamentStore.get(selectedId)); refreshContent(); savedSnapshot = snapshot(); }
	}

	@Override public String getStatusText() { return status; }

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		return strings.get(key);
	}
}
