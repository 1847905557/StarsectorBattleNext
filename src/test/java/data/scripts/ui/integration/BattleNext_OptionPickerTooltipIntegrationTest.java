package data.scripts.ui.integration;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_OptionPickerTooltipIntegrationTest extends BattleNext_TestPageFixture {

	private static final String LONG_PARAGRAPH = "Full description 20%. " + "Complete equipment paragraph. ".repeat(100).strip();

	@ParameterizedTest
	@ValueSource(ints = {320, 800})
	void nativeTooltipsBindToEveryTileAndRenderCompleteContentBeforeSizing(int width) throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(width, 700f, new BaseCustomUIPanelPlugin());
		BattleNext_OptionPicker picker = picker(owner, router, source(LONG_PARAGRAPH, null, false));
		open(picker);
		TooltipMakerAPI grid = grid(picker);
		List<UIComponentAPI> targets = nativeTargets(owner);
		assertEquals(30, targets.size());
		for (int index = 0; index < targets.size(); index++) {
			UIComponentAPI target = targets.get(index);
			var creator = attachedTooltips.get(target);
			var binding = tooltipBindings.get(target);
			assertInstanceOf(BaseTooltipCreator.class, creator);
			assertSame(tooltips.get(grid), binding.owner());
			assertEquals(TooltipMakerAPI.TooltipLocation.RIGHT, binding.location());
			assertFalse(binding.recreateEveryFrame());
			assertFalse(creator.isTooltipExpandable(null));
			assertEquals(360f, creator.getTooltipWidth(null));
			TooltipMakerAPI content = render(target);
			List<String> values = textValues(content);
			assertEquals(3, values.size());
			assertEquals(LONG_PARAGRAPH, values.get(2));
			assertEquals("Name " + values.get(1).substring(3), values.get(0));
			assertTrue(content.getHeightSoFar() > 240f);
			assertEquals(0f, content.getPosition().getHeight());
		}
		assertEquals(3, ((List<?>) getField(picker, "ownedPanels")).size());
		picker.close();
		assertTrue(nativeTargets(owner).isEmpty());
		assertTrue(router.isEmpty());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "REGISTERED")).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"image", "text", "missing"})
	void nativeBindingUsesTheWholeCardForEveryVisualKind(String kind) throws Exception {
		failSprites = kind.equals("missing");
		String icon = kind.equals("text") ? null : "graphics/option.png";
		var owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		var picker = picker(owner, new BattleNext_UiInputRouter(), source("Short paragraph.", icon, !kind.equals("text")));
		open(picker);
		var target = nativeTargets(owner).get(0);
		assertEquals(64f, target.getPosition().getWidth());
		assertEquals(64f, target.getPosition().getHeight());
		assertInstanceOf(CustomPanelAPI.class, target);
		var content = render(target);
		assertEquals("Short paragraph.", textValues(content).get(2));
		assertTrue(content.getHeightSoFar() < 240f);
		assertEquals(3, ((List<?>) getField(picker, "ownedPanels")).size());
		if (kind.equals("image")) assertTrue(imagePaths.containsValue(icon));
		if (kind.equals("text")) assertTrue(labels.contains("Nam"));
		picker.close();
	}

	@Test
	void filteringAndRepeatedOpeningKeepBindingsInTheCurrentComponentTree() throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		var owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		var picker = picker(owner, router, source("Short paragraph.", null, false));
		for (int cycle = 0; cycle < 3; cycle++) {
			open(picker);
			assertEquals(30, nativeTargets(owner).size());
			CustomPanelAPI previousGrid = (CustomPanelAPI) getField(picker, "gridPanel");
			List<UIComponentAPI> previousTargets = nativeTargets(previousGrid);
			((BattleNext_TextField) getField(picker, "search")).setText("Name 1");
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			plugins.get(popup).advance(0f);
			assertTrue(removedComponents.contains(previousGrid));
			List<UIComponentAPI> currentTargets = nativeTargets(owner);
			assertEquals(11, currentTargets.size());
			for (UIComponentAPI target : currentTargets) {
				assertFalse(previousTargets.contains(target));
				assertTrue(textValues(render(target)).get(0).contains("Name 1"));
			}
			((BattleNext_TextField) getField(picker, "search")).setText("unmatched");
			plugins.get(popup).advance(0f);
			assertTrue(nativeTargets(owner).isEmpty());
			assertEquals(3, ((List<?>) getField(picker, "ownedPanels")).size());
			picker.closeOverlays();
			assertTrue(nativeTargets(owner).isEmpty());
			assertEquals(1, ((List<?>) getField(picker, "ownedPanels")).size());
			assertTrue(router.isEmpty());
			assertTrue(((Map<?, ?>) getField(router.getSession(), "REGISTERED")).isEmpty());
		}
		TooltipMakerAPI refreshedHost = owner.createUIElement(80f, 28f, false);
		picker.attach(refreshedHost, owner);
		owner.addUIElement(refreshedHost).inTL(20f, 20f);
		open(picker);
		assertEquals(30, nativeTargets(owner).size());
		picker.close();
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"confirm", "cancel", "escape", "outside", "close"})
	void everyClosingEntryReleasesNativeTargetsAndSearchInput(String action) throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		var owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		List<String> draft = new ArrayList<>(List.of("first"));
		var host = owner.createUIElement(80f, 28f, false);
		var picker = new BattleNext_OptionPicker(router, owner, 80f, 28f, "...", 0f, optionSource(),
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, () -> draft, ids -> {
					draft.clear();
					draft.addAll(ids);
				});
		picker.attach(host, owner);
		owner.addUIElement(host).inTL(20f, 20f);
		open(picker);
		assertEquals(2, nativeTargets(owner).size());
		((BattleNext_TextField) getField(picker, "search")).grabFocus(false);
		Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
		selection.clear();
		selection.put("second", 1);
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		switch (action) {
			case "confirm", "cancel" -> plugins.get(popup).buttonPressed(buttonIds.get(action.toUpperCase()));
			case "escape", "outside" -> {
				AtomicBoolean consumed = new AtomicBoolean();
				assertTrue(router.handleGlobalInput(List.of(event(action, consumed))));
				assertTrue(consumed.get());
			}
			case "close" -> picker.close();
			default -> throw new IllegalArgumentException(action);
		}
		assertEquals(List.of(action.equals("confirm") ? "second" : "first"), draft);
		assertTrue(nativeTargets(owner).isEmpty());
		assertNull(getField(picker, "popup"));
		assertTrue(router.isEmpty());
		assertFalse(router.getSession().hasInputState());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "REGISTERED")).isEmpty());
		picker.close();
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(ints = {320, 800})
	void gridScrollAndConsumedModalClicksKeepTheirInputOwnership(int width) throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		var owner = panel(width, 700f, new BaseCustomUIPanelPlugin());
		var picker = picker(owner, router, source(LONG_PARAGRAPH, null, false));
		open(picker);
		TooltipMakerAPI grid = grid(picker);
		var scroller = grid.getExternalScroller();
		float offset = Math.min(100f, grid.getHeightSoFar() - scroller.getPosition().getHeight());
		scroller.setYOffset(offset);
		assertEquals(offset, scroller.getYOffset());
		UIComponentAPI first = tooltips.get(grid).getComponents().get(0);
		UIComponentAPI visible = tooltips.get(grid).getComponents().get(15);
		AtomicBoolean consumed = new AtomicBoolean();
		assertFalse(scroller.getPosition().containsEvent(click(first, consumed)));
		assertTrue(scroller.getPosition().containsEvent(click(visible, consumed)));
		var wheel = proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "isMouseScrollEvent" -> true;
			case "getEventValue" -> -120;
			case "getX" -> (int) visible.getPosition().getCenterX();
			case "getY" -> (int) visible.getPosition().getCenterY();
			case "isConsumed" -> consumed.get();
			case "consume" -> { consumed.set(true); yield null; }
			default -> null;
		});
		assertFalse(picker.handleGlobalInput(List.of(wheel)));
		assertFalse(consumed.get());
		render(visible);
		assertEquals(offset, scroller.getYOffset());

		BattleNext_UiInputRouter.Overlay modal = new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return owner.getPosition();
			}

			@Override
			public boolean handleGlobalInput(List<InputEventAPI> events) {
				for (InputEventAPI event : events) {
					event.consume();
				}
				return true;
			}

			@Override
			public void close() {

			}
		};
		router.push(modal);
		InputEventAPI blocked = click(visible, consumed);
		assertTrue(router.handleGlobalInput(List.of(blocked)));
		plugins.get(visible).processInput(List.of(blocked));
		Map<?, ?> selection = (Map<?, ?>) getField(picker, "selection");
		assertTrue(selection.isEmpty());
		router.pop(modal);
		consumed.set(false);
		plugins.get(visible).processInput(List.of(click(visible, consumed)));
		assertTrue(consumed.get());
		assertEquals(Map.of("id_15", 1), selection);
		picker.close();
		assertTrue(nativeTargets(owner).isEmpty());
	}

	private BattleNext_OptionPicker.OptionChoiceSource source(String text, String icon, boolean expectsIcon) {
		return new BattleNext_OptionPicker.OptionChoiceSource() {
			@Override
			public List<BattleNext_OptionPicker.OptionChoice> getOptions() {
				List<BattleNext_OptionPicker.OptionChoice> result = new ArrayList<>();
				for (int index = 0; index < 30; index++) {
					result.add(new BattleNext_OptionPicker.OptionChoice("id_" + index, "Name " + index));
				}
				return result;
			}

			@Override
			public int getMaxStack(String id) {
				return 1;
			}

			@Override
			public String getIconPath(String id) {
				return icon;
			}

			@Override
			public boolean expectsIcon() {
				return expectsIcon;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionPicker.OptionChoice option) {
				BattleNext_EquipmentTooltip.render(tooltip,
						new BattleNext_EquipmentTooltip.Content(option.getLabel(), option.getId(), text, List.of()));
			}
		};
	}

	private BattleNext_OptionPicker picker(CustomPanelAPI owner, BattleNext_UiInputRouter router,
			BattleNext_OptionPicker.OptionChoiceSource source) {
		var host = owner.createUIElement(80f, 28f, false);
		var picker = new BattleNext_OptionPicker(router, owner, 80f, 28f, "...", 0f, source,
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> {});
		picker.attach(host, owner);
		owner.addUIElement(host).inTL(20f, 20f);
		return picker;
	}

	private void open(BattleNext_OptionPicker picker) throws Exception {
		var method = picker.getClass().getDeclaredMethod("open");
		method.setAccessible(true);
		method.invoke(picker);
	}

	private TooltipMakerAPI grid(BattleNext_OptionPicker picker) throws Exception {
		CustomPanelAPI panel = (CustomPanelAPI) getField(picker, "gridPanel");
		assertEquals(1, children.get(panel).size());
		return (TooltipMakerAPI) children.get(panel).get(0);
	}

	private TooltipMakerAPI render(UIComponentAPI target) {
		var creator = attachedTooltips.get(target);
		TooltipMakerAPI content = tooltip(creator.getTooltipWidth(null), 0f, false);
		assertEquals(0f, content.getPosition().getHeight());
		creator.createTooltip(content, false, null);
		return content;
	}

	private List<UIComponentAPI> nativeTargets(UIComponentAPI root) {
		List<UIComponentAPI> result = new ArrayList<>();
		if (attachedTooltips.containsKey(root)) result.add(root);
		List<?> contents = root instanceof CustomPanelAPI panel ? children.getOrDefault(panel, List.of())
				: root instanceof TooltipMakerAPI tooltip ? tooltips.get(tooltip).getComponents() : List.of();
		for (Object child : contents) {
			result.addAll(nativeTargets((UIComponentAPI) child));
		}
		return result;
	}

	private List<String> textValues(TooltipMakerAPI content) {
		List<String> values = new ArrayList<>();
		for (UIComponentAPI component : tooltips.get(content).getComponents()) {
			if (component instanceof LabelAPI label) values.add(label.getText());
		}
		return values;
	}

	private InputEventAPI click(UIComponentAPI target, AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "isLMBDownEvent", "isMouseDownEvent" -> true;
			case "getX" -> (int) target.getPosition().getCenterX();
			case "getY" -> (int) target.getPosition().getCenterY();
			case "isConsumed" -> consumed.get();
			case "consume" -> { consumed.set(true); yield null; }
			default -> null;
		});
	}
}
