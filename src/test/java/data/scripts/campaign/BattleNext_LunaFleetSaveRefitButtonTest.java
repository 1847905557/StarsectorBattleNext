package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.BattleNextModPlugin;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestCampaignFleetFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

class BattleNext_LunaFleetSaveRefitButtonTest extends BattleNext_TestCampaignFleetFixture {

	private List<BaseRefitButton> previousButtons;
	private BattleNext_LunaFleetSaveRefitButton button;

	@BeforeEach
	void configureButton() {
		previousButtons = List.copyOf(LunaRefitManager.INSTANCE.getButtons());
		LunaRefitManager.INSTANCE.getButtons().removeIf(entry -> entry instanceof BattleNext_LunaFleetSaveRefitButton);
		BattleNext_LunaFleetSaveRefitButton.register();
		button = (BattleNext_LunaFleetSaveRefitButton) LunaRefitManager.getFirstButtonOfClass(BattleNext_LunaFleetSaveRefitButton.class);
	}

	@AfterEach
	void restoreButtons() {
		button.onPanelClose(null, null, null);
		LunaRefitManager.INSTANCE.getButtons().clear();
		LunaRefitManager.INSTANCE.getButtons().addAll(previousButtons);
	}

	@Test
	void applicationRegistrationAndGameLoadAreIdempotent() {
		lunaEnabled = true;
		new BattleNextModPlugin().onApplicationLoad();
		BattleNext_LunaFleetSaveRefitButton.register();
		assertEquals(1, LunaRefitManager.INSTANCE.getButtons().stream()
				.filter(entry -> entry instanceof BattleNext_LunaFleetSaveRefitButton).count());
		BattleNext_LunaFleetSaveRefitButton.onGameLoad();
		BattleNext_LunaFleetSaveRefitButton.onGameLoad();
		assertEquals(List.of(button), scripts);
		assertTrue(button.runWhilePaused());
		assertFalse(button.isDone());
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_action"), button.getButtonName(selected, activeVariant.getApi()));
		assertEquals("graphics/icons/missions/analyze_entity.png", button.getIconName(selected, activeVariant.getApi()));
		BaseRefitButton pinned = LunaRefitManager.getFirstButtonOfClass(BattleNext_LunaPinnedVariantRefitButton.class);
		assertTrue(button.getOrder(selected, activeVariant.getApi()) > pinned.getOrder(selected, activeVariant.getApi()));
	}

	@Test
	void buttonAllowsPlayerMotherShipsAndExplainsDisabledModuleAndForeignContexts() {
		assertTrue(button.shouldShow(null, activeVariant.getApi(), null));
		assertTrue(button.hasPanel(selected, activeVariant.getApi(), null));
		assertTrue(button.hasTooltip(selected, activeVariant.getApi(), null));
		assertTrue(button.isClickable(selected, activeVariant.getApi(), null));
		assertFalse(button.isClickable(null, activeVariant.getApi(), null));
		assertFalse(button.isClickable(selected, null, null));
		assertFalse(button.isClickable(member("Foreign", otherVariant.getApi(), officer, false), activeVariant.getApi(), null));
		button.addTooltip(tooltip(300f, 0f, false), null, activeVariant.getApi(), null);
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_return_to_parent")));
		button.addTooltip(tooltip(300f, 0f, false), selected, activeVariant.getApi(), null);
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_description")));
	}

	@Test
	void panelDimensionsRespectScreenLimits() {
		assertEquals(560f, button.getPanelWidth(null, null));
		assertEquals(300f, button.getPanelHeight(null, null));
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> switch (method) {
			case "getScreenWidth" -> 500f;
			case "getScreenHeight" -> 250f;
			default -> null;
		}));
		assertEquals(400f, button.getPanelWidth(null, null));
		assertEquals(200f, button.getPanelHeight(null, null));
	}

	@Test
	void sharedContextUsesActualParentAndSelectedMotherVariant() throws Throwable {
		CustomPanelAPI actual = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		ParentHost background = proxy(ParentHost.class, (method, args) -> "getParent".equals(method) ? actual : null);
		assertSame(actual, BattleNext_CampaignRefitContext.parentOf(background));
		Refit refit = new Refit(selected, activeVariant.getApi());
		var context = BattleNext_CampaignRefitContext.from(new Core(new Tab(List.of(refit))), actual);
		assertSame(actual, context.host());
		assertSame(selected, context.member());
		assertSame(activeVariant.getApi(), context.variant());
		assertSame(refit, context.refitPanel());
	}

	@ParameterizedTest
	@ValueSource(strings = {"callback", "reload", "leave", "detached", "left", "right", "switch"})
	void ownerCleansMountedContentsAndInputAcrossLifecyclePaths(String path) throws Exception {

		CustomPanelAPI root = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI background = root.createCustomPanel(560f, 300f, new BaseCustomUIPanelPlugin());
		root.addComponent(background).inTL(0f, 0f);
		BattleNext_LunaFleetSavePanel display = new BattleNext_LunaFleetSavePanel(() -> {});
		display.open(selected, activeVariant.getApi(), "");
		CustomPanelAPI content = background.createCustomPanel(560f, 300f, display);
		background.addComponent(content).inTL(0f, 0f);
		display.attach(content, 560f, 300f);
		BattleNext_TextField field = (BattleNext_TextField) getField(display, "name");
		field.grabFocus(false);
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(display, "inputRouter");
		ChildrenHost host = proxy(ChildrenHost.class, (method, args) -> {
			if ("getChildrenCopy".equals(method)) return children.get(root);
			if ("removeComponent".equals(method)) root.removeComponent((UIComponentAPI) args[0]);
			return null;
		});
		setField(button, "background", background);
		setField(button, "content", content);
		setField(button, "display", display);
		setField(button, "host", host);
		if ("callback".equals(path)) {
			button.onPanelClose(null, null, null);
			root.removeComponent(background);
		} else if ("reload".equals(path)) {
			BattleNext_LunaFleetSaveRefitButton.onGameLoad();
		} else if ("left".equals(path) || "right".equals(path)) {
			button.onClick(selected, activeVariant.getApi(), proxy(InputEventAPI.class,
					(method, args) -> "isLMBEvent".equals(method) && "left".equals(path)), null);
		} else {
			if ("detached".equals(path)) root.removeComponent(background);
			if ("leave".equals(path)) tab = CoreUITabId.FLEET;
			if ("switch".equals(path)) {
				setField(button, "openedContext", new BattleNext_CampaignRefitContext(root, new Object(), selected, activeVariant.getApi()));
			}
			button.advance(0f);
		}
		assertEquals("right".equals(path), field.hasFocus());
		assertEquals("right".equals(path) ? 1 : 0, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		if (!"right".equals(path)) {
			assertTrue(children.get(root).isEmpty());
			assertTrue(children.get(background).isEmpty());
			assertTrue(children.get(content).isEmpty());
			assertNull(getField(button, "background"));
		}
		assertFalse(Files.exists(directory.resolve("tournament")));
		if ("right".equals(path)) {
			button.onPanelClose(null, null, null);
			root.removeComponent(background);
		}
	}

	public interface ParentHost extends UIPanelAPI {
		UIPanelAPI getParent();
	}
}
