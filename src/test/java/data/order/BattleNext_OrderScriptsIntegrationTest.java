package data.order;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import data.scripts.order.BattleNext_OrderThread;
import data.test.fixture.BattleNext_TestOrderFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

class BattleNext_OrderScriptsIntegrationTest extends BattleNext_TestOrderFixture {

	@ParameterizedTest
	@ValueSource(strings = {"targetHp", "selfHp", "targetFlux", "selfFlux"})
	void singleBoundsUseTheDeclaredReceiverAndUnitWithInclusiveZeroAndUpperEndpoints(String id) {

		ShipState source = world.ship(0, 0f);
		ShipState target = world.ship(1, 10f);
		source.setHp(4000f, 0.5f);
		source.setFlux(2000f, 0.2f);
		target.setHp(1000f, 0.1f);
		target.setFlux(1000f, 0.1f);
		List<CombatEntityAPI> candidates = List.of(target.getApi());
		int absolute = id.startsWith("self") ? id.endsWith("Hp") ? 4000 : 2000 : 1000;
		int percent = id.startsWith("self") ? id.endsWith("Hp") ? 50 : 20 : 10;
		assertEquals(candidates, filtered(id, List.of(Integer.toString(absolute)), source, candidates));
		assertTrue(filtered(id, List.of(Integer.toString(absolute - 1)), source, candidates).isEmpty());
		assertEquals(candidates, filtered(id, List.of(percent + "%"), source, candidates));
		assertTrue(filtered(id, List.of((percent - 1) + "%"), source, candidates).isEmpty());
		assertEquals(candidates, filtered(id, List.of("150%"), source, candidates));
		assertTrue(filtered(id, List.of("0"), source, candidates).isEmpty());
		source.setHp(0f, 0f);
		source.setFlux(0f, 0f);
		target.setHp(0f, 0f);
		target.setFlux(0f, 0f);
		assertEquals(candidates, filtered(id, List.of("0"), source, candidates));
		assertEquals(candidates, filtered(id, List.of("0%"), source, candidates));
	}

	@ParameterizedTest
	@ValueSource(strings = {"targetHp", "selfHp", "targetFlux", "selfFlux"})
	void mixedBoundsCompareAbsoluteAndRelativeEndpointsIndependently(String id) {
		ShipState ship = world.ship(0, 0f);
		ship.setHp(1500f, 0.4f);
		ship.setFlux(1500f, 0.4f);
		assertEquals(1, filtered(id, List.of("1000", "50%"), ship, List.of(ship.getApi())).size());
		assertEquals(1, filtered(id, List.of("30%", "1500"), ship, List.of(ship.getApi())).size());
		assertTrue(filtered(id, List.of("1600", "50%"), ship, List.of(ship.getApi())).isEmpty());
		assertTrue(filtered(id, List.of("30%", "1400"), ship, List.of(ship.getApi())).isEmpty());
	}

	@Test
	void numericFiltersRemoveEveryViolatingTargetAndIncludeBothEndpoints() {
		ShipState source = world.ship(0, 0f);
		ShipState low = world.ship(1, 20f);
		ShipState middle = world.ship(1, 40f);
		ShipState high = world.ship(1, 60f);
		low.setHp(200f, 0.2f);
		middle.setHp(500f, 0.5f);
		high.setHp(900f, 0.9f);
		low.setFlux(200f, 0.2f);
		middle.setFlux(500f, 0.5f);
		high.setFlux(900f, 0.9f);
		List<CombatEntityAPI> candidates = List.of(low.getApi(), middle.getApi(), high.getApi());
		assertEquals(List.of(middle.getApi()), filtered("targetHp", List.of("30%", "70%"), source, candidates));
		assertEquals(List.of(middle.getApi()), filtered("targetFlux", List.of("30%", "70%"), source, candidates));
		assertEquals(List.of(low.getApi(), middle.getApi()),
				filtered("targetHp", List.of("200", "500"), source, candidates));
		assertEquals(List.of(low.getApi(), middle.getApi()),
				filtered("targetFlux", List.of("20%", "50%"), source, candidates));
	}

	@Test
	void overloadStillUsesTheDeclaredFluxRange() {
		ShipState source = world.ship(0, 0f);
		ShipState enemy = world.ship(1, 20f);
		enemy.setOverloaded(true);
		enemy.setFlux(9500f, 0.95f);
		assertTrue(filtered("targetFlux", List.of("30%", "70%"), source, List.of(enemy.getApi())).isEmpty());
		assertEquals(1, filtered("targetFlux", List.of("100%"), source, List.of(enemy.getApi())).size());
	}

	@Test
	void distanceAndMaximumSpeedSupportInclusiveZeroAndSelfBounds() {
		ShipState source = world.ship(0, 0f);
		ShipState zero = world.ship(1, 0f);
		ShipState near = world.ship(1, 100f);
		ShipState far = world.ship(1, 200f);
		zero.setSpeed(0f);
		near.setSpeed(100f);
		far.setSpeed(120f);
		List<CombatEntityAPI> candidates = List.of(zero.getApi(), near.getApi(), far.getApi());
		assertEquals(List.of(zero.getApi()), filtered("distance", List.of("0"), source, candidates));
		assertEquals(List.of(near.getApi(), far.getApi()), filtered("distance", List.of("100", "200"), source, candidates));
		assertEquals(List.of(zero.getApi()), filtered("targetSpeed", List.of("0"), source, candidates));
		assertEquals(List.of(zero.getApi(), near.getApi()), filtered("targetSpeed", List.of("self"), source, candidates));
		assertEquals(List.of(near.getApi(), far.getApi()), filtered("targetSpeed", List.of("self", "120"), source, candidates));
	}

	@ParameterizedTest
	@CsvSource({"largest,CRUISER", "smallest,FRIGATE"})
	void hullSizeSelectsLargestOrSmallestWithinAllowedSizes(String extremum, String size) {
		ShipState source = world.ship(0, 0f);
		ShipState frigate = world.ship(1, 10f);
		ShipState destroyer = world.ship(1, 20f);
		ShipState cruiser = world.ship(1, 30f);
		destroyer.setHullSize(ShipAPI.HullSize.DESTROYER);
		cruiser.setHullSize(ShipAPI.HullSize.CRUISER);
		var selected = filtered("hullsize", List.of("frigate", "cruiser", extremum), source,
				List.of(frigate.getApi(), destroyer.getApi(), cruiser.getApi()));
		assertEquals(1, selected.size());
		assertEquals(ShipAPI.HullSize.valueOf(size), ((ShipAPI) selected.get(0)).getHullSize());
		assertEquals(List.of(frigate.getApi(), cruiser.getApi()),
				filtered("hullsize", List.of("largest", "smallest"), source,
						List.of(frigate.getApi(), destroyer.getApi(), cruiser.getApi())));
	}

	@Test
	void hullTypeMatchesTheUnionAndAnyRestoresAllCandidates() {
		ShipState source = world.ship(0, 0f);
		ShipState phase = world.ship(1, 10f);
		ShipState carrier = world.ship(1, 20f);
		ShipState ordinary = world.ship(1, 30f);
		phase.getHints().add(ShipHullSpecAPI.ShipTypeHints.PHASE);
		carrier.getHints().add(ShipHullSpecAPI.ShipTypeHints.CARRIER);
		List<CombatEntityAPI> candidates = List.of(phase.getApi(), carrier.getApi(), ordinary.getApi());
		assertEquals(List.of(phase.getApi(), carrier.getApi()), filtered("hulltype", List.of("phase", "carrier"), source, candidates));
		assertEquals(candidates, filtered("hulltype", List.of("any"), source, candidates));
	}

	@ParameterizedTest
	@ValueSource(strings = {"engage.targetHp.nearest", "engage.targetHp().nearest", "engage.targetHp(1,2,3).nearest",
			"engage.targetHp(NaN).nearest", "engage.targetHp(Infinity).nearest", "engage.targetHp(-1).nearest",
			"engage.targetHp(10,5).nearest", "engage.targetFlux(70%,30%).nearest", "engage.distance(50%).nearest",
			"engage.targetSpeed(50%).nearest", "engage.hullsize(FRIGATE).nearest", "engage.hullsize().nearest",
			"engage.hulltype(any,unknown).nearest", "assault.targetHp(4000).nearest",
			"capture.hullsize(frigate).nearest", "defend.targetSpeed(self).nearest", "control.hulltype(any).nearest",
			"engage.nearest.priority", "engage.nearest.priority(NaN)", "engage.nearest.priority(1,2)",
			"engage.nearest.persist(1)"})
	void compilationRejectsInvalidParametersAndTargetKinds(String expression) {
		var registry = BattleNext_OrderRegistry.getInstance();
		var parsed = BattleNext_OrderParser.parse(expression, registry);
		assertTrue(parsed.isValid(), parsed.getError());
		assertThrows(BattleNext_OrderThread.OrderScriptExecutionException.class,
				() -> new BattleNext_OrderThread(parsed.getCommand(), registry));
	}

	@Test
	void registeredStarterKindsAndActionsCompileAgainstEveryPicker() {
		var registry = BattleNext_OrderRegistry.getInstance();
		for (var starter : registry.getByType("starter")) {
			for (var picker : registry.getByType("picker")) {
				assertDoesNotThrow(() -> new BattleNext_OrderThread(
						BattleNext_OrderParser.parse(starter.getId() + "." + picker.getId()).getCommand(), registry));
			}
		}
	}

	private List<CombatEntityAPI> filtered(String id, List<String> params, ShipState source,
			List<? extends CombatEntityAPI> candidates) {
		var filter = (BattleNext_OrderFilter) BattleNext_OrderRegistry.getInstance().create(id);
		filter.configure(params);
		var result = new ArrayList<CombatEntityAPI>(candidates);
		filter.filterTargets(source.getApi(), result, world.getEngine());
		return result;
	}
}
