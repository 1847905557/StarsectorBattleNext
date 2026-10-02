package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;

public interface BattleNext_EditorView {

	void setInputRouter(BattleNext_UiInputRouter router);

	void attach(CustomPanelAPI panel);

	void createContents();

	void refreshContent();

	void buttonPressed(Object buttonId);

	String serializeState();

	boolean serialize();

	void deserialize();

	boolean hasUnsavedChanges();

	default boolean save() {
		return serialize();
	}

	default void discard() {
		deserialize();
	}

	String getStatusText();
}
