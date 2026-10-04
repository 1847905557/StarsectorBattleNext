package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIPanelManager;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.List;

public class BattleNext_LunaPinnedVariantPanel extends BaseCustomUIPanelPlugin {

	private static final float INSET = 10f;
	private static final Color BACKGROUND = Color.BLACK;
	private static final float BORDER_WIDTH = 2f;
	private final Runnable closeAction;
	private final BattleNext_PinnedVariantStore store;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
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
			status = variantStrings.get("pin_snapshot_error") + ": " + exception;
			BattleNext_Logger.error(BattleNext_LunaPinnedVariantPanel.class, "variant.pin.snapshot.failed",
				BattleNext_Logger.fields().put("variant", variant).build(), exception);
		}
	}

	void attach(CustomPanelAPI panel, float width, float height) {

		this.panel = panel;
		BattleNext_PageLayout.heading(new UIRect(INSET, INSET, width - INSET * 2f,
				BattleNext_PageLayout.HEADER_HEIGHT), panel, variantStrings.get("pin_current_action"));
		float bodyTop = INSET * 2f + BattleNext_PageLayout.HEADER_HEIGHT;
		float bodyHeight = height - bodyTop - INSET * 2f - BattleNext_PageLayout.FOOTER_HEIGHT;
		rightPanel = panel.createCustomPanel(width - INSET * 2f, bodyHeight, new FramePlugin());
		panel.addComponent(rightPanel).inTL(INSET, bodyTop);
		float buttonWidth = Math.min(220f, (width - INSET * 3f) * 0.5f);
		float footerY = height - INSET - BattleNext_PageLayout.FOOTER_HEIGHT;
		saveButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(INSET, footerY, buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				variantStrings.get("pin_add_action"), "pin_save", CutStyle.ALL, true);
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
		details.close();
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
		contents.addPara(variantStrings.get("pin_name"), 0f);
		BattleNext_TextField nameWidget = new BattleNext_TextField(inputRouter, metadataPanel, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, contents);
		nameWidget.attach(contents, metadataPanel);
		nameWidget.setText(displayName);
		name = nameWidget;
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
				details.setVariantJson(snapshot.toJson());
				details.attach(detailsPanel);
			} catch (Exception exception) {
				TooltipMakerAPI error = detailsPanel.createUIElement(width, detailsHeight, true);
				error.addPara(variantStrings.get("pin_snapshot_error") + ": " + exception, INSET);
				detailsPanel.addUIElement(error).inTL(0f, 0f);
				BattleNext_Logger.warn(BattleNext_LunaPinnedVariantPanel.class, "variant.pin.display.degraded",
				BattleNext_Logger.fields().put("snapshot", snapshot).put("saved", saved).build(), exception);
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
		if (displayName.isBlank()) {
			BattleNext_Logger.warn(BattleNext_LunaPinnedVariantPanel.class, "save.name.rejected",
				BattleNext_Logger.fields().put("display_name", displayName).put("criterion", "Nonblank name").build());
			status = variantStrings.get("pin_name_required");
			refreshContents(displayName);
			return;
		}
		try {
			store.reload();
			BattleNext_PinnedVariantModel pin = store.saveVariant(displayName, snapshot.build());
			saved = true;
			status = variantStrings.format("pin_saved", pin.getDisplayName());
			if (!store.getLoadErrors().isEmpty()) {
				status += "\n" + variantStrings.get("pin_load_warnings") + "\n" + String.join("\n", store.getLoadErrors());
			}
		} catch (Exception exception) {
			status = strings.get("save_failed") + ": " + exception;
			BattleNext_Logger.error(BattleNext_LunaPinnedVariantPanel.class, "variant.pin.save.failed",
				BattleNext_Logger.fields().put("snapshot", snapshot).put("display_name", displayName).build(), exception);
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

		details.close();
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
