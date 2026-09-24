package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_FleetCatalog;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_VariantModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.util.BattleNext_I18nSection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BattleNext_FleetDraftEditor {

	private final Runnable backAction;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_FleetCatalog catalog = new BattleNext_FleetCatalog();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "");
	private final List<MemberFields> memberFields = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI heading;
	private TooltipMakerAPI metaContent;
	private TooltipMakerAPI memberContent;
	private TooltipMakerAPI footer;
	private LabelAPI status;
	private String statusText = "";
	private TextFieldAPI id;
	private TextFieldAPI name;
	private TextFieldAPI player;
	private TextFieldAPI author;
	private TextFieldAPI description;
	private TextFieldAPI behavior;
	private TextFieldAPI skills;
	private TextFieldAPI tags;
	private TextFieldAPI rulesSlot;
	private BattleNext_FleetModel draft;
	private String savedSnapshot = "";

	public BattleNext_FleetDraftEditor(Runnable backAction) {
		this.backAction = backAction;
		catalog.refresh();
	}

	public void attach(CustomPanelAPI panel, BattleNext_FleetModel source, boolean clone) {
		if (this.panel != panel) {
			heading = null;
			metaContent = null;
			memberContent = null;
			footer = null;
		}
		this.panel = panel;
		this.draft = source == null ? new BattleNext_FleetModel() : copy(source, clone);
		rebuild();
		savedSnapshot = serializeFields();
	}

	public boolean hasUnsavedChanges() {
		return !serializeFields().equals(savedSnapshot);
	}

	public String getStatusText() {
		return statusText;
	}

	public void advance() {
	}

	public void processInput(List<InputEventAPI> events) {
	}

	public void buttonPressed(Object buttonId) {
		if ("save".equals(buttonId)) {
			save();
		} else if ("add_member".equals(buttonId)) {
			if (!captureDraft(false)) return;
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembers());
			members.add(new BattleNext_MemberModel());
			draft.setMembers(members);
			rebuild();
		} else if ("back".equals(buttonId)) {
			backAction.run();
		}
	}

	private void rebuild() {
		if (heading != null) panel.removeComponent(heading);
		if (metaContent != null) panel.removeComponent(metaContent);
		if (memberContent != null) panel.removeComponent(memberContent);
		if (footer != null) panel.removeComponent(footer);

		heading = BattleNext_PageLayout.heading(panel, text("BattleNext_ui_fleet_editor"));
		float metaWidth = BattleNext_PageLayout.listWidth(panel);
		metaContent = BattleNext_PageLayout.column(panel, 0f, metaWidth);
		memberContent = BattleNext_PageLayout.column(panel, metaWidth + 10f,
				panel.getPosition().getWidth() - metaWidth - 10f);
		footer = BattleNext_PageLayout.footer(panel);
		metaContent.addTitle(text("BattleNext_ui_fleet_section_details"));
		status = metaContent.addPara(text("BattleNext_ui_ready"), 6f, BattleNext_UiTheme.MUTED);
		id = field(metaContent, text("BattleNext_ui_fleet_id"), draft.getId());
		name = field(metaContent, text("BattleNext_ui_fleet_name"), draft.getName());
		player = field(metaContent, text("BattleNext_ui_fleet_player"), draft.getPlayer());
		author = field(metaContent, text("BattleNext_ui_fleet_author"), draft.getAuthor());
		description = field(metaContent, text("BattleNext_ui_fleet_description"), draft.getDescription());
		behavior = field(metaContent, text("BattleNext_ui_fleet_behavior"), draft.getBehavior());
		skills = field(metaContent, text("BattleNext_ui_fleet_skills"), String.join(",", draft.getSkills()));
		tags = field(metaContent, text("BattleNext_ui_fleet_tags"), String.join(",", draft.getTags()));
		rulesSlot = field(metaContent, text("BattleNext_ui_fleet_rules"), Integer.toString(draft.getRulesSlot()));
		memberContent.addTitle(text("BattleNext_ui_fleet_section_members"));
		memberFields.clear();
		for (int index = 0; index < draft.getMembers().size(); index++) {
			addMember(index, draft.getMembers().get(index));
		}
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_add_member"), "add_member", 180f).getPosition().inBL(0f, 0f);
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_save"), "save", 180f).getPosition().inBL(190f, 0f);
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_back"), "back", 180f).getPosition().inBL(380f, 0f);
	}

	private void addMember(int index, BattleNext_MemberModel member) {
		memberContent.addSectionHeading(text("BattleNext_ui_member") + " " + (index + 1),
				BattleNext_UiTheme.TEXT, BattleNext_UiTheme.HEADER,
				com.fs.starfarer.api.ui.Alignment.MID, 8f);
		MemberFields fields = new MemberFields();
		fields.variant = field(memberContent, text("BattleNext_ui_variant"), member.getVariantId());
		fields.name = field(memberContent, text("BattleNext_ui_member_name"), member.getName());
		fields.personality = field(memberContent, text("BattleNext_ui_personality"), member.getPersonality());
		fields.skills = field(memberContent, text("BattleNext_ui_member_skills"), String.join(",", member.getSkills()));
		fields.portrait = field(memberContent, text("BattleNext_ui_portrait"), member.getPortrait());
		fields.orders = field(memberContent, text("BattleNext_ui_orders"), member.getOrders());
		fields.tags = field(memberContent, text("BattleNext_ui_member_tags"), String.join(",", member.getTags()));
		memberFields.add(fields);
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value) {
		host.addPara(label, 5f, BattleNext_UiTheme.MUTED);
		TextFieldAPI field = host.addTextField(Math.min(300f, host.getPosition().getWidth() - 20f), 2f);
		field.setText(value == null ? "" : value);
		field.setMaxChars(240);
		return field;
	}

	public boolean save() {
		if (!captureDraft(true)) return false;
		try {
			store.save(draft);
			savedSnapshot = serializeFields();
			setStatus(text("BattleNext_ui_saved"));
			return true;
		} catch (Exception exception) {
			setStatus(text("BattleNext_ui_save_failed"));
			return false;
		}
	}

	private boolean captureDraft(boolean validate) {
		String fleetId = id.getText().trim();
		if ((validate && fleetId.isEmpty()) || fleetId.contains("/") || fleetId.contains("\\")) {
			setStatus(text("BattleNext_ui_invalid_fleet_id"));
			return false;
		}
		draft.setId(fleetId);
		draft.setName(name.getText());
		draft.setPlayer(player.getText());
		draft.setAuthor(author.getText());
		draft.setDescription(description.getText());
		draft.setBehavior(behavior.getText());
		draft.setSkills(split(skills.getText()));
		draft.setTags(split(tags.getText()));
		try {
			draft.setRulesSlot(Integer.parseInt(rulesSlot.getText().trim()));
		} catch (NumberFormatException exception) {
			setStatus(text("BattleNext_ui_invalid_rules_slot"));
			return false;
		}
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (MemberFields fields : memberFields) {
			String variantId = fields.variant.getText().trim();
			if (validate && variantId.isEmpty()) {
				setStatus(text("BattleNext_ui_invalid_variant"));
				return false;
			}
			if (!variantId.isEmpty()) {
				try {
					if (draft.getVariant(variantId) == null) draft.putVariant(BattleNext_VariantModel.fromRegistered(variantId));
				} catch (Exception exception) {
					if (validate) {
						setStatus(text("BattleNext_ui_invalid_variant"));
						return false;
					}
				}
			}
			members.add(new BattleNext_MemberModel(variantId, fields.name.getText(), fields.personality.getText(),
					split(fields.skills.getText()), fields.portrait.getText(), fields.orders.getText(), split(fields.tags.getText())));
		}
		draft.setMembers(members);
		return true;
	}

	private String serializeFields() {
		if (id == null) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{id, name, player, author, description, behavior, skills, tags, rulesSlot}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		for (MemberFields member : memberFields) {
			for (TextFieldAPI field : new TextFieldAPI[]{member.variant, member.name, member.personality,
					member.skills, member.portrait, member.orders, member.tags}) {
				value.append(field.getText().length()).append(':').append(field.getText());
			}
		}
		return value.toString();
	}

	private BattleNext_FleetModel copy(BattleNext_FleetModel source, boolean clone) {
		BattleNext_FleetModel result = new BattleNext_FleetModel(source.getId(), source.getName(), source.getPlayer(),
				source.getSkills(), source.getTags(), source.getMembers());
		result.setAuthor(source.getAuthor());
		result.setDescription(source.getDescription());
		result.setBehavior(source.getBehavior());
		result.setColor(source.getColor());
		result.setVsImage(source.getVsImage());
		result.setRulesSlot(source.getRulesSlot());
		for (BattleNext_VariantModel variant : source.getVariants()) result.putVariant(variant);
		if (clone) result.setId(source.getId() + "_copy");
		return result;
	}

	private List<String> split(String value) {
		if (value == null || value.trim().isEmpty()) return List.of();
		return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
	}

	private void setStatus(String value) {
		statusText = value;
		status.setText(value);
	}

	private String text(String key) {
		return strings.get(key);
	}

	private static final class MemberFields {
		private TextFieldAPI variant;
		private TextFieldAPI name;
		private TextFieldAPI personality;
		private TextFieldAPI skills;
		private TextFieldAPI portrait;
		private TextFieldAPI orders;
		private TextFieldAPI tags;
	}
}
