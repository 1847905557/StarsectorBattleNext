package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_TextField;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class BattleNext_LunaFleetSavePanel extends BaseCustomUIPanelPlugin {

	private static final float INSET = 10f;
	private final Runnable closeAction;
	private final BattleNext_FleetStore store;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private CustomPanelAPI panel;
	private CustomPanelAPI contentsPanel;
	private BattleNext_TextField name;
	private ButtonAPI saveButton;
	private BattleNext_FleetModel snapshot;
	private String commanderName = "";
	private String status = "";
	private boolean saved;
	private boolean closed = true;

	BattleNext_LunaFleetSavePanel(Runnable closeAction) {
		this(closeAction, new BattleNext_FleetStore());
	}

	BattleNext_LunaFleetSavePanel(Runnable closeAction, BattleNext_FleetStore store) {
		this.closeAction = closeAction;
		this.store = store;
	}

	void open(FleetMemberAPI selected, ShipVariantAPI variant, String error) {
		close();
		closed = false;
		saved = false;
		status = error;
		if (!error.isEmpty()) return;
		try {
			commanderName = Global.getSector().getPlayerPerson().getNameString();
			snapshot = BattleNext_CampaignFleetSnapshot.capture(Global.getSector().getPlayerFleet(),
					Global.getSector().getPlayerPerson(), selected, variant);
		} catch (Exception exception) {
			status = fleetStrings.get("player_save_snapshot_error") + ": " + exception;
			BattleNext_Logger.error(BattleNext_LunaFleetSavePanel.class, "fleet.player.snapshot.failed",
				BattleNext_Logger.fields().put("member", selected).put("variant", variant).build(), exception);
		}
	}

	void attach(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		BattleNext_UiInputRouter.activate(inputRouter);
		rebuildContents(snapshot == null ? "" : snapshot.getName());
	}

	private void rebuildContents(String displayName) {

		if (name != null) name.close();
		if (contentsPanel != null) panel.removeComponent(contentsPanel);
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		contentsPanel = panel.createCustomPanel(width, height, new ContentsPlugin());
		panel.addComponent(contentsPanel).inTL(0f, 0f);
		BattleNext_PageLayout.heading(new UIRect(INSET, INSET, width - INSET * 2f,
				BattleNext_PageLayout.HEADER_HEIGHT), contentsPanel, fleetStrings.get("player_save_action"));
		float top = INSET * 2f + BattleNext_PageLayout.HEADER_HEIGHT;
		float contentHeight = height - top - INSET * 2f - BattleNext_PageLayout.FOOTER_HEIGHT;
		TooltipMakerAPI content = contentsPanel.createUIElement(width - INSET * 2f, contentHeight, true);
		content.addPara(fleetStrings.get("name"), 0f);
		name = new BattleNext_TextField(inputRouter, contentsPanel, width - INSET * 3f,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, content);
		name.attach(content, contentsPanel);
		name.setMaxChars(-1);
		name.setText(displayName);
		if (snapshot != null) {
			content.addPara(fleetStrings.format("player_save_commander", commanderName), INSET);
			content.addPara(fleetStrings.format("player_save_members", snapshot.getMembers().size()), INSET);
		}
		if (!status.isEmpty()) {
			content.addPara(status, INSET);
		}
		contentsPanel.addUIElement(content).inTL(INSET, top);
		float buttonWidth = Math.min(220f, (width - INSET * 3f) * 0.5f);
		float footerY = height - INSET - BattleNext_PageLayout.FOOTER_HEIGHT;
		saveButton = BattleNext_PageLayout.fixedButton(contentsPanel,
				new UIRect(INSET, footerY, buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get(saved ? "saved" : "save"), "fleet_capture_save", CutStyle.ALL, true);
		saveButton.setEnabled(!closed && snapshot != null && !saved);
		BattleNext_PageLayout.fixedButton(contentsPanel,
				new UIRect(width - INSET - buttonWidth, footerY, buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("close"), "fleet_capture_close", CutStyle.ALL, false);
	}

	@Override
	public void buttonPressed(Object buttonId) {

		if (closed) return;
		if ("fleet_capture_close".equals(buttonId)) {
			closeAction.run();
			return;
		}
		if (!"fleet_capture_save".equals(buttonId) || saved || snapshot == null) return;
		String displayName = name.getText();
		if (displayName.trim().isEmpty()) {
			BattleNext_Logger.warn(BattleNext_LunaFleetSavePanel.class, "save.name.rejected",
				BattleNext_Logger.fields().put("display_name", displayName).put("criterion", "Nonblank trimmed name").build());
			status = fleetStrings.get("player_save_name_required");
			rebuildContents(displayName);
			return;
		}
		try {
			store.reload();
			snapshot.setName(displayName.trim());
			store.save(snapshot);
			saved = true;
			status = fleetStrings.format("player_save_saved", snapshot.getName(), snapshot.getSourcePath());
		} catch (Exception exception) {
			status = strings.get("save_failed") + ": " + exception;
			BattleNext_Logger.error(BattleNext_LunaFleetSavePanel.class, "fleet.player.save.failed",
				BattleNext_Logger.fields().put("fleet", snapshot).put("display_name", displayName).build(), exception);
		}
		rebuildContents(displayName);
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		if (closed) return;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			boolean escape = event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE;
			boolean outside = event.isMouseDownEvent() && !panel.getPosition().containsEvent(event);
			if (escape || outside) {
				event.consume();
				closeAction.run();
				return;
			}
		}
		inputRouter.handleGlobalInput(events);
		for (InputEventAPI event : events) {
			if (event.isConsumed() || !event.isKeyboardEvent()) continue;
			event.consume();
		}
	}

	void close() {
		if (name != null) name.close();
		inputRouter.clear();
		if (saveButton != null) saveButton.setEnabled(false);
		if (contentsPanel != null) panel.removeComponent(contentsPanel);
		contentsPanel = null;
		panel = null;
		name = null;
		saveButton = null;
		snapshot = null;
		commanderName = "";
		status = "";
		closed = true;
	}

	private class ContentsPlugin extends BaseCustomUIPanelPlugin {
		@Override
		public void buttonPressed(Object buttonId) {
			BattleNext_LunaFleetSavePanel.this.buttonPressed(buttonId);
		}
	}
}
