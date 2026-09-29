package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_MatchProgress;
import data.scripts.battle.BattleNext_Round;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_BattleReportPage implements BattleNext_PageView {

	private static final float REPORT_SUMMARY_TOP_GAP = 8f;
	private static final float REPORT_CONTEXT_TOP_GAP = 6f;
	private static final float REPORT_SECTION_TOP_GAP = 10f;
	private static final float DETAIL_CONTROL_SIDE_RESERVE = 12f;
	private static final float MEMBER_THUMB_SIZE = 32f;
	private static final float MEMBER_BODY_INSET = 8f;
	private static final float MEMBER_BODY_LINE_HEIGHT = 24f;
	private static final float SOURCE_ROW_HEIGHT = 40f;
	private static final float SOURCE_ROW_GAP = 4f;

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_BattleReportStore reportStore = new BattleNext_BattleReportStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final BattleNext_I18nSection reportStrings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");

	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private final List<BattleReportListItem> entries = new ArrayList<>();
	private final Set<MemberRow> expandedMembers = new HashSet<>();
	private final Map<String, MemberRow> memberActions = new HashMap<>();
	private String selectedId = "";
	private boolean detailOpen;
	private TextFieldAPI nameField;
	private String savedName = "";
	private int memberActionIndex;
	private LabelAPI statusLabel;
	private String status = "";

	public BattleNext_BattleReportPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		selectedId = "";
		detailOpen = false;
		nameField = null;
		expandedMembers.clear();
		status = "";
		try {
			reportStore.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			status = exception.getMessage();
		}
		try {
			tournamentStore.reload();
		} catch (Exception exception) {
			status += (status.isEmpty() ? "" : " | ") + exception.getMessage();
		}
		load();
		createContents();
	}

	@Override
	public void createContents() {
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), BattleNext_PageLayout.HEADER_HEIGHT),
				panel, text("page_reports"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.HEADER,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		detailRect.addBorder(panel, BattleNext_UiTheme.HEADER,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		UIRect footerRect = regions[2];
		String[] actions = {"back"};
		float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(footerRect, actions.length);
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(panel,
					BattleNext_PageLayout.managementFooterButtonRect(footerRect, buttonWidth, index),
					text(actions[index]), actions[index], CutStyle.ALL, true);
		}
		UIRect statusRect = BattleNext_PageLayout.managementFooterStatusRect(footerRect, buttonWidth, actions.length);
		if (statusRect.getWidth() >= BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_MIN_WIDTH) {
			TooltipMakerAPI footerStatus = panel.createUIElement(statusRect.getWidth(), statusRect.getHeight(), false);
			statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
			statusLabel.getPosition().inTL(0f, BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_TOP_INSET);
			footerStatus.setHeightSoFar(statusRect.getHeight());
			panel.addUIElement(footerStatus).inTL(statusRect.getX(), statusRect.getY());
		}

		refreshContent();
	}

	@Override
	public void refreshContent() {
		String editedName = nameField == null ? null : nameField.getText();
		BattleNext_TextField.clearRegisteredFields();
		nameField = null;
		memberActions.clear();
		memberActionIndex = 0;
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		UIRect listContentRect = BattleNext_PageLayout.managementContentRect(listRect);
		TooltipMakerAPI listContent = listPanel.createUIElement(listContentRect.getWidth(), listContentRect.getHeight(), true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(listContentRect.getX() - listRect.getX(),
				listContentRect.getY() - listRect.getY());

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		if (!detailOpen) {
			UIRect promptRect = BattleNext_PageLayout.managementContentRect(detailRect);
			TooltipMakerAPI prompt = detailPanel.createUIElement(promptRect.getWidth(), promptRect.getHeight(), false);
			prompt.addTitle(text("select_report"));
			detailPanel.addUIElement(prompt).inTL(promptRect.getX() - detailRect.getX(),
					promptRect.getY() - detailRect.getY());
			return;
		}
		UIRect contentRect = BattleNext_PageLayout.managementEditorRect(detailRect);
		TooltipMakerAPI detailContent = detailPanel.createUIElement(contentRect.getWidth(), contentRect.getHeight(), true);
		addDetails(detailContent, detailPanel, contentRect.getWidth(), editedName);
		detailPanel.addUIElement(detailContent).inTL(contentRect.getX() - detailRect.getX(),
				contentRect.getY() - detailRect.getY());
		BattleReportListItem selected = entry(selectedId);
		ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
				BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect),
				text("delete"), "delete", CutStyle.ALL, true);
		delete.setEnabled(selected != null && (selected.tournament == null
				? reportStore.isLoaded() : tournamentStore.isLoaded()));
		UIRect deleteRect = BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect);
		BattleNext_PageLayout.fixedButton(detailPanel,
				new UIRect(deleteRect.getX() + deleteRect.getWidth() + 10f, deleteRect.getY(),
						deleteRect.getWidth(), deleteRect.getHeight()),
				text("save"), "save", CutStyle.ALL, true);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("report:")) {
			String id = ((String) buttonId).substring("report:".length());
			navigation.accept(() -> {
				selectedId = id;
				detailOpen = true;
				nameField = null;
				expandedMembers.clear();
				savedName = entry(id).report.getName();
				refreshContent();
			});
		} else if (buttonId instanceof String && memberActions.containsKey(buttonId)) {
			MemberRow row = memberActions.get(buttonId);
			toggleExpanded(expandedMembers, row);
			refreshContent();
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty()
				&& entry(selectedId) != null) {
			BattleReportListItem selected = entry(selectedId);
			Runnable action = () -> deletion.accept(listLabel(selected), () -> {
				try {
					deleteReport(selected);
					load();
					selectedId = "";
					detailOpen = false;
					nameField = null;
					expandedMembers.clear();
					status = text("deleted");
					refreshContent();
				} catch (Exception exception) {
					setStatus(text("save_failed") + ": " + exception.getMessage());
				}
			});
			if (hasUnsavedChanges()) navigation.accept(action);
			else action.run();
		} else if ("back".equals(buttonId)) {
			if (detailOpen) navigation.accept(() -> {
				selectedId = "";
				detailOpen = false;
				nameField = null;
				expandedMembers.clear();
				refreshContent();
			});
			else backAction.run();
		}
	}

	private void load() {
		entries.clear();
		for (BattleNext_BattleReportModel report : reportStore.getAll()) {
			entries.add(new BattleReportListItem(report, "", null, null));
		}
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			for (BattleNext_Match match : tournament.getMatches()) {
				for (int index = 0; index < match.getRounds().size(); index++) {
					BattleNext_Round round = match.getRounds().get(index);
					if (!round.getReport().getId().isEmpty()) {
						entries.add(new BattleReportListItem(round.getReport(),
								context(tournament, match.getId(), index + 1), tournament, round));
					}
				}
			}
		}
		entries.sort(Comparator.comparingLong((BattleReportListItem entry) -> entry.report.getCreatedAt()).reversed());
	}

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_reports"));
		if (!reportStore.isLoaded()) listContent.addPara(text("load_failed") + ": "
				+ reportStore.getLoadError(), BattleNext_PageLayout.MANAGEMENT_LIST_ERROR_TOP_GAP,
				BattleNext_UiTheme.MUTED);
		if (entries.isEmpty()) listContent.addPara(text("no_reports"),
				BattleNext_PageLayout.MANAGEMENT_LIST_EMPTY_TOP_GAP, BattleNext_UiTheme.MUTED);
		for (BattleReportListItem entry : entries) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, listLabel(entry),
					"report:" + entry.report.getId(), BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	private void addDetails(TooltipMakerAPI detailContent, CustomPanelAPI detailPanel,
			float contentWidth, String editedName) {
		BattleReportListItem selected = entry(selectedId);
		if (selected == null) return;
		BattleNext_BattleReportModel report = selected.report;
		detailContent.addTitle(report.getName());
		detailContent.addPara(text("report_name"), REPORT_SUMMARY_TOP_GAP, BattleNext_UiTheme.TEXT);
		nameField = BattleNext_TextField.addTo(detailContent, detailPanel,
				detailControlWidth(contentWidth), BattleNext_PageLayout.FORM_FIELD_HEIGHT,
				nameForRefresh(report, editedName), detailContent);
		nameField.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		detailContent.addPara(report.getId(), REPORT_CONTEXT_TOP_GAP, BattleNext_UiTheme.MUTED);
		String time = timeText(report);
		detailContent.addPara((time.isEmpty() ? "" : time + " / ") + text("winner") + ": " + report.getWinner()
				+ " / " + text("duration") + ": " + report.getDurationMillis() + " ms",
				REPORT_SUMMARY_TOP_GAP, BattleNext_UiTheme.TEXT);
		if (!selected.context.isEmpty()) detailContent.addPara(selected.context,
				REPORT_CONTEXT_TOP_GAP, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("damage_dealt"), REPORT_SECTION_TOP_GAP, BattleNext_UiTheme.ACCENT);
		appendMembers(detailContent, detailPanel, contentWidth, sorted(report, true), true);
		detailContent.addPara(text("damage_taken"), REPORT_SECTION_TOP_GAP, BattleNext_UiTheme.ACCENT);
		appendMembers(detailContent, detailPanel, contentWidth, sorted(report, false), false);
	}

	private void appendMembers(TooltipMakerAPI detailContent, CustomPanelAPI detailPanel, float contentWidth,
			List<BattleNext_MemberReport> members, boolean dealt) {
		for (BattleNext_MemberReport member : members) {
			String action = "member:" + memberActionIndex++;
			MemberRow row = new MemberRow(member, dealt);
			memberActions.put(action, row);
			boolean expanded = expandedMembers.contains(row);
			float width = detailControlWidth(contentWidth);
			List<BattleNext_DamageSourceReport> sources = sourcesFor(row);
			float bodyHeight = expanded ? memberBodyHeight(sources.size()) : 0f;
			HullSummary hull = resolveHull(member.getVariantId(), this::loadVariant);
			String shipName = member.getName().isEmpty() ? member.getVariantId() : member.getName();
			BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
					hull.spritePath(), false, "", shipName, hull.name(),
					totalLabel(row) + "  " + statusLabel(member.getStatus()),
					text("final_hull") + ": " + member.getFinalHullFraction()
							+ "  " + text("final_cr") + ": " + member.getFinalCr());
			BattleNext_ExpandablePanel.addTo(detailContent, detailPanel, width, expanded, bodyHeight,
					header, () -> buttonPressed(action), this::buttonPressed,
					bodyPanel -> addMemberBody(bodyPanel, row, sources, width, bodyHeight));
		}
	}

	private void addMemberBody(CustomPanelAPI body, MemberRow row,
			List<BattleNext_DamageSourceReport> sources, float width, float height) {
		float contentWidth = width - MEMBER_BODY_INSET * 2f;
		TooltipMakerAPI content = body.createUIElement(contentWidth, height - MEMBER_BODY_INSET * 2f, false);
		CustomPanelAPI summary = body.createCustomPanel(contentWidth, MEMBER_BODY_LINE_HEIGHT,
				new BaseCustomUIPanelPlugin());
		TooltipMakerAPI summaryHost = summary.createUIElement(contentWidth, MEMBER_BODY_LINE_HEIGHT, false);
		summaryHost.setParaSmallOrbitron();
		addDamageColumns(summaryHost, 0f, contentWidth, damageValues(row));
		summaryHost.setHeightSoFar(MEMBER_BODY_LINE_HEIGHT);
		summary.addUIElement(summaryHost).inTL(0f, 0f);
		content.addCustom(summary, 0f);
		if (sources.isEmpty()) {
			CustomPanelAPI empty = body.createCustomPanel(contentWidth, SOURCE_ROW_HEIGHT,
					new BaseCustomUIPanelPlugin());
			TooltipMakerAPI emptyHost = empty.createUIElement(contentWidth, SOURCE_ROW_HEIGHT, false);
			emptyHost.addPara(text("no_damage_sources"), 0f, BattleNext_UiTheme.MUTED)
					.getPosition().inTL(0f, 10f);
			emptyHost.setHeightSoFar(SOURCE_ROW_HEIGHT);
			empty.addUIElement(emptyHost).inTL(0f, 0f);
			content.addCustom(empty, SOURCE_ROW_GAP);
		} else {
			for (BattleNext_DamageSourceReport source : sortedSources(sources)) {
				addSourceRow(body, content, source, contentWidth);
			}
		}
		content.setHeightSoFar(height - MEMBER_BODY_INSET * 2f);
		body.addUIElement(content).inTL(MEMBER_BODY_INSET, MEMBER_BODY_INSET);
	}

	private void addSourceRow(CustomPanelAPI body, TooltipMakerAPI content,
			BattleNext_DamageSourceReport source, float width) {
		SourceRowPlugin plugin = new SourceRowPlugin();
		CustomPanelAPI sourcePanel = body.createCustomPanel(width, SOURCE_ROW_HEIGHT, plugin);
		plugin.setPosition(sourcePanel.getPosition());
		BattleNext_ReportSourceDisplay.SourceVisual visual =
				BattleNext_ReportSourceDisplay.resolve(source, Global.getSettings());
		TooltipMakerAPI icon = sourcePanel.createUIElement(MEMBER_THUMB_SIZE, SOURCE_ROW_HEIGHT, false);
		if (!addSprite(icon, visual.primarySprite()) && !addSprite(icon, visual.secondarySprite())) {
			icon.addPara(visual.placeholder(), 0f, BattleNext_UiTheme.MUTED).getPosition().inMid();
		}
		sourcePanel.addUIElement(icon).inTL(0f, (SOURCE_ROW_HEIGHT - MEMBER_THUMB_SIZE) * 0.5f);
		float textX = MEMBER_THUMB_SIZE + 8f;
		float textWidth = sourceTextWidth(width);
		TooltipMakerAPI labels = sourcePanel.createUIElement(textWidth, SOURCE_ROW_HEIGHT, false);
		String name = visual.resolved() ? visual.name() : sourceKindLabel(source.getKind()) + ": " + visual.name();
		labels.addPara(labels.shortenString(name, Math.max(1f, textWidth - 4f)), 0f,
				BattleNext_UiTheme.TEXT).getPosition().inTL(0f, 0f);
		addDamageColumns(labels, 18f, textWidth,
				new double[]{source.getHull(), source.getArmor(), source.getShields(), source.getEmp()});
		labels.setHeightSoFar(SOURCE_ROW_HEIGHT);
		sourcePanel.addUIElement(labels).inTL(textX, 0f);
		content.addCustom(sourcePanel, SOURCE_ROW_GAP);
		content.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return 360f;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addTitle(visual.name());
				tooltip.addPara(sourceKindLabel(source.getKind()) + ": " + source.getId(),
						4f, BattleNext_UiTheme.MUTED);
			}
		}, TooltipLocation.RIGHT);
	}

	private void addDamageColumns(TooltipMakerAPI host, float y, float width, double[] values) {
		String[] labels = {text("hull"), text("armor"), text("shield"), text("emp")};
		float columnWidth = width / values.length;
		for (int index = 0; index < values.length; index++) {
			String value = labels[index] + ": " + formatDamage(values[index]);
			LabelAPI label = host.addPara(host.shortenString(value, Math.max(1f, columnWidth - 4f)),
					0f, BattleNext_UiTheme.MUTED);
			label.getPosition().inTL(index * columnWidth, y);
		}
	}

	static float detailControlWidth(float contentWidth) {
		return Math.max(1f, contentWidth - DETAIL_CONTROL_SIDE_RESERVE);
	}

	static String nameForRefresh(BattleNext_BattleReportModel report, String editedName) {
		return editedName == null ? report.getName() : editedName;
	}

	static float memberBodyHeight(int sourceCount) {
		return MEMBER_BODY_INSET * 2f + MEMBER_BODY_LINE_HEIGHT
				+ (SOURCE_ROW_HEIGHT + SOURCE_ROW_GAP) * Math.max(1, sourceCount);
	}

	static float sourceTextWidth(float rowWidth) {
		return Math.max(1f, rowWidth - MEMBER_THUMB_SIZE - 8f - MEMBER_BODY_INSET);
	}

	static String formatDamage(double value) {
		return String.format(Locale.ROOT, "%,.1f", value);
	}

	static List<BattleNext_DamageSourceReport> sourcesFor(MemberRow row) {
		return row.dealt() ? row.member().getDealtBySource() : row.member().getTakenBySource();
	}

	static HullSummary resolveHull(String variantId, Function<String, ShipVariantAPI> variants) {
		ShipVariantAPI variant = variants.apply(variantId);
		ShipHullSpecAPI hull = variant == null ? null : variant.getHullSpec();
		if (hull == null) return new HullSummary(variantId, "");
		String name = hull.getHullName();
		String sprite = hull.getSpriteName();
		return new HullSummary(name == null || name.isEmpty() ? variantId : name,
				sprite == null ? "" : sprite);
	}

	private ShipVariantAPI loadVariant(String id) {
		if (id == null || id.isEmpty()) return null;
		try {
			return Global.getSettings().getVariant(id);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private boolean addSprite(TooltipMakerAPI host, String path) {
		if (path == null || path.isEmpty()) return false;
		try {
			Global.getSettings().getSprite(path);
			host.addImage(path, MEMBER_THUMB_SIZE, MEMBER_THUMB_SIZE, 0f);
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	static String statusKey(String status) {
		return switch (status) {
			case "destroyed" -> "status_destroyed";
			case "retreated" -> "status_retreated";
			default -> "status_alive";
		};
	}

	private String statusLabel(String status) {
		return reportStrings.get(statusKey(status));
	}

	private String totalLabel(MemberRow row) {
		return (row.dealt() ? text("damage_dealt") : text("damage_taken")) + ": "
				+ (row.dealt() ? row.member().getDamageDealt() : row.member().getDamageTaken());
	}

	static double[] damageValues(MemberRow row) {
		BattleNext_MemberReport member = row.member();
		boolean dealt = row.dealt();
		return new double[]{
				dealt ? member.getHullDamageDealt() : member.getHullDamageTaken(),
				dealt ? member.getArmorDamageDealt() : member.getArmorDamageTaken(),
				dealt ? member.getShieldDamageDealt() : member.getShieldDamageTaken(),
				dealt ? member.getEmpDamageDealt() : member.getEmpDamageTaken()
		};
	}

	private String sourceKindLabel(String kind) {
		return switch (kind) {
			case "weapon" -> reportStrings.get("source_weapon");
			case "fighter_wing" -> reportStrings.get("source_fighter_wing");
			case "ship" -> reportStrings.get("source_ship");
			case "projectile_spec" -> reportStrings.get("source_projectile");
			case "source_type" -> reportStrings.get("source_type");
			case "damage_type" -> reportStrings.get("source_damage_type");
			default -> kind;
		};
	}

	static List<BattleNext_DamageSourceReport> sortedSources(List<BattleNext_DamageSourceReport> sources) {
		List<BattleNext_DamageSourceReport> ordered = new ArrayList<>(sources);
		ordered.sort(Comparator.comparingDouble(BattleNext_DamageSourceReport::getTotal).reversed());
		return ordered;
	}

	static void toggleExpanded(Set<MemberRow> expanded, MemberRow row) {
		if (!expanded.add(row)) expanded.remove(row);
	}

	static record MemberRow(BattleNext_MemberReport member, boolean dealt) { }
	static record HullSummary(String name, String spritePath) { }

	private static class SourceRowPlugin extends BaseCustomUIPanelPlugin {
		private PositionAPI position;

		private void setPosition(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.lineStrip(new float[]{
					position.getX(), position.getY() + position.getHeight(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight()
			}, BattleNext_UiTheme.MUTED, alphaMult * 0.6f, 1f);
		}
	}

	private List<BattleNext_MemberReport> sorted(BattleNext_BattleReportModel report, boolean dealt) {
		List<BattleNext_MemberReport> result = new ArrayList<>(report.getPlayerMembers());
		result.addAll(report.getEnemyMembers());
		result.sort(BattleNext_MemberReport.damageOrder(dealt));
		return result;
	}

	private String context(BattleNext_TournamentModel tournament, String matchId, int roundNumber) {
		BattleNext_Match match = tournament.findMatch(matchId);
		if (match == null || match.getEnemy().isEmpty()) {
			return text("tournament_context") + " " + tournament.getId() + " / " + matchId;
		}
		return text("tournament_context") + " " + tournament.getId() + " / "
				+ BattleNext_MatchProgress.tournament(tournament, match, roundNumber).format(strings);
	}

	private String listLabel(BattleReportListItem entry) {
		String time = timeText(entry.report);
		return entry.report.getName() + " / " + (time.isEmpty() ? "" : time + " / ") + entry.report.getWinner()
				+ " / " + (entry.report.getPlayerMembers().size() + entry.report.getEnemyMembers().size());
	}

	private String timeText(BattleNext_BattleReportModel report) {
		if (report.getCreatedAt() <= 0L) return "";
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(report.getCreatedAt()));
	}

	private BattleReportListItem entry(String id) {
		for (BattleReportListItem entry : entries) {
			if (entry.report.getId().equals(id)) return entry;
		}
		return null;
	}

	private void deleteReport(BattleReportListItem entry) throws IOException, JSONException {
		if (entry.tournament == null) {
			reportStore.delete(entry.report.getId());
			return;
		}
		entry.round.setReport(new BattleNext_BattleReportModel());
		try {
			tournamentStore.save(entry.tournament);
		} catch (IOException | JSONException | RuntimeException exception) {
			entry.round.setReport(entry.report);
			throw exception;
		}
	}

	@Override
	public String getStatusText() {
		return status;
	}

	@Override
	public boolean hasUnsavedChanges() {
		return nameField != null && !nameField.getText().equals(savedName);
	}

	@Override
	public boolean save() {
		if (nameField == null || entry(selectedId) == null) return true;
		String nextName = nameField.getText().trim();
		if (nextName.isEmpty()) {
			setStatus(text("invalid_report_name"));
			return false;
		}
		BattleReportListItem selected = entry(selectedId);
		try {
			persistName(selected.report, selected.tournament, reportStore, tournamentStore, nextName);
			savedName = nextName;
			nameField.setText(nextName);
			setStatus(text("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(text("save_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	static void persistName(BattleNext_BattleReportModel report, BattleNext_TournamentModel tournament,
			BattleNext_BattleReportStore reports, BattleNext_TournamentStore tournaments, String name)
			throws IOException, JSONException {
		String nextName = name == null ? "" : name.trim();
		if (nextName.isEmpty()) throw new IllegalArgumentException("Report name is empty");
		String previous = report.getName();
		report.setName(nextName);
		try {
			if (tournament == null) reports.save(report);
			else tournaments.save(tournament);
		} catch (IOException | JSONException | RuntimeException exception) {
			report.setName(previous);
			throw exception;
		}
	}

	@Override
	public void discard() {
		if (nameField != null) nameField.setText(savedName);
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		if (key.equals("damage_dealt") || key.equals("damage_taken") || key.equals("winner")
				|| key.equals("duration") || key.equals("status") || key.equals("hull") || key.equals("shield")
				|| key.equals("armor") || key.equals("emp") || key.equals("final_hull") || key.equals("final_cr")) return reportStrings.get(key);
		return strings.get(key);
	}

	private static final class BattleReportListItem {
		private final BattleNext_BattleReportModel report;
		private final String context;
		private final BattleNext_TournamentModel tournament;
		private final BattleNext_Round round;

		private BattleReportListItem(BattleNext_BattleReportModel report, String context,
				BattleNext_TournamentModel tournament, BattleNext_Round round) {
			this.report = report;
			this.context = context;
			this.tournament = tournament;
			this.round = round;
		}
	}
}
