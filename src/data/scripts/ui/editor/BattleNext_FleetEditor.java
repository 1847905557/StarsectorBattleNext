package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
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
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.provider.*;
import org.json.JSONException;

import java.util.*;

public class BattleNext_FleetEditor extends BattleNext_EditorView {

	private static final float MEMBER_DETAIL_INSET = 8f;

	private final BattleNext_FleetStore store;
	private final BattleNext_VariantSourceProvider variantSource = new BattleNext_VariantSourceProvider();
	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection memberStrings = BattleNext_I18nSection.getInstance("Member", "BattleNext_member_");
	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
	private final List<FleetMemberEditorFields> memberFields = new ArrayList<>();
	private final Set<Integer> expandedMembers = new HashSet<>();
	private final List<Object> memberKeys = new ArrayList<>();

	private TextFieldAPI idField;
	private TextFieldAPI folderNameField;
	private TextFieldAPI nameField;
	private TextFieldAPI authorField;
	private TextFieldAPI descriptionField;
	private TextFieldAPI commanderSkillsField;
	private TextFieldAPI tagsField;
	private BattleNext_FleetModel draft;
	private BattleNext_FleetModel baseline;
	private boolean baselineClone;

	private static final class FleetMemberEditorFields {
		private TextFieldAPI variantField;
		private TextFieldAPI nameField;
		private TextFieldAPI officerPersonalityField;
		private TextFieldAPI officerSkillsField;
		private TextFieldAPI officerPortraitField;
		private TextFieldAPI ordersField;
		private TextFieldAPI tagsField;
	}

	public BattleNext_FleetEditor(BattleNext_FleetStore store) {
		this.store = store;
	}

	public void open(BattleNext_FleetModel source, boolean clone) {
		beginSession();
		memberKeys.clear();
		expandedMembers.clear();
		memberFields.clear();
		idField = null;
		baseline = source;
		baselineClone = clone;
		draft = source == null ? new BattleNext_FleetModel() : copy(source, clone);
		for (var member : draft.getMembers()) {
			memberKeys.add(new Object());
		}
	}

	@Override
	protected void buildEditorContent() {
		createColumns("metadata", "members", this::fillMeta, this::fillMembers);
	}

	private void fillMeta(TooltipMakerAPI metadataContent, float columnWidth) {
		metadataContent.addTitle(fleetStrings.get("details_heading"));
		UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(columnWidth);

		idField = createTextField("id", panel, metadataContent, fieldArea, fleetStrings.get("id"), draft.getId(), true, metadataContent);
		folderNameField = createTextField("folder", panel, metadataContent, fieldArea, fleetStrings.get("folder_name"), draft.getFolderName(), true, metadataContent);
		nameField = createTextField("name", panel, metadataContent, fieldArea, fleetStrings.get("name"), draft.getName(), true, metadataContent);
		authorField = createTextField("author", panel, metadataContent, fieldArea, fleetStrings.get("author"), draft.getAuthor(), true, metadataContent);
		descriptionField = createTextField("description", panel, metadataContent, fieldArea, fleetStrings.get("description"), draft.getDescription(), true, metadataContent);
		commanderSkillsField = createOptionField("skills", panel, metadataContent, fieldArea, fleetStrings.get("commander_skills"),
				String.join(",", draft.getSkills()), new BattleNext_CommanderSkillSourceProvider(),
				BattleNext_OptionField.OptionSelectionMode.MULTIPLE, metadataContent);
		tagsField = createTextField("tags", panel, metadataContent, fieldArea, fleetStrings.get("tags"), String.join(",", draft.getTags()), true, metadataContent);
	}

	private void fillMembers(TooltipMakerAPI rowsContent, float columnWidth) {
		rowsContent.addTitle(fleetStrings.get("members_heading"));
		memberFields.clear();
		for (int index = 0; index < draft.getMembers().size(); index++) {
			addMember(index, draft.getMembers().get(index), rowsContent, columnWidth);
		}
		createButton(rowsContent, columnWidth, fleetStrings.get("add_member"), "add_member");
	}

	private void addMember(int index, BattleNext_MemberModel member, TooltipMakerAPI rowsContent, float columnWidth) {
		boolean expanded = expandedMembers.contains(index);
		ShipHullSpecAPI hull = hullSpec(member.getVariantId());
		String hullName = hull == null ? member.getVariantId() : hull.getHullName();
		if (hullName == null || hullName.isEmpty()) hullName = variantStrings.get("missing");
		BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
				hull == null ? "" : hull.getSpriteName(), true, member.getPortrait(),
				hullName, member.getName(), "", "", null);
		FleetMemberEditorFields fields = new FleetMemberEditorFields();
		memberFields.add(fields);
		BattleNext_ExpandablePanel.add(rowsContent, panel, columnWidth, expanded, 0f, header,
				() -> buttonPressed("toggle_member:" + index), this::buttonPressed, bodyPanel -> {
			float areaWidth = columnWidth - MEMBER_DETAIL_INSET * 2f;
			UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(areaWidth);
			TooltipMakerAPI fieldContent = bodyPanel.createUIElement(areaWidth, 0f, false);
			fieldContent.addPara(memberStrings.get("label") + " " + (index + 1), 0f);
			Object memberKey = memberKeys.get(index);
			fields.variantField = createOptionField(List.of(memberKey, "variant"), bodyPanel, fieldContent, fieldArea,
					variantStrings.get("label"), member.getVariantId(), variantSource, BattleNext_OptionField.OptionSelectionMode.SINGLE, fieldContent, rowsContent);
			fields.nameField = createTextField(List.of(memberKey, "name"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("ship_name"), member.getName(), true, fieldContent, rowsContent);
			fields.officerPersonalityField = createOptionField(List.of(memberKey, "personality"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("officer_personality"), member.getPersonality(), new BattleNext_PersonalitySourceProvider(),
					BattleNext_OptionField.OptionSelectionMode.SINGLE, fieldContent, rowsContent);
			fields.officerSkillsField = createOptionField(List.of(memberKey, "skills"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("officer_skills"), String.join(",", member.getSkills()), new BattleNext_OfficerSkillSourceProvider(),
					BattleNext_OptionField.OptionSelectionMode.MULTIPLE, fieldContent, rowsContent);
			fields.officerPortraitField = createOptionField(List.of(memberKey, "portrait"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("officer_portrait"), member.getPortrait(), new BattleNext_PortraitSourceProvider(),
					BattleNext_OptionField.OptionSelectionMode.SINGLE, fieldContent, rowsContent);
			fields.ordersField = createTextField(List.of(memberKey, "orders"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("orders"), member.getOrders(), true, fieldContent, rowsContent);
			fields.tagsField = createTextField(List.of(memberKey, "tags"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("tags"), String.join(",", member.getTags()), true, fieldContent, rowsContent);
			createButton(fieldContent, areaWidth, fleetStrings.get("remove_member"), "remove_member:" + index);
			float contentHeight = fieldContent.getHeightSoFar();
			fieldContent.getPosition().setSize(areaWidth, contentHeight);
			bodyPanel.getPosition().setSize(columnWidth, contentHeight + MEMBER_DETAIL_INSET * 2f);
			bodyPanel.addUIElement(fieldContent).inTL(MEMBER_DETAIL_INSET, MEMBER_DETAIL_INSET);
		});
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

	private String value(TextFieldAPI field, String fallback) {
		return field == null ? fallback : field.getText();
	}

	private BattleNext_FleetModel copy(BattleNext_FleetModel source, boolean clone) {
		String copiedFolder = source.getFolderName();
		if (clone && !copiedFolder.isEmpty()) copiedFolder += "_copy";
		BattleNext_FleetModel result = new BattleNext_FleetModel();
		try {
			result.fromJson(source.toJson());
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to copy fleet: " + source.getId(), exception);
		}
		result.setFolderName(copiedFolder);
		for (String error : source.getLoadErrors()) {
			if (result.getLoadErrors().contains(error)) continue;
			result.addLoadError(error);
		}
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
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("save".equals(buttonId)) {
			save();
		} else if ("add_member".equals(buttonId)) {
			if (!captureDraft(false)) return;
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembers());
			members.add(new BattleNext_MemberModel());
			memberKeys.add(new Object());
			draft.setMembers(members);
			expandedMembers.add(members.size() - 1);
			refreshStructure();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("remove_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("remove_member:".length()));
			if (!captureDraft(false)) return;
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembers());
			if (index < 0 || index >= members.size()) return;
			members.remove(index);
			memberKeys.remove(index);
			draft.setMembers(members);
			Set<Integer> adjusted = new HashSet<>();
			for (Integer expanded : expandedMembers) {
				if (expanded < index) adjusted.add(expanded);
				if (expanded > index) adjusted.add(expanded - 1);
			}
			expandedMembers.clear();
			expandedMembers.addAll(adjusted);
			refreshStructure();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("toggle_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("toggle_member:".length()));
			if (!captureDraft(false)) return;
			if (!expandedMembers.add(index)) expandedMembers.remove(index);
			refreshStructure();
		}
	}

	@Override
	protected String draftState() {
		if (idField == null) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, folderNameField, nameField, authorField, descriptionField, commanderSkillsField, tagsField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		for (int index = 0; index < draft.getMembers().size(); index++) {
			BattleNext_MemberModel member = draft.getMembers().get(index);
			FleetMemberEditorFields fields = memberFields.size() > index ? memberFields.get(index) : null;
			for (String field : new String[]{value(fields == null ? null : fields.variantField, member.getVariantId()),
					value(fields == null ? null : fields.nameField, member.getName()),
					value(fields == null ? null : fields.officerPersonalityField, member.getPersonality()),
					value(fields == null ? null : fields.officerSkillsField, String.join(",", member.getSkills())),
					value(fields == null ? null : fields.officerPortraitField, member.getPortrait()),
					value(fields == null ? null : fields.ordersField, member.getOrders()),
					value(fields == null ? null : fields.tagsField, String.join(",", member.getTags()))}) {
				value.append(field.length()).append(':').append(field);
			}
		}
		return value.toString();
	}

	public String getDraftId() {
		return idField == null ? "" : idField.getText().trim();
	}

	public String getDraftFolderName() {
		return folderNameField == null ? "" : folderNameField.getText().trim();
	}

	@Override
	protected boolean submitDraft() {
		if (!validateName(nameField.getText(), strings.get("name_required"))) return false;
		if (!captureDraft(true)) return false;
		try {
			store.saveEdited(draft, baseline == null || baselineClone ? "" : baseline.getId());
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_FleetEditor.class, "fleet.editor.commit.failed",
				BattleNext_Logger.fields().put("draft", draft).build(), exception);
			setStatus(failureStatus(exception));
			return false;
		}
		baseline = copy(draft, false);
		baselineClone = false;
		setStatus(strings.get("saved"));
		return true;
	}

	@Override
	protected void captureDraftInputs() {
		captureDraft(false);
	}

	private boolean captureDraft(boolean validate) {
		if (idField == null) return true;
		String fleetId = idField.getText().trim();
		if (validate && !BattleNext_EntryId.isValidPathSegment(fleetId)) {
			BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.id.rejected",
				BattleNext_Logger.fields().put("id", fleetId).put("draft", draft).put("criterion", "Valid path segment").build());
			setStatus(fleetStrings.get("invalid_id"));
			return false;
		}
		String fleetFolderName = folderNameField.getText().trim();
		if (fleetFolderName.isEmpty()) {
			fleetFolderName = fleetId;
			BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.folder.identity.completed",
				BattleNext_Logger.fields().put("id", fleetId).put("folder", fleetFolderName).build());
			folderNameField.setText(fleetFolderName);
		}
		if (validate && !BattleNext_EntryId.isValidPathSegment(fleetFolderName)) {
			BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.folder.rejected",
				BattleNext_Logger.fields().put("folder", fleetFolderName).put("draft", draft).put("criterion", "Valid path segment").build());
			setStatus(fleetStrings.get("invalid_folder"));
			return false;
		}
		draft.setId(fleetId);
		draft.setFolderName(fleetFolderName);
		draft.setName(nameField.getText());
		draft.setAuthor(authorField.getText());
		draft.setDescription(descriptionField.getText());
		draft.setSkills(split(commanderSkillsField.getText()));
		draft.setTags(split(tagsField.getText()));
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (int index = 0; index < draft.getMembers().size(); index++) {
			FleetMemberEditorFields fields = memberFields.size() > index ? memberFields.get(index) : null;
			BattleNext_MemberModel existing = draft.getMembers().get(index);
			String variantId = value(fields == null ? null : fields.variantField, existing.getVariantId()).trim();
			if (validate && variantId.isEmpty()) {
				BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.member.variant.rejected",
				BattleNext_Logger.fields().put("fleet", draft).put("member_index", index).put("variant_id", variantId).build());
				setStatus(variantStrings.get("invalid_reference"));
				return false;
			}
			if (!variantId.isEmpty()) {
				try {
					if (variantId.startsWith(BattleNext_VariantSourceProvider.PINNED_PREFIX)) {
						BattleNext_VariantBuilder snapshot = BattleNext_VariantBuilder.fromVariant(variantSource.resolve(variantId));
						variantId = snapshot.getId();
						draft.putVariant(snapshot);
						if (fields != null && fields.variantField != null) fields.variantField.setText(variantId);
					}
					if (draft.getVariant(variantId) == null) draft.putVariant(BattleNext_VariantBuilder.fromRegistered(variantId));
				} catch (Exception exception) {
					if (variantId.startsWith(BattleNext_VariantSourceProvider.PINNED_PREFIX) && validate) {
						setStatus(variantStrings.get("invalid_reference") + ": " + exception.getMessage());
						return false;
					}
					BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.member.variant.degraded",
				BattleNext_Logger.fields().put("fleet_id", draft.getId()).put("member_index", index).put("variant_id", variantId).put("validate", validate).build(), exception);
				}
			}
			members.add(new BattleNext_MemberModel(variantId, value(fields == null ? null : fields.nameField, existing.getName()),
					value(fields == null ? null : fields.officerPersonalityField, existing.getPersonality()),
					split(value(fields == null ? null : fields.officerSkillsField, String.join(",", existing.getSkills()))),
					value(fields == null ? null : fields.officerPortraitField, existing.getPortrait()),
					value(fields == null ? null : fields.ordersField, existing.getOrders()),
					split(value(fields == null ? null : fields.tagsField, String.join(",", existing.getTags())))));
		}
		draft.setMembers(members);
		return true;
	}

	@Override
	protected void restoreDraft() {
		expandedMembers.clear();
		idField = null;
		draft = baseline == null ? new BattleNext_FleetModel() : copy(baseline, baselineClone);
		memberKeys.clear();
		for (var member : draft.getMembers()) {
			memberKeys.add(new Object());
		}
	}
}
