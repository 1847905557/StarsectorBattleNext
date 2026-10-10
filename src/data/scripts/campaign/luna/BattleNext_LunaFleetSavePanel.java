package data.scripts.campaign.luna;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_TextField;
import org.lwjgl.input.Keyboard;

import java.util.*;
import java.util.function.Function;

public class BattleNext_LunaFleetSavePanel extends BaseCustomUIPanelPlugin {

	private static final float INSET = 10f;
	private final Runnable closeAction;
	private final BattleNext_FleetStore store;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection memberStrings = BattleNext_I18nSection.getInstance("Member", "BattleNext_member_");
	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
	private final Set<Integer> expandedMembers = new HashSet<>();
	private CustomPanelAPI panel;
	private CustomPanelAPI contentsPanel;
	private CustomPanelAPI membersPanel;
	private TooltipMakerAPI membersContent;
	private BattleNext_TextField name;
	private ButtonAPI saveButton;
	private BattleNext_FleetModel snapshot;
	private String commanderName = "";
	private String status = "";
	private boolean saved;
	private boolean closed = true;

	BattleNext_LunaFleetSavePanel(Runnable closeAction) {
		this(closeAction, new BattleNext_FleetStore());
	}

	BattleNext_LunaFleetSavePanel(Runnable closeAction, BattleNext_FleetStore store) {
		this.closeAction = closeAction;
		this.store = store;
	}

	void open(FleetMemberAPI selected, ShipVariantAPI variant, String error) {
		close();
		closed = false;
		saved = false;
		status = error;
		if (!error.isEmpty()) return;
		try {
			commanderName = Global.getSector().getPlayerPerson().getNameString();
			snapshot = BattleNext_CampaignFleetSnapshot.capture(Global.getSector().getPlayerFleet(),
					Global.getSector().getPlayerPerson(), selected, variant);
		} catch (Exception exception) {
			status = fleetStrings.get("player_save_snapshot_error") + ": " + exception;
			BattleNext_Logger.error(BattleNext_LunaFleetSavePanel.class, "fleet.player.snapshot.failed",
				BattleNext_Logger.fields().put("member", selected).put("variant", variant).build(), exception);
		}
	}

	void attach(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		BattleNext_UiInputRouter.activate(inputRouter);
		buildContents(snapshot == null ? "" : snapshot.getName());
	}

	private void buildContents(String displayName) {

		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		contentsPanel = panel.createCustomPanel(width, height, new ContentsPlugin());
		panel.addComponent(contentsPanel).inTL(0f, 0f);
		BattleNext_PageLayout.heading(new UIRect(INSET, INSET, width - INSET * 2f,
				BattleNext_PageLayout.HEADER_HEIGHT), contentsPanel, fleetStrings.get("player_save_action"));
		float top = INSET * 2f + BattleNext_PageLayout.HEADER_HEIGHT;
		TooltipMakerAPI content = contentsPanel.createUIElement(width - INSET * 2f, 0f, false);
		BattleNext_UiTheme.configure(content);
		content.addPara(fleetStrings.get("name"), 0f);
		name = new BattleNext_TextField(inputRouter, contentsPanel, width - INSET * 3f,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, content);
		name.attach(content, contentsPanel);
		name.setMaxChars(-1);
		name.setText(displayName);
		contentsPanel.addUIElement(content).inTL(INSET, top);
		float membersTop = top + content.getHeightSoFar() + INSET;
		membersPanel = contentsPanel.createCustomPanel(width - INSET * 2f,
				height - membersTop - INSET * 2f - BattleNext_PageLayout.FOOTER_HEIGHT, new ContentsPlugin());
		contentsPanel.addComponent(membersPanel).inTL(INSET, membersTop);
		float buttonWidth = Math.min(220f, (width - INSET * 3f) * 0.5f);
		float footerY = height - INSET - BattleNext_PageLayout.FOOTER_HEIGHT;
		saveButton = BattleNext_PageLayout.fixedButton(contentsPanel,
				new UIRect(INSET, footerY, buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get(saved ? "saved" : "save"), "fleet_capture_save", CutStyle.ALL, true);
		saveButton.setEnabled(!closed && snapshot != null && !saved);
		BattleNext_PageLayout.fixedButton(contentsPanel,
				new UIRect(width - INSET - buttonWidth, footerY, buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("close"), "fleet_capture_close", CutStyle.ALL, false);
		refreshMembers();
	}

	private void refreshMembers() {

		float offset = membersContent == null ? 0f : membersContent.getExternalScroller().getYOffset();
		float width = membersPanel.getPosition().getWidth();
		float height = membersPanel.getPosition().getHeight();
		if (membersContent != null) {
			float top = contentsPanel.getPosition().getY() + contentsPanel.getPosition().getHeight()
					- membersPanel.getPosition().getY() - height;
			contentsPanel.removeComponent(membersPanel);
			membersPanel = contentsPanel.createCustomPanel(width, height, new ContentsPlugin());
			contentsPanel.addComponent(membersPanel).inTL(INSET, top);
		}
		membersContent = membersPanel.createUIElement(width + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, height, true);
		BattleNext_UiTheme.configure(membersContent);
		membersContent.setTextWidthOverride(width);
		if (snapshot != null) {
			membersContent.addPara(fleetStrings.format("player_save_commander", commanderName), 0f);
			membersContent.addPara(fleetStrings.format("player_save_members", snapshot.getMembers().size()), BattleNext_ExpandablePanel.ROW_GAP);
			membersContent.addPara(fleetStrings.get("player_save_preview_hint"), BattleNext_ExpandablePanel.ROW_GAP);
		}
		if (!status.isEmpty()) {
			membersContent.addPara(status, INSET);
		}
		if (snapshot != null) {
			for (int index = 0; index < snapshot.getMembers().size(); index++) {
				BattleNext_MemberModel member = snapshot.getMembers().get(index);
				var json = snapshot.getVariant(member.getVariantId()).toJson();
				var hull = Global.getSettings().getHullSpec(json.optString("hullId"));
				BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(hull.getSpriteName(),
						true, member.getPortrait(), member.getName(), hull.getHullName() + " / " + json.optString("displayName"), "", "", null);
				int key = index;
				BattleNext_ExpandablePanel.add(membersContent, membersPanel, width, expandedMembers.contains(index), 0f,
						header, () -> buttonPressed(key), this::buttonPressed,
						body -> buildMemberBody(body, member, width));
			}
		}
		membersContent.addSpacer(INSET);
		membersPanel.addUIElement(membersContent).inTL(-BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET, 0f);
		membersContent.getExternalScroller().setYOffset(Math.min(offset, Math.max(0f, membersContent.getHeightSoFar() - height)));
		saveButton.setText(strings.get(saved ? "saved" : "save"));
		saveButton.setEnabled(!closed && snapshot != null && !saved);
	}

	private void buildMemberBody(CustomPanelAPI body, BattleNext_MemberModel member, float width) {

		ShipVariantAPI variant = snapshot.getVariant(member.getVariantId()).build();
		float textWidth = width - INSET * 2f;
		TooltipMakerAPI content = body.createUIElement(textWidth, 0f, false);
		BattleNext_UiTheme.configure(content);
		content.setTextWidthOverride(textWidth);
		content.addPara(memberStrings.get("ship_name") + ": " + member.getName(), 0f);
		String personality = fleetStrings.get("officer_none");
		if (!member.getPersonality().isEmpty()) {
			PersonAPI person = Global.getSettings().createPerson();
			person.setPersonality(member.getPersonality());
			personality = person.getPersonalityAPI().getDisplayName();
		}
		content.addPara(memberStrings.get("officer_personality") + ": " + personality, BattleNext_ExpandablePanel.ROW_GAP);
		Map<String, Integer> skills = new LinkedHashMap<>();
		for (String skill : member.getSkills()) {
			skills.merge(skill, 1, Integer::sum);
		}
		List<String> skillLabels = new ArrayList<>();
		for (String id : sortedIds(skills.keySet(), skill -> Global.getSettings().getSkillSpec(skill).getName())) {
			skillLabels.add(Global.getSettings().getSkillSpec(id).getName() + " / "
					+ strings.format("skill_effect_level", Integer.toString(skills.get(id))));
		}
		content.addPara(memberStrings.get("officer_skills") + ": " + (skillLabels.isEmpty()
				? variantStrings.get("none") : String.join(", ", skillLabels)), BattleNext_ExpandablePanel.ROW_GAP);
		content.addPara(variantStrings.get("flux_capacitors") + ": " + variant.getNumFluxCapacitors()
				+ " / " + variantStrings.get("flux_vents") + ": " + variant.getNumFluxVents(), BattleNext_ExpandablePanel.ROW_GAP);
		List<String> weapons = new ArrayList<>();
		for (String slot : variant.getFittedWeaponSlots()) {
			weapons.add(variant.getWeaponId(slot));
		}
		content.addPara(variantStrings.get("weapons") + ": " + equipmentSummary(weapons,
				id -> Global.getSettings().getWeaponSpec(id).getWeaponName()), BattleNext_ExpandablePanel.ROW_GAP);
		content.addPara(variantStrings.get("hullmods") + ": " + hullmodsSummary(variant), BattleNext_ExpandablePanel.ROW_GAP);
		content.addPara(variantStrings.get("fighter_wings") + ": " + equipmentSummary(variant.getFittedWings(),
				id -> Global.getSettings().getFighterWingSpec(id).getWingName()), BattleNext_ExpandablePanel.ROW_GAP);
		content.addPara(variantStrings.get("modules"), BattleNext_ExpandablePanel.ROW_GAP);
		if (variant.getStationModules().isEmpty()) {
			content.addPara(variantStrings.get("none"), BattleNext_ExpandablePanel.ROW_GAP);
		} else {
			appendModules(content, variant, 0);
		}
		float height = content.getHeightSoFar();
		content.getPosition().setSize(textWidth, height);
		body.getPosition().setSize(width, height + INSET * 2f);
		body.addUIElement(content).inTL(INSET - BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET, INSET);
	}

	private List<String> sortedIds(Collection<String> ids, Function<String, String> name) {
		Comparator<String> byName = Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER);
		Comparator<String> byId = Comparator.naturalOrder();
		return ids.stream()
				.sorted(byName.thenComparing(byId))
				.toList();
	}

	private String equipmentSummary(Collection<String> ids, Function<String, String> name) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String id : ids) {
			counts.merge(id, 1, Integer::sum);
		}
		List<String> values = new ArrayList<>();
		for (String id : sortedIds(counts.keySet(), name)) {
			values.add(name.apply(id) + " × " + counts.get(id));
		}
		return values.isEmpty() ? variantStrings.get("none") : String.join(", ", values);
	}

	private String hullmodsSummary(ShipVariantAPI variant) {

		Set<String> ids = new LinkedHashSet<>(variant.getHullSpec().getBuiltInMods());
		ids.addAll(variant.getHullMods());
		ids.addAll(variant.getPermaMods());
		ids.addAll(variant.getSMods());
		ids.addAll(variant.getSModdedBuiltIns());
		ids.addAll(variant.getSuppressedMods());
		List<String> values = new ArrayList<>();
		for (String id : sortedIds(ids, mod -> Global.getSettings().getHullModSpec(mod).getDisplayName())) {
			var spec = Global.getSettings().getHullModSpec(id);
			List<String> markers = new ArrayList<>();
			if (variant.getSMods().contains(id) || variant.getSModdedBuiltIns().contains(id)) {
				markers.add(variantStrings.get("s_mod"));
			}
			if (spec.hasTag(Tags.HULLMOD_DMOD)) {
				markers.add(variantStrings.get("d_mod"));
			}
			if (variant.getHullSpec().getBuiltInMods().contains(id)) {
				markers.add(variantStrings.get("built_in"));
			}
			if (variant.getSuppressedMods().contains(id)) {
				markers.add(variantStrings.get("suppressed"));
			}
			values.add(spec.getDisplayName() + (markers.isEmpty() ? "" : " [" + String.join(", ", markers) + "]"));
		}
		return values.isEmpty() ? variantStrings.get("none") : String.join(", ", values);
	}

	private void appendModules(TooltipMakerAPI content, ShipVariantAPI variant, int depth) {
		for (String slot : sortedIds(variant.getStationModules().keySet(),
				id -> variant.getModuleVariant(id).getHullSpec().getHullName())) {
			ShipVariantAPI module = variant.getModuleVariant(slot);
			content.addPara("    ".repeat(depth) + "• " + module.getHullSpec().getHullName() + " / " + slot,
					BattleNext_ExpandablePanel.ROW_GAP);
			appendModules(content, module, depth + 1);
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {

		if (closed) return;
		if ("fleet_capture_close".equals(buttonId)) {
			closeAction.run();
			return;
		}
		if (buttonId instanceof Integer index) {
			if (!expandedMembers.add(index)) expandedMembers.remove(index);
			refreshMembers();
			return;
		}
		if (!"fleet_capture_save".equals(buttonId) || saved || snapshot == null) return;
		String displayName = name.getText();
		if (displayName.trim().isEmpty()) {
			BattleNext_Logger.warn(BattleNext_LunaFleetSavePanel.class, "save.name.rejected",
				BattleNext_Logger.fields().put("display_name", displayName).put("criterion", "Nonblank trimmed name").build());
			status = fleetStrings.get("player_save_name_required");
			refreshMembers();
			return;
		}
		try {
			store.reload();
			snapshot.setName(displayName.trim());
			store.save(snapshot);
			saved = true;
			status = fleetStrings.format("player_save_saved", snapshot.getName(), snapshot.getSourcePath());
		} catch (Exception exception) {
			status = strings.get("save_failed") + ": " + exception;
			BattleNext_Logger.error(BattleNext_LunaFleetSavePanel.class, "fleet.player.save.failed",
				BattleNext_Logger.fields().put("fleet", snapshot).put("display_name", displayName).build(), exception);
		}
		refreshMembers();
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		if (closed) return;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			boolean escape = event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE;
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
		if (name != null) name.close();
		inputRouter.clear();
		if (saveButton != null) saveButton.setEnabled(false);
		if (contentsPanel != null) panel.removeComponent(contentsPanel);
		contentsPanel = null;
		membersPanel = null;
		membersContent = null;
		expandedMembers.clear();
		panel = null;
		name = null;
		saveButton = null;
		snapshot = null;
		commanderName = "";
		status = "";
		closed = true;
	}

	private class ContentsPlugin extends BaseCustomUIPanelPlugin {
		@Override
		public void buttonPressed(Object buttonId) {
			BattleNext_LunaFleetSavePanel.this.buttonPressed(buttonId);
		}
	}
}
