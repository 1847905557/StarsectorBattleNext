package data.scripts.ui.page;

import com.fs.starfarer.api.ui.CustomPanelAPI;

public interface BattleNext_PageView {

	default void setInputRouter(data.scripts.ui.BattleNext_UiInputRouter router) { }

	void attach(CustomPanelAPI body);

	void createContents();

	void refreshContent();

	default void buttonPressed(Object buttonId) {

	}

	default boolean hasUnsavedChanges() {
		return false;
	}

	default boolean save() {
		return true;
	}

	default void discard() {

	}

	default String getStatusText() {
		return "";
	}
}
