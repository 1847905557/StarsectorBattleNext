package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.BattleNext_UiInputRouter;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_BattleSourceField extends BattleNext_PanelBasedWidget {

	private static final float LABEL_MAX_WIDTH = 175f;
	private static final float LABEL_WIDTH_SHARE = 0.25f;
	private static final float BUTTON_MAX_WIDTH = 420f;
	private static final float BUTTON_WIDTH_SHARE = 0.45f;
	private static final float CONTROL_GAP = 12f;
	private static final float LABEL_TOP_INSET = 5f;

	private final CustomPanelAPI overlayHost;
	private final BattleNext_DropdownField.DropdownSelectionListener listener;
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private BattleNext_DropdownField dropdown;
	private LabelAPI label;

	public BattleNext_BattleSourceField(BattleNext_UiInputRouter inputRouter, CustomPanelAPI overlayHost,
			BattleNext_DropdownField.DropdownSelectionListener listener) {
		super(inputRouter);
		this.overlayHost = overlayHost;
		this.listener = listener;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {

		float areaWidth = panel.getPosition().getWidth();
		float areaHeight = panel.getPosition().getHeight();
		CustomPanelAPI contentPanel = ownPanel(panel, panel.createCustomPanel(areaWidth, areaHeight, panel.getPlugin()));
		panel.addComponent(contentPanel).inTL(0f, 0f);
		float labelWidth = Math.min(LABEL_MAX_WIDTH, areaWidth * LABEL_WIDTH_SHARE);
		float buttonWidth = Math.min(BUTTON_MAX_WIDTH, areaWidth * BUTTON_WIDTH_SHARE);
		float groupX = (areaWidth - labelWidth - CONTROL_GAP - buttonWidth) * 0.5f;
		UIRect labelRect = new UIRect(groupX, 0f, labelWidth, areaHeight);
		TooltipMakerAPI label = labelRect.beginTooltip(contentPanel, false);
		label.setParaOrbitronLarge();
		this.label = label.addPara(battleStrings.get("source_label"), 0f);
		this.label.getPosition().inTL(0f, LABEL_TOP_INSET);
		label.setHeightSoFar(areaHeight);
		labelRect.addTooltip();
		CustomPanelAPI buttonPanel = new UIRect(groupX + labelWidth + CONTROL_GAP, 0f,
				buttonWidth, areaHeight).createSubPanel(contentPanel, contentPanel.getPlugin()).getPanel();
		dropdown = new BattleNext_DropdownField(inputRouter, overlayHost, listener);
		dropdown.attach(buttonPanel);
	}

	public void configure(BattleNext_TournamentStore store, String selectedId) {
		List<BattleNext_DropdownField.DropdownChoice> options = new ArrayList<>();
		options.add(new BattleNext_DropdownField.DropdownChoice("", battleStrings.get("quick_battle")));
		for (BattleNext_TournamentModel tournament : store.getAll()) {
			options.add(new BattleNext_DropdownField.DropdownChoice(tournament.getId(), tournament.getName()));
		}
		dropdown.setOptions(options);
		dropdown.setValue(selectedId);
		dropdown.setEnabled(store.isLoaded());
	}

	public void configureInstances(String sourceId, BattleNext_TournamentModel configuration, BattleNext_StartedTournamentStore store,
			BattleNext_StartedTournament.ExecutionKind kind, String selectedId) {
		label.setText(battleStrings.get("tournament_instance"));
		List<BattleNext_DropdownField.DropdownChoice> options = new ArrayList<>();
		options.add(new BattleNext_DropdownField.DropdownChoice("", battleStrings.get("tournament_base_template")));
		for (BattleNext_StartedTournament started : store.getForSource(sourceId, kind)) {
			options.add(new BattleNext_DropdownField.DropdownChoice(started.getId(), started.getName(configuration)));
		}
		dropdown.setOptions(options);
		dropdown.setValue(selectedId);
		dropdown.setEnabled(store.isLoaded());
	}

	@Override
	protected void clearOverlays() {
		if (dropdown != null) {
			dropdown.closeOverlays();
		}
	}

	@Override
	protected void clearContent() {
		if (dropdown != null) {
			dropdown.close();
			dropdown = null;
		}
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		return dropdown != null && dropdown.handleGlobalInput(events);
	}
}
