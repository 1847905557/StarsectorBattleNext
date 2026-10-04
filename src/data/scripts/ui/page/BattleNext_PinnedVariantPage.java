package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_PinnedVariantEditor;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_PinnedVariantPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_PinnedVariantStore store;
	private String selectedPath = "";
	private final BattleNext_PinnedVariantEditor editor;

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
		editor = new BattleNext_PinnedVariantEditor(store);
	}

	@Override
	protected void loadPageState() {

		selectedPath = "";
		status = "";
		try {
			store.reload();
		} catch (Exception exception) {
			status = strings.get("load_failed") + ": " + exception;
		}
	}
	@Override
	protected BattleNext_EditorView editorView() {
		return editor;
	}

	@Override
	protected String pageTitle() {
		return variantStrings.get("pinned_list_title");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"save", "back"};
	}

	@Override
	protected String promptText() {
		return variantStrings.get("select_pinned");
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
		return variantStrings.get("no_pinned");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		for (CatalogEntry entry : sorted(store.getCatalog())) {
			BattleNext_PageLayout.button(listContent, (entry.error().isEmpty() ? "" : variantStrings.get("error_marker") + " ")
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
		attachEditor(contentPanel);
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
								CatalogEntry entry = selected();
				editor.open(entry);
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
					setStatus(strings.get("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(strings.get("save_failed") + ": " + exception);
				}
			});
		}
	}

	@Override
	protected boolean isPageDirty() {
		return !selectedPath.isEmpty() && editor.hasUnsavedChanges();
	}

	@Override
	protected boolean savePage() {
		if (!editor.save()) {
			setStatus(editor.getStatusText());
			return false;
		}
		setStatus(strings.get("saved"));
		try {
			store.reload();
			refreshContent();
		} catch (Exception exception) {
			setStatus(strings.get("saved") + " / " + strings.get("load_failed") + ": " + exception);
		}
		return true;
	}

	@Override
	protected void discardPage() {
		editor.discard();
	}

	public static List<CatalogEntry> sorted(List<CatalogEntry> entries) {
		return entries.stream().sorted(Comparator.comparing(CatalogEntry::name)
				.thenComparing(CatalogEntry::pinId).thenComparing(CatalogEntry::path)).toList();
	}

	private CatalogEntry selected() {
		return store.getCatalog().stream().filter(entry -> entry.path().equals(selectedPath)).findFirst().orElse(null);
	}
}
