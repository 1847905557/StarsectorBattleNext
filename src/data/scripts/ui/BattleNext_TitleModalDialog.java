package data.scripts.ui;

import data.scripts.tools.BattleNext_Logger;

import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

class BattleNext_TitleModalDialog {

	private static final String TITLE_DIALOG_CLASS = "com.fs.starfarer.ui.oo0O";

	interface Access {
		List<?> children(UIPanelAPI screen) throws Throwable;
		boolean isDialog(Object child);
		void prepare(Class<?> titleClass) throws Throwable;
		void show(Object titleState) throws Throwable;
		void configure(Object dialog) throws Throwable;
		void dismiss(Object dialog) throws Throwable;
		void detach(UIPanelAPI screen, Object dialog) throws Throwable;
	}

	private final Access access;
	private Object owned;
	private UIPanelAPI ownerScreen;
	private boolean errorLogged;

	BattleNext_TitleModalDialog() {
		this(new VanillaAccess());
	}

	BattleNext_TitleModalDialog(Access access) {
		this.access = access;
	}

	void open(UIPanelAPI screen, Object titleState) {

		if (owned != null) return;
		List<?> before;
		try {
			before = access.children(screen);
			for (Object child : before) {
				if (access.isDialog(child)) return;
			}
			access.prepare(titleState.getClass());
		} catch (Throwable exception) {
			logFailure(exception);
			return;
		}

		Object created = null;
		try {
			access.show(titleState);
			created = addedDialog(before, access.children(screen));
			if (created == null) throw new IllegalStateException("Title message dialog was not attached");
			access.configure(created);
			owned = created;
			ownerScreen = screen;
		} catch (Throwable exception) {
			if (created != null) {
				try {
					access.dismiss(created);
					access.detach(screen, created);
				} catch (Throwable cleanup) {
					exception.addSuppressed(cleanup);
				}
			}
			logFailure(exception);
		}
	}

	void close() {
		if (owned == null) return;
		Object dialog = owned;
		UIPanelAPI screen = ownerScreen;
		try {
			access.dismiss(dialog);
			owned = null;
			ownerScreen = null;
		} catch (Throwable exception) {
			logFailure(exception);
			return;
		}
		try {
			access.detach(screen, dialog);
		} catch (Throwable exception) {
			logFailure(exception);
		}
	}

	boolean hasOwnedDialog() {
		return owned != null;
	}

	private Object addedDialog(List<?> before, List<?> after) {
		for (Object candidate : after) {
			if (!access.isDialog(candidate)) continue;
			boolean alreadyPresent = false;
			for (Object existing : before) {
				if (existing == candidate) alreadyPresent = true;
			}
			if (!alreadyPresent) return candidate;
		}
		return null;
	}

	private void logFailure(Throwable exception) {
		if (errorLogged) return;
		errorLogged = true;
		BattleNext_Logger.warn(BattleNext_TitleModalDialog.class, "title.modal.isolation.failed",
				BattleNext_Logger.fields().put("dialog_class", TITLE_DIALOG_CLASS).put("owns_dialog", owned != null).put("owns_screen", ownerScreen != null).build(), exception);
	}

	private static class VanillaAccess implements Access {
		private MethodHandle show;
		private MethodHandle backgroundDim;
		private MethodHandle instantOption;
		private MethodHandle keyboardAccept;
		private MethodHandle keyboardCancel;
		private MethodHandle keyboardConfirm;
		private MethodHandle rightClickCancel;
		private MethodHandle dismiss;

		@Override
		public List<?> children(UIPanelAPI screen) throws Throwable {
			return (List<?>) BattleNext_TitleEntry.invokeNoArg(screen, "getChildrenCopy");
		}

		@Override
		public boolean isDialog(Object child) {
			for (Class<?> type = child.getClass(); type != null; type = type.getSuperclass()) {
				if (TITLE_DIALOG_CLASS.equals(type.getName())) return true;
			}
			return false;
		}

		@Override
		public void prepare(Class<?> titleClass) throws Throwable {
			Class<?> dialogClass = Class.forName(TITLE_DIALOG_CLASS, false, titleClass.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			backgroundDim = lookup.findVirtual(dialogClass, "setBackgroundDimAmount",
					MethodType.methodType(void.class, float.class));
			instantOption = lookup.findVirtual(dialogClass, "makeOptionInstant",
					MethodType.methodType(void.class, int.class));
			keyboardAccept = lookup.findVirtual(dialogClass, "setKeyboardShortcutsToAcceptEnabled",
					MethodType.methodType(void.class, boolean.class));
			keyboardCancel = lookup.findVirtual(dialogClass, "setOptionOnKeyboardCancel",
					MethodType.methodType(void.class, int.class));
			keyboardConfirm = lookup.findVirtual(dialogClass, "setOptionOnKeyboardConfirm",
					MethodType.methodType(void.class, int.class));
			rightClickCancel = lookup.findVirtual(dialogClass, "setRightClickOutsideCancels",
					MethodType.methodType(void.class, boolean.class));
			dismiss = lookup.findVirtual(dialogClass, "dismiss", MethodType.methodType(void.class, int.class));
			show = MethodHandles.privateLookupIn(titleClass, MethodHandles.lookup())
					.findVirtual(titleClass, "showMessageDialog", MethodType.methodType(void.class, String.class));
		}

		@Override
		public void show(Object titleState) throws Throwable {
			show.invoke(titleState, "");
		}

		@Override
		public void configure(Object dialog) throws Throwable {
			backgroundDim.invoke(dialog, 0f);
			instantOption.invoke(dialog, 0);
			keyboardAccept.invoke(dialog, false);
			keyboardCancel.invoke(dialog, -1);
			keyboardConfirm.invoke(dialog, -1);
			rightClickCancel.invoke(dialog, false);
		}

		@Override
		public void dismiss(Object dialog) throws Throwable {
			dismiss.invoke(dialog, 0);
		}

		@Override
		public void detach(UIPanelAPI screen, Object dialog) throws Throwable {
			Object shield = BattleNext_TitleEntry.invokeNoArg(dialog, "getInterceptor");
			if (shield instanceof UIComponentAPI component) screen.removeComponent(component);
			if (dialog instanceof UIComponentAPI component) screen.removeComponent(component);
		}
	}
}
