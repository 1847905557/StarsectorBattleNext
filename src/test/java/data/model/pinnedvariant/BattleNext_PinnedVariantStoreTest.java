package data.model.pinnedvariant;

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
import data.test.fixture.BattleNext_TestGameFixture;
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

class BattleNext_PinnedVariantStoreTest extends BattleNext_TestGameFixture {

	@Test
	void catalogRetainsEveryErrorAndDeletesOnlyTheLoadedSourceFile() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		BattleNext_PinnedVariantModel pin = store.saveVariant("Valid", source().getApi());
		Path root = directory.resolve("tournament/_pinnedVariants");
		Files.writeString(root.resolve("broken.json"), "not json");
		Files.writeString(root.resolve("wrong.json"), pin.toJson().toString());
		JSONObject unavailable = pin.toJson();
		unavailable.put("pinId", "missing");
		unavailable.getJSONObject("variantJson").put("hullId", "unknown");
		Files.writeString(root.resolve("missing.json"), unavailable.toString());
		store.reload();
		assertEquals(4, store.getCatalog().size());
		assertEquals(1, store.getAll().size());
		assertEquals(3, store.getLoadErrors().size());
		var missing = store.getCatalog().stream().filter(entry -> entry.path().endsWith("missing.json")).findFirst().orElseThrow();
		assertEquals("Valid", missing.name());
		assertEquals("unknown", missing.hullId());
		assertNull(missing.model());
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/_pinnedVariants/../wrong.json"));
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/_pinnedVariants/unloaded.json"));
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/other.json"));
		store.deleteFile("tournament/_pinnedVariants/wrong.json");
		assertFalse(Files.exists(root.resolve("wrong.json")));
		assertTrue(Files.exists(root.resolve(pin.getPinId() + ".json")));
		store.deleteFile("tournament/_pinnedVariants/broken.json");
		assertEquals(2, store.getCatalog().size());
	}

	@Test
	void catalogSortUsesNameIdAndPath() {
		var a = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("z", "Same", "b", "", null, "");
		var b = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("a", "Same", "b", "", null, "");
		var c = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("c", "Same", "a", "", null, "");
		var d = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("d", "Alpha", "z", "", null, "");
		assertEquals(List.of(d, c, b, a), BattleNext_PinnedVariantPage.sorted(List.of(a, b, c, d)));
	}

}
