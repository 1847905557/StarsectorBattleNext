package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.provider.*;

import java.util.*;

public class BattleNext_FleetEditor implements BattleNext_EditorView {

	private BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();

	@Override
	public void setInputRouter(BattleNext_UiInputRouter router) {
		this.inputRouter = router;
	}

	private static final float FIELD_SIDE_RESERVE = 24f;
	private static final float FIELD_MIN_WIDTH = 40f;
	private static final float PICKER_MIN_WIDTH = 72f;
	private static final float MEMBER_BUTTON_MAX_WIDTH = 150f;
	private static final float MEMBER_BUTTON_SIDE_RESERVE = 20f;
	private static final float MEMBER_HEADING_GAP = 8f;
	private static final float FIELD_LABEL_GAP = 5f;
	private static final float EDITOR_STATUS_GAP = 6f;
	private static final float MEMBER_DETAIL_INSET = 8f;

	private final BattleNext_FleetStore store;
	private final BattleNext_VariantSourceProvider variantSource = new BattleNext_VariantSourceProvider();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection memberStrings = BattleNext_I18nSection.getInstance("Member", "BattleNext_member_");
	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
	private final List<FleetMemberEditorFields> memberFields = new ArrayList<>();
	private final Set<Integer> expandedMembers = new HashSet<>();
	private CustomPanelAPI panel;
	private CustomPanelAPI overlayHost;
	private BattleNext_OptionField skillsPicker;
	private final List<BattleNext_OptionField> memberPickers = new ArrayList<>();
	private final List<BattleNext_TextField> textFields = new ArrayList<>();
	private UIRect contentRect;
	private LabelAPI status;
	private String statusText = "";
	private TextFieldAPI id;
	private TextFieldAPI folderName;
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
	}

	public void open(BattleNext_FleetModel source, boolean clone) {
		expandedMembers.clear();
		memberFields.clear();
		id = null;
		baseline = source;
		baselineClone = clone;
		draft = source == null ? new BattleNext_FleetModel() : copy(source, clone);
	}

	public String getDraftId() {
		return id == null ? "" : id.getText().trim();
	}

	public String getDraftFolderName() {
		return folderName == null ? "" : folderName.getText().trim();
	}

	public void setOverlayHost(CustomPanelAPI overlayHost) {
		this.overlayHost = overlayHost;
	}

	public void closePicker() {
		captureDraft(false);
		if (skillsPicker != null) skillsPicker.closeOverlays();
		for (BattleNext_OptionField picker : memberPickers) {
			picker.closeOverlays();
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
		boolean opened = id == null;
		this.panel = panel;
		createContents();
		if (opened) {
			savedSnapshot = serializeState();
		}
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
			expandedMembers.add(members.size() - 1);
			refreshContent();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("remove_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("remove_member:".length()));
			if (!captureDraft(false)) return;
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembers());
			if (index < 0 || index >= members.size()) return;
			members.remove(index);
			draft.setMembers(members);
			Set<Integer> adjusted = new HashSet<>();
			for (Integer expanded : expandedMembers) {
				if (expanded < index) adjusted.add(expanded);
				if (expanded > index) adjusted.add(expanded - 1);
			}
			expandedMembers.clear();
			expandedMembers.addAll(adjusted);
			refreshContent();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("toggle_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("toggle_member:".length()));
			if (!captureDraft(false)) return;
			if (!expandedMembers.add(index)) expandedMembers.remove(index);
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
		if (skillsPicker != null) skillsPicker.close();
		for (BattleNext_OptionField picker : memberPickers) {
			picker.close();
		}
		for (BattleNext_TextField field : textFields) {
			field.close();
		}
		textFields.clear();
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		float usableWidth = Math.max(BattleNext_PageLayout.EDITOR_MIN_SPLIT_WIDTH,
				contentRect.getWidth() - BattleNext_PageLayout.EDITOR_COLUMN_GAP);
		float metaWidth = (float) Math.floor(usableWidth * BattleNext_PageLayout.EDITOR_LEFT_COLUMN_SHARE);
		float memberWidth = usableWidth - metaWidth;
		TooltipMakerAPI meta = contentPanel.createUIElement(metaWidth, contentRect.getHeight(), true);
		fillMeta(meta, metaWidth);
		contentPanel.addUIElement(meta).inTL(0f, 0f);
		TooltipMakerAPI members = contentPanel.createUIElement(memberWidth, contentRect.getHeight(), true);
		fillMembers(members, memberWidth);
		contentPanel.addUIElement(members).inTL(metaWidth + BattleNext_PageLayout.EDITOR_COLUMN_GAP, 0f);
	}

	private void fillMeta(TooltipMakerAPI metaContent, float width) {
		metaContent.addTitle(fleetStrings.get("details_heading"));
		status = metaContent.addPara(strings.get("ready"), EDITOR_STATUS_GAP);
		id = field(metaContent, fleetStrings.get("id"), draft.getId(), width, metaContent);
		folderName = field(metaContent, fleetStrings.get("folder_name"), draft.getFolderName(), width, metaContent);
		name = field(metaContent, fleetStrings.get("name"), draft.getName(), width, metaContent);
		author = field(metaContent, fleetStrings.get("author"), draft.getAuthor(), width, metaContent);
		description = field(metaContent, fleetStrings.get("description"), draft.getDescription(), width, metaContent);
		skillsPicker = new BattleNext_OptionField(inputRouter, overlayHost, Math.max(PICKER_MIN_WIDTH, width - FIELD_SIDE_RESERVE),
				new BattleNext_CommanderSkillSourceProvider(), BattleNext_OptionField.OptionSelectionMode.MULTIPLE, metaContent);
		skills = optionField(metaContent, panel, fleetStrings.get("commander_skills"), skillsPicker,
				String.join(",", draft.getSkills()));
		tags = field(metaContent, fleetStrings.get("tags"), String.join(",", draft.getTags()), width, metaContent);
	}

	private void fillMembers(TooltipMakerAPI memberContent, float width) {
		memberContent.addTitle(fleetStrings.get("members_heading"));
		memberFields.clear();
		memberPickers.clear();
		for (int index = 0; index < draft.getMembers().size(); index++) {
			addMember(index, draft.getMembers().get(index), memberContent, width);
		}
		BattleNext_PageLayout.button(memberContent, fleetStrings.get("add_member"), "add_member",
				Math.min(MEMBER_BUTTON_MAX_WIDTH, width - MEMBER_BUTTON_SIDE_RESERVE));
	}

	private void addMember(int index, BattleNext_MemberModel member, TooltipMakerAPI memberContent, float width) {
		boolean expanded = expandedMembers.contains(index);
		ShipHullSpecAPI hull = hullSpec(member.getVariantId());
		String hullName = hull == null ? member.getVariantId() : hull.getHullName();
		if (hullName == null || hullName.isEmpty()) hullName = variantStrings.get("missing");
		BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
				hull == null ? "" : hull.getSpriteName(), true, member.getPortrait(),
				hullName, member.getName(), "", "", null);
		FleetMemberEditorFields fields = new FleetMemberEditorFields();
		memberFields.add(fields);
		BattleNext_ExpandablePanel.add(memberContent, panel, width, expanded, 0f, header,
				() -> buttonPressed("toggle_member:" + index), this::buttonPressed, bodyPanel -> {
			float detailWidth = width - MEMBER_DETAIL_INSET * 2f;
			float pickerWidth = Math.max(PICKER_MIN_WIDTH, detailWidth - FIELD_SIDE_RESERVE);
			TooltipMakerAPI content = bodyPanel.createUIElement(detailWidth, 0f, false);
			content.addPara(memberStrings.get("label") + " " + (index + 1), 0f);
			BattleNext_OptionField variantPicker = new BattleNext_OptionField(inputRouter,
					overlayHost, pickerWidth, variantSource, BattleNext_OptionField.OptionSelectionMode.SINGLE, memberContent);
			fields.variant = optionField(content, bodyPanel, variantStrings.get("label"), variantPicker, member.getVariantId());
			memberPickers.add(variantPicker);
			fields.name = field(content, bodyPanel, memberStrings.get("ship_name"), member.getName(), detailWidth, memberContent);
			BattleNext_OptionField personalityPicker = new BattleNext_OptionField(inputRouter,
					overlayHost, pickerWidth, new BattleNext_PersonalitySourceProvider(), BattleNext_OptionField.OptionSelectionMode.SINGLE, memberContent);
			fields.personality = optionField(content, bodyPanel, memberStrings.get("captain_personality"), personalityPicker,
					member.getPersonality());
			memberPickers.add(personalityPicker);
			BattleNext_OptionField memberSkills = new BattleNext_OptionField(inputRouter,
					overlayHost, pickerWidth, new BattleNext_OfficerSkillSourceProvider(), BattleNext_OptionField.OptionSelectionMode.MULTIPLE, memberContent);
			fields.skills = optionField(content, bodyPanel, memberStrings.get("captain_skills"), memberSkills,
					String.join(",", member.getSkills()));
			memberPickers.add(memberSkills);
			BattleNext_OptionField portraitPicker = new BattleNext_OptionField(inputRouter,
					overlayHost, pickerWidth, new BattleNext_PortraitSourceProvider(), BattleNext_OptionField.OptionSelectionMode.SINGLE, memberContent);
			fields.portrait = optionField(content, bodyPanel, memberStrings.get("captain_portrait"), portraitPicker, member.getPortrait());
			memberPickers.add(portraitPicker);
			fields.orders = field(content, bodyPanel, memberStrings.get("orders"), member.getOrders(), detailWidth, memberContent);
			fields.tags = field(content, bodyPanel, memberStrings.get("tags"), String.join(",", member.getTags()),
					detailWidth, memberContent);
			BattleNext_PageLayout.button(content, fleetStrings.get("remove_member"), "remove_member:" + index,
					Math.min(MEMBER_BUTTON_MAX_WIDTH, detailWidth - MEMBER_BUTTON_SIDE_RESERVE));
			float contentHeight = content.getHeightSoFar();
			content.getPosition().setSize(detailWidth, contentHeight);
			bodyPanel.getPosition().setSize(width, contentHeight + MEMBER_DETAIL_INSET * 2f);
			bodyPanel.addUIElement(content).inTL(MEMBER_DETAIL_INSET, MEMBER_DETAIL_INSET);
		});
	}

	private TextFieldAPI optionField(TooltipMakerAPI host, CustomPanelAPI owner, String label,
			BattleNext_OptionField option, String value) {
		host.addPara(label, FIELD_LABEL_GAP);
		option.attach(host, owner);
		TextFieldAPI field = option.getField();
		field.setText(value);
		return field;
	}

	private ShipHullSpecAPI hullSpec(String variantId) {
		try {
			BattleNext_VariantBuilder local = draft.getVariant(variantId);
			if (local != null) {
				String hullId = local.toJson().optString("hullId", "");
				return hullId.isEmpty() ? null : Global.getSettings().getHullSpec(hullId);
			}
			ShipVariantAPI variant = variantSource.resolve(variantId);
			return variant == null ? null : variant.getHullSpec();
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value, float sectionWidth,
			TooltipMakerAPI... viewports) {
		return field(host, panel, label, value, sectionWidth, viewports);
	}

	private TextFieldAPI field(TooltipMakerAPI host, CustomPanelAPI owner, String label, String value,
			float sectionWidth, TooltipMakerAPI... viewports) {
		host.addPara(label, FIELD_LABEL_GAP);
		float width = Math.max(FIELD_MIN_WIDTH, sectionWidth - FIELD_SIDE_RESERVE);
		BattleNext_TextField widget = new BattleNext_TextField(inputRouter, owner, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, viewports);
		textFields.add(widget);
		widget.attach(host, owner);
		widget.setText(value);
		TextFieldAPI field = widget;
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
			setStatus(strings.get("save_failed") + ": " + exception.getMessage());
			return false;
		}
		savedSnapshot = serializeState();
		baseline = copy(draft, false);
		baselineClone = false;
		setStatus(strings.get("saved"));
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
		for (TextFieldAPI field : new TextFieldAPI[]{id, folderName, name, author, description, skills, tags}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		for (int index = 0; index < draft.getMembers().size(); index++) {
			BattleNext_MemberModel member = draft.getMembers().get(index);
			FleetMemberEditorFields fields = memberFields.size() > index ? memberFields.get(index) : null;
			for (String field : new String[]{value(fields == null ? null : fields.variant, member.getVariantId()),
					value(fields == null ? null : fields.name, member.getName()),
					value(fields == null ? null : fields.personality, member.getPersonality()),
					value(fields == null ? null : fields.skills, String.join(",", member.getSkills())),
					value(fields == null ? null : fields.portrait, member.getPortrait()),
					value(fields == null ? null : fields.orders, member.getOrders()),
					value(fields == null ? null : fields.tags, String.join(",", member.getTags()))}) {
				value.append(field.length()).append(':').append(field);
			}
		}
		return value.toString();
	}

	private boolean captureDraft(boolean validate) {
		if (id == null) return true;
		String fleetId = id.getText().trim();
		if (validate && !BattleNext_EntryId.isValidPathSegment(fleetId)) {
			setStatus(fleetStrings.get("invalid_id"));
			return false;
		}
		String fleetFolderName = folderName.getText().trim();
		if (fleetFolderName.isEmpty()) {
			fleetFolderName = fleetId;
			folderName.setText(fleetFolderName);
		}
		if (validate && !BattleNext_EntryId.isValidPathSegment(fleetFolderName)) {
			setStatus(fleetStrings.get("invalid_folder"));
			return false;
		}
		draft.setId(fleetId);
		draft.setFolderName(fleetFolderName);
		draft.setName(name.getText());
		draft.setAuthor(author.getText());
		draft.setDescription(description.getText());
		draft.setSkills(split(skills.getText()));
		draft.setTags(split(tags.getText()));
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (int index = 0; index < draft.getMembers().size(); index++) {
			FleetMemberEditorFields fields = memberFields.size() > index ? memberFields.get(index) : null;
			BattleNext_MemberModel existing = draft.getMembers().get(index);
			String variantId = value(fields == null ? null : fields.variant, existing.getVariantId()).trim();
			if (validate && variantId.isEmpty()) {
				setStatus(variantStrings.get("invalid_reference"));
				return false;
			}
			if (!variantId.isEmpty()) {
				try {
					if (variantId.startsWith(BattleNext_VariantSourceProvider.PINNED_PREFIX)) {
						BattleNext_VariantBuilder snapshot = BattleNext_VariantBuilder.fromVariant(variantSource.resolve(variantId));
						variantId = snapshot.getId();
						draft.putVariant(snapshot);
						if (fields != null && fields.variant != null) fields.variant.setText(variantId);
					}
					if (draft.getVariant(variantId) == null) draft.putVariant(BattleNext_VariantBuilder.fromRegistered(variantId));
				} catch (Exception exception) {
					if (variantId.startsWith(BattleNext_VariantSourceProvider.PINNED_PREFIX) && validate) {
						setStatus(variantStrings.get("invalid_reference") + ": " + exception.getMessage());
						return false;
					}
					// Keep references to variants unavailable in the current game setup.
				}
			}
			members.add(new BattleNext_MemberModel(variantId, value(fields == null ? null : fields.name, existing.getName()),
					value(fields == null ? null : fields.personality, existing.getPersonality()),
					split(value(fields == null ? null : fields.skills, String.join(",", existing.getSkills()))),
					value(fields == null ? null : fields.portrait, existing.getPortrait()),
					value(fields == null ? null : fields.orders, existing.getOrders()),
					split(value(fields == null ? null : fields.tags, String.join(",", existing.getTags())))));
		}
		draft.setMembers(members);
		return true;
	}

	private String value(TextFieldAPI field, String fallback) {
		return field == null ? fallback : field.getText();
	}

	private BattleNext_FleetModel copy(BattleNext_FleetModel source, boolean clone) {
		String copiedFolder = source.getFolderName();
		if (clone && !copiedFolder.isEmpty()) copiedFolder += "_copy";
		BattleNext_FleetModel result = new BattleNext_FleetModel(source.getId(), copiedFolder, source.getName(),
				source.getSkills(), source.getTags(), source.getMembers());
		result.setAuthor(source.getAuthor());
		result.setDescription(source.getDescription());
		for (BattleNext_VariantBuilder variant : source.getVariants()) {
			result.putVariant(variant);
		}
		result.setSourceType(clone ? BattleNext_LoadSource.CREATED : source.getSourceType());
		result.setSourcePath(clone ? "" : source.getSourcePath());
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

	private static final class FleetMemberEditorFields {
		private TextFieldAPI variant;
		private TextFieldAPI name;
		private TextFieldAPI personality;
		private TextFieldAPI skills;
		private TextFieldAPI portrait;
		private TextFieldAPI orders;
		private TextFieldAPI tags;
	}

}
