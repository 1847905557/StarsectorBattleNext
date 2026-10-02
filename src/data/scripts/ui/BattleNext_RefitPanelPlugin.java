package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.provider.BattleNext_RefitDModSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_RefitSModSourceProvider;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_RefitPanelPlugin extends BaseCustomUIPanelPlugin
		implements BattleNext_ConfirmationDialog.ConfirmationDialogHost {

	private static final float BORDER_INSET = 8f;
	private static final float SIDE_SHARE = 0.09f;
	private static final float LEFT_SHARE = 0.38f;
	private static final float MIDDLE_SHARE = 0.20f;
	private static final float RIGHT_SHARE = 0.18f;
	private static final float GAP_SHARE = 0.03f;
	private static final float CONTENT_TOP_SHARE = 0.09f;
	private static final float CONTENT_HEIGHT_SHARE = 0.80f;
	private static final float FRAME_INSET = 8f;
	private static final float FRAME_BORDER = 2f;
	private static final float ROW_HEIGHT = 44f;
	private static final float ROW_GAP = 4f;
	private static final float ICON_SIZE = 36f;
	private static final float PICKER_GAP = 36f;
	private static final float FOOTER_BUTTON_WIDTH = 160f;
	private static final float FOOTER_BUTTON_GAP = 10f;
	private static final Color FRAME_BACKGROUND = new Color(0, 0, 0, 150);

	private final Runnable closeAction;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");
	private final BattleNext_ConfirmationDialog confirmation = new BattleNext_ConfirmationDialog(this, inputRouter);

	private UIBorderObject border;
	private CustomPanelAPI panel;
	private CustomPanelAPI leftPanel;
	private CustomPanelAPI middlePanel;
	private CustomPanelAPI rightPanel;
	private CustomPanelAPI leftContent;
	private TooltipMakerAPI middleContent;
	private BattleNext_OptionPicker sModPicker;
	private BattleNext_OptionPicker dModPicker;
	private ButtonAPI saveButton;
	private LabelAPI statusLabel;
	private FleetMemberAPI member;
	private Object refitScreen;
	private ShipVariantAPI draft;
	private Set<String> savedSMods = Set.of();
	private Set<String> savedDMods = Set.of();
	private String selectedMod = "";
	private String saveError = "";
	private boolean confirmationWasActive;
	private float borderX;
	private float borderY;
	private int backdropTexture;
	private float backdropU;
	private float backdropV;

	public BattleNext_RefitPanelPlugin(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void init(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		BattleNext_UiInputRouter.activate(inputRouter);
		border = new UIBorderObject(false, true);
		borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - BORDER_INSET;
		borderY = (ShaderCore.getScreenHeight() - height) * 0.5f - BORDER_INSET;
		border.setSize(width + BORDER_INSET * 2f, height + BORDER_INSET * 2f);
		confirmation.attach(panel);

		float top = height * CONTENT_TOP_SHARE;
		float contentHeight = height * CONTENT_HEIGHT_SHARE;
		float gap = width * GAP_SHARE;
		float leftWidth = width * LEFT_SHARE;
		float middleWidth = width * MIDDLE_SHARE;
		float rightWidth = width * RIGHT_SHARE;
		float leftX = width * SIDE_SHARE;
		float middleX = leftX + leftWidth + gap;
		float rightX = middleX + middleWidth + gap;
		leftPanel = panel.createCustomPanel(leftWidth, contentHeight, new FramePlugin());
		middlePanel = panel.createCustomPanel(middleWidth, contentHeight, new FramePlugin());
		rightPanel = panel.createCustomPanel(rightWidth, contentHeight, new BaseCustomUIPanelPlugin());
		panel.addComponent(leftPanel).inTL(leftX, top);
		panel.addComponent(middlePanel).inTL(middleX, top);
		panel.addComponent(rightPanel).inTL(rightX, top);

		buildPickers(rightWidth);

		float closeX = width - BORDER_INSET - FOOTER_BUTTON_WIDTH;
		float footerY = height - BORDER_INSET - BattleNext_PageLayout.FOOTER_HEIGHT;
		saveButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(closeX - FOOTER_BUTTON_GAP - FOOTER_BUTTON_WIDTH,
						footerY, FOOTER_BUTTON_WIDTH, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("save"), "save", CutStyle.BOTTOM, false);
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(closeX, footerY, FOOTER_BUTTON_WIDTH, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("close"), "close", CutStyle.BOTTOM, false);
		TooltipMakerAPI status = panel.createUIElement(width * 0.5f, BattleNext_PageLayout.FOOTER_HEIGHT, false);
		statusLabel = status.addPara("", 0f);
		panel.addUIElement(status).inTL(BORDER_INSET, footerY);
		saveButton.setEnabled(false);
	}

	private void buildPickers(float width) {
		sModPicker = new BattleNext_OptionPicker(inputRouter, panel, width, 30f, strings.get("refit_smods"), 0f,
				new BattleNext_RefitSModSourceProvider(() -> draft),
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE,
				() -> draft == null ? List.of() : new ArrayList<>(sModsOf(draft)), this::selectSMods);
		dModPicker = new BattleNext_OptionPicker(inputRouter, panel, width, 30f, strings.get("refit_dmods"), 0f,
				new BattleNext_RefitDModSourceProvider(() -> draft),
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE,
				() -> draft == null ? List.of() : new ArrayList<>(dModsOf(draft)), this::selectDMods);
		TooltipMakerAPI sModHost = rightPanel.createUIElement(width, 30f, false);
		sModPicker.attach(sModHost, rightPanel);
		rightPanel.addUIElement(sModHost).inTL(0f, 0f);
		TooltipMakerAPI dModHost = rightPanel.createUIElement(width, 30f, false);
		dModPicker.attach(dModHost, rightPanel);
		rightPanel.addUIElement(dModHost).inTL(0f, PICKER_GAP);
	}

	public void open(FleetMemberAPI member, Object refitScreen) {
		closePickers();
		this.member = member;
		this.refitScreen = refitScreen;
		selectedMod = "";
		saveError = "";
		statusLabel.setText("");
		try {
			ShipVariantAPI current = currentVariant();
			draft = current == null ? null : current.clone();
		} catch (Throwable exception) {
			draft = null;
			saveError = exception.getMessage();
			statusLabel.setText(strings.get("save_failed") + ": " + saveError);
		}
		savedSMods = draft == null ? Set.of() : sModsOf(draft);
		savedDMods = draft == null ? Set.of() : dModsOf(draft);
		confirmationWasActive = false;
		updateControls();
		rebuildShip();
		rebuildMods();
	}

	public void close() {
		closePickers();
		sModPicker.setEnabled(false);
		dModPicker.setEnabled(false);
		if (confirmation.isActive()) confirmation.handle("confirm_cancel");
		inputRouter.clear();
		member = null;
		refitScreen = null;
		draft = null;
	}

	public void requestClose() {
		closePickers();
		if (confirmation.isActive()) return;
		if (hasUnsavedChanges()) {
			confirmation.request(closeAction);
			confirmationWasActive = true;
			updateControls();
			return;
		}
		closeAction.run();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (sModPicker != null && sModPicker.handleGlobalInput(events)) return true;
		if (dModPicker != null && dModPicker.handleGlobalInput(events)) return true;
		if (inputRouter.handleGlobalInput(events)) return true;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (!event.isKeyboardEvent() || !event.isKeyDownEvent()
					|| event.getEventValue() != Keyboard.KEY_ESCAPE) continue;
			if (confirmation.isActive()) confirmation.handle("confirm_cancel");
			else requestClose();
			confirmationWasActive = confirmation.isActive();
			updateControls();
			event.consume();
			return true;
		}
		return false;
	}

	@Override
	public boolean saveCurrentPage() {
		if (draft == null || refitScreen == null || member == null) {
			saveError = strings.get("refit_no_member");
			statusLabel.setText(saveError);
			return false;
		}
		ShipVariantAPI original = null;
		ShipVariantAPI originalMember = null;
		Object display = null;
		boolean committed = false;
		try {
			originalMember = member.getVariant().clone();
			display = BattleNext_TitleEntry.invokeNoArg(refitScreen, "getShipDisplay");
			ShipVariantAPI current = (ShipVariantAPI) BattleNext_TitleEntry.invokeNoArg(display, "getCurrentVariant");
			original = current.clone();
			if (!savedDMods.equals(dModsOf(draft))) applyDMods(current, dModsOf(draft));
			if (!savedSMods.equals(sModsOf(draft))) applySMods(current, sModsOf(draft));
			MethodHandles.publicLookup().findVirtual(display.getClass(), "syncWithVariant",
					MethodType.methodType(void.class, current.getClass())).invoke(display, current);
			MethodHandles.publicLookup().findVirtual(refitScreen.getClass(), "syncWithCurrentVariant",
					MethodType.methodType(void.class)).invoke(refitScreen);
			MethodHandles.publicLookup().findVirtual(refitScreen.getClass(), "saveCurrentVariant",
					MethodType.methodType(void.class)).invoke(refitScreen);
			committed = true;
			ShipVariantAPI saved = currentVariant();
			draft = saved.clone();
			savedSMods = sModsOf(draft);
			savedDMods = dModsOf(draft);
			saveError = "";
			statusLabel.setText(strings.get("saved"));
			rebuildShip();
			rebuildMods();
			return true;
		} catch (Throwable exception) {
			if (committed) {
				savedSMods = sModsOf(draft);
				savedDMods = dModsOf(draft);
				saveError = "";
				Global.getLogger(BattleNext_RefitPanelPlugin.class).warn("Refit panel refresh failed after save", exception);
				statusLabel.setText(strings.get("saved"));
				return true;
			}
			if (original != null && display != null) {
				try {
					MethodHandles.publicLookup().findVirtual(display.getClass(), "syncWithVariant",
							MethodType.methodType(void.class, original.getClass())).invoke(display, original);
					MethodHandles.publicLookup().findVirtual(refitScreen.getClass(), "syncWithCurrentVariant",
							MethodType.methodType(void.class)).invoke(refitScreen);
				} catch (Throwable rollback) {
					exception.addSuppressed(rollback);
				}
			}
			if (originalMember != null) {
				try {
					member.setVariant(originalMember, false, true);
				} catch (Throwable rollback) {
					exception.addSuppressed(rollback);
				}
			}
			saveError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			statusLabel.setText(strings.get("save_failed") + ": " + saveError);
			Global.getLogger(BattleNext_RefitPanelPlugin.class).warn("Mission refit save failed", exception);
			return false;
		}
	}

	@Override
	public String getSaveError() {
		return saveError;
	}

	@Override
	public void discardCurrentPage() {
		try {
			ShipVariantAPI current = currentVariant();
			draft = current == null ? null : current.clone();
		} catch (Throwable exception) {
			Global.getLogger(BattleNext_RefitPanelPlugin.class).warn("Mission refit discard failed", exception);
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (confirmation.isActive()) return;
		if ("save".equals(buttonId)) saveCurrentPage();
		if ("close".equals(buttonId)) requestClose();
	}

	@Override
	public void positionChanged(PositionAPI position) {
	}

	@Override
	public void advance(float amount) {
		if (confirmationWasActive == confirmation.isActive()) return;
		confirmationWasActive = confirmation.isActive();
		updateControls();
	}

	@Override
	public void renderBelow(float alphaMult) {
		if (panel == null) return;
		if (backdropTexture != 0) {
			BattleNext_GeometryRenderer.texturedFullscreen(backdropTexture, backdropU, backdropV,
					ShaderCore.getScreenScaleWidth(), ShaderCore.getScreenScaleHeight());
		}
		border.setAlpha(alphaMult);
		border.render(borderX, borderY);
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			event.consume();
		}
	}

	public void setBackdrop(int texture, float u, float v) {
		backdropTexture = texture;
		backdropU = u;
		backdropV = v;
	}

	private ShipVariantAPI currentVariant() throws Throwable {
		if (refitScreen == null) return null;
		Object display = BattleNext_TitleEntry.invokeNoArg(refitScreen, "getShipDisplay");
		return (ShipVariantAPI) BattleNext_TitleEntry.invokeNoArg(display, "getCurrentVariant");
	}

	private boolean hasUnsavedChanges() {
		return draft != null && (!savedSMods.equals(sModsOf(draft)) || !savedDMods.equals(dModsOf(draft)));
	}

	private void closePickers() {
		inputRouter.releaseFocus();
		if (sModPicker != null) sModPicker.closeOverlays();
		if (dModPicker != null) dModPicker.closeOverlays();
	}

	private void updateControls() {
		if (saveButton == null) return;
		boolean enabled = member != null && draft != null && !confirmation.isActive();
		saveButton.setEnabled(enabled);
		sModPicker.setEnabled(enabled);
		dModPicker.setEnabled(enabled);
	}

	private void selectSMods(List<String> ids) {
		if (draft == null) return;
		Set<String> selected = new LinkedHashSet<>(ids);
		if (selected.equals(sModsOf(draft))) return;
		applySMods(draft, selected);
		rebuildMods();
		statusLabel.setText("");
	}

	private void selectDMods(List<String> ids) {
		if (draft == null) return;
		Set<String> selected = new LinkedHashSet<>(ids);
		if (selected.equals(dModsOf(draft))) return;
		ShipVariantAPI previous = draft.clone();
		try {
			applyDMods(draft, selected);
			rebuildShip();
			rebuildMods();
			statusLabel.setText("");
		} catch (RuntimeException exception) {
			draft = previous;
			statusLabel.setText(strings.get("save_failed") + ": " + exception.getMessage());
		}
	}

	static void applySMods(ShipVariantAPI variant, Set<String> selected) {
		Set<String> existing = sModsOf(variant);
		for (String id : existing) {
			if (selected.contains(id)) continue;
			if (variant.getHullSpec().isBuiltInMod(id)) {
				variant.getSModdedBuiltIns().remove(id);
				variant.getSMods().remove(id);
			} else {
				variant.getSMods().remove(id);
				if (variant.getPermaMods().contains(id)) {
					variant.removePermaMod(id);
					variant.addMod(id);
				}
			}
		}
		for (String id : selected) {
			if (existing.contains(id)) continue;
			if (variant.getHullSpec().isBuiltInMod(id)) variant.getSModdedBuiltIns().add(id);
			else variant.addPermaMod(id, true);
		}
	}

	static void applyDMods(ShipVariantAPI variant, Set<String> selected) {
		Set<String> existing = dModsOf(variant);
		for (String id : existing) {
			if (selected.contains(id)) continue;
			DModManager.removeDMod(variant, id);
			if (variant.getHullSpec().isBuiltInMod(id)) variant.addSuppressedMod(id);
		}
		for (String id : selected) {
			if (existing.contains(id)) continue;
			variant.removeSuppressedMod(id);
			variant.addPermaMod(id, false);
		}
		ShipHullSpecAPI hull = variant.getHullSpec();
		if (hull.isDefaultDHull() && hull.getDParentHull() != null
				&& hull.getDParentHull().isDHull()) {
			variant.setHullSpecAPI(hull.getDParentHull());
		} else if (variant.hasDMods() && !hull.isDHull()) {
			DModManager.setDHull(variant);
		} else if (!variant.hasDMods() && hull.isDefaultDHull() && hull.getDParentHull() != null) {
			variant.setHullSpecAPI(hull.getDParentHull());
		}
	}

	static Set<String> sModsOf(ShipVariantAPI variant) {
		Set<String> ids = new LinkedHashSet<>(variant.getSMods());
		ids.addAll(variant.getSModdedBuiltIns());
		return ids;
	}

	static Set<String> dModsOf(ShipVariantAPI variant) {
		Set<String> ids = new LinkedHashSet<>();
		for (String id : variant.getHullMods()) {
			HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
			if (spec != null && spec.hasTag(Tags.HULLMOD_DMOD)) ids.add(id);
		}
		return ids;
	}

	private void rebuildShip() {
		if (leftContent != null) leftPanel.removeComponent(leftContent);
		float width = leftPanel.getPosition().getWidth();
		float height = leftPanel.getPosition().getHeight();
		leftContent = leftPanel.createCustomPanel(width - FRAME_INSET * 2f,
				height - FRAME_INSET * 2f, new BaseCustomUIPanelPlugin());
		leftPanel.addComponent(leftContent).inTL(FRAME_INSET, FRAME_INSET);
		if (draft == null || draft.getHullSpec() == null) return;
		ShipHullSpecAPI hull = draft.getHullSpec();
		TooltipMakerAPI title = leftContent.createUIElement(width - FRAME_INSET * 2f, 42f, false);
		title.addSectionHeading(title.shortenString(hull.getHullName(), width - FRAME_INSET * 3f),
				BattleNext_UiTheme.TEXT, BattleNext_UiTheme.PANEL, Alignment.MID, 0f);
		leftContent.addUIElement(title).inTL(0f, 0f);
		String path = hull.getSpriteName();
		if (path == null || path.isBlank()) return;
		try {
			SpriteAPI sprite = Global.getSettings().getSprite(path);
			float availableWidth = width - FRAME_INSET * 4f;
			float availableHeight = height - 70f;
			float scale = BattleNext_PageLayout.spriteScaleToFit(
					sprite.getWidth(), sprite.getHeight(), availableWidth, availableHeight);
			float imageWidth = sprite.getWidth() * scale;
			float imageHeight = sprite.getHeight() * scale;
			TooltipMakerAPI image = leftContent.createUIElement(imageWidth, imageHeight, false);
			image.addImage(path, imageWidth, imageHeight, 0f);
			leftContent.addUIElement(image).inTL((availableWidth - imageWidth) * 0.5f + FRAME_INSET,
					(availableHeight - imageHeight) * 0.5f + 48f);
		} catch (RuntimeException exception) {
			Global.getLogger(BattleNext_RefitPanelPlugin.class).warn("Refit hull sprite unavailable: " + path, exception);
		}
	}

	private void rebuildMods() {
		if (middleContent != null) middlePanel.removeComponent(middleContent);
		float width = middlePanel.getPosition().getWidth() - FRAME_INSET * 2f;
		float height = middlePanel.getPosition().getHeight() - FRAME_INSET * 2f;
		middleContent = middlePanel.createUIElement(width, height, true);
		if (draft != null) {
			List<String> ids = new ArrayList<>(draft.getHullMods());
			ids.sort(Comparator.comparing(id -> Global.getSettings().getHullModSpec(id).getDisplayName()));
			for (String id : ids) addModRow(id, width);
		}
		middlePanel.addUIElement(middleContent).inTL(FRAME_INSET, FRAME_INSET);
	}

	private void addModRow(String id, float width) {
		HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
		if (spec == null) return;
		ModRowPlugin plugin = new ModRowPlugin(id);
		CustomPanelAPI row = middlePanel.createCustomPanel(width, ROW_HEIGHT, plugin);
		TooltipMakerAPI icon = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
		String path = spec.getSpriteName();
		boolean renderedIcon = false;
		if (path != null && !path.isBlank()) {
			try {
				Global.getSettings().getSprite(path);
				icon.addImage(path, ICON_SIZE, ICON_SIZE, 0f);
				renderedIcon = true;
			} catch (RuntimeException exception) {
				Global.getLogger(BattleNext_RefitPanelPlugin.class).warn("Refit hullmod icon unavailable: " + path, exception);
			}
		}
		if (renderedIcon) {
			row.addUIElement(icon).inTL(4f, 4f);
		} else {
			plugin.missingIcon = true;
		}
		TooltipMakerAPI name = row.createUIElement(width - ICON_SIZE - 12f, ROW_HEIGHT, false);
		name.addPara(name.shortenString(spec.getDisplayName(), width - ICON_SIZE - 18f), 3f);
		String state = spec.hasTag(Tags.HULLMOD_DMOD) ? strings.get("refit_dmod")
				: sModsOf(draft).contains(id) ? strings.get("refit_smod")
				: draft.getHullSpec().isBuiltInMod(id) ? strings.get("read_only") : "";
		if (!state.isEmpty()) name.addPara(state, 0f);
		row.addUIElement(name).inTL(ICON_SIZE + 8f, 0f);
		middleContent.addCustom(row, ROW_GAP);
		middleContent.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return 360f;
			}
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addTitle(spec.getDisplayName());
				BattleNext_PageLayout.addMutedIdLine(tooltip, id, 4f);
				String description = spec.getDescription(draft.getHullSize());
				if (description != null && !description.isBlank()) {
					tooltip.addPara(description, 8f);
				}
			}
		}, TooltipMakerAPI.TooltipLocation.RIGHT);
	}

	private class ModRowPlugin extends BaseCustomUIPanelPlugin {
		private final String id;
		private PositionAPI position;
		private boolean hovered;
		private boolean missingIcon;
		private ModRowPlugin(String id) {
			this.id = id;
		}
		@Override
		public void positionChanged(PositionAPI position) {
			this.position = position;
		}
		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.fillRect(position,
					id.equals(selectedMod) ? BattleNext_UiTheme.PANEL : BattleNext_UiTheme.PANEL.darker(), alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()}, BattleNext_UiTheme.PANEL, alphaMult, 1f);
		}
		@Override
		public void render(float alphaMult) {
			if (position == null || !missingIcon) return;
			float size = ICON_SIZE * 0.75f;
			float x = position.getX() + 4f + (ICON_SIZE - size) * 0.5f;
			float y = position.getY() + position.getHeight() - 4f - ICON_SIZE + (ICON_SIZE - size) * 0.5f;
			BattleNext_PageLayout.renderMissingIcon(BattleNext_PageLayout.missingIcon(), x, y, size, alphaMult);
		}
		@Override
		public void advance(float amount) {
			if (position == null) return;
			float scale = Global.getSettings().getScreenScaleMult();
			float x = Mouse.getX() / scale;
			float y = Mouse.getY() / scale;
			boolean inside = x >= position.getX() && x < position.getX() + position.getWidth()
					&& y >= position.getY() && y < position.getY() + position.getHeight();
			if (inside && !hovered) {
				Global.getSoundPlayer().playUISound(BattleNext_UiTheme.BUTTON_HOVER_SOUND_ID, 1f, 1f);
			}
			hovered = inside;
		}
		@Override
		public void processInput(List<InputEventAPI> events) {
			if (confirmation.isActive() || position == null) return;
			for (InputEventAPI event : events) {
				if (event.isConsumed() || !event.isLMBDownEvent() || !position.containsEvent(event)) continue;
				selectedMod = id;
				Global.getSoundPlayer().playUISound(BattleNext_UiTheme.BUTTON_PRESSED_SOUND_ID, 1f, 1f);
				event.consume();
				return;
			}
		}
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
			BattleNext_GeometryRenderer.fillRect(position, FRAME_BACKGROUND, alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()}, BattleNext_UiTheme.PANEL, alphaMult, FRAME_BORDER);
		}
	}
}
