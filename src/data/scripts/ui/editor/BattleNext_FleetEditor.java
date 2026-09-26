package data.ui.editor;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetCatalog;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_VariantAdapter;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_EntryId;
import data.ui.BattleNext_PageLayout;
import data.ui.BattleNext_UiTheme;
import data.ui.widget.BattleNext_OptionField;
import data.ui.widget.BattleNext_TextField;
import data.ui.widget.provider.BattleNext_CommanderSkillSourceProvider;
import data.ui.widget.provider.BattleNext_OfficerSkillSourceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BattleNext_FleetEditor implements BattleNext_EditorView {

	private static final float FIELD_HEIGHT = 28f;
	private static final int MEMBER_SKILL_MAX_COUNT = 2;

	private final BattleNext_FleetStore store;
	private final BattleNext_FleetCatalog catalog = new BattleNext_FleetCatalog();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final List<MemberFields> memberFields = new ArrayList<>();
	private CustomPanelAPI panel;
	private CustomPanelAPI overlayHost;
	private BattleNext_OptionField skillsPicker;
	private final List<BattleNext_OptionField> memberPickers = new ArrayList<>();
	private UIRect contentRect;
	private LabelAPI status;
	private String statusText = "";
	private TextFieldAPI id;
	private TextFieldAPI name;
	private TextFieldAPI author;
	private TextFieldAPI description;
	private TextFieldAPI skills;
	private TextFieldAPI tags;
	private BattleNext_FleetModel draft;
	private BattleNext_FleetModel baseline;
	private boolean baselineClone;
	private String savedSnapshot = "";

	public BattleNext_FleetEditor(BattleNext_FleetStore store) {
		this.store = store;
		catalog.refresh();
	}

	public void open(BattleNext_FleetModel source, boolean clone) {
		baseline = source;
		baselineClone = clone;
		draft = source == null ? new BattleNext_FleetModel() : copy(source, clone);
	}

	public String getDraftId() {
		return id == null ? "" : id.getText().trim();
	}

	public void setOverlayHost(CustomPanelAPI overlayHost) {
		this.overlayHost = overlayHost;
	}

	public void closePicker() {
		if (skillsPicker != null) skillsPicker.close();
		for (BattleNext_OptionField picker : memberPickers) {
			picker.close();
		}
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (skillsPicker != null && skillsPicker.handleGlobalInput(events)) return true;
		for (BattleNext_OptionField picker : memberPickers) {
			if (picker.handleGlobalInput(events)) return true;
		}
		return false;
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
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("remove_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("remove_member:".length()));
			if (!captureDraft(false)) return;
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembers());
			if (index < 0 || index >= members.size()) return;
			members.remove(index);
			draft.setMembers(members);
			refreshContent();
		}
	}

	@Override
	public void createContents() {
		contentRect = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		refreshContent();
	}

	@Override
	public void refreshContent() {
		closePicker();
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		float gap = 10f;
		float usableWidth = Math.max(2f, contentRect.getWidth() - gap);
		float metaWidth = (float) Math.floor(usableWidth * 0.45f);
		float memberWidth = usableWidth - metaWidth;
		TooltipMakerAPI meta = contentPanel.createUIElement(metaWidth, contentRect.getHeight(), true);
		fillMeta(meta, metaWidth);
		contentPanel.addUIElement(meta).inTL(0f, 0f);
		TooltipMakerAPI members = contentPanel.createUIElement(memberWidth, contentRect.getHeight(), true);
		fillMembers(members, memberWidth);
		contentPanel.addUIElement(members).inTL(metaWidth + gap, 0f);
	}

	private void fillMeta(TooltipMakerAPI metaContent, float width) {
		metaContent.addTitle(text("fleet_section_details"));
		status = metaContent.addPara(text("ready"), 6f, BattleNext_UiTheme.MUTED);
		id = field(metaContent, text("fleet_id"), draft.getId(), width);
		name = field(metaContent, text("fleet_name"), draft.getName(), width);
		author = field(metaContent, text("fleet_author"), draft.getAuthor(), width);
		description = field(metaContent, text("fleet_description"), draft.getDescription(), width);
		metaContent.addPara(text("fleet_skills"), 5f, BattleNext_UiTheme.MUTED);
		skillsPicker = new BattleNext_OptionField(overlayHost, new BattleNext_CommanderSkillSourceProvider(), BattleNext_OptionField.Mode.MULTIPLE);
		skills = skillsPicker.addTo(metaContent, panel, Math.max(72f, width - 24f),
				String.join(",", draft.getSkills()));
		tags = field(metaContent, text("fleet_tags"), String.join(",", draft.getTags()), width);
	}

	private void fillMembers(TooltipMakerAPI memberContent, float width) {
		memberContent.addTitle(text("fleet_section_members"));
		BattleNext_PageLayout.button(memberContent, text("add_member"), "add_member", Math.min(150f, width - 20f));
		memberFields.clear();
		memberPickers.clear();
		for (int index = 0; index < draft.getMembers().size(); index++) {
			addMember(index, draft.getMembers().get(index), memberContent, width);
		}
	}

	private void addMember(int index, BattleNext_MemberModel member, TooltipMakerAPI memberContent, float width) {
		memberContent.addSectionHeading(text("member") + " " + (index + 1),
				BattleNext_UiTheme.TEXT, BattleNext_UiTheme.HEADER,
				com.fs.starfarer.api.ui.Alignment.MID, 8f);
		MemberFields fields = new MemberFields();
		fields.variant = field(memberContent, text("variant"), member.getVariantId(), width);
		fields.name = field(memberContent, text("member_name"), member.getName(), width);
		fields.personality = field(memberContent, text("personality"), member.getPersonality(), width);
		memberContent.addPara(text("member_skills"), 5f, BattleNext_UiTheme.MUTED);
		BattleNext_OptionField memberSkills = new BattleNext_OptionField(overlayHost,
				new BattleNext_OfficerSkillSourceProvider(), BattleNext_OptionField.Mode.MULTIPLE);
		fields.skills = memberSkills.addTo(memberContent, panel, Math.max(72f, width - 24f),
				String.join(",", member.getSkills()));
		memberPickers.add(memberSkills);
		fields.portrait = field(memberContent, text("portrait"), member.getPortrait(), width);
		fields.orders = field(memberContent, text("orders"), member.getOrders(), width);
		fields.tags = field(memberContent, text("member_tags"), String.join(",", member.getTags()), width);
		BattleNext_PageLayout.button(memberContent, text("remove_member"), "remove_member:" + index, Math.min(150f, width - 20f));
		memberFields.add(fields);
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value, float sectionWidth) {
		host.addPara(label, 5f, BattleNext_UiTheme.MUTED);
		TextFieldAPI field = BattleNext_TextField.addTo(host, panel, Math.max(40f, sectionWidth - 24f), FIELD_HEIGHT, value);
		field.setVerticalCursor(true);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		return field;
	}

	@Override
	public boolean serialize() {
		if (!captureDraft(true)) return false;
		try {
			store.save(draft);
		} catch (Exception exception) {
			setStatus(text("save_failed") + ": " + exception.getMessage());
			return false;
		}
		savedSnapshot = serializeState();
		baseline = copy(draft, false);
		baselineClone = false;
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
		for (TextFieldAPI field : new TextFieldAPI[]{id, name, author, description, skills, tags}) {
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
		if (validate && !BattleNext_EntryId.isValid(fleetId)) {
			setStatus(text("invalid_fleet_id"));
			return false;
		}
		draft.setId(fleetId);
		draft.setName(name.getText());
		draft.setAuthor(author.getText());
		draft.setDescription(description.getText());
		draft.setSkills(split(skills.getText()));
		draft.setTags(split(tags.getText()));
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (MemberFields fields : memberFields) {
			String variantId = fields.variant.getText().trim();
			if (validate && variantId.isEmpty()) {
				setStatus(text("invalid_variant"));
				return false;
			}
			if (!variantId.isEmpty()) {
				try {
					if (draft.getVariant(variantId) == null) draft.putVariant(BattleNext_VariantAdapter.fromRegistered(variantId));
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
		BattleNext_FleetModel result = new BattleNext_FleetModel(source.getId(), source.getName(),
				source.getSkills(), source.getTags(), source.getMembers());
		result.setAuthor(source.getAuthor());
		result.setDescription(source.getDescription());
		for (BattleNext_VariantAdapter variant : source.getVariants()) {
			result.putVariant(variant);
		}
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
