package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.BattleNext_LoadSource;
import data.model.BattleNext_BaseStore.EditRejectionReason;
import data.model.BattleNext_BaseStore.EditRejection;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.fleet.BattleNext_FleetVariantFormatter;
import data.scripts.fleet.BattleNext_FleetVariantFormatter.Result;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.ui.widget.provider.*;
import org.json.JSONException;

import java.util.*;

public class BattleNext_FleetEditor extends BattleNext_EditorView {

	private static final float MEMBER_DETAIL_INSET = 8f;

	private final BattleNext_FleetStore store;
	private VisibleRows visibleMembers;
	private TooltipMakerAPI memberViewport;
	private float memberTop;
	private boolean releasingRows;
	private final Map<Integer, CustomPanelAPI> memberPanels = new LinkedHashMap<>();
	private final Map<Integer, RowContent> memberContent = new LinkedHashMap<>();
	private final Map<Object, Float> memberHeights = new IdentityHashMap<>();
	private final BattleNext_VariantSourceProvider variantSource;
	private final BattleNext_FleetMemberProvider memberSource;

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
	private TextFieldAPI variantPrefixField;
	private BattleNext_FleetModel draft;
	private BattleNext_FleetModel baseline;
	private boolean baselineClone;
	private BattleNext_OptionPicker memberPicker;
	private ButtonAPI addMemberButton;

	private record FleetMemberEditorFields(TextFieldAPI variantField, TextFieldAPI nameField,
			TextFieldAPI officerPersonalityField, TextFieldAPI officerSkillsField, TextFieldAPI officerPortraitField,
			TextFieldAPI ordersField, TextFieldAPI tagsField) {

	}

	public BattleNext_FleetEditor(BattleNext_FleetStore store) {
		this(store, new BattleNext_VariantCatalog(new BattleNext_PinnedVariantStore()));
	}

	public BattleNext_FleetEditor(BattleNext_FleetStore store, BattleNext_VariantCatalog catalog) {
		this.store = store;
		variantSource = new BattleNext_VariantSourceProvider(catalog);
		memberSource = new BattleNext_FleetMemberProvider(catalog);
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
		for (BattleNext_MemberModel member : draft.getMembersReadonly()) memberKeys.add(new Object());
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
				String.join(",", draft.getSkillsReadonly()), new BattleNext_CommanderSkillSourceProvider(),
				BattleNext_OptionField.OptionSelectionMode.MULTIPLE, metadataContent);
		tagsField = createTextField("tags", panel, metadataContent, fieldArea, fleetStrings.get("tags"), String.join(",", draft.getTagsReadonly()), true, metadataContent);
		variantPrefixField = createTextField("variant_prefix", panel, metadataContent, fieldArea, fleetStrings.get("variant_id_prefix"), "", true, metadataContent);
		createButton(metadataContent, columnWidth, fleetStrings.get("format_ids"), "format_ids");
	}

	private void fillMembers(TooltipMakerAPI rowsContent, float columnWidth) {
		rowsContent.addTitle(fleetStrings.get("members_heading"));
		memberFields.clear();
		memberFields.addAll(Collections.nCopies(draft.getMembersReadonly().size(), null));
		memberViewport = rowsContent;
		memberTop = rowsContent.getHeightSoFar();

		visibleMembers = new VisibleRows(draft.getMembersReadonly().size(),
				index -> expandedMembers.contains(index) ? memberHeights.getOrDefault(memberKeys.get(index), 680f) : 44f,
				index -> mountMember(index, columnWidth), this::releaseMember,
				index -> memberContent.get(index).hasFocus());
		visibleMembers.update(0f, panel.getPosition().getHeight());
		rowsContent.setHeightSoFar(memberTop + visibleMembers.height());

		addMemberButton = createButton(rowsContent, columnWidth, fleetStrings.get("add_member"), "add_member");
		memberPicker = createPicker(memberSource, BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, this::addMembers);
	}

	private void mountMember(int index, float width) {
		CustomPanelAPI row = panel.createCustomPanel(width, 44f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = row.createUIElement(width, 0f, false);
		RowContent owned = mountRow(() -> addMember(index, draft.getMembersReadonly().get(index), content, width));
		memberPanels.put(index, row);
		memberContent.put(index, owned);

		float height = content.getHeightSoFar();
		row.getPosition().setSize(width, height);
		row.addUIElement(content).inTL(0f, 0f);
		memberViewport.addCustomDoNotSetPosition(row);

		memberHeights.put(memberKeys.get(index), height);
		visibleMembers.setHeight(index, height);
		positionMembers();
	}

	private void positionMembers() {
		for (Map.Entry<Integer, CustomPanelAPI> entry : memberPanels.entrySet()) {
			entry.getValue().getPosition().inTL(0f, memberTop + visibleMembers.top(entry.getKey()));
		}
		memberViewport.setHeightSoFar(memberTop + visibleMembers.height() + BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT + 16f);
		if (addMemberButton != null) addMemberButton.getPosition().inTL(0f, memberTop + visibleMembers.height() + 8f);
	}

	private void releaseMember(int index) {
		if (!releasingRows) readMember(index, draft.getMembersReadonly().get(index));
		memberContent.remove(index).close();
		memberFields.set(index, null);
		memberViewport.removeComponent(memberPanels.remove(index));
	}

	@Override
	public void advance(float amount) {
		if (visibleMembers != null) {
			visibleMembers.update(memberViewport.getExternalScroller().getYOffset(), panel.getPosition().getHeight());
			positionMembers();
		}
	}

	@Override
	protected void releaseRows() {
		releasingRows = true;
		if (visibleMembers != null) visibleMembers.close();
		releasingRows = false;
		visibleMembers = null;
		addMemberButton = null;
	}

	private void addMembers(List<String> ids) {
		if (ids.isEmpty()) return;

		captureDraft(false);
		List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembersReadonly());
		for (String id : ids) {
			BattleNext_MemberModel member = new BattleNext_MemberModel();
			member.setVariantId(id);
			members.add(member);
			memberKeys.add(new Object());
			expandedMembers.add(members.size() - 1);
		}

		draft.setMembers(members);
		refreshStructure();
	}

	private void addMember(int index, BattleNext_MemberModel member, TooltipMakerAPI rowsContent, float columnWidth) {

		boolean expanded = expandedMembers.contains(index);
		ShipHullSpecAPI hull = hullSpec(member.getVariantId());
		String hullName = hull == null ? member.getVariantId() : hull.getHullName();
		if (hullName == null || hullName.isEmpty()) hullName = variantStrings.get("missing");

		BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
				hull == null ? "" : hull.getSpriteName(), true, member.getPortrait(),
				hullName, member.getName(), "", "", null);
		memberFields.set(index, null);

		BattleNext_ExpandablePanel.add(rowsContent, panel, columnWidth, expanded, 0f, header,
				() -> buttonPressed("toggle_member:" + index), this::buttonPressed, bodyPanel -> {
			float areaWidth = columnWidth - MEMBER_DETAIL_INSET * 2f;
			UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(areaWidth);
			TooltipMakerAPI fieldContent = bodyPanel.createUIElement(areaWidth, 0f, false);
			fieldContent.addPara(memberStrings.get("label") + " " + (index + 1), 0f);
			createMemberActions(fieldContent, areaWidth,
					new EditorAction(fleetStrings.get("move_up"), "move_member_up:" + index, index > 0),
					new EditorAction(fleetStrings.get("move_down"), "move_member_down:" + index, index + 1 < draft.getMembersReadonly().size()));

			Object memberKey = memberKeys.get(index);
			TextFieldAPI variantField = createOptionField(List.of(memberKey, "variant"), bodyPanel, fieldContent, fieldArea,
					variantStrings.get("label"), member.getVariantId(), variantSource, BattleNext_OptionField.OptionSelectionMode.SINGLE, fieldContent, memberViewport);
			TextFieldAPI nameField = createTextField(List.of(memberKey, "name"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("ship_name"), member.getName(), true, fieldContent, memberViewport);

			TextFieldAPI officerPersonalityField = createOptionField(List.of(memberKey, "personality"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("officer_personality"), member.getPersonality(), new BattleNext_PersonalitySourceProvider(),
					BattleNext_OptionField.OptionSelectionMode.SINGLE, fieldContent, memberViewport);
			TextFieldAPI officerSkillsField = createOptionField(List.of(memberKey, "skills"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("officer_skills"), String.join(",", member.getSkillsReadonly()), new BattleNext_OfficerSkillSourceProvider(),
					BattleNext_OptionField.OptionSelectionMode.MULTIPLE, fieldContent, memberViewport);
			TextFieldAPI officerPortraitField = createOptionField(List.of(memberKey, "portrait"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("officer_portrait"), member.getPortrait(), new BattleNext_PortraitSourceProvider(),
					BattleNext_OptionField.OptionSelectionMode.SINGLE, fieldContent, memberViewport);
			TextFieldAPI ordersField = createTextField(List.of(memberKey, "orders"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("orders"), member.getOrders(), true, fieldContent, memberViewport);
			TextFieldAPI tagsField = createTextField(List.of(memberKey, "tags"), bodyPanel, fieldContent, fieldArea,
					memberStrings.get("tags"), String.join(",", member.getTagsReadonly()), true, fieldContent, memberViewport);
			memberFields.set(index, new FleetMemberEditorFields(variantField, nameField, officerPersonalityField,
					officerSkillsField, officerPortraitField, ordersField, tagsField));

			createMemberActions(fieldContent, areaWidth,
					new EditorAction(fleetStrings.get("remove_member"), "remove_member:" + index, true),
					new EditorAction(fleetStrings.get("copy_member"), "copy_member:" + index, true));

			float contentHeight = fieldContent.getHeightSoFar();
			fieldContent.getPosition().setSize(areaWidth, contentHeight);
			bodyPanel.getPosition().setSize(columnWidth, contentHeight + MEMBER_DETAIL_INSET * 2f);

			bodyPanel.addUIElement(fieldContent).inTL(MEMBER_DETAIL_INSET, MEMBER_DETAIL_INSET);
		});
	}

	private void createMemberActions(TooltipMakerAPI content, float areaWidth, EditorAction... actions) {
		float gap = BattleNext_PageLayout.EDITOR_BUTTON_GAP;
		float buttonWidth = BattleNext_PageLayout.editorButtonWidth((areaWidth - (actions.length - 1) * gap) / actions.length);
		float top = content.getHeightSoFar() + gap;
		for (int index = 0; index < actions.length; index++) {
			EditorAction action = actions[index];
			ButtonAPI button = createButton(content, buttonWidth, action.label(), action.id());
			button.getPosition().inTL(index * (buttonWidth + gap), top);
			button.setEnabled(action.enabled());
		}
		content.setHeightSoFar(top + BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT);
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
		for (String error : source.getLoadErrorsCopy()) {
			if (result.getLoadErrorsCopy().contains(error)) continue;
			result.addLoadError(error);
		}
		for (BattleNext_VariantBuilder variant : source.getVariantsCopy()) result.putVariant(variant);

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
		} else if ("format_ids".equals(buttonId)) {
			formatVariantIds();
		} else if ("add_member".equals(buttonId)) {
			memberPicker.openAt(panel, () -> {
				PositionAPI position = addMemberButton.getPosition();
				return new BattleNext_OptionPicker.Anchor(position.getX(), position.getY(), position.getWidth(), position.getHeight());
			});
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("remove_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("remove_member:".length()));
			captureDraft(false);
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembersReadonly());
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
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("copy_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("copy_member:".length()));
			captureDraft(false);
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembersReadonly());
			BattleNext_MemberModel member = members.get(index);
			members.add(index + 1, new BattleNext_MemberModel(member.getVariantId(), member.getName(), member.getPersonality(),
					member.getSkillsReadonly(), member.getPortrait(), member.getOrders(), member.getTagsReadonly()));
			memberKeys.add(index + 1, new Object());
			draft.setMembers(members);

			Set<Integer> adjusted = new HashSet<>();
			for (Integer expanded : expandedMembers) {
				adjusted.add(expanded > index ? expanded + 1 : expanded);
			}
			adjusted.add(index + 1);
			expandedMembers.clear();
			expandedMembers.addAll(adjusted);
			refreshStructure();
		} else if (buttonId instanceof String action && (action.startsWith("move_member_up:") || action.startsWith("move_member_down:"))) {
			int index = Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
			int target = action.startsWith("move_member_up:") ? index - 1 : index + 1;
			captureDraft(false);
			List<BattleNext_MemberModel> members = new ArrayList<>(draft.getMembersReadonly());
			Collections.swap(members, index, target);
			Collections.swap(memberKeys, index, target);
			draft.setMembers(members);

			Set<Integer> adjusted = new HashSet<>();
			for (Integer expanded : expandedMembers) {
				adjusted.add(expanded == index ? target : expanded == target ? index : expanded);
			}
			expandedMembers.clear();
			expandedMembers.addAll(adjusted);
			refreshStructure();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("toggle_member:")) {
			int index = Integer.parseInt(((String) buttonId).substring("toggle_member:".length()));
			captureDraft(false);
			if (!expandedMembers.add(index)) expandedMembers.remove(index);
			refreshStructure();
		}
	}

	private void formatVariantIds() {

		saveErrors.clear();
		statusText = "";
		BattleNext_FleetModel input = copy(draft, false);
		readInputs(input);
		Result result = BattleNext_FleetVariantFormatter.preview(input, variantPrefixField.getText(),
				id -> {
					ShipVariantAPI variant = variantSource.resolve(id);
					return variant == null ? null : BattleNext_VariantBuilder.fromVariant(variant);
				});
		for (String error : result.errors()) saveErrors.add(error);
		if (!saveErrors.isEmpty()) return;

		if (result.renamedIds() > 0 || result.affectedMembers() > 0) {
			draft = result.candidate();
			for (int index = 0; index < memberFields.size(); index++) {
				FleetMemberEditorFields fields = memberFields.get(index);
				if (fields != null && fields.variantField != null) fields.variantField.setText(draft.getMembersReadonly().get(index).getVariantId());
			}
			refreshStructure();
		}

		setStatus(fleetStrings.format("format_success", result.renamedIds(), result.affectedMembers()));
	}

	@Override
	protected String draftState() {
		if (idField == null) return "";

		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, folderNameField, nameField, authorField, descriptionField, commanderSkillsField, tagsField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}

		for (int index = 0; index < draft.getMembersReadonly().size(); index++) {
			BattleNext_MemberModel member = draft.getMembersReadonly().get(index);
			FleetMemberEditorFields fields = memberFields.size() > index ? memberFields.get(index) : null;
			for (String field : new String[]{value(fields == null ? null : fields.variantField, member.getVariantId()),
					value(fields == null ? null : fields.nameField, member.getName()),
					value(fields == null ? null : fields.officerPersonalityField, member.getPersonality()),
					value(fields == null ? null : fields.officerSkillsField, String.join(",", member.getSkillsReadonly())),
					value(fields == null ? null : fields.officerPortraitField, member.getPortrait()),
					value(fields == null ? null : fields.ordersField, member.getOrders()),
					value(fields == null ? null : fields.tagsField, String.join(",", member.getTagsReadonly()))}) {
				value.append(field.length()).append(':').append(field);
			}
		}
		for (BattleNext_VariantBuilder variant : draft.getVariantsCopy()) {
			String json = variant.toJson().toString();
			value.append(json.length()).append(':').append(json);
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
		validateName(fleetStrings.get("details_heading") + " / " + getDraftId(), strings.get("name"), nameField.getText(), strings.get("name_required"));
		captureDraft(true);

		try {
			for (EditRejection rejection : store.editRejections(draft, baseline == null || baselineClone ? "" : baseline.getId())) {
				saveErrors.rejection(fleetStrings.get("details_heading"),
					fleetStrings.get(rejection.getReason() == EditRejectionReason.DUPLICATE_FOLDER ? "folder_name" : "id"), rejection);
			}
			if (!saveErrors.isEmpty()) return false;

			String sourceId = baseline == null || baselineClone ? "" : baseline.getId();
			return persist(() -> {
				store.saveEdited(draft, sourceId);
				return true;
			}, () -> {
				baseline = copy(draft, false);
				draft = copy(baseline, false);
				baselineClone = false;
				setStatus(strings.get("saved"));
			});
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_FleetEditor.class, "fleet.editor.commit.failed",
				BattleNext_Logger.fields().put("draft", draft).build(), exception);
			setStatus(failureStatus(draft.getId(), exception));
			return false;
		}
	}

	@Override
	protected void captureDraftInputs() {
		captureDraft(false);
	}

	private void captureDraft(boolean validate) {
		if (idField == null) return;

		String fleetId = idField.getText().trim();
		if (validate && !BattleNext_EntryId.isValidPathSegment(fleetId)) {
			BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.id.rejected",
				BattleNext_Logger.fields().put("id", fleetId).put("draft", draft).put("criterion", "Valid path segment").build());
			setStatus(fleetStrings.get("invalid_id"));
			saveErrors.id(fleetStrings.get("details_heading"), fleetStrings.get("id"), idField.getText());
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
			saveErrors.id(fleetStrings.get("details_heading"), fleetStrings.get("folder_name"), folderNameField.getText());
		}

		readInputs(draft);
		draft.setFolderName(fleetFolderName);

		for (int index = 0; index < draft.getMembersReadonly().size(); index++) {
			FleetMemberEditorFields fields = memberFields.size() > index ? memberFields.get(index) : null;
			BattleNext_MemberModel existing = draft.getMembersReadonly().get(index);
			String subject = memberStrings.get("label") + " " + (index + 1) + " / "
					+ value(fields == null ? null : fields.nameField, existing.getName());
			String variantId = value(fields == null ? null : fields.variantField, existing.getVariantId()).trim();
			if (validate && variantId.isEmpty()) {
				BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.member.variant.rejected",
				BattleNext_Logger.fields().put("fleet", draft).put("member_index", index).put("variant_id", variantId).build());
				setStatus(variantStrings.get("invalid_reference"));
				saveErrors.field(subject, variantStrings.get("label"), variantId, strings.get("error_required"));
			}

			if (!variantId.isEmpty()) {
				try {
					if (variantId.startsWith(BattleNext_VariantSourceProvider.PINNED_PREFIX)) {
						BattleNext_VariantBuilder snapshot = BattleNext_VariantBuilder.fromVariant(variantSource.resolve(variantId));
						variantId = snapshot.getId();
						draft.putVariant(snapshot);
						if (fields != null && fields.variantField != null) fields.variantField.setText(variantId);
					}
					if (validate && draft.getVariant(variantId) == null) draft.putVariant(BattleNext_VariantBuilder.fromRegistered(variantId));
				} catch (Exception exception) {
					if (variantId.startsWith(BattleNext_VariantSourceProvider.PINNED_PREFIX) && validate) {
						setStatus(variantStrings.get("invalid_reference") + ": " + exception.getMessage());
						saveErrors.failure(subject + " / " + variantStrings.get("label"), variantId, exception);
					}
					BattleNext_Logger.warn(BattleNext_FleetEditor.class, "fleet.member.variant.degraded",
				BattleNext_Logger.fields().put("fleet_id", draft.getId()).put("member_index", index).put("variant_id", variantId).put("validate", validate).build(), exception);
				}
			}

			existing.setVariantId(variantId);
		}
	}

	private void readInputs(BattleNext_FleetModel target) {

		target.setId(idField.getText());
		target.setFolderName(folderNameField.getText());
		target.setName(nameField.getText());
		target.setAuthor(authorField.getText());
		target.setDescription(descriptionField.getText());
		target.setSkills(split(commanderSkillsField.getText()));
		target.setTags(split(tagsField.getText()));
		for (int index = 0; index < draft.getMembersReadonly().size(); index++) {
			readMember(index, target.getMembersReadonly().get(index));
		}
	}

	private void readMember(int index, BattleNext_MemberModel member) {
		FleetMemberEditorFields fields = memberFields.get(index);
		if (fields == null) return;
		member.setVariantId(value(fields.variantField, member.getVariantId()));
		member.setName(value(fields.nameField, member.getName()));
		member.setPersonality(value(fields.officerPersonalityField, member.getPersonality()));
		member.setSkills(split(value(fields.officerSkillsField, String.join(",", member.getSkillsReadonly()))));
		member.setPortrait(value(fields.officerPortraitField, member.getPortrait()));
		member.setOrders(value(fields.ordersField, member.getOrders()));
		member.setTags(split(value(fields.tagsField, String.join(",", member.getTagsReadonly()))));
	}

	@Override
	protected void restoreDraft() {
		expandedMembers.clear();
		idField = null;
		draft = baseline == null ? new BattleNext_FleetModel() : copy(baseline, baselineClone);
		memberKeys.clear();
		for (BattleNext_MemberModel member : draft.getMembersReadonly()) memberKeys.add(new Object());
	}

	@Override
	protected void closeEditorState() {
		variantPrefixField = null;
		memberHeights.clear();
	}
}
