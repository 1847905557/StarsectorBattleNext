package data.scripts.campaign.luna;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.BattleNextModPlugin;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import lunalib.lunaRefit.LunaRefitManager;
import lunalib.lunaRefit.BaseRefitButton;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

class BattleNext_LunaPinnedVariantRefitButtonTest extends BattleNext_TestVariantFixture {

	@Test
	void registrationAndLoadCleanupAreIdempotentAndMenuIsAlwaysVisible() {

		ArrayList<BaseRefitButton> registry = LunaRefitManager.INSTANCE.getButtons();
		List<BaseRefitButton> previous = List.copyOf(registry);
		registry.removeIf(button -> button instanceof BattleNext_LunaPinnedVariantRefitButton);
		List<EveryFrameScript> scripts = new ArrayList<>();
		Global.setSector(proxy(SectorAPI.class, (method, args) -> {
			if ("addTransientScript".equals(method)) {
				scripts.add((EveryFrameScript) args[0]);
			}
			if ("removeTransientScript".equals(method)) {
				scripts.remove(args[0]);
			}
			return null;
		}));
		try {
			lunaEnabled = true;
			new BattleNextModPlugin().onApplicationLoad();
			BattleNext_LunaPinnedVariantRefitButton.register();
			assertEquals(1, registry.stream().filter(button -> button instanceof BattleNext_LunaPinnedVariantRefitButton).count());
			BattleNext_LunaPinnedVariantRefitButton button = (BattleNext_LunaPinnedVariantRefitButton)
					LunaRefitManager.getFirstButtonOfClass(BattleNext_LunaPinnedVariantRefitButton.class);
			assertTrue(button.shouldShow(null, null, null));
			assertTrue(button.hasPanel(null, null, null));
			assertTrue(button.hasTooltip(null, null, null));
			assertTrue(button.runWhilePaused());
			assertEquals(1280f, button.getPanelWidth(null, null));
			assertEquals(720f, button.getPanelHeight(null, null));
			assertFalse(button.isDone());
			BattleNext_LunaPinnedVariantRefitButton.onGameLoad();
			BattleNext_LunaPinnedVariantRefitButton.onGameLoad();
			assertEquals(List.of(button), scripts);
		} finally {
			registry.clear();
			registry.addAll(previous);
		}
	}

	@Test
	void vanillaContextDistinguishesParentShipAndModuleAndRejectsMissingPanel() throws Throwable {

		Variant variant = source();
		FleetMemberAPI member = proxy(FleetMemberAPI.class, (method, args) -> null);
		UIPanelAPI host = panel(640f, 560f, new BaseCustomUIPanelPlugin());
		Refit refit = new Refit(member, variant.getApi());
		Core core = new Core(new Tab(List.of(new Object(), refit)));
		BattleNext_CampaignRefitContext context = BattleNext_CampaignRefitContext.from(core, host);
		assertSame(member, context.member());
		assertSame(variant.getApi(), context.variant());
		assertSame(refit, context.refitPanel());
		assertEquals("", BattleNext_LunaPinnedVariantRefitButton.contextError(member, variant.getApi()));
		refit.setMember(null);
		context = BattleNext_CampaignRefitContext.from(core, host);
		assertNull(context.member());
		assertEquals(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_pin_return_to_parent"), BattleNext_LunaPinnedVariantRefitButton.contextError(null, context.variant()));
		assertEquals(BattleNext_TestTextCatalog.text("Refit", "BattleNext_refit_no_ship_selected"), BattleNext_LunaPinnedVariantRefitButton.contextError(null, null));
		assertThrows(IllegalStateException.class,
				() -> BattleNext_CampaignRefitContext.from(new Core(new Tab(List.of())), host));
	}

	@ParameterizedTest
	@ValueSource(strings = {"callback", "reload", "leave", "detached", "left", "right", "context_unavailable"})
	void buttonOwnerReleasesItsPanelAcrossLifecyclePaths(String path) throws Exception {

		ArrayList<BaseRefitButton> registry = LunaRefitManager.INSTANCE.getButtons();
		List<BaseRefitButton> previous = registry.stream().filter(button -> button instanceof BattleNext_LunaPinnedVariantRefitButton).toList();
		registry.removeAll(previous);
		BattleNext_LunaPinnedVariantRefitButton.register();
		BattleNext_LunaPinnedVariantRefitButton button = (BattleNext_LunaPinnedVariantRefitButton)
				LunaRefitManager.getFirstButtonOfClass(BattleNext_LunaPinnedVariantRefitButton.class);
		CustomPanelAPI root = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI background = root.createCustomPanel(640f, 560f, new BaseCustomUIPanelPlugin());
		root.addComponent(background).inTL(0f, 0f);
		CustomPanelAPI sibling = root.createCustomPanel(20f, 20f, new BaseCustomUIPanelPlugin());
		root.addComponent(sibling).inTL(0f, 0f);
		BattleNext_LunaPinnedVariantPanel display = new BattleNext_LunaPinnedVariantPanel(() -> {});
		display.open(source().getApi(), "");
		CustomPanelAPI content = background.createCustomPanel(640f, 560f, display);
		background.addComponent(content).inTL(0f, 0f);
		display.attach(content, 640f, 560f);
		TextFieldAPI field = name(display);
		field.grabFocus(false);
		UIPanelAPI host = proxy(ChildrenHost.class, (method, args) -> {
			if ("removeComponent".equals(method)) root.removeComponent((UIComponentAPI) args[0]);
			if ("getChildrenCopy".equals(method)) return children.get(root);
			return null;
		});
		setField(button, "display", display);
		setField(button, "background", background);
		setField(button, "content", content);
		setField(button, "host", host);
		if ("context_unavailable".equals(path)) {
			setField(button, "openedContext", new BattleNext_CampaignRefitContext(root, new Object(), null, null));
		}
		Global.setSector(proxy(SectorAPI.class, (method, args) -> "getCampaignUI".equals(method)
				? proxy(CampaignUIAPI.class, (name, values) -> "getCurrentCoreTab".equals(name)
						? "leave".equals(path) ? CoreUITabId.FLEET : CoreUITabId.REFIT : null) : null));
		try {
			if ("callback".equals(path)) {
				button.onPanelClose(null, null, null);
				assertEquals(List.of(background, sibling), children.get(root));
				root.removeComponent(background);
			} else if ("reload".equals(path)) {
				BattleNext_LunaPinnedVariantRefitButton.onGameLoad();
			} else if ("left".equals(path) || "right".equals(path)) {
				button.onClick(null, null, proxy(InputEventAPI.class,
						(method, args) -> "isLMBEvent".equals(method) && "left".equals(path)), null);
			} else {
				if ("detached".equals(path)) root.removeComponent(background);
				button.advance(0f);
			}
			if (!"right".equals(path)) {
				assertEquals(List.of(sibling), children.get(root));
				assertTrue(children.get(background).isEmpty());
				assertEquals(1L, removedComponents.stream().filter(component -> component == content).count());
				assertNull(getField(button, "background"));
				assertNull(getField(button, "content"));
				assertNull(getField(button, "host"));
				assertNull(getField(button, "openedContext"));
				assertNull(getField(button, "display"));
				int removals = removedComponents.size();
				button.onPanelClose(null, null, null);
				button.advance(0f);
				assertEquals(removals, removedComponents.size());
			}
			assertEquals("right".equals(path), field.hasFocus());
			assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		} finally {
			button.onPanelClose(null, null, null);
			if ("right".equals(path)) root.removeComponent(background);
			registry.remove(button);
			registry.addAll(previous);
		}
	}
}
