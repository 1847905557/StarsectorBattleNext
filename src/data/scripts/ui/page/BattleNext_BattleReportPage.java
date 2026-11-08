package data.scripts.ui.page;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.report.BattleNext_ReportTarget;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.editor.BattleNext_ReportEditor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_BattleReportPage extends BattleNext_ManagementPage {

	private final BattleNext_BattleReportStore reportStore;
	private final BattleNext_I18nSection reportStrings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final List<BattleNext_ReportTarget> entries = new ArrayList<>();
	private final BattleNext_ReportEditor editor;

	public BattleNext_BattleReportPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this(backAction, navigation, deletion, new BattleNext_BattleReportStore());
	}

	public BattleNext_BattleReportPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, BattleNext_BattleReportStore reports) {
		super(backAction, navigation, deletion, "report:");
		reportStore = reports;
		editor = new BattleNext_ReportEditor(reports);
	}

	@Override
	protected void loadPageState() {
		super.loadPageState();
		load();
	}

	@Override
	public void catalogChanged() {
		load();
		refreshContent();
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_reports");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"save", "back"};
	}

	@Override
	protected String promptText() {
		return reportStrings.get("select_prompt");
	}

	@Override
	protected String emptyListText() {
		return reportStrings.get("empty");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		addCatalogDiagnostics(listContent, reportStore.getErrors());
		for (BattleNext_ReportTarget entry : entries) {
			addListItem(listContent, listLabel(entry), "report:" + entry.identity());
		}
	}

	@Override
	protected BattleNext_ReportEditor editorView() {
		return editor;
	}

	private void load() {
		entries.clear();
		for (BattleNext_BattleReportModel report : reportStore.getAll()) {
			entries.add(new BattleNext_ReportTarget(report));
		}
		entries.sort(Comparator.comparingLong((BattleNext_ReportTarget entry) -> entry.report().getCreatedAt()).reversed());
	}

	private String listLabel(BattleNext_ReportTarget entry) {
		String time = entry.report().getCreatedAt() <= 0L ? ""
				: new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.report().getCreatedAt()));
		return entry.report().getName() + " / " + (time.isEmpty() ? "" : time + " / ") + entry.report().getWinner()
				+ " / " + entry.report().getMemberCount();
	}

	private BattleNext_ReportTarget entry(String id) {
		return entries.stream().filter(entry -> entry.identity().equals(id)).findFirst().orElse(null);
	}

	@Override
	protected boolean selectionAvailable(String id) {
		return entry(id) != null;
	}

	@Override
	protected boolean openEditor(String id) {
		BattleNext_ReportTarget selected = entry(id);
		persistMutation(() -> {
			selected.report().prepareDetails();
			return true;
		}, () -> {
			editor.open(selected);
			showSelection(id);
		}, strings.format("loading", selected.report().getName()), strings.get("load_failed"));
		return false;
	}

	@Override
	protected String deleteDescription() {
		return listLabel(entry(selectedId));
	}

	@Override
	protected void deleteSelection(String id) throws Exception {
		entry(id).delete(reportStore);
	}

	@Override
	protected BattleNext_BattleReportStore catalog() {
		return reportStore;
	}
}
