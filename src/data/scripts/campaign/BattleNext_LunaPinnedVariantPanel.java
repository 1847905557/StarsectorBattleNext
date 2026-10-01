package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.battlesnext.util.ui.UIPanelManager;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.awt.Color;

public class BattleNext_LunaPinnedVariantPanel extends BaseCustomUIPanelPlugin {

	private static final float INSET = 10f;
	private static final Color BACKGROUND = Color.BLACK;
	private static final float BORDER_WIDTH = 2f;
	private final Runnable closeAction;
	private final BattleNext_PinnedVariantStore store;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");
	private final BattleNext_VariantDetails details = new BattleNext_VariantDetails(
			() -> refreshContents(this.name.getText()), () -> !this.closed && inputRouter.isEmpty());
	private BattleNext_VariantBuilder snapshot;
	private CustomPanelAPI panel;
	private CustomPanelAPI rightPanel;
	private CustomPanelAPI contentsPanel;
	private UIPanelManager contentsManager;
	private TooltipMakerAPI contents;
	private TextFieldAPI name;
	private ButtonAPI saveButton;
	private String status = "";
	private boolean saved;
	private boolean closed = true;

	BattleNext_LunaPinnedVariantPanel(Runnable closeAction) {
		this(closeAction, new BattleNext_PinnedVariantStore());
	}

	BattleNext_LunaPinnedVariantPanel(Runnable closeAction, BattleNext_PinnedVariantStore store) {
		this.closeAction = closeAction;
		this.store = store;
	}

	void open(ShipVariantAPI variant, String error) {
		close();
		closed = false;
		saved = false;
		status = error;
		if (!error.isEmpty()) return;
		try {
			snapshot = BattleNext_VariantBuilder.fromVariant(variant);
			snapshot.build();
		} catch (Exception exception) {
			snapshot = null;
			status = strings.get("pin_snapshot_error") + ": " + exception;
			Global.getLogger(getClass()).warn("Unable to snapshot campaign refit variant", exception);
		}
	}

	void attach(CustomPanelAPI panel, float width, float height) {

		this.panel = panel;
		BattleNext_PageLayout.heading(new UIRect(INSET, INSET, width - INSET * 2f,
				BattleNext_PageLayout.HEADER_HEIGHT), panel, strings.get("pin_variant"));
		float bodyTop = INSET * 2f + BattleNext_PageLayout.HEADER_HEIGHT;
		float bodyHeight = height - bodyTop - INSET * 2f - BattleNext_PageLayout.FOOTER_HEIGHT;
		rightPanel = panel.createCustomPanel(width - INSET * 2f, bodyHeight, new FramePlugin());
		panel.addComponent(rightPanel).inTL(INSET, bodyTop);
		float buttonWidth = Math.min(180f, (width - INSET * 3f) * 0.5f);
		float footerY = height - INSET - BattleNext_PageLayout.FOOTER_HEIGHT;
		saveButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(INSET, footerY, buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("pin_add"), "pin_save", CutStyle.ALL, true);
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(width - INSET - buttonWidth, footerY, buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("close"), "pin_close", CutStyle.ALL, false);
		String defaultName = "";
		if (snapshot != null) {
			ShipVariantAPI variant = snapshot.build();
			defaultName = variant.getHullSpec().getHullName() + " - " + variant.getDisplayName();
		}
		refreshContents(defaultName);
	}

	private void refreshContents(String displayName) {

		inputRouter.clear();
		float offset = contents == null || contents.getExternalScroller() == null
				? 0f : contents.getExternalScroller().getYOffset();
		if (contentsManager != null) {
			contentsManager.clearAllComponents();
			rightPanel.removeComponent(contentsManager.getPanel());
		}
		float width = rightPanel.getPosition().getWidth() - INSET * 2f;
		float height = rightPanel.getPosition().getHeight() - INSET * 2f;
		contentsPanel = rightPanel.createCustomPanel(width, height, new BaseCustomUIPanelPlugin());
		contentsManager = new UIPanelManager(contentsPanel, null);
		rightPanel.addComponent(contentsPanel).inTL(INSET, INSET);
		float metadataLimit = Math.max(1f, (float) Math.floor(height / 3f));
		CustomPanelAPI metadataPanel = contentsPanel.createCustomPanel(width, metadataLimit, new BaseCustomUIPanelPlugin());
		contents = metadataPanel.createUIElement(width + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, metadataLimit, true);
		BattleNext_UiTheme.configure(contents);
		contents.setTextWidthOverride(width);
		contents.addPara(strings.get("pin_name"), 0f);
		name = BattleNext_TextField.addTo(inputRouter, contents, metadataPanel, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, displayName, contents);
		name.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		if (!status.isEmpty()) {
			contents.addPara(status, BattleNext_UiTheme.TEXT, INSET);
		}
		float metadataHeight = Math.min(metadataLimit, contents.getHeightSoFar());
		metadataPanel.getPosition().setSize(width, metadataHeight);
		contents.getPosition().setSize(width + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, metadataHeight);
		metadataPanel.addUIElement(contents).inTL(-BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET, 0f);
		contentsManager.addComponent(metadataPanel);
		metadataPanel.getPosition().inTL(0f, 0f);
		float detailsHeight = Math.max(1f, height - metadataHeight - BattleNext_PageLayout.MANAGEMENT_CONTENT_INSET);
		CustomPanelAPI detailsPanel = contentsPanel.createCustomPanel(width, detailsHeight, new BaseCustomUIPanelPlugin());
		contentsManager.addComponent(detailsPanel);
		detailsPanel.getPosition().inTL(0f, metadataHeight + BattleNext_PageLayout.MANAGEMENT_CONTENT_INSET);
		if (snapshot != null) {
			try {
				details.addTo(detailsPanel, width, detailsHeight, snapshot.toJson());
			} catch (Exception exception) {
				TooltipMakerAPI error = detailsPanel.createUIElement(width, detailsHeight, true);
				error.addPara(strings.get("pin_snapshot_error") + ": " + exception, INSET);
				detailsPanel.addUIElement(error).inTL(0f, 0f);
				Global.getLogger(getClass()).warn("Unable to display pinned variant snapshot", exception);
			}
		}
		if (contents.getExternalScroller() != null) {
			contents.getExternalScroller().setYOffset(Math.min(Math.max(0f, contents.getHeightSoFar() - metadataHeight), offset));
		}
		saveButton.setEnabled(!closed && snapshot != null && !saved);
	}

	@Override
	public void buttonPressed(Object buttonId) {

		if (closed) return;
		if ("pin_close".equals(buttonId)) {
			closeAction.run();
			return;
		}
		if (!"pin_save".equals(buttonId) || saved || snapshot == null) return;
		String displayName = name.getText();
		if (displayName.trim().isEmpty()) {
			status = strings.get("pin_name_required");
			refreshContents(displayName);
			return;
		}
		try {
			store.reload();
			BattleNext_PinnedVariantModel pin = store.saveVariant(displayName, snapshot.build());
			saved = true;
			status = strings.format("pin_saved_name", pin.getDisplayName());
			if (!store.getLoadErrors().isEmpty()) {
				status += "\n" + strings.get("pin_load_warnings") + "\n" + String.join("\n", store.getLoadErrors());
			}
		} catch (Exception exception) {
			status = strings.get("save_failed") + ": " + exception;
			Global.getLogger(getClass()).warn("Unable to save campaign refit collection", exception);
		}
		refreshContents(displayName);
	}

	@Override
	public void processInput(List<InputEventAPI> events) {

		if (closed) return;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			boolean escape = event.isKeyboardEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE;
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

		inputRouter.clear();
		if (saveButton != null) {
			saveButton.setEnabled(false);
		}
		if (rightPanel != null && contentsManager != null) {
			contentsManager.clearAllComponents();
			rightPanel.removeComponent(contentsManager.getPanel());
		}
		if (panel != null && rightPanel != null) {
			panel.removeComponent(rightPanel);
		}
		details.clear();
		rightPanel = null;
		contentsPanel = null;
		contentsManager = null;
		snapshot = null;
		panel = null;
		contents = null;
		name = null;
		saveButton = null;
		status = "";
		closed = true;
	}

	private static class FramePlugin extends BaseCustomUIPanelPlugin {
		private PositionAPI position;

		@Override
		public void positionChanged(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.fillRect(position, BACKGROUND, alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()}, BattleNext_UiTheme.PANEL, alphaMult, BORDER_WIDTH);
		}
	}

}
