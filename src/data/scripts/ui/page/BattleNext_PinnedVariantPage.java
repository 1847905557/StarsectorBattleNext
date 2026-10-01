package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.*;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_PinnedVariantDetails;
import data.scripts.ui.widget.BattleNext_TextField;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_PinnedVariantPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_PinnedVariantStore store;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");
	private BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_PinnedVariantDetails details = new BattleNext_PinnedVariantDetails(
			this::refreshContent, () -> inputRouter.isEmpty());
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private String selectedPath = "";
	private String baseline = "";
	private TextFieldAPI name;
	private ButtonAPI saveButton;
	private LabelAPI statusLabel;
	private String status = "";

	public BattleNext_PinnedVariantPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this(backAction, navigation, deletion, new BattleNext_PinnedVariantStore());
	}

	BattleNext_PinnedVariantPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, BattleNext_PinnedVariantStore store) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
		this.store = store;
	}

	@Override
	public void setInputRouter(BattleNext_UiInputRouter router) {
		inputRouter = router;
	}

	@Override
	public void attach(CustomPanelAPI body) {

		panel = body;
		selectedPath = "";
		baseline = "";
		name = null;
		status = "";
		details.clear();
		try {
			store.reload();
		} catch (Exception exception) {
			status = strings.get("load_failed") + ": " + exception;
		}
		createContents();
	}

	@Override
	public void createContents() {

		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), BattleNext_PageLayout.HEADER_HEIGHT),
				panel, strings.get("pinned_variants"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.PANEL, BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		detailRect.addBorder(panel, BattleNext_UiTheme.PANEL, BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		UIRect footer = regions[2];
		float width = BattleNext_PageLayout.managementFooterButtonWidth(footer, 2);
		saveButton = BattleNext_PageLayout.fixedButton(panel,
				BattleNext_PageLayout.managementFooterButtonRect(footer, width, 0), strings.get("save"), "save", CutStyle.ALL, true);
		BattleNext_PageLayout.fixedButton(panel, BattleNext_PageLayout.managementFooterButtonRect(footer, width, 1),
				strings.get("back"), "back", CutStyle.ALL, true);
		UIRect statusRect = BattleNext_PageLayout.managementFooterStatusRect(footer, width, 2);
		statusLabel = null;
		if (statusRect.getWidth() >= BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_MIN_WIDTH) {
			TooltipMakerAPI text = panel.createUIElement(statusRect.getWidth(), statusRect.getHeight(), false);
			statusLabel = text.addPara(status, 0f);
			panel.addUIElement(text).inTL(statusRect.getX(), statusRect.getY());
		}
		refreshContent();
	}

	@Override
	public void refreshContent() {

		String edited = name == null ? baseline : name.getText();
		inputRouter.clear();
		name = null;
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		UIRect listArea = BattleNext_PageLayout.managementContentRect(listRect);
		TooltipMakerAPI list = listPanel.createUIElement(listArea.getWidth(), listArea.getHeight(), true);
		list.addTitle(strings.get("pinned_variants"));
		if (!store.isLoaded()) {
			list.addPara(strings.get("load_failed") + ": " + store.getLoadError(), 8f);
		}
		if (store.getCatalog().isEmpty()) {
			list.addPara(strings.get("no_pinned_variants"), 10f);
		}
		for (CatalogEntry entry : sorted(store.getCatalog())) {
			BattleNext_PageLayout.button(list, (entry.error().isEmpty() ? "" : strings.get("variant_error_marker") + " ")
					+ entry.name(), "pin:" + entry.path(), BattleNext_PageLayout.managementListButtonWidth(listRect));
			list.addTooltipToPrevious(new BaseTooltipCreator() {
				@Override
				public float getTooltipWidth(Object ignored) {
					return 400f;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
					tooltip.addTitle(entry.name());
					tooltip.addPara(entry.pinId(), 5f);
					String hull = entry.hullId();
					try {
						var spec = Global.getSettings().getHullSpec(hull);
						if (spec != null) {
							hull = spec.getHullName() + " [" + hull + "]";
						}
					} catch (RuntimeException exception) {
						hull += " / " + exception;
					}
					tooltip.addPara(hull, 5f);
					tooltip.addPara(entry.path(), 5f);
					if (!entry.error().isEmpty()) {
						tooltip.addPara(entry.error(), 5f);
					}
				}
			}, TooltipMakerAPI.TooltipLocation.RIGHT);
		}
		listPanel.addUIElement(list).inTL(listArea.getX() - listRect.getX(), listArea.getY() - listRect.getY());
		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		CatalogEntry selected = selected();
		saveButton.setEnabled(store.isLoaded() && selected != null && selected.model() != null);
		UIRect area = BattleNext_PageLayout.managementEditorRect(detailRect);
		TooltipMakerAPI content = detailPanel.createUIElement(area.getWidth(), area.getHeight(), true);
		if (selected == null) {
			content.addTitle(strings.get("select_pinned_variant"));
		} else {
			content.addTitle(selected.name());
			content.addPara(strings.get("pin_name"), 8f);
			if (selected.model() != null && store.isLoaded()) {
				name = BattleNext_TextField.addTo(inputRouter, content, detailPanel, area.getWidth() - 12f,
						BattleNext_PageLayout.FORM_FIELD_HEIGHT, edited, content);
				name.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
			}
			content.addPara(strings.get("variant_pin_id") + ": " + selected.pinId(), 8f);
			content.addPara(strings.get("variant_source") + ": " + selected.path(), 8f);
			if (!selected.error().isEmpty()) {
				content.addPara(selected.error(), 8f);
			} else {
				try {
					details.addTo(content, detailPanel, area.getWidth() - 12f, selected.model().getVariantJson());
				} catch (Exception exception) {
					content.addPara(selected.path() + ": " + exception, 8f);
				}
			}
			ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
					BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect), strings.get("delete"), "delete", CutStyle.ALL, true);
			delete.setEnabled(store.isLoaded());
		}
		detailPanel.addUIElement(content).inTL(area.getX() - detailRect.getX(), area.getY() - detailRect.getY());
	}

	public static List<CatalogEntry> sorted(List<CatalogEntry> entries) {
		return entries.stream().sorted(Comparator.comparing(CatalogEntry::name)
				.thenComparing(CatalogEntry::pinId).thenComparing(CatalogEntry::path)).toList();
	}

	private CatalogEntry selected() {
		return store.getCatalog().stream().filter(entry -> entry.path().equals(selectedPath)).findFirst().orElse(null);
	}

	@Override
	public void buttonPressed(Object buttonId) {

		if (buttonId instanceof String id && id.startsWith("pin:")) {
			navigation.accept(() -> {
				selectedPath = id.substring(4);
				name = null;
				CatalogEntry entry = selected();
				baseline = entry == null ? "" : entry.name();
				details.clear();
				refreshContent();
			});
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("back".equals(buttonId)) {
			if (selectedPath.isEmpty()) {
				backAction.run();
			} else {
				navigation.accept(() -> {
					selectedPath = "";
					name = null;
					baseline = "";
					details.clear();
					refreshContent();
				});
			}
		} else if ("delete".equals(buttonId) && store.isLoaded()) {
			CatalogEntry entry = selected();
			if (entry == null) return;
			deletion.accept(entry.name() + " [" + entry.pinId() + "]\n" + entry.path(), () -> {
				try {
					store.deleteFile(entry.path());
					selectedPath = "";
					baseline = "";
					name = null;
					details.clear();
					setStatus(strings.get("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(strings.get("save_failed") + ": " + exception);
				}
			});
		}
	}

	@Override
	public boolean hasUnsavedChanges() {
		return name != null && !name.getText().equals(baseline);
	}

	@Override
	public boolean save() {

		CatalogEntry entry = selected();
		if (entry == null) return true;
		if (!store.isLoaded() || entry.model() == null || name == null) return false;
		String next = name.getText().trim();
		if (next.isEmpty()) {
			setStatus(strings.get("pin_name_required"));
			return false;
		}
		try {
			store.rename(entry.pinId(), next);
			baseline = next;
			name.setText(next);
			store.reload();
			setStatus(strings.get("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(strings.get("save_failed") + ": " + exception);
			return false;
		}
	}

	@Override
	public void discard() {
		if (name != null) {
			name.setText(baseline);
		}
	}

	@Override
	public String getStatusText() {
		return status;
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) {
			statusLabel.setText(value);
		}
	}
}
