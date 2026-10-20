package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_VariantDetails;

public class BattleNext_PinnedVariantEditor extends BattleNext_EditorView {

	private final BattleNext_PinnedVariantStore store;
	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
	private final BattleNext_VariantDetails details = new BattleNext_VariantDetails(this::refreshContent, () -> inputRouter.isEmpty());

	private CatalogEntry entry;
	private TextFieldAPI nameField;
	private TooltipMakerAPI metadataContent;

	public BattleNext_PinnedVariantEditor(BattleNext_PinnedVariantStore store) {
		this.store = store;
	}

	public void open(CatalogEntry source) {
		beginSession();
		entry = source;
		nameField = null;
		metadataContent = null;
		details.clear();
	}

	@Override
	protected void buildEditorContent() {
		String edited = nameField == null ? entry.name() : nameField.getText();
		float offset = metadataContent == null || metadataContent.getExternalScroller() == null
				? 0f : metadataContent.getExternalScroller().getYOffset();
		nameField = null;
		CatalogEntry selected = entry;
		UIRect area = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		float metadataLimit = Math.max(1f, (float) Math.floor(area.getHeight() / 3f));
		CustomPanelAPI metadataPanel = panel.createCustomPanel(area.getWidth(), metadataLimit,
				new BaseCustomUIPanelPlugin());
		TooltipMakerAPI fieldContent = metadataPanel.createUIElement(
				area.getWidth() + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, metadataLimit, true);
		fieldContent.setTextWidthOverride(area.getWidth());
		fieldContent.addTitle(selected.name());
		if (selected.model() != null && store.isLoaded()) {
			UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(area.getWidth());
			nameField = createTextField("name", metadataPanel, fieldContent, fieldArea, variantStrings.get("pin_name"), edited, true, fieldContent);
		}
		fieldContent.addPara(variantStrings.get("pin_id") + ": " + selected.pinId(), 8f);
		fieldContent.addPara(variantStrings.get("source_file") + ": " + selected.path(), 8f);
		if (!selected.error().isEmpty()) {
			fieldContent.addPara(selected.error(), 8f);
		}
		float metadataHeight = Math.min(metadataLimit, fieldContent.getHeightSoFar());
		metadataPanel.getPosition().setSize(area.getWidth(), metadataHeight);
		fieldContent.getPosition().setSize(area.getWidth() + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, metadataHeight);
		metadataPanel.addUIElement(fieldContent).inTL(-BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET, 0f);
		panel.addComponent(metadataPanel).inTL(0f, 0f);
		metadataContent = fieldContent;
		bindViewport("metadata", fieldContent);
		if (fieldContent.getExternalScroller() != null) {
			fieldContent.getExternalScroller().setYOffset(Math.min(Math.max(0f, fieldContent.getHeightSoFar() - metadataHeight), offset));
		}
		if (selected.error().isEmpty() && selected.model() != null && store.isLoaded()) {
			float detailsHeight = Math.max(1f, area.getHeight() - metadataHeight - 8f);
			CustomPanelAPI visual = panel.createCustomPanel(area.getWidth(), detailsHeight,
					new BaseCustomUIPanelPlugin());
			panel.addComponent(visual).inTL(0f,
					metadataHeight + 8f);
			try {
				details.setVariantJson(selected.model().getVariantJson());
				own(details);
				details.attach(visual);
			} catch (Exception exception) {
				TooltipMakerAPI error = visual.createUIElement(area.getWidth(), detailsHeight, true);
				error.addPara(selected.path() + ": " + exception, 8f);
				visual.addUIElement(error).inTL(0f, 0f);
			}
		}
	}

	@Override
	public void buttonPressed(Object action) {

	}

	@Override
	protected String draftState() {
		return nameField == null ? entry.name() : nameField.getText();
	}

	@Override
	protected boolean submitDraft() {
		if (entry.model() == null || nameField == null || !store.isLoaded()) return false;
		if (!validateName(entry.path(), variantStrings.get("pin_name"), nameField.getText(), variantStrings.get("pin_name_required"))) return false;
		String next = nameField.getText().trim();
		try {
			store.rename(entry.pinId(), next);
			entry = new CatalogEntry(entry.path(), next, entry.pinId(), entry.hullId(), store.get(entry.pinId()), "", store.get(entry.pinId()).toJson().toString());
			nameField.setText(next);
			return true;
		} catch (Exception exception) {
			statusText = strings.get("save_failed") + ": " + exception;
			saveErrors.failure(strings.get("save_failed"), entry.path(), exception);
			BattleNext_Logger.error(getClass(), "variant.pin.editor.commit.failed",
					BattleNext_Logger.fields().put("pin_id", entry.pinId()).put("name", next).build(), exception);
			return false;
		}
	}

	@Override
	protected void restoreDraft() {
		nameField = null;
	}

	@Override
	protected void closeEditorState() {
		details.clear();
		metadataContent = null;
	}
}
