package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.*;
import data.model.BattleNext_BaseStore;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.BattleNextModPlugin;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.page.*;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import data.test.fixture.BattleNext_TestUiFixture;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lunalib.lunaRefit.LunaRefitManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;
import static org.junit.jupiter.api.Assertions.*;

class BattleNext_TableWidgetTest extends BattleNext_TestUiFixture {

	@ParameterizedTest
	@ValueSource(ints = {0, 20})
	void tablesMeasureRowsAndAdditionalContentBeforeMount(int count) {
		CustomPanelAPI root = panel(600f, 240f, new BaseCustomUIPanelPlugin());
		BattleNext_TableWidget table = new BattleNext_TableWidget(
				root.getPlugin(), new String[]{"Name"}, new float[]{1f}, count,
				(index, width, cells, content) -> content.addCustom(panel(width, 42f, root.getPlugin()), 0f),
				(width, content) -> content.addCustom(panel(width, 30f, root.getPlugin()), 5f));
		table.attach(root);
		TooltipState state = tooltips.get(table.getContent());
		assertEquals(count * 42f + 35f, state.getMountedHeight());
		assertEquals(Math.min(222f, state.getMountedHeight() + 10f), state.getViewport().getHeight());
		assertTrue(state.isScrollable());
		state.getScroller().setYOffset(10000f);
		assertEquals(Math.max(0f, state.getMountedHeight() - state.getViewport().getHeight()), state.getScroller().getYOffset());
	}
}
