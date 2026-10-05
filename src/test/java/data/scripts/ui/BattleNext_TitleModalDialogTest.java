package data.scripts.ui;

import com.fs.starfarer.api.ui.UIPanelAPI;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

class BattleNext_TitleModalDialogTest {

	@Test
	void ownedDialogIsCreatedOnceAndDismissedBeforeDetaching() throws ReflectiveOperationException {
		FakeAccess access = new FakeAccess();
		BattleNext_TitleModalDialog modal = modal(access);
		UIPanelAPI screen = screen();

		modal.open(screen, new Object());
		modal.open(screen, new Object());
		assertTrue(modal.hasOwnedDialog());
		assertEquals(List.of("prepare", "show", "configure"), access.calls);

		modal.close();
		modal.close();
		assertFalse(modal.hasOwnedDialog());
		assertEquals(List.of("prepare", "show", "configure", "dismiss", "detach"), access.calls);
		assertTrue(access.children.isEmpty());

		modal.open(screen, new Object());
		assertTrue(modal.hasOwnedDialog());
		assertEquals(2, access.shows);
	}

	@Test
	void existingRefitDialogKeepsItsOwnerAndState() throws ReflectiveOperationException {
		FakeAccess access = new FakeAccess();
		FakeDialog original = new FakeDialog();
		access.children.add(original);
		BattleNext_TitleModalDialog modal = modal(access);

		modal.open(screen(), new Object());
		modal.close();

		assertFalse(modal.hasOwnedDialog());
		assertEquals(List.of(original), access.children);
		assertTrue(access.calls.isEmpty());
	}

	@Test
	void failedSetupLeavesEntryAvailableAndCleansCreatedDialog() throws ReflectiveOperationException {
		FakeAccess access = new FakeAccess();
		access.failConfigure = true;
		BattleNext_TitleModalDialog modal = modal(access);

		modal.open(screen(), new Object());

		assertFalse(modal.hasOwnedDialog());
		assertEquals(List.of("prepare", "show", "configure", "dismiss", "detach"), access.calls);
		assertTrue(access.children.isEmpty());
	}

	@Test
	void failedPreflightSkipsNativeDialogCreation() throws ReflectiveOperationException {
		FakeAccess access = new FakeAccess();
		access.failPrepare = true;
		BattleNext_TitleModalDialog modal = modal(access);

		modal.open(screen(), new Object());

		assertFalse(modal.hasOwnedDialog());
		assertEquals(List.of("prepare"), access.calls);
		assertTrue(access.children.isEmpty());
	}

	private BattleNext_TitleModalDialog modal(FakeAccess access) throws ReflectiveOperationException {
		BattleNext_TitleModalDialog modal = new BattleNext_TitleModalDialog();
		Field bridge = BattleNext_TitleModalDialog.class.getDeclaredField("access");
		bridge.setAccessible(true);
		bridge.set(modal, access);
		return modal;
	}

	private UIPanelAPI screen() {
		return (UIPanelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{UIPanelAPI.class}, (instance, method, args) -> null);
	}

	private static class FakeDialog {
	}

	private static class FakeAccess implements BattleNext_TitleModalDialog.Access {
		private final List<Object> children = new ArrayList<>();
		private final List<String> calls = new ArrayList<>();
		private boolean failPrepare;
		private boolean failConfigure;
		private int shows;

		@Override
		public List<?> children(UIPanelAPI screen) {
			return new ArrayList<>(children);
		}

		@Override
		public boolean isDialog(Object child) {
			return child instanceof FakeDialog;
		}

		@Override
		public void prepare(Class<?> titleClass) {
			calls.add("prepare");
			if (failPrepare) throw new IllegalStateException("preflight failed");
		}

		@Override
		public void show(Object titleState) {
			calls.add("show");
			shows++;
			children.add(new FakeDialog());
		}

		@Override
		public void configure(Object dialog) {
			calls.add("configure");
			if (failConfigure) throw new IllegalStateException("configure failed");
		}

		@Override
		public void dismiss(Object dialog) {
			calls.add("dismiss");
		}

		@Override
		public void detach(UIPanelAPI screen, Object dialog) {
			calls.add("detach");
			children.remove(dialog);
		}
	}
}
