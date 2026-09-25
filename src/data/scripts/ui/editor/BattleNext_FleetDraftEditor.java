package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetCatalog;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_VariantModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BattleNext_FleetDraftEditor implements BattleNext_EditorView {

	private final Runnable backAction;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_FleetCatalog catalog = new BattleNext_FleetCatalog();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final List<MemberFields> memberFields = new ArrayList<>();
	private CustomPanelAPI panel;
	private UIRect metaRect;
	private UIRect memberRect;
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
	private BattleNext_FleetModel baseline;
	private boolean baselineClone;
	private String savedSnapshot = "";

	public BattleNext_FleetDraftEditor(Runnable backAction) {
		this.backAction = backAction;
		catalog.refresh();
	}

	public void open(BattleNext_FleetModel source, boolean clone) {
		baseline = source;
		baselineClone = clone;
		draft = source == null ? new BattleNext_FleetModel() : copy(source, clone);
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		createContents();
		savedSnapshot = serializeState();
	}

	@Override
	public boolean hasUnsavedChanges() {
		return !serializeState().equals(savedSnapshot);
	}

	@Override
	public String getStatusText() {
		return statusText;
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("save".equals(buttonId)) {
			save();
		} else if ("add_member".equals(buttonId)) {
			if (!captureDraft(false)) return;
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembers());
			members.add(new BattleNext_MemberModel());
			draft.setMembers(members);
			refreshContent();
		} else if ("back".equals(buttonId)) {
			backAction.run();
		}
	}

	// left meta fields / right member list / bottom buttons
	@Override
	public void createContents() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] bottom = rest[1].splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		float metaWidth = BattleNext_PageLayout.listWidth(panel);
		UIRect[] columns = bottom[0].splitHorizontally(metaWidth + 10f);
		metaRect = columns[0];
		memberRect = columns[1];

		BattleNext_PageLayout.heading(header[0], panel, text("fleet_editor"));

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		TooltipMakerAPI footer = BattleNext_PageLayout.footer(footerRect, panel);
		BattleNext_PageLayout.button(footer, text("add_member"), "add_member", 180f).getPosition().inBL(0f, 0f);
		BattleNext_PageLayout.button(footer, text("save"), "save", 180f).getPosition().inBL(190f, 0f);
		BattleNext_PageLayout.button(footer, text("back"), "back", 180f).getPosition().inBL(380f, 0f);

		refreshContent();
	}

	@Override
	public void refreshContent() {
		metaRect.removeLatestSubPanel();
		CustomPanelAPI metaPanel = metaRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI metaContent = metaPanel.createUIElement(metaRect.getWidth(), metaRect.getHeight(), true);
		fillMeta(metaContent);
		metaPanel.addUIElement(metaContent).inTL(0f, 0f);

		memberRect.removeLatestSubPanel();
		CustomPanelAPI memberPanel = memberRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI memberContent = memberPanel.createUIElement(memberRect.getWidth(), memberRect.getHeight(), true);
		fillMembers(memberContent);
		memberPanel.addUIElement(memberContent).inTL(0f, 0f);
	}

	private void fillMeta(TooltipMakerAPI metaContent) {
		metaContent.addTitle(text("fleet_section_details"));
		status = metaContent.addPara(text("ready"), 6f, BattleNext_UiTheme.MUTED);
		id = field(metaContent, text("fleet_id"), draft.getId());
		name = field(metaContent, text("fleet_name"), draft.getName());
		player = field(metaContent, text("fleet_player"), draft.getPlayer());
		author = field(metaContent, text("fleet_author"), draft.getAuthor());
		description = field(metaContent, text("fleet_description"), draft.getDescription());
		behavior = field(metaContent, text("fleet_behavior"), draft.getBehavior());
		skills = field(metaContent, text("fleet_skills"), String.join(",", draft.getSkills()));
		tags = field(metaContent, text("fleet_tags"), String.join(",", draft.getTags()));
		rulesSlot = field(metaContent, text("fleet_rules"), Integer.toString(draft.getRulesSlot()));
	}

	private void fillMembers(TooltipMakerAPI memberContent) {
		memberContent.addTitle(text("fleet_section_members"));
		memberFields.clear();
		for (int index = 0; index < draft.getMembers().size(); index++) {
			addMember(index, draft.getMembers().get(index), memberContent);
		}
	}

	private void addMember(int index, BattleNext_MemberModel member, TooltipMakerAPI memberContent) {
		memberContent.addSectionHeading(text("member") + " " + (index + 1),
				BattleNext_UiTheme.TEXT, BattleNext_UiTheme.HEADER,
				com.fs.starfarer.api.ui.Alignment.MID, 8f);
		MemberFields fields = new MemberFields();
		fields.variant = field(memberContent, text("variant"), member.getVariantId());
		fields.name = field(memberContent, text("member_name"), member.getName());
		fields.personality = field(memberContent, text("personality"), member.getPersonality());
		fields.skills = field(memberContent, text("member_skills"), String.join(",", member.getSkills()));
		fields.portrait = field(memberContent, text("portrait"), member.getPortrait());
		fields.orders = field(memberContent, text("orders"), member.getOrders());
		fields.tags = field(memberContent, text("member_tags"), String.join(",", member.getTags()));
		memberFields.add(fields);
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value) {
		host.addPara(label, 5f, BattleNext_UiTheme.MUTED);
		TextFieldAPI field = host.addTextField(Math.min(300f, host.getPosition().getWidth() - 20f), 2f);
		field.setText(value == null ? "" : value);
		field.setMaxChars(240);
		return field;
	}

	@Override
	public boolean serialize() {
		if (!captureDraft(true)) return false;
		try {
			store.save(draft);
		} catch (Exception exception) {
			setStatus(text("save_failed"));
			return false;
		}
		savedSnapshot = serializeState();
		setStatus(text("saved"));
		return true;
	}

	@Override
	public void deserialize() {
		open(baseline, baselineClone);
		refreshContent();
		savedSnapshot = serializeState();
	}

	@Override
	public String serializeState() {
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

	private boolean captureDraft(boolean validate) {
		String fleetId = id.getText().trim();
		if ((validate && fleetId.isEmpty()) || fleetId.contains("/") || fleetId.contains("\\")) {
			setStatus(text("invalid_fleet_id"));
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
			setStatus(text("invalid_rules_slot"));
			return false;
		}
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (MemberFields fields : memberFields) {
			String variantId = fields.variant.getText().trim();
			if (validate && variantId.isEmpty()) {
				setStatus(text("invalid_variant"));
				return false;
			}
			if (!variantId.isEmpty()) {
				try {
					if (draft.getVariant(variantId) == null) draft.putVariant(BattleNext_VariantModel.fromRegistered(variantId));
				} catch (Exception exception) {
					if (validate) {
						setStatus(text("invalid_variant"));
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
