package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import data.scripts.ui.widget.BattleNext_TextField;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_PinnedVariantPage extends BattleNext_ManagementPage {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_PinnedVariantStore store;
	private final BattleNext_VariantDetails details = new BattleNext_VariantDetails(
			this::refreshContent, () -> inputRouter.isEmpty());
	private String selectedPath = "";
	private String baseline = "";
	private TextFieldAPI name;
	private TooltipMakerAPI metadata;

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
	protected void loadPageState() {

		selectedPath = "";
		baseline = "";
		name = null;
		metadata = null;
		status = "";
		details.clear();
		try {
			store.reload();
		} catch (Exception exception) {
			status = text("load_failed") + ": " + exception;
		}
	}

	@Override
	protected String pageTitle() {
		return text("pinned_variants");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"save", "back"};
	}

	@Override
	protected String promptText() {
		return text("select_pinned_variant");
	}

	@Override
	protected boolean isListLoaded() {
		return store.isLoaded();
	}

	@Override
	protected String listError() {
		return store.getLoadError();
	}

	@Override
	protected boolean isListEmpty() {
		return store.getCatalog().isEmpty();
	}

	@Override
	protected String emptyListText() {
		return text("no_pinned_variants");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		for (CatalogEntry entry : sorted(store.getCatalog())) {
			BattleNext_PageLayout.button(listContent, (entry.error().isEmpty() ? "" : text("variant_error_marker") + " ")
					+ entry.name(), "pin:" + entry.path(), BattleNext_PageLayout.managementListButtonWidth(listRect));
			listContent.addTooltipToPrevious(new BaseTooltipCreator() {
				@Override
				public float getTooltipWidth(Object ignored) {
					return 400f;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
					tooltip.addTitle(entry.name());
					BattleNext_PageLayout.addMutedIdLine(tooltip, entry.pinId(), 5f);
					String hull = entry.hullId();
					try {
						var spec = Global.getSettings().getHullSpec(hull);
						if (spec != null) {
							hull = spec.getHullName();
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
	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {
		String edited = name == null ? baseline : name.getText();
		float offset = metadata == null || metadata.getExternalScroller() == null
				? 0f : metadata.getExternalScroller().getYOffset();
		name = null;
		CatalogEntry selected = selected();
		UIRect area = new UIRect(0f, 0f, contentPanel.getPosition().getWidth(), contentPanel.getPosition().getHeight());
		float metadataLimit = Math.max(1f, (float) Math.floor(area.getHeight() / 3f));
		CustomPanelAPI metadataPanel = contentPanel.createCustomPanel(area.getWidth(), metadataLimit,
				new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = metadataPanel.createUIElement(
				area.getWidth() + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, metadataLimit, true);
		content.setTextWidthOverride(area.getWidth());
		content.addTitle(selected.name());
		content.addPara(text("pin_name"), 8f);
		if (selected.model() != null && store.isLoaded()) {
			BattleNext_TextField nameWidget = new BattleNext_TextField(inputRouter, metadataPanel, area.getWidth(),
					BattleNext_PageLayout.FORM_FIELD_HEIGHT, content);
			nameWidget.attach(content, metadataPanel);
			nameWidget.setText(edited);
			name = nameWidget;
			name.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		}
		content.addPara(text("variant_pin_id") + ": " + selected.pinId(), 8f);
		content.addPara(text("variant_source") + ": " + selected.path(), 8f);
		if (!selected.error().isEmpty()) {
			content.addPara(selected.error(), 8f);
		}
		float metadataHeight = Math.min(metadataLimit, content.getHeightSoFar());
		metadataPanel.getPosition().setSize(area.getWidth(), metadataHeight);
		content.getPosition().setSize(area.getWidth() + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, metadataHeight);
		metadataPanel.addUIElement(content).inTL(-BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET, 0f);
		contentPanel.addComponent(metadataPanel).inTL(0f, 0f);
		metadata = content;
		if (content.getExternalScroller() != null) {
			content.getExternalScroller().setYOffset(Math.min(Math.max(0f, content.getHeightSoFar() - metadataHeight), offset));
		}
		if (selected.error().isEmpty() && selected.model() != null && store.isLoaded()) {
			float detailsHeight = Math.max(1f, area.getHeight() - metadataHeight - 8f);
			CustomPanelAPI visual = contentPanel.createCustomPanel(area.getWidth(), detailsHeight,
					new BaseCustomUIPanelPlugin());
			contentPanel.addComponent(visual).inTL(0f,
					metadataHeight + 8f);
			try {
				details.setVariantJson(selected.model().getVariantJson());
				details.attach(visual);
			} catch (Exception exception) {
				TooltipMakerAPI error = visual.createUIElement(area.getWidth(), detailsHeight, true);
				error.addPara(selected.path() + ": " + exception, 8f);
				visual.addUIElement(error).inTL(0f, 0f);
			}
		}
	}

	@Override
	protected boolean isDetailOpen() {
		return selected() != null;
	}

	@Override
	protected boolean hasSelection() {
		return selected() != null;
	}

	@Override
	protected boolean isDetailEditable() {
		return selected().model() != null;
	}

	@Override
	protected boolean isSaveDataLoaded() {
		return store.isLoaded();
	}

	@Override
	protected boolean canDelete() {
		return store.isLoaded();
	}

	@Override
	protected void handlePageAction(Object buttonId) {

		if (buttonId instanceof String id && id.startsWith("pin:")) {
			navigation.accept(() -> {
				selectedPath = id.substring(4);
				name = null;
				metadata = null;
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
					metadata = null;
					baseline = "";
					details.clear();
					refreshContent();
				});
			}
		} else if ("delete".equals(buttonId) && store.isLoaded()) {
			CatalogEntry entry = selected();
			if (entry == null) return;
			deletion.accept(entry.name(), () -> {
				try {
					store.deleteFile(entry.path());
					selectedPath = "";
					baseline = "";
					name = null;
					metadata = null;
					details.clear();
					setStatus(text("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(text("save_failed") + ": " + exception);
				}
			});
		}
	}

	@Override
	protected boolean isPageDirty() {
		return name != null && !name.getText().equals(baseline);
	}

	@Override
	protected boolean savePage() {

		CatalogEntry entry = selected();
		if (entry == null) return true;
		if (!store.isLoaded() || entry.model() == null || name == null) return false;
		String next = name.getText().trim();
		if (next.isEmpty()) {
			setStatus(text("pin_name_required"));
			return false;
		}
		try {
			store.rename(entry.pinId(), next);
			baseline = next;
			name.setText(next);
			store.reload();
			setStatus(text("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(text("save_failed") + ": " + exception);
			return false;
		}
	}

	@Override
	protected void discardPage() {
		if (name != null) {
			name.setText(baseline);
		}
	}

	public static List<CatalogEntry> sorted(List<CatalogEntry> entries) {
		return entries.stream().sorted(Comparator.comparing(CatalogEntry::name)
				.thenComparing(CatalogEntry::pinId).thenComparing(CatalogEntry::path)).toList();
	}

	private CatalogEntry selected() {
		return store.getCatalog().stream().filter(entry -> entry.path().equals(selectedPath)).findFirst().orElse(null);
	}
}
