package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_ExpandablePanelInsetIntegrationTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@CsvSource({
			"320,false,false", "320,true,false", "320,false,true", "320,true,true",
			"400,false,false", "400,true,false", "400,false,true", "400,true,true",
			"1200,false,false", "1200,true,false", "1200,false,true", "1200,true,true"
	})
	void mountedHeadersKeepSymmetricInsetsAndTheirOuterBounds(int width, boolean portrait, boolean summary) throws Exception {

		deferTooltipWidth = true;
		paragraphLineHeight = 15f;
		CustomPanelAPI owner = panel(width + 50f, 200f, new BaseCustomUIPanelPlugin());
		for (boolean expanded : List.of(false, true)) {
			TooltipMakerAPI list = owner.createUIElement(width + 10f, 100f, true);
			BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
					"graphics/ship.png", portrait, portrait ? "graphics/portrait.png" : "",
					"Alpha", "Wolf", summary ? "Damage" : "", summary ? "CR" : "", null);
			BattleNext_ExpandablePanel.add(list, owner, width, expanded, 60f, header,
					() -> {}, ignored -> {}, body -> {});
			owner.addUIElement(list).inTL(11f, 13f);
			CustomPanelAPI root = (CustomPanelAPI) tooltips.get(list).getComponents().get(0);
			CustomPanelAPI top = (CustomPanelAPI) children.get(root).get(0);
			assertEquals(owner.getPosition().getX() + 16f, root.getPosition().getX());
			assertEquals(width, root.getPosition().getWidth());
			assertEquals(expanded ? 100f : 40f, root.getPosition().getHeight());
			assertEquals(root.getPosition().getX(), top.getPosition().getX());
			assertEquals(root.getPosition().getY() + root.getPosition().getHeight(),
					top.getPosition().getY() + top.getPosition().getHeight());
			assertEquals(40f, top.getPosition().getHeight());
			assertEquals(4f, owner.getPosition().getY() + owner.getPosition().getHeight() - 13f
					- root.getPosition().getY() - root.getPosition().getHeight());
			var layout = BattleNext_ExpandablePanel.headerLayout(width, portrait, summary);
			TooltipMakerAPI names = (TooltipMakerAPI) children.get(top).get(0);
			TooltipState nameState = tooltips.get(names);
			assertEquals(portrait ? 84f : 48f, layout.textX());
			for (var label : nameState.getComponents()) {
				assertEquals(top.getPosition().getX() + layout.textX(), label.getPosition().getX());
			}
			assertEquals(3f, top.getPosition().getY() + 40f
					- nameState.getComponents().get(0).getPosition().getY() - 15f);
			if (summary) {
				TooltipState metrics = tooltips.get(children.get(top).get(1));
				assertEquals(top.getPosition().getX() + layout.metricsX(), metrics.getViewport().getX());
				assertEquals(8f, top.getPosition().getX() + width
						- metrics.getViewport().getX() - metrics.getViewport().getWidth());
				assertEquals(8f, layout.metricsX() - layout.textX() - layout.textWidth());
				for (var label : metrics.getComponents()) {
					assertEquals(metrics.getViewport().getX(), label.getPosition().getX());
				}
			} else {
				assertEquals(8f, width - layout.textX() - layout.textWidth());
			}
			if (expanded) {
				CustomPanelAPI body = (CustomPanelAPI) children.get(root).get(1);
				assertEquals(root.getPosition().getX(), body.getPosition().getX());
				assertEquals(width, body.getPosition().getWidth());
				assertEquals(60f, body.getPosition().getHeight());
			}
			spriteCenters.clear();
			plugins.get(top).render(1f);
			assertEquals(portrait ? 2 : 1, spriteCenters.size());
			assertEquals(top.getPosition().getX() + 24f, spriteCenters.get(0)[0]);
			assertEquals(top.getPosition().getCenterY(), spriteCenters.get(0)[1]);
			if (portrait) {
				assertEquals(top.getPosition().getX() + 60f, spriteCenters.get(1)[0]);
				assertEquals(top.getPosition().getCenterY(), spriteCenters.get(1)[1]);
			}
			AtomicBoolean consumed = new AtomicBoolean();
			AtomicBoolean toggled = new AtomicBoolean();
			var event = proxy(InputEventAPI.class, (name, args) -> switch (name) {
				case "isConsumed" -> consumed.get();
				case "isLMBDownEvent" -> true;
				case "getX" -> (int) top.getPosition().getX() + 1;
				case "getY" -> (int) top.getPosition().getCenterY();
				case "consume" -> { consumed.set(true); yield null; }
				default -> null;
			});
			assertTrue(BattleNext_ExpandablePanel.handleHeaderInput(List.of(event), top.getPosition(),
					tooltips.get(list).getViewport(), () -> {}, () -> toggled.set(true)));
			assertTrue(consumed.get());
			assertTrue(toggled.get());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"single", "portrait", "missing", "missing_portrait", "missing_primary"})
	void loadedAndMissingImagesShareTheInsetWhileScrolling(String mode) throws Exception {

		boolean primary = !mode.equals("missing") && !mode.equals("missing_primary");
		boolean portrait = !mode.equals("single");
		boolean loadedPortrait = mode.equals("portrait") || mode.equals("missing_primary");
		CustomPanelAPI owner = panel(450f, 200f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI list = owner.createUIElement(410f, 60f, true);
		BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
				primary ? "graphics/ship.png" : "", portrait, loadedPortrait ? "graphics/portrait.png" : "",
				"Alpha", "Wolf", "", "", null);
		BattleNext_ExpandablePanel.add(list, owner, 400f, true, 100f, header,
				() -> {}, ignored -> {}, body -> {});
		owner.addUIElement(list).inTL(11f, 13f);
		CustomPanelAPI root = (CustomPanelAPI) tooltips.get(list).getComponents().get(0);
		CustomPanelAPI top = (CustomPanelAPI) children.get(root).get(0);
		for (float offset : List.of(0f, 40f)) {
			list.getExternalScroller().setYOffset(offset);
			spriteCenters.clear();
			plugins.get(top).render(1f);
			assertEquals(loadedPortrait ? 2 : 1, spriteCenters.size());
			assertEquals(top.getPosition().getX() + 24f, spriteCenters.get(0)[0]);
			assertEquals(top.getPosition().getCenterY(), spriteCenters.get(0)[1]);
			if (loadedPortrait) assertEquals(top.getPosition().getX() + 60f, spriteCenters.get(1)[0]);
			assertEquals(loadedPortrait ? 84f : 48f,
					((TooltipMakerAPI) children.get(top).get(0)).getPosition().getX() - top.getPosition().getX());
		}
	}
}
