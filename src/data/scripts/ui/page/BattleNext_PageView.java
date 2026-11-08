package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.BattleNext_View;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class BattleNext_PageView extends BattleNext_View {

	protected CustomPanelAPI panel;
	protected CustomPanelAPI overlayHost;
	protected String status = "";
	protected LabelAPI statusLabel;
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
		clearOperation();
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
		if (editorView() != null) editorView().advance(amount);
	}

	public String selection() {
		return "";
	}

	public void restoreSelection(String id) {
	}

	public void catalogChanged() {
		refreshContent();
	}

	protected abstract void loadPageState();

	protected abstract void buildPage();

	protected abstract void refreshPage();

	protected void bindPageInput() {
		if (editorView() != null) editorView().setInputRouter(inputRouter);
	}

	protected BattleNext_EditorView editorView() {
		return null;
	}

	protected final void attachEditor(CustomPanelAPI host) {
		BattleNext_EditorView editor = editorView();
		editor.setInputRouter(inputRouter);
		editor.setOverlayHost(overlayHost == null ? panel : overlayHost);
		editor.setPersistence(persistence());
		editor.attach(host);
	}

	@Override
	public final void setPersistence(Function<Callable<Boolean>, CompletableFuture<Boolean>> persistence) {
		super.setPersistence(persistence);
		if (editorView() != null) editorView().setPersistence(persistence);
	}

	@Override
	public final boolean isSaving() {
		return super.isSaving() || editorView() != null && editorView().isSaving();
	}

	protected final void persistMutation(Callable<Boolean> write, Runnable publish) {
		persistMutation(write, publish, strings.get("saving"), strings.get("delete_failed"));
	}

	public final void persistMutation(Callable<Boolean> write, Runnable publish, String status, String failure) {
		try {
			perform(write, publish, status, failure, false);
			if (isSaving()) setStatus(status);
		} catch (Exception exception) {
			setStatus(failure + ": " + exception.getMessage());
		}
	}

	public final Boolean completeSave() {
		boolean own = super.isSaving();
		boolean pending = isSaving();
		Boolean completed = own ? completeOperation() : editorView() == null ? null : editorView().completeSave();
		if (pending && !isSaving()) {
			if (!own && (completed != null || !editorView().getSaveErrors().isEmpty())) status = editorView().getStatusText();
			if (Boolean.TRUE.equals(completed)) savePublished();
			updatePageControls();
			updateStatus();
		}
		return completed;
	}

	protected void afterSave() {
	}

	protected void savePublished() {
	}

	public final void refreshAfterSave() {
		afterSave();
	}

	protected void establishPageState() {

	}

	protected void clearPageOverlays() {
		if (editorView() != null) editorView().closeOverlays();
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

	@Override
	protected final void operationStatus(String value) {
		setStatus(value);
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
