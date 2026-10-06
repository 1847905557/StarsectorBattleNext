package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_ReportTarget;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.page.BattleNext_ReportSourceDisplay;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

public class BattleNext_ReportDetails extends BattleNext_PanelBasedWidget {

	private static final float REPORT_SUMMARY_TOP_GAP = 8f;
	private static final float REPORT_CONTEXT_TOP_GAP = 6f;
	private static final float REPORT_SECTION_TOP_GAP = 10f;
	private static final float DETAIL_CONTROL_SIDE_RESERVE = 12f;
	private static final float MEMBER_THUMB_SIZE = 32f;
	private static final float MEMBER_BODY_INSET = 8f;
	private static final float MEMBER_BODY_LINE_HEIGHT = 24f;
	private static final float SOURCE_ROW_HEIGHT = 40f;
	private static final float SOURCE_ROW_GAP = 4f;
	private static final float SOURCE_TEXT_GAP = 2f;

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection reportStrings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");
	private final Set<MemberRow> expandedMembers = new HashSet<>();
	private final Map<String, MemberRow> memberActions = new HashMap<>();
	private int memberActionIndex;
	private final BattleNext_ReportTarget target;
	private final Runnable refresh;
	private TooltipMakerAPI content;

	public BattleNext_ReportDetails(BattleNext_UiInputRouter router, BattleNext_ReportTarget target, Runnable refresh) {
		super(router);
		this.target = target;
		this.refresh = refresh;
	}

	public void clear() {
		expandedMembers.clear();
		memberActions.clear();
		content = null;
	}

	public TooltipMakerAPI getContent() {
		return content;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		float offset = content == null ? 0f : content.getExternalScroller().getYOffset();
		memberActions.clear();
		memberActionIndex = 0;
		content = panel.createUIElement(width, height, true);
		BattleNext_BattleReportModel report = target.report();
		content.addPara(report.getId(), REPORT_CONTEXT_TOP_GAP);
		String time = report.getCreatedAt() <= 0L ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(report.getCreatedAt()));
		content.addPara((time.isEmpty() ? "" : time + " / ") + strings.get("winner") + ": " + report.getWinner()
				+ " / " + reportStrings.get("duration") + ": " + report.getDurationMillis() + " ms", REPORT_SUMMARY_TOP_GAP);
		content.addPara(reportStrings.get("damage_dealt"), REPORT_SECTION_TOP_GAP);
		appendMembers(content, panel, width, sorted(report, true));
		content.addPara(reportStrings.get("damage_taken"), REPORT_SECTION_TOP_GAP);
		appendMembers(content, panel, width, sorted(report, false));
		panel.addUIElement(content).inTL(0f, 0f);
		content.getExternalScroller().setYOffset(offset);
	}

	public void buttonPressed(Object action) {
		if (!inputRouter.isEmpty()) return;
		if (memberActions.containsKey(action)) {
			toggleExpanded(expandedMembers, memberActions.get(action));
			refresh.run();
		}
	}

	private void appendMembers(TooltipMakerAPI detailContent, CustomPanelAPI detailPanel, float contentWidth,
			List<MemberRow> members) {
		for (MemberRow row : members) {
			BattleNext_MemberReport member = row.member();
			String action = "member:" + memberActionIndex++;
			memberActions.put(action, row);
			boolean expanded = expandedMembers.contains(row);
			float width = detailControlWidth(contentWidth);
			List<BattleNext_DamageSourceReport> sources = sourcesFor(row);
			float bodyHeight = expanded ? memberBodyHeight(sources.size()) : 0f;
			HullSummary hull = resolveHull(member.getHullId(), this::loadHull);
			String shipName = member.getName().isEmpty() ? member.getVariantId() : member.getName();
			String side = reportStrings.get(row.playerSide() ? "side_player" : "side_enemy");
			BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
					hull.spritePath(), false, "", shipName + " - " + side, hull.name(),
					totalLabel(row) + "  " + statusLabel(member.getStatus()),
					reportStrings.get("final_hull") + ": " + formatPercent(member.getFinalHullFraction())
							+ "  " + reportStrings.get("final_cr") + ": " + formatPercent(member.getFinalCr()), null);
			LabelAPI nameLabel = BattleNext_ExpandablePanel.add(detailContent, detailPanel, width, expanded, bodyHeight,
					header, () -> buttonPressed(action), this::buttonPressed,
					bodyPanel -> addMemberBody(bodyPanel, row, sources, width, bodyHeight));
			nameLabel.highlightLast(side);
			nameLabel.setHighlightColor(row.playerSide() ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor());
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
			emptyHost.addPara(reportStrings.get("no_damage_sources"), 0f)
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
		TooltipMakerAPI icon = sourcePanel.createUIElement(MEMBER_THUMB_SIZE, MEMBER_THUMB_SIZE, false);
		float iconY = (SOURCE_ROW_HEIGHT - MEMBER_THUMB_SIZE) * 0.5f;
		if (visual.accentIcon()) {
			try {
				plugin.accentSprite = Global.getSettings().getSprite("report", "BattleNext_arc");
			} catch (RuntimeException exception) {
				plugin.missingIcon = true;
			}
		} else if (addSprite(icon, visual.primarySprite()) || addSprite(icon, visual.secondarySprite())) {
			icon.getPrev().getPosition().inTL(0f, 0f);
			icon.setHeightSoFar(MEMBER_THUMB_SIZE);
			sourcePanel.addUIElement(icon).inTL(0f, iconY);
		} else if ("weapon".equals(source.getKind()) || "fighter_wing".equals(source.getKind())
				|| "ship".equals(source.getKind()) || "source_type".equals(source.getKind())
				|| "?".equals(visual.placeholder())) {
			plugin.missingIcon = true;
		} else {
			icon.addPara(visual.placeholder(), 0f).getPosition().inMid();
			icon.setHeightSoFar(MEMBER_THUMB_SIZE);
			sourcePanel.addUIElement(icon).inTL(0f, iconY);
		}
		float textX = MEMBER_THUMB_SIZE + 8f;
		float textWidth = sourceTextWidth(width);
		TooltipMakerAPI labels = sourcePanel.createUIElement(textWidth, SOURCE_ROW_HEIGHT, false);
		String name = visual.resolved() ? visual.name() : sourceKindLabel(source.getKind()) + ": " + visual.name();
		LabelAPI nameLabel = labels.addPara(labels.shortenString(name, Math.max(1f, textWidth - 4f)), 0f);
		LabelAPI[] damageLabels = addDamageColumns(labels, 0f, textWidth,
				new double[]{source.getHull(), source.getArmor(), source.getShields(), source.getEmp()});
		float nameHeight = nameLabel.getPosition().getHeight();
		float damageHeight = 0f;
		for (LabelAPI label : damageLabels) {
			damageHeight = Math.max(damageHeight, label.getPosition().getHeight());
		}
		float textGap = Math.min(SOURCE_TEXT_GAP, Math.max(0f, SOURCE_ROW_HEIGHT - nameHeight - damageHeight));
		float textTop = (SOURCE_ROW_HEIGHT - nameHeight - textGap - damageHeight) * 0.5f;
		nameLabel.getPosition().inTL(0f, textTop);
		for (int index = 0; index < damageLabels.length; index++) {
			damageLabels[index].getPosition().inTL(index * textWidth / damageLabels.length,
					textTop + nameHeight + textGap);
		}
		labels.setHeightSoFar(SOURCE_ROW_HEIGHT);
		sourcePanel.addUIElement(labels).inTL(textX, 0f);
		content.addCustom(sourcePanel, SOURCE_ROW_GAP);
		content.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return BattleNext_EquipmentTooltip.WIDTH;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				switch (source.getKind()) {
					case "weapon" ->
							BattleNext_EquipmentTooltip.render(tooltip, BattleNext_EquipmentTooltip.description(visual.name(), source.getId(), source.getId(), Description.Type.WEAPON, Global.getSettings(), List.of()));
					case "fighter_wing" ->
							BattleNext_EquipmentTooltip.render(tooltip, BattleNext_EquipmentTooltip.wing(visual.name(), source.getId(), Global.getSettings(), List.of()));
					case "ship" -> {
						try {
							ShipHullSpecAPI hull = Global.getSettings().getHullSpec(source.getId());
							if (hull == null) throw new IllegalArgumentException("Unknown hull: " + source.getId());
							BattleNext_EquipmentTooltip.render(tooltip, BattleNext_EquipmentTooltip.hull(visual.name(), source.getId(), hull, Global.getSettings(), List.of()));
						} catch (RuntimeException exception) {
							BattleNext_EquipmentTooltip.render(tooltip, new BattleNext_EquipmentTooltip.Content(visual.name(), source.getId(), "", List.of(exception.toString())));
						}
					}
					default -> {
						tooltip.addTitle(visual.name());
						BattleNext_PageLayout.addMutedIdLine(tooltip, sourceKindLabel(source.getKind()) + ": " + source.getId(), 4f);
					}
				}
			}
		}, TooltipLocation.RIGHT);
	}

	private LabelAPI[] addDamageColumns(TooltipMakerAPI host, float y, float width, double[] values) {
		String[] labels = {reportStrings.get("hull"), reportStrings.get("armor"), reportStrings.get("shield"), reportStrings.get("emp")};
		LabelAPI[] result = new LabelAPI[values.length];
		float columnWidth = width / values.length;
		for (int index = 0; index < values.length; index++) {
			String value = labels[index] + ": " + formatDamage(values[index]);
			LabelAPI label = host.addPara(host.shortenString(value, Math.max(1f, columnWidth - 4f)), 0f);
			label.getPosition().inTL(index * columnWidth, y);
			result[index] = label;
		}
		return result;
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

	static String formatPercent(float fraction) {
		return String.format(Locale.ROOT, "%.1f%%", fraction * 100f);
	}

	static List<BattleNext_DamageSourceReport> sourcesFor(MemberRow row) {
		return row.dealt() ? row.member().getDealtBySource() : row.member().getTakenBySource();
	}

	static HullSummary resolveHull(String hullId, Function<String, ShipHullSpecAPI> hulls) {
		ShipHullSpecAPI hull = hulls.apply(hullId);
		if (hull == null) return new HullSummary(hullId, "");
		String name = hull.getHullName();
		String sprite = hull.getSpriteName();
		return new HullSummary(name == null || name.isEmpty() ? hullId : name,
				sprite == null ? "" : sprite);
	}

	private ShipHullSpecAPI loadHull(String id) {
		if (id == null || id.isEmpty()) return null;
		try {
			return Global.getSettings().getHullSpec(id);
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
		return (row.dealt() ? reportStrings.get("damage_dealt") : reportStrings.get("damage_taken")) + ": "
				+ formatDamage(row.dealt() ? row.member().getDamageDealt() : row.member().getDamageTaken());
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
			case "arc" -> reportStrings.get("source_arc");
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

	record MemberRow(BattleNext_MemberReport member, boolean dealt, boolean playerSide) { }
	record HullSummary(String name, String spritePath) { }

	private static class SourceRowPlugin extends BaseCustomUIPanelPlugin {
		private PositionAPI position;
		private boolean missingIcon;
		private SpriteAPI accentSprite;

		private void setPosition(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.lineStrip(new float[]{
					position.getX(), position.getY() + position.getHeight(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight()
			}, BattleNext_UiTheme.PANEL, alphaMult * 0.6f, 1f);
		}

		@Override
		public void render(float alphaMult) {
			if (position == null) return;
			if (accentSprite != null) {
				BattleNext_PageLayout.renderTintedFittedSprite(accentSprite,
						position.getX() + MEMBER_THUMB_SIZE * 0.5f,
						position.getY() + SOURCE_ROW_HEIGHT * 0.5f,
						MEMBER_THUMB_SIZE, MEMBER_THUMB_SIZE, BattleNext_UiTheme.ACCENT, alphaMult);
				return;
			}
			if (!missingIcon) return;
			float size = MEMBER_THUMB_SIZE * 0.75f;
			float x = position.getX() + (MEMBER_THUMB_SIZE - size) * 0.5f;
			float y = position.getY() + (SOURCE_ROW_HEIGHT - size) * 0.5f;
			BattleNext_PageLayout.renderMissingIcon(BattleNext_PageLayout.missingIcon(), x, y, size, alphaMult);
		}
	}

	private List<MemberRow> sorted(BattleNext_BattleReportModel report, boolean dealt) {
		List<MemberRow> result = new ArrayList<>();
		for (BattleNext_MemberReport member : report.getPlayerMembers()) {
			result.add(new MemberRow(member, dealt, true));
		}
		for (BattleNext_MemberReport member : report.getEnemyMembers()) {
			result.add(new MemberRow(member, dealt, false));
		}
		result.sort(Comparator.comparing(MemberRow::member, BattleNext_MemberReport.damageOrder(dealt)));
		return result;
	}
}
