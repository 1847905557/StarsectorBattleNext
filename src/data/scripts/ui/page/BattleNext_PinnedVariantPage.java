package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_PinnedVariantEditor;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_PinnedVariantPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");

	private final BattleNext_PinnedVariantStore store;
	private final BattleNext_PinnedVariantEditor editor;

	public BattleNext_PinnedVariantPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this(backAction, navigation, deletion, new BattleNext_PinnedVariantStore());
	}

	public BattleNext_PinnedVariantPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, BattleNext_PinnedVariantStore store) {
		super(backAction, navigation, deletion, "pin:");
		this.store = store;
		editor = new BattleNext_PinnedVariantEditor(store);
	}

	@Override
	protected BattleNext_PinnedVariantEditor editorView() {
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
			addListItem(listContent, (entry.error().isEmpty() ? "" : variantStrings.get("error_marker") + " ")
					+ entry.name(), "pin:" + entry.path(), new BaseTooltipCreator() {
				@Override
				public float getTooltipWidth(Object tooltipParam) {
					return 400f;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					tooltip.addTitle(entry.name());
					BattleNext_PageLayout.addMutedIdLine(tooltip, entry.pinId(), BattleNext_PageLayout.CONTROL_GAP);
					String hull = entry.hullId();
					try {
						ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hull);
						if (spec != null) hull = spec.getHullName();
					} catch (RuntimeException exception) {
						hull += " / " + exception;
					}
					tooltip.addPara(hull, BattleNext_PageLayout.CONTROL_GAP);
					tooltip.addPara(entry.path(), BattleNext_PageLayout.CONTROL_GAP);
					if (!entry.error().isEmpty()) tooltip.addPara(entry.error(), BattleNext_PageLayout.CONTROL_GAP);
				}
			});
		}
	}

	@Override
	protected boolean isDetailEditable() {
		return selected().model() != null;
	}

	public static List<CatalogEntry> sorted(List<CatalogEntry> entries) {
		return entries.stream().sorted(Comparator.comparing(CatalogEntry::name)
				.thenComparing(CatalogEntry::pinId).thenComparing(CatalogEntry::path)).toList();
	}

	private CatalogEntry selected() {
		return store.getCatalog().stream().filter(entry -> entry.path().equals(selectedId)).findFirst().orElse(null);
	}

	@Override
	protected boolean selectionAvailable(String id) {
		return store.getCatalog().stream().anyMatch(entry -> entry.path().equals(id));
	}

	@Override
	protected boolean openEditor(String id) {
		selectedId = id;
		editor.open(selected());
		return true;
	}

	@Override
	protected String deleteDescription() {
		return selected().name();
	}

	@Override
	protected void deleteSelection(String path) throws Exception {
		store.deleteFile(path);
	}

	@Override
	protected BattleNext_PinnedVariantStore catalog() {
		return store;
	}
}
