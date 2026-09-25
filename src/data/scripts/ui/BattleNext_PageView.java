package data.scripts.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;

public interface BattleNext_PageView {

	void attach(CustomPanelAPI body);

	default void buttonPressed(Object buttonId) {

	}

	default boolean hasUnsavedChanges() {
		return false;
	}

	default boolean save() {
		return true;
	}

	default String getStatusText() {
		return "";
	}

	default void discard() {

	}
}
