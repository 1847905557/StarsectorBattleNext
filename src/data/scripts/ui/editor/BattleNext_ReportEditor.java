package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.report.BattleNext_BattleReportStore;
import data.model.report.BattleNext_ReportTarget;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_ReportDetails;

public class BattleNext_ReportEditor extends BattleNext_EditorView {

	private final BattleNext_BattleReportStore reports;
	private final BattleNext_TournamentStore tournaments;
	private final BattleNext_I18nSection reportStrings = BattleNext_I18nSection.getInstance("Report", "BattleNext_report_");

	private BattleNext_ReportTarget target;
	private BattleNext_ReportDetails details;
	private TextFieldAPI nameField;
	private String savedName;

	public BattleNext_ReportEditor(BattleNext_BattleReportStore reports, BattleNext_TournamentStore tournaments) {
		this.reports = reports;
		this.tournaments = tournaments;
	}

	public void open(BattleNext_ReportTarget source) {
		beginSession();
		target = source;
		savedName = source.report().getName();
		nameField = null;
		details = new BattleNext_ReportDetails(inputRouter, target, this::refreshContent);
	}

	@Override
	protected void buildEditorContent() {
		float areaWidth = panel.getPosition().getWidth();
		float areaHeight = panel.getPosition().getHeight();
		String edited = nameField == null ? savedName : nameField.getText();
		TooltipMakerAPI metadataContent = panel.createUIElement(areaWidth, 0f, false);
		metadataContent.addTitle(target.report().getName());
		nameField = createTextField("name", panel, metadataContent, areaWidth, reportStrings.get("name"), edited, true, metadataContent);
		float top = metadataContent.getHeightSoFar() + 8f;
		panel.addUIElement(metadataContent).inTL(0f, 0f);
		CustomPanelAPI contentPanel = panel.createCustomPanel(areaWidth, Math.max(1f, areaHeight - top), new BaseCustomUIPanelPlugin());
		panel.addComponent(contentPanel).inTL(0f, top);
		own(details).attach(contentPanel);
		bindViewport("details", details.getContent());
	}

	@Override
	public void buttonPressed(Object action) {
		details.buttonPressed(action);
	}

	@Override
	protected String draftState() {
		return nameField == null ? savedName : nameField.getText();
	}

	@Override
	protected boolean submitDraft() {
		if (!validateName(nameField.getText(), reportStrings.get("name_required"))) return false;
		String next = nameField.getText().trim();
		try {
			target.rename(reports, tournaments, next);
			savedName = next;
			nameField.setText(next);
			return true;
		} catch (Exception exception) {
			statusText = strings.get("save_failed") + ": " + exception;
			BattleNext_Logger.error(getClass(), "report.editor.commit.failed",
					BattleNext_Logger.fields().put("target", target.identity()).put("name", next).build(), exception);
			return false;
		}
	}

	@Override
	protected void restoreDraft() {
		nameField = null;
		details.clear();
	}

	@Override
	protected void closeEditorState() {
		if (details != null) details.clear();
	}
}
