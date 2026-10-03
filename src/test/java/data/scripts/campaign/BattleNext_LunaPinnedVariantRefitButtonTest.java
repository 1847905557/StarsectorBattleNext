package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.BattleNextModPlugin;
import data.test.fixture.BattleNext_TestVariantFixture;
import lunalib.lunaRefit.LunaRefitManager;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_LunaPinnedVariantRefitButtonTest extends BattleNext_TestVariantFixture {

	@Test
	void registrationAndLoadCleanupAreIdempotentAndMenuIsAlwaysVisible() {

		var registry = LunaRefitManager.INSTANCE.getButtons();
		var previous = List.copyOf(registry);
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
		var context = BattleNext_CampaignRefitContext.from(core, host);
		assertSame(member, context.member());
		assertSame(variant.getApi(), context.variant());
		assertSame(refit, context.refitPanel());
		assertEquals("", BattleNext_LunaPinnedVariantRefitButton.contextError(member, variant.getApi()));
		refit.setMember(null);
		context = BattleNext_CampaignRefitContext.from(core, host);
		assertNull(context.member());
		assertEquals("BattleNext_variant_pin_return_to_parent", BattleNext_LunaPinnedVariantRefitButton.contextError(null, context.variant()));
		assertEquals("BattleNext_refit_no_ship_selected", BattleNext_LunaPinnedVariantRefitButton.contextError(null, null));
		assertThrows(IllegalStateException.class,
				() -> BattleNext_CampaignRefitContext.from(new Core(new Tab(List.of())), host));
	}

	@ParameterizedTest
	@ValueSource(strings = {"callback", "reload", "leave", "detached", "left", "right"})
	void buttonOwnerReleasesItsPanelAcrossLifecyclePaths(String path) throws Exception {

		var registry = LunaRefitManager.INSTANCE.getButtons();
		var previous = registry.stream().filter(button -> button instanceof BattleNext_LunaPinnedVariantRefitButton).toList();
		registry.removeAll(previous);
		BattleNext_LunaPinnedVariantRefitButton.register();
		BattleNext_LunaPinnedVariantRefitButton button = (BattleNext_LunaPinnedVariantRefitButton)
				LunaRefitManager.getFirstButtonOfClass(BattleNext_LunaPinnedVariantRefitButton.class);
		BattleNext_LunaPinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), 640);
		TextFieldAPI field = name(display);
		field.grabFocus(false);
		AtomicBoolean removed = new AtomicBoolean();
		UIPanelAPI host = proxy(ChildrenHost.class, (method, args) -> {
			if ("removeComponent".equals(method)) {
				removed.set(true);
			}
			if ("getChildrenCopy".equals(method)) return List.of();
			return null;
		});
		setField(button, "display", display);
		setField(button, "background", panel(640f, 560f, display));
		setField(button, "host", host);
		Global.setSector(proxy(SectorAPI.class, (method, args) -> "getCampaignUI".equals(method)
				? proxy(CampaignUIAPI.class, (name, values) ->
						"getCurrentCoreTab".equals(name) && "detached".equals(path)
								? CoreUITabId.REFIT : null) : null));
		try {
			if ("callback".equals(path)) {
				button.onPanelClose(null, null, null);
			} else if ("reload".equals(path)) {
				BattleNext_LunaPinnedVariantRefitButton.onGameLoad();
			} else if ("left".equals(path) || "right".equals(path)) {
				button.onClick(null, null, proxy(InputEventAPI.class,
						(method, args) -> "isLMBEvent".equals(method) && "left".equals(path)), null);
			} else {
				button.advance(0f);
			}
			assertEquals(!"right".equals(path), removed.get());
			assertEquals("right".equals(path), field.hasFocus());
			assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		} finally {
			button.onPanelClose(null, null, null);
			registry.remove(button);
			registry.addAll(previous);
		}
	}
}
