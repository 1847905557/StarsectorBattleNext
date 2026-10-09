package data.test.fixture;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.*;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.campaign.luna.BattleNext_LunaPinnedVariantPanel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class BattleNext_TestVariantFixture extends BattleNext_TestPageFixture {

	protected void assertEquipmentSpacing(TooltipState state) {

		PositionAPI frame = geometry.get(state.getViewport()).getParent();
		List<UIComponentAPI> items = state.getComponents().subList(0, state.getComponents().size() - 1);
		assertFalse(items.isEmpty());
		PositionAPI first = items.get(0).getPosition();
		assertEquals(4f, frame.getY() + frame.getHeight() - 18f - first.getY() - first.getHeight()
				+ state.getScroller().getYOffset(), 0.001f);
		for (int index = 0; index < items.size(); index++) {
			PositionAPI row = items.get(index).getPosition();
			assertEquals(8f, row.getX() - frame.getX(), 0.001f);
			assertEquals(8f, frame.getX() + frame.getWidth() - row.getX() - row.getWidth(), 0.001f);
			if (index > 0) {
				PositionAPI previous = items.get(index - 1).getPosition();
				assertEquals(4f, previous.getY() - row.getY() - row.getHeight(), 0.001f);
			}
		}
		assertEquals(8f, state.getComponents().get(state.getComponents().size() - 1).getPosition().getHeight());
		assertEquals(8f, state.getViewport().getY() - frame.getY());
	}

	protected JSONObject equipmentJson() throws Exception {

		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		JSONArray mods = new JSONArray();
		JSONArray wings = new JSONArray();
		JSONArray groups = new JSONArray();
		JSONObject modules = new JSONObject();
		for (int index = 0; index < 12; index++) {
			mods.put("mod_" + index + "_with_a_long_name");
			wings.put("wing_" + index + "_with_a_long_name");
			modules.put("slot_" + index, new JSONObject().put("hullId", "child")
					.put("variantId", "module_" + index + "_long_variant_identity")
					.put("displayName", "Long module description " + index));
			JSONObject weapons = new JSONObject();
			for (int weapon = 0; weapon < 4; weapon++) {
				weapons.put("slot_" + weapon, "weapon_" + weapon + "_with_a_long_identity");
			}
			groups.put(new JSONObject().put("mode", "LINKED").put("autofire", true).put("weapons", weapons));
		}
		json.put("hullMods", mods).put("wings", wings).put("modules", modules).put("weaponGroups", groups);
		return json;
	}

	protected PositionAPI detailBounds(Object host) throws Exception {
		TooltipState identity = tooltips.get(lists(host).get("identity"));
		PositionAPI frame = geometry.get(identity.getViewport()).getParent();
		return geometry.get(frame).getParent();
	}

	protected void assertAligned(Object host) throws Exception {
		PositionAPI grid = detailBounds(host);
		TextFieldAPI field = (TextFieldAPI) getField(host, "name");
		assertEquals(0f, field.getPosition().getX() - grid.getX());
		assertEquals(0f, field.getPosition().getX() + field.getPosition().getWidth() - grid.getX() - grid.getWidth());
	}

	protected BattleNext_LunaPinnedVariantPanel open(Variant source, BattleNext_PinnedVariantStore store, int width) {
		BattleNext_LunaPinnedVariantPanel display;
		try {
			Constructor<BattleNext_LunaPinnedVariantPanel> constructor = BattleNext_LunaPinnedVariantPanel.class
					.getDeclaredConstructor(Runnable.class, BattleNext_PinnedVariantStore.class);
			constructor.setAccessible(true);
			display = constructor.newInstance(new Object[]{(Runnable) () -> {}, store});
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
		try {
			Method open = BattleNext_LunaPinnedVariantPanel.class.getDeclaredMethod("open",
					ShipVariantAPI.class, String.class);
			open.setAccessible(true);
			open.invoke(display, source.getApi(), "");
			Method attach = BattleNext_LunaPinnedVariantPanel.class.getDeclaredMethod("attach",
					CustomPanelAPI.class, float.class, float.class);
			attach.setAccessible(true);
			attach.invoke(display, panel(width, 560f, display), width, 560f);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
		return display;
	}

	protected TextFieldAPI name(BattleNext_LunaPinnedVariantPanel display) throws Exception {
		Field field = BattleNext_LunaPinnedVariantPanel.class.getDeclaredField("name");
		field.setAccessible(true);
		return (TextFieldAPI) field.get(display);
	}

	protected void setField(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	@SuppressWarnings("unchecked")
	protected Map<String, TooltipMakerAPI> lists(Object host) throws Exception {
		Object details = host instanceof BattleNext_VariantDetails ? host : getField(host, "details");
		return (Map<String, TooltipMakerAPI>) getField(details, "lists");
	}

}
