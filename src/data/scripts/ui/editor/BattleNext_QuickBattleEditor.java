package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.missions.BattleNext_MissionBattleMap;
import data.model.fleet.BattleNext_FleetStore;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_SaveErrors;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Row;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_QuickBattleEditor extends BattleNext_EditorView {

	private static final int FOOTER_BUTTON_COUNT = 3;
	private static final float FOOTER_BUTTON_GAP = 10f;
	private static final float FOOTER_BUTTON_MAX_WIDTH = 150f;

	private static final float STATUS_CONTENT_WIDTH_RESERVE = 10f;
	private static final float STATUS_INITIAL_TEXT_RESERVE = 15f;
	private static final float STATUS_TOP_INSET = 5f;

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private final BattleNext_QuickBattleStore store = provider.getQuickBattleStore();
	private final BattleNext_FleetStore fleetStore = provider.getFleetStore();
	private final BattleNext_BattleValidation validation = provider.getValidation();
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final List<BattleNext_QuickBattleModel> entries = new ArrayList<>();
	private final Map<BattleNext_QuickBattleModel, Object> rowKeys = new IdentityHashMap<>();
	private BattleNext_QuickBattleDisplay display;
	private BattleNext_QuickBattleDisplay.ViewState viewState;
	private LabelAPI statusLabel;
	private TooltipMakerAPI statusContent;
	private boolean loaded;

	public BattleNext_QuickBattleEditor(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	@Override
	protected void buildEditorContent() {

		if (!loaded) load();
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] bottom = root.splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		UIRect[] table = bottom[0].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f)
				.splitVertically(BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		UIRect tableRect = BattleNext_PageLayout.pageContentRect(new UIRect(0f, table[0].getY(), table[0].getWidth(),
				table[0].getHeight() + table[1].getHeight()));
		CustomPanelAPI tablePanel = tableRect.createSubPanel(panel, inputOwner).getPanel();
		BattleNext_QuickBattleDisplay.Presentation presentation = new BattleNext_QuickBattleDisplay.Presentation(
				List.of(Column.NUMBER, Column.PLAYER, Column.ENEMY, Column.MAP, Column.SCALE, Column.STATUS, Column.ENABLED, Column.ACTIONS),
				List.of(0.025f, 0.29f, 0.29f, 0.05f, 0.08f, 0.095f, 0.07f, 0.1f), true,
				row -> new BattleNext_QuickBattleDisplay.Action(strings.get("delete"), "delete:" + row.getIndex(), true), inputOwner::buttonPressed);
		display = own(new BattleNext_QuickBattleDisplay(inputRouter, entries, presentation,
				entry -> rowKeys.computeIfAbsent(entry, ignored -> new Object()), this::validationErrors, fleetStore, overlayHost));
		display.attach(tablePanel);

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN,
				BattleNext_PageLayout.PAGE_CONTENT_INSET, BattleNext_PageLayout.PAGE_CONTENT_INSET);
		float buttonWidth = Math.min(FOOTER_BUTTON_MAX_WIDTH,
				(footerRect.getWidth() - FOOTER_BUTTON_GAP * (FOOTER_BUTTON_COUNT - 1)) / FOOTER_BUTTON_COUNT);
		createFixedButton(panel,
				new UIRect(footerRect.getX(), footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("add"), "add", true, true);
		createFixedButton(panel,
				new UIRect(footerRect.getX() + buttonWidth + FOOTER_BUTTON_GAP,
						footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("save"), "save", true, fleetStore.isLoaded());
		createFixedButton(panel,
				new UIRect(footerRect.getX() + 2f * (buttonWidth + FOOTER_BUTTON_GAP),
						footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("back"), "back", true, true);
		float statusX = footerRect.getX() + FOOTER_BUTTON_COUNT * (buttonWidth + FOOTER_BUTTON_GAP);
		float statusWidth = footerRect.getWidth() - (statusX - footerRect.getX());
		statusContent = panel.createUIElement(statusWidth - STATUS_CONTENT_WIDTH_RESERVE,
				BattleNext_PageLayout.FOOTER_HEIGHT, false);
		statusLabel = statusContent.addPara(statusContent.shortenString(
				getStatusText(), statusWidth - STATUS_INITIAL_TEXT_RESERVE),
				0f);
		statusContent.addTooltipTo(BattleNext_SaveErrors.tooltip(this::getSaveErrors), statusContent.getPrev(), TooltipMakerAPI.TooltipLocation.ABOVE, true);
		statusLabel.getPosition().inTL(0f, STATUS_TOP_INSET);
		statusContent.setHeightSoFar(BattleNext_PageLayout.FOOTER_HEIGHT);
		panel.addUIElement(statusContent).inTL(statusX, footerRect.getY());

		if (viewState != null) {
			display.restoreState(viewState);
		}
		bindViewport("rows", display.getContent());
	}

	@Override
	protected void afterRefresh() {
		refreshRowStatuses();
	}

	private void load() {
		List<String> errors = provider.reloadQuickBattleCatalog();
		statusText = errors.isEmpty() ? "" : strings.get("load_failed") + ": " + String.join(" | ", errors);
		entries.clear();
		entries.addAll(store.getAll());
		loaded = true;
		rowKeys.clear();
	}

	private boolean promoteEditedRawRows() {
		for (Row row : display.getRows()) {
			if (row.getModel().getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW || row.getRawField().hasFocus()) {
				continue;
			}
			BattleNext_QuickBattleModel parsed = BattleNext_QuickBattleModel.parseLine(
					row.getRawField().getText(), row.getModel().usesLegacyIds());
			if (parsed != null && parsed.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				row.getModel().promoteRaw(row.getRawField().getText());
				refreshContent();
				return true;
			}
		}
		return false;
	}

	private List<String> validationErrors(Row row) {
		if (row.getModel().getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			return List.of();
		}
		return validation.quickBattleErrors(row.getFleetId(Column.PLAYER), row.getFleetId(Column.ENEMY),
				row.getField(Column.MAP).getText(), row.getField(Column.SCALE).getText());
	}

	private void removeRow(int index) {
		BattleNext_QuickBattleModel removed = entries.remove(index);
		rowKeys.remove(removed);
		refreshContent();
		setStatus(strings.get("deleted"));
	}

	private void deleteRemovedEntries() throws IOException, JSONException {
		List<String> keptIds = new ArrayList<>();
		for (BattleNext_QuickBattleModel entry : entries) {
			keptIds.add(entry.getId());
		}
		for (BattleNext_QuickBattleModel stored : store.getAll()) {
			if (keptIds.contains(stored.getId())) continue;
			store.delete(stored.getId());
		}
	}

	private String mintId() {
		int index = 0;
		while (true) {
			String candidate = "quick-" + index;
			if (store.get(candidate) == null && !containsId(candidate)) return candidate;
			index++;
		}
	}

	private boolean containsId(String id) {
		for (BattleNext_QuickBattleModel entry : entries) {
			if (entry.getId().equals(id)) return true;
		}
		return false;
	}

	private void setStatus(String value) {
		statusText = value;
		if (statusLabel != null) {
			statusLabel.setText(statusContent.shortenString(value,
					statusContent.getPosition().getWidth() - STATUS_CONTENT_WIDTH_RESERVE));
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("add".equals(buttonId)) {
			entries.add(new BattleNext_QuickBattleModel("", "", -1, 1f));
			refreshContent();
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("back".equals(buttonId)) {
			backAction.run();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("delete:")) {
			removeRow(Integer.parseInt(((String) buttonId).substring("delete:".length())));
		}
	}

	public void refreshRowStatuses() {
		if (promoteEditedRawRows()) return;
		display.refreshRowStatuses();
	}

	@Override
	protected String draftState() {
		StringBuilder value = new StringBuilder();
		for (Row row : display.getRows()) {
			value.append(row.getModel().getRowType()).append(row.isEnabled());
			for (Column column : List.of(Column.PLAYER, Column.ENEMY)) {
				String id = row.getFleetId(column);
				value.append(id.length()).append(':').append(id);
			}
			for (TextFieldAPI field : new TextFieldAPI[]{row.getField(Column.MAP), row.getField(Column.SCALE), row.getRawField()}) {
				if (field != null) {
					value.append(field.getText().length()).append(':').append(field.getText());
				}
			}
		}
		return value.toString();
	}

	@Override
	protected boolean submitDraft() {
		if (!fleetStore.isLoaded()) {
			saveErrors.add(strings.get("load_failed") + " / tournament / " + fleetStore.getLoadError());
		}
		if (!store.isLoaded()) {
			saveErrors.add(strings.get("load_failed") + " / quick battle / " + store.getLoadError());
		}
		if (!captureFields()) {
			setStatus(saveErrors.summary());
			return false;
		}
		try {
			deleteRemovedEntries();
			for (BattleNext_QuickBattleModel entry : entries) {
				if (entry.getId().isEmpty()) entry.setId(mintId());
				store.save(entry);
			}
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.error(BattleNext_QuickBattleEditor.class, "quick_battle.editor.commit.failed",
				BattleNext_Logger.fields().put("entries", entries).put("input_state", serializeState()).build(), exception);
			saveErrors.failure(strings.get("save_failed"), "tournament/quick_battle.csv / tournament/PvP_matches.csv", exception);
			setStatus(saveErrors.summary());
			return false;
		}
		setStatus(strings.get("saved"));
		return true;
	}

	@Override
	protected void captureDraftInputs() {
		viewState = display.captureState();
	}

	private boolean captureFields() {

		List<BattleNext_QuickBattleModel> parsed = new ArrayList<>();
		for (int index = 0; index < display.getRows().size(); index++) {
			Row row = display.getRows().get(index);
			BattleNext_QuickBattleModel model = row.getModel().copy();
			if (row.getRawField() != null) {
				model.promoteRaw(row.getRawField().getText());
				parsed.add(model);
				continue;
			}
			String subject = strings.format("row", index + 1);
			for (Column column : List.of(Column.PLAYER, Column.ENEMY)) {
				if (row.getFleetId(column).isEmpty()) {
					saveErrors.field(subject, battleStrings.get(column == Column.PLAYER ? "player_fleet_id_label" : "enemy_fleet_id_label"),
							row.getFleetId(column), strings.get("error_required"));
				}
			}
			Integer map = saveErrors.integer(subject, battleStrings.get("map_number_label"), row.getField(Column.MAP).getText(),
					BattleNext_MissionBattleMap.RANDOM_MAP_ID, BattleNext_MissionBattleMap.LAST_MAP_ID);
			Float mapScale = saveErrors.number(subject, battleStrings.get("map_scale_label"), row.getField(Column.SCALE).getText(), true);
			if (map == null || mapScale == null) continue;
			model.setPlayer(row.getFleetId(Column.PLAYER));
			model.setEnemy(row.getFleetId(Column.ENEMY));
			model.setMap(map);
			model.setMapScale(mapScale);
			model.setEnabled(row.isEnabled());
			model.setRowType(model.isEnabled() ? BattleNext_QuickBattleModel.QuickBattleRowType.BATTLE : model.getRowType());
			parsed.add(model);
		}
		if (!saveErrors.isEmpty()) return false;
		entries.clear();
		entries.addAll(parsed);
		boolean changedShape = false;
		int target = 0;
		for (int index = 0; index < display.getRows().size(); index++) {
			Row row = display.getRows().get(index);
			BattleNext_QuickBattleModel next = parsed.get(target++);
			rowKeys.put(next, rowKeys.get(row.getModel()));
			rowKeys.remove(row.getModel());
			row.setModel(next);
			if (row.getRawField() != null && row.getModel().getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				changedShape = true;
			}
		}
		if (changedShape) {
			refreshContent();
		}
		return true;
	}

	@Override
	protected void restoreDraft() {
		load();
		viewState = null;
	}
}
