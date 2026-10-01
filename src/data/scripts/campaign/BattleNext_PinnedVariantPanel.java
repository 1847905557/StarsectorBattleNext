package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class BattleNext_PinnedVariantPanel extends BaseCustomUIPanelPlugin {

	private static final float INSET = 10f;
	private static final float PREVIEW_HEIGHT = 180f;
	private final Runnable closeAction;
	private final BattleNext_PinnedVariantStore store;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");
	private BattleNext_VariantBuilder snapshot;
	private CustomPanelAPI panel;
	private TooltipMakerAPI contents;
	private TextFieldAPI name;
	private ButtonAPI saveButton;
	private String status = "";
	private boolean saved;
	private boolean closed = true;

	BattleNext_PinnedVariantPanel(Runnable closeAction) {
		this(closeAction, new BattleNext_PinnedVariantStore());
	}

	BattleNext_PinnedVariantPanel(Runnable closeAction, BattleNext_PinnedVariantStore store) {
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
		if (contents != null) {
			panel.removeComponent(contents);
		}
		float width = panel.getPosition().getWidth() - INSET * 2f;
		float height = panel.getPosition().getHeight() - INSET * 3f - BattleNext_PageLayout.FOOTER_HEIGHT;
		contents = panel.createUIElement(width, height, true);
		BattleNext_UiTheme.configure(contents);
		contents.addTitle(strings.get("pin_variant"));
		if (snapshot != null) {
			try {
				ShipVariantAPI variant = snapshot.build();
				String path = variant.getHullSpec().getSpriteName();
				SpriteAPI sprite = Global.getSettings().getSprite(path);
				float scale = BattleNext_PageLayout.spriteScaleToFit(sprite.getWidth(), sprite.getHeight(), width, PREVIEW_HEIGHT);
				contents.addImage(path, sprite.getWidth() * scale, sprite.getHeight() * scale, INSET);
				contents.addPara(variant.getHullSpec().getHullName(), BattleNext_UiTheme.TEXT, INSET);
				contents.addPara(variant.getDisplayName(), BattleNext_UiTheme.TEXT, INSET);
			} catch (Exception exception) {
				status = strings.get("pin_snapshot_error") + ": " + exception;
				snapshot = null;
				Global.getLogger(getClass()).warn("Unable to display pinned variant snapshot", exception);
			}
		}
		contents.addPara(strings.get("pin_name"), INSET);
		name = BattleNext_TextField.addTo(inputRouter, contents, panel, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, displayName, contents);
		name.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		contents.addPara(status, BattleNext_UiTheme.TEXT, INSET);
		panel.addUIElement(contents).inTL(INSET, INSET);
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
			status = strings.format("pin_saved", pin.getDisplayName(), pin.getPinId());
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
		if (panel != null && contents != null) {
			panel.removeComponent(contents);
		}
		snapshot = null;
		panel = null;
		contents = null;
		name = null;
		saveButton = null;
		status = "";
		closed = true;
	}
}
