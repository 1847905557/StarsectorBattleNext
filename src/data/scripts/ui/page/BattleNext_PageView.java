package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.widget.BattleNext_SaveErrors;

import java.util.List;

public abstract class BattleNext_PageView {

	protected CustomPanelAPI panel;
	protected CustomPanelAPI overlayHost;
	protected BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	protected final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	protected String status = "";
	protected LabelAPI statusLabel;
	protected final BattleNext_SaveErrors saveErrors = new BattleNext_SaveErrors();
	private CustomPanelAPI owner;

	public final void setInputRouter(BattleNext_UiInputRouter router) {
		inputRouter = router;
		bindPageInput();
	}

	public final void setOverlayHost(CustomPanelAPI host) {
		overlayHost = host;
	}

	public final void attach(CustomPanelAPI body) {
		close();
		owner = body;
		status = "";
		bindPageInput();
		loadPageState();
		createContents();
		establishPageState();
	}

	public final void createContents() {
		clearPageOverlays();
		if (editorView() != null) editorView().detach();
		inputRouter.clear();
		clearPageResources();
		releasePanel();
		panel = owner.createCustomPanel(owner.getPosition().getWidth(), owner.getPosition().getHeight(), owner.getPlugin());
		owner.addComponent(panel).inTL(0f, 0f);
		statusLabel = null;
		buildPage();
		refreshContent();
	}

	public final void refreshContent() {
		clearPageOverlays();
		if (editorView() != null) editorView().detach();
		inputRouter.clear();
		clearPageResources();
		refreshPage();
		updatePageControls();
		updateStatus();
	}

	public final void close() {
		saveErrors.clear();
		clearPageOverlays();
		if (editorView() != null) editorView().close();
		inputRouter.clear();
		clearPageResources();
		releasePanel();
	}

	public final void closeOverlays() {
		clearPageOverlays();
	}

	public final void buttonPressed(Object buttonId) {
		clearPageOverlays();
		handlePageAction(buttonId);
		updatePageControls();
		updateStatus();
	}

	public final boolean hasUnsavedChanges() {
		return isPageDirty();
	}

	public final boolean save() {
		saveErrors.clear();
		boolean saved = savePage();
		updatePageControls();
		updateStatus();
		return saved;
	}

	public final void discard() {
		saveErrors.clear();
		status = "";
		discardPage();
		updatePageControls();
		updateStatus();
	}

	public final String getStatusText() {
		return pageStatus();
	}

	public final List<String> getSaveErrors() {
		if (!saveErrors.isEmpty()) return saveErrors.getMessages();
		return editorView() == null ? List.of() : editorView().getSaveErrors();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return false;
	}

	public boolean handleEscape() {
		return false;
	}

	public void advance(float amount) {

	}

	protected abstract void loadPageState();

	protected abstract void buildPage();

	protected abstract void refreshPage();

	protected void bindPageInput() {

	}

	protected BattleNext_EditorView editorView() {
		return null;
	}

	protected final void attachEditor(CustomPanelAPI host) {
		BattleNext_EditorView editor = editorView();
		editor.setInputRouter(inputRouter);
		editor.setOverlayHost(overlayHost == null ? panel : overlayHost);
		editor.attach(host);
	}

	protected void establishPageState() {

	}

	protected void clearPageOverlays() {

	}

	protected void clearPageResources() {

	}

	protected void updatePageControls() {

	}

	protected void handlePageAction(Object buttonId) {

	}

	protected boolean isPageDirty() {
		return false;
	}

	protected boolean savePage() {
		return true;
	}

	protected void discardPage() {

	}

	protected String pageStatus() {
		return status.isEmpty() && editorView() != null ? editorView().getStatusText() : status;
	}

	protected final void setStatus(String value) {
		status = value;
		updateStatus();
	}

	private void releasePanel() {
		if (panel == null) return;
		owner.removeComponent(panel);
		panel = null;
		statusLabel = null;
	}

	private void updateStatus() {
		if (statusLabel != null) {
			statusLabel.setText(pageStatus());
		}
	}
}
