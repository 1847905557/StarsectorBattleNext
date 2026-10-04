package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.lwjgl.input.Keyboard;

class BattleNext_TextFieldSessionTest extends BattleNext_TestPageFixture {

	@Test
	void closingOneFieldPreservesTheOtherFieldsFocusAndRegistration() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = owner.createUIElement(200f, 100f, false);
		BattleNext_TextField first = new BattleNext_TextField(router, owner, 200f, 28f, host);
		BattleNext_TextField second = new BattleNext_TextField(router, owner, 200f, 28f, host);
		first.attach(host, owner);
		second.attach(host, owner);
		owner.addUIElement(host).inTL(0f, 0f);
		second.grabFocus(false);
		first.close();
		assertTrue(second.hasFocus());
		assertSame(router, getField(second, "inputRouter"));
		assertEquals(1, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		assertEquals(List.of(host), ((Map<?, ?>) getField(router.getSession(), "registrations")).get(second));
		second.close();
		assertFalse(router.getSession().hasInputState());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
		assertTrue(tooltips.get(host).getComponents().isEmpty());
	}

	@Test
	void textInputUsesBothInnerAndOuterScrollingViewports() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI outer = owner.createUIElement(400f, 100f, true);
		CustomPanelAPI innerPanel = owner.createCustomPanel(300f, 200f, null);
		outer.addCustom(innerPanel, 0f);
		outer.addSpacer(400f);
		TooltipMakerAPI inner = innerPanel.createUIElement(300f, 200f, true);
		inner.addSpacer(60f);
		BattleNext_TextField field = new BattleNext_TextField(router, innerPanel, 200f, 28f, inner, outer);
		field.attach(inner, innerPanel);
		inner.addSpacer(400f);
		innerPanel.addUIElement(inner).inTL(0f, 0f);
		owner.addUIElement(outer).inTL(0f, 0f);
		assertEquals(List.of(inner, outer), ((Map<?, ?>) getField(router.getSession(), "registrations")).get(field));
		outer.getExternalScroller().setYOffset(100f);
		PositionAPI position = field.getPosition();
		InputEventAPI event = proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isLMBDownEvent" -> true;
			case "getX" -> (int) position.getCenterX();
			case "getY" -> (int) position.getCenterY();
			default -> null;
		});
		assertFalse(router.handleGlobalInput(List.of(event)));
		assertFalse(field.hasFocus());
		field.grabFocus(false);
		field.close();
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void registrationKeepsOneImmutableViewportRecordForEachField() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		BattleNext_TextField field = createField(router, owner, 0f, 0f);
		TooltipMakerAPI inner = tooltip(200f, 100f, false);
		TooltipMakerAPI outer = tooltip(800f, 700f, false);
		TooltipMakerAPI[] viewports = {inner, outer};
		router.getSession().register(field, viewports);
		viewports[0] = outer;
		Map<?, ?> registrations = (Map<?, ?>) getField(router.getSession(), "registrations");
		assertEquals(1, registrations.size());
		assertEquals(List.of(inner, outer), registrations.get(field));
		List<?> registered = (List<?>) registrations.get(field);
		assertThrows(UnsupportedOperationException.class, registered::clear);
		router.getSession().register(field, outer);
		assertEquals(1, registrations.size());
		assertEquals(List.of(outer), registrations.get(field));
		field.close();
		assertTrue(registrations.isEmpty());
	}

	@Test
	void rootRoutersKeepFocusKeyboardInputAndCleanupIndependent() throws Exception {
		BattleNext_UiInputRouter firstRouter = new BattleNext_UiInputRouter();
		BattleNext_UiInputRouter secondRouter = new BattleNext_UiInputRouter();
		CustomPanelAPI firstRoot = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI secondRoot = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		BattleNext_TextField first = createField(firstRouter, firstRoot, 0f, 0f);
		BattleNext_TextField second = createField(secondRouter, secondRoot, 0f, 0f);
		first.grabFocus(false);
		second.grabFocus(false);
		assertTrue(first.hasFocus());
		assertTrue(second.hasFocus());
		assertTrue(firstRouter.handleGlobalInput(List.of(key('a'))));
		assertEquals("abcdefghija", first.getText());
		assertEquals("abcdefghij", second.getText());
		firstRouter.clear();
		assertFalse(first.hasFocus());
		assertTrue(second.hasFocus());
		assertTrue(secondRouter.handleGlobalInput(List.of(key('b'))));
		assertEquals("abcdefghijb", second.getText());
		first.close();
		second.close();
		assertFalse(firstRouter.getSession().hasInputState());
		assertFalse(secondRouter.getSession().hasInputState());
	}

	@Test
	void pointerCaptureContinuesOutsideTheFieldAndViewportUntilMouseRelease() throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		BattleNext_TextField field = createField(router, owner, 20f, 20f);
		field.setText("abcdefghij".repeat(20));
		field.processKeyboard(proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "getEventValue" -> Keyboard.KEY_HOME;
			default -> null;
		}));
		PositionAPI bounds = field.getPosition();
		InputEventAPI press = pointer("down", bounds.getX() + 3f, bounds.getCenterY(), false);
		assertTrue(router.handleGlobalInput(List.of(press)));
		assertEquals("PRESSED", getField(router.getSession(), "phase").toString());
		InputEventAPI move = pointer("move", bounds.getX() + 500f, bounds.getCenterY() - 300f, false);
		assertTrue(router.handleGlobalInput(List.of(move)));
		assertTrue(router.getSession().isDragging(field));
		var plugin = plugins.get((CustomPanelAPI) getField(field, "panel"));
		plugin.advance(0.5f);
		assertTrue(field.captureEditState().scroll() > 0f);
		assertEquals(0, field.captureEditState().anchor());
		assertTrue(field.captureEditState().cursor() > 0);
		InputEventAPI release = pointer("up", bounds.getX() + 500f, bounds.getCenterY() - 300f, false);
		assertTrue(router.handleGlobalInput(List.of(release)));
		assertFalse(router.getSession().isDragging(field));
		assertEquals("IDLE", getField(router.getSession(), "phase").toString());
		assertTrue(field.hasFocus());
		assertTrue(field.captureEditState().cursor() > field.captureEditState().anchor());
		int selected = field.captureEditState().cursor();
		assertTrue(router.handleGlobalInput(List.of(key('z'))));
		assertEquals("z" + "abcdefghij".repeat(20).substring(selected), field.getText());
		field.close();
	}

	@Test
	void closingAnUnrelatedFieldKeepsTheCapturedPointerAndClosingItsOwnerReleasesIt() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		BattleNext_TextField first = createField(router, owner, 20f, 20f);
		BattleNext_TextField second = createField(router, owner, 20f, 100f);
		assertTrue(router.handleGlobalInput(List.of(click(first))));
		assertTrue(router.handleGlobalInput(List.of(pointer("move", first.getPosition().getX() + 150f,
				first.getPosition().getCenterY(), false))));
		second.close();
		assertTrue(first.hasFocus());
		assertTrue(router.getSession().isDragging(first));
		first.close();
		assertFalse(router.getSession().hasInputState());
		assertEquals("IDLE", getField(router.getSession(), "phase").toString());
		InputEventAPI release = pointer("up", 500f, 400f, false);
		assertFalse(router.handleGlobalInput(List.of(release)));
		assertFalse(release.isConsumed());
	}

	@Test
	void nestedOverlaysUseTheCurrentTopMembershipForPointerAndKeyboardInput() throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TestOverlay parent = overlay(root, 20f, 20f);
		TestOverlay child = overlay(root, 20f, 20f);
		BattleNext_TextField background = createField(router, root, 500f, 20f);
		BattleNext_TextField parentField = createField(router, parent.panel, 0f, 0f);
		BattleNext_TextField childField = createField(router, child.panel, 0f, 0f);
		router.push(parent);
		router.registerModalField(parentField);
		router.registerModalField(parentField);
		assertEquals(Set.of(parentField), ((Map<?, ?>) getField(router, "modalFields")).get(parent));
		assertTrue(router.handleGlobalInput(List.of(click(parentField))));
		assertTrue(parentField.hasFocus());
		router.push(child);
		router.registerModalField(childField);
		assertFalse(parentField.hasFocus());
		background.grabFocus(false);
		InputEventAPI blocked = key('x');
		assertFalse(router.handleGlobalInput(List.of(blocked)));
		assertFalse(blocked.isConsumed());
		assertEquals("abcdefghij", background.getText());
		assertTrue(router.handleGlobalInput(List.of(click(childField))));
		assertTrue(childField.hasFocus());
		assertTrue(router.handleGlobalInput(List.of(key('c'))));
		assertEquals("abcdefghij", parentField.getText());
		router.pop(child);
		assertFalse(childField.hasFocus());
		assertTrue(router.isTop(parent));
		assertTrue(router.handleGlobalInput(List.of(click(parentField))));
		assertTrue(parentField.hasFocus());
		assertTrue(router.handleGlobalInput(List.of(key('p'))));
		router.pop(parent);
		assertTrue(router.isEmpty());
		assertTrue(router.handleGlobalInput(List.of(click(background))));
		assertTrue(background.hasFocus());
		background.close();
		parentField.close();
		childField.close();
	}

	@Test
	void removingAnUnderlyingOverlayPreservesTheTopFieldsFocusAndScope() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TestOverlay parent = overlay(root, 20f, 20f);
		TestOverlay child = overlay(root, 20f, 20f);
		BattleNext_TextField field = createField(router, child.panel, 0f, 0f);
		router.push(parent);
		router.push(child);
		router.registerModalField(field);
		assertTrue(router.handleGlobalInput(List.of(click(field))));
		router.pop(parent);
		assertTrue(field.hasFocus());
		assertTrue(router.isTop(child));
		assertTrue(router.handleGlobalInput(List.of(key('z'))));
		field.close();
		router.clear();
	}

	@Test
	void repeatedPushUsesExistingMembershipAndLiveOverlayBounds() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TestOverlay overlay = overlay(root, 20f, 20f);
		BattleNext_TextField field = createField(router, overlay.panel, 0f, 0f);
		router.push(overlay);
		router.registerModalField(field);
		assertTrue(router.handleGlobalInput(List.of(click(field))));
		router.push(overlay);
		assertFalse(field.hasFocus());
		assertTrue(router.handleGlobalInput(List.of(click(field))));
		InputEventAPI outside = pointer("down", field.getPosition().getCenterX(),
				field.getPosition().getCenterY(), false);
		overlay.bounds = position(10f, 10f);
		assertFalse(router.handleGlobalInput(List.of(outside)));
		assertFalse(field.hasFocus());
		overlay.bounds = overlay.panel.getPosition();
		assertTrue(router.handleGlobalInput(List.of(click(field))));
		field.close();
		router.clear();
	}

	@Test
	void modalPointerCaptureContinuesOutsideTheOverlayAndEndsOnLayerChange() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TestOverlay overlay = overlay(root, 20f, 20f);
		BattleNext_TextField field = createField(router, overlay.panel, 0f, 0f);
		router.push(overlay);
		router.registerModalField(field);
		assertTrue(router.handleGlobalInput(List.of(click(field))));
		assertTrue(router.handleGlobalInput(List.of(pointer("move", 1000f, 1000f, false))));
		assertTrue(router.getSession().isDragging(field));
		router.pop(overlay);
		assertFalse(router.getSession().isDragging(field));
		assertFalse(field.hasFocus());
		assertFalse(router.handleGlobalInput(List.of(pointer("up", 1000f, 1000f, false))));
		field.close();
	}

	@Test
	void closingAModalFieldRemovesItsMembershipFromEveryLayer() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TestOverlay parent = overlay(root, 20f, 20f);
		TestOverlay child = overlay(root, 20f, 20f);
		BattleNext_TextField field = createField(router, parent.panel, 0f, 0f);
		router.push(parent);
		router.registerModalField(field);
		router.push(child);
		router.registerModalField(field);
		field.close();
		Map<?, ?> memberships = (Map<?, ?>) getField(router, "modalFields");
		assertEquals(Set.of(), memberships.get(parent));
		assertEquals(Set.of(), memberships.get(child));
		assertFalse(router.getSession().hasInputState());
		router.clear();
		assertTrue(parent.closed);
		assertTrue(child.closed);
		assertTrue(memberships.isEmpty());
	}

	@Test
	void rootCleanupClosesAllLayersAndReleasesFocusPointerAndRegistrationTogether() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TestOverlay parent = overlay(root, 20f, 20f);
		TestOverlay child = overlay(root, 20f, 20f);
		BattleNext_TextField field = createField(router, child.panel, 0f, 0f);
		router.push(parent);
		router.push(child);
		router.registerModalField(field);
		assertTrue(router.handleGlobalInput(List.of(click(field))));
		assertTrue(router.handleGlobalInput(List.of(pointer("move", 400f, 400f, false))));
		router.clear();
		assertTrue(parent.closed);
		assertTrue(child.closed);
		assertFalse(field.hasFocus());
		assertFalse(router.getSession().isDragging(field));
		assertFalse(router.getSession().hasInputState());
		assertTrue(((Map<?, ?>) getField(router, "modalFields")).isEmpty());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
		field.close();
	}

	@Test
	void inputInterfacesExposeTheRootLifecycleAndKeepOneRegistrationStore() {
		Set<String> methods = Set.of("unregister", "clear", "finish", "handleGlobalInput", "hasInputState");
		assertEquals(methods, Set.copyOf(Arrays.stream(BattleNext_TextFieldSession.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers())).map(method -> method.getName()).toList()));
		assertEquals(1, Arrays.stream(BattleNext_TextFieldSession.class.getDeclaredFields())
				.filter(field -> Map.class.isAssignableFrom(field.getType())).count());
		assertEquals(0, Arrays.stream(BattleNext_TextField.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())).count());
	}

	private BattleNext_TextField createField(BattleNext_UiInputRouter router, CustomPanelAPI owner,
			float left, float top) throws Exception {
		TooltipMakerAPI host = owner.createUIElement(200f, 28f, false);
		BattleNext_TextField field = new BattleNext_TextField(router, owner, 200f, 28f, host);
		field.attach(host, owner);
		owner.addUIElement(host).inTL(left, top);
		field.setText("abcdefghij");
		var sound = BattleNext_TextField.class.getDeclaredField("soundSink");
		sound.setAccessible(true);
		sound.set(field, (Consumer<String>) ignored -> {});
		return field;
	}

	private TestOverlay overlay(CustomPanelAPI owner, float left, float top) {
		CustomPanelAPI panel = owner.createCustomPanel(300f, 200f, new BaseCustomUIPanelPlugin());
		owner.addComponent(panel).inTL(left, top);
		return new TestOverlay(panel);
	}

	private InputEventAPI click(BattleNext_TextField field) {
		PositionAPI bounds = field.getPosition();
		return pointer("down", bounds.getCenterX(), bounds.getCenterY(), false);
	}

	private InputEventAPI pointer(String type, float horizontal, float vertical, boolean shift) {
		AtomicBoolean consumed = new AtomicBoolean();
		return proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "consume" -> { consumed.set(true); yield null; }
			case "isLMBDownEvent" -> type.equals("down");
			case "isLMBUpEvent" -> type.equals("up");
			case "isMouseMoveEvent" -> type.equals("move");
			case "isShiftDown" -> shift;
			case "getX" -> (int) horizontal;
			case "getY" -> (int) vertical;
			default -> null;
		});
	}

	private InputEventAPI key(char character) {
		AtomicBoolean consumed = new AtomicBoolean();
		return proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "consume" -> { consumed.set(true); yield null; }
			case "isKeyboardEvent", "isKeyDownEvent" -> true;
			case "getEventChar" -> character;
			case "getEventValue" -> Keyboard.KEY_Z;
			default -> null;
		});
	}

	private class TestOverlay implements BattleNext_UiInputRouter.Overlay {
		private final CustomPanelAPI panel;
		private PositionAPI bounds;
		private boolean closed;

		private TestOverlay(CustomPanelAPI panel) {
			this.panel = panel;
			bounds = panel.getPosition();
		}

		@Override
		public PositionAPI getBounds() {
			return bounds;
		}

		@Override
		public boolean handleGlobalInput(List<InputEventAPI> events) {
			return false;
		}

		@Override
		public void close() {
			closed = true;
		}
	}
}
