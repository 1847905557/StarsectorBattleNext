package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import data.scripts.ui.widget.provider.BattleNext_RefitDModSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_RefitSModSourceProvider;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.json.JSONException;
import org.lwjgl.input.Keyboard;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_RefitPanelPlugin extends BaseCustomUIPanelPlugin
		implements BattleNext_ConfirmationDialog.ConfirmationDialogHost {

	private static final float BORDER_INSET = 8f;
	private static final float SIDE_SHARE = 0.1f;
	private static final float LEFT_SHARE = 0.6f;
	private static final float RIGHT_SHARE = 0.18f;
	private static final float GAP_SHARE = 0.02f;
	private static final float CONTENT_TOP_SHARE = 0.1f;
	private static final float CONTENT_HEIGHT_SHARE = 0.80f;
	private static final float PICKER_GAP = 36f;
	private static final float FOOTER_BUTTON_WIDTH = 160f;
	private static final float FOOTER_BUTTON_GAP = 10f;

	private final Runnable closeAction;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection refitStrings = BattleNext_I18nSection.getInstance("Refit", "BattleNext_refit_");
	private final BattleNext_ConfirmationDialog confirmation = new BattleNext_ConfirmationDialog(this, inputRouter);

	private UIBorderObject border;
	private CustomPanelAPI panel;
	private CustomPanelAPI leftPanel;
	private CustomPanelAPI rightPanel;
	private BattleNext_VariantDetails details;
	private BattleNext_OptionPicker sModPicker;
	private BattleNext_OptionPicker dModPicker;
	private ButtonAPI saveButton;
	private LabelAPI statusLabel;
	private FleetMemberAPI member;
	private Object refitScreen;
	private ShipVariantAPI draft;
	private Set<String> savedSMods = Set.of();
	private Set<String> savedDMods = Set.of();
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
		details = new BattleNext_VariantDetails(this::refreshDetails, () -> inputRouter.isEmpty());
		border = new UIBorderObject(false, true);
		borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - BORDER_INSET;
		borderY = (ShaderCore.getScreenHeight() - height) * 0.5f - BORDER_INSET;
		border.setSize(width + BORDER_INSET * 2f, height + BORDER_INSET * 2f);
		confirmation.attach(panel);

		float top = height * CONTENT_TOP_SHARE;
		float contentHeight = height * CONTENT_HEIGHT_SHARE;
		float gap = width * GAP_SHARE;
		float leftWidth = width * LEFT_SHARE;
		float rightWidth = width * RIGHT_SHARE;
		float leftX = width * SIDE_SHARE;
		float rightX = leftX + leftWidth + gap;
		leftPanel = panel.createCustomPanel(leftWidth, contentHeight, new BaseCustomUIPanelPlugin());
		rightPanel = panel.createCustomPanel(rightWidth, contentHeight, new BaseCustomUIPanelPlugin());
		panel.addComponent(leftPanel).inTL(leftX, top);
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
		sModPicker = new BattleNext_OptionPicker(inputRouter, panel, width, 30f, refitStrings.get("s_mods"), 0f,
				new BattleNext_RefitSModSourceProvider(() -> draft),
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE,
				() -> draft == null ? List.of() : new ArrayList<>(sModsOf(draft)), this::selectSMods);
		dModPicker = new BattleNext_OptionPicker(inputRouter, panel, width, 30f, refitStrings.get("d_mods"), 0f,
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
		details.close();
		details.clear();
		details.setVariantJson(null);
		this.member = member;
		this.refitScreen = refitScreen;
		saveError = "";
		statusLabel.setText("");
		try {
			ShipVariantAPI current = currentVariant();
			draft = current == null ? null : current.clone();
		} catch (Throwable exception) {
			draft = null;
			saveError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			statusLabel.setText(strings.get("save_failed") + ": " + saveError);
		}
		savedSMods = draft == null ? Set.of() : sModsOf(draft);
		savedDMods = draft == null ? Set.of() : dModsOf(draft);
		confirmationWasActive = false;
		updateControls();
		refreshDetails();
	}

	public void close() {
		closePickers();
		sModPicker.setEnabled(false);
		dModPicker.setEnabled(false);
		if (confirmation.isActive()) confirmation.handle("confirm_cancel");
		details.close();
		details.clear();
		details.setVariantJson(null);
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
			saveError = refitStrings.get("no_ship_selected");
			statusLabel.setText(saveError);
			return false;
		}
		BattleNext_Logger.operationStarted(BattleNext_RefitPanelPlugin.class, "refit.commit",
				BattleNext_Logger.fields().put("member", member).put("draft", draft).put("saved_s_mods", savedSMods).put("saved_d_mods", savedDMods).build());
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
			BattleNext_Logger.operationSucceeded(BattleNext_RefitPanelPlugin.class, "refit.commit",
				BattleNext_Logger.fields().put("member", member).put("variant", current).put("scope", "Mission refit").build());
			ShipVariantAPI saved = currentVariant();
			draft = saved.clone();
			savedSMods = sModsOf(draft);
			savedDMods = dModsOf(draft);
			refreshDetails();
			saveError = "";
			statusLabel.setText(strings.get("saved"));
			return true;
		} catch (Throwable exception) {
			if (committed) {
				details.close();
				details.clear();
				details.setVariantJson(null);
				savedSMods = sModsOf(draft);
				savedDMods = dModsOf(draft);
				saveError = "";
				BattleNext_Logger.warn(BattleNext_RefitPanelPlugin.class, "refit.display.after_commit.failed",
				BattleNext_Logger.fields().put("member", member).put("draft", draft).put("saved_s_mods", savedSMods).put("saved_d_mods", savedDMods).build(), exception);
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
					BattleNext_Logger.error(BattleNext_RefitPanelPlugin.class, "refit.rollback.failed",
						BattleNext_Logger.fields().put("member", member).put("original", original).put("original_member", originalMember).build(), rollback);
							exception.addSuppressed(rollback);
				}
			}
			if (originalMember != null) {
				try {
					member.setVariant(originalMember, false, true);
				} catch (Throwable rollback) {
					BattleNext_Logger.error(BattleNext_RefitPanelPlugin.class, "refit.rollback.failed",
				BattleNext_Logger.fields().put("member", member).put("original", original).put("original_member", originalMember).build(), rollback);
					exception.addSuppressed(rollback);
				}
			}
			saveError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			statusLabel.setText(strings.get("save_failed") + ": " + saveError);
			BattleNext_Logger.error(BattleNext_RefitPanelPlugin.class, "refit.commit.failed",
				BattleNext_Logger.fields().put("member", member).put("draft", draft).put("saved_s_mods", savedSMods).put("saved_d_mods", savedDMods).build(), exception);
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
			refreshDetails();
		} catch (Throwable exception) {
			details.close();
			details.clear();
			details.setVariantJson(null);
			saveError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			statusLabel.setText(strings.get("save_failed") + ": " + saveError);
			BattleNext_Logger.warn(BattleNext_RefitPanelPlugin.class, "refit.discard.failed",
				BattleNext_Logger.fields().put("member", member).put("draft", draft).put("saved_s_mods", savedSMods).put("saved_d_mods", savedDMods).build(), exception);
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
		statusLabel.setText("");
		refreshDetails();
	}

	private void selectDMods(List<String> ids) {
		if (draft == null) return;
		Set<String> selected = new LinkedHashSet<>(ids);
		if (selected.equals(dModsOf(draft))) return;
		ShipVariantAPI previous = draft.clone();
		try {
			applyDMods(draft, selected);
			statusLabel.setText("");
			refreshDetails();
		} catch (RuntimeException exception) {
			draft = previous;
			refreshDetails();
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

	private void refreshDetails() {
		if (draft == null) {
			details.close();
			details.clear();
			details.setVariantJson(null);
			return;
		}
		try {
			details.setVariantJson(BattleNext_VariantBuilder.fromVariant(draft).toJson());
			if (details.getPanel() == null) {
				details.attach(leftPanel);
			} else {
				details.refreshContent();
			}
		} catch (JSONException | RuntimeException exception) {
			details.close();
			details.clear();
			details.setVariantJson(null);
			saveError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			statusLabel.setText(strings.get("save_failed") + ": " + saveError);
			BattleNext_Logger.warn(BattleNext_RefitPanelPlugin.class, "refit.details.refresh.failed",
				BattleNext_Logger.fields().put("member", member).put("draft", draft).put("saved_s_mods", savedSMods).put("saved_d_mods", savedDMods).build(), exception);
		}
	}
}
