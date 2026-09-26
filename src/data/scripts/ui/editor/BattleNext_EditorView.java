package data.scripts.ui.editor;

import data.scripts.ui.page.BattleNext_PageView;

public interface BattleNext_EditorView extends BattleNext_PageView {

	/** serialize the whole editor state (every input) into a comparable snapshot */
	String serializeState();

	/** Capture current inputs and persist a valid edit; false leaves persisted data unchanged. */
	boolean serialize();

	/** deserialize: reload the open baseline into the editor and lay it out; replaces unsaved edits */
	void deserialize();

	/** whether the current state differs from the last serialized baseline */
	@Override
	boolean hasUnsavedChanges();

	/** confirm-dialog bridge: saving an editor is serializing it */
	@Override
	default boolean save() {
		return serialize();
	}

	/** confirm-dialog bridge: discarding an editor re-deserializes the open baseline */
	@Override
	default void discard() {
		deserialize();
	}

	@Override
	String getStatusText();
}
