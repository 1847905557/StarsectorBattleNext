package data.order;

import data.order.starter.AssaultStarter;
import data.scripts.tools.BattleNext_CsvLine;
import data.test.fixture.BattleNext_TestOrderFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_OrderRegistryTest extends BattleNext_TestOrderFixture {

	@Test
	void mergedRowsPopulateRegistryAndRejectBadTypes() throws Exception {
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		JSONArray rows = new JSONArray();
		rows.put(new JSONObject().put("id", "custom").put("type", "starter")
				.put("script", "data.order.starter.AssaultStarter").put("desc", "Custom starter"));
		registry.load(rows, BattleNext_OrderRegistryTest::instantiate);
		assertEquals("Custom starter", registry.get("custom").getDescription());
		assertEquals(List.of(registry.get("custom")), registry.getByType("starter"));
		assertNotSame(registry.create("custom"), registry.create("custom"));
		rows.put(new JSONObject().put("id", "broken").put("type", "unknown")
				.put("script", "Example").put("desc", "Broken"));
		assertThrows(IllegalArgumentException.class, () -> registry.load(rows, BattleNext_OrderRegistryTest::instantiate));
		assertEquals(1, registry.getAll().size());
		assertNotNull(registry.create("custom"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"current", "added", "removed", "reversed", "empty"})
	void registeredOrdersFollowInputRowsAcrossCatalogChanges(String change) throws Exception {

		List<String> lines = Files.readAllLines(Path.of(BattleNext_OrderRegistry.PATH), StandardCharsets.UTF_8);
		assertEquals(List.of("id", "type", "script", "desc"), BattleNext_CsvLine.parse(lines.get(0)));
		JSONArray current = csvRows();
		List<JSONObject> inputs = new ArrayList<>();
		for (int index = 0; index < current.length(); index++) {
			inputs.add(current.getJSONObject(index));
		}
		if (change.equals("added")) {
			String id = "fixture_extension";
			Set<String> names = new HashSet<>(inputs.stream().map(input -> input.optString("id")).toList());
			while (names.contains(id)) {
				id += "_extra";
			}
			inputs.add(row(id, "starter", AssaultStarter.class.getName()));
		} else if (change.equals("removed")) {
			inputs = new ArrayList<>(inputs.stream().skip(1).toList());
		} else if (change.equals("reversed")) {
			Collections.reverse(inputs);
		} else if (change.equals("empty")) {
			inputs.clear();
		}
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		registry.load(new JSONArray(inputs), BattleNext_OrderRegistryTest::instantiate);
		assertEquals(inputs.stream().map(input -> input.optString("id").trim()).toList(),
				registry.getAll().stream().map(BattleNext_OrderSpec::getId).toList());
		Set<String> ids = new HashSet<>();
		for (JSONObject input : inputs) {
			BattleNext_OrderSpec spec = registry.get(input.getString("id").trim());
			assertTrue(ids.add(spec.getId()), spec.getId());
			assertEquals(input.getString("type").trim(), spec.getType());
			assertEquals(input.getString("script").trim(), spec.getScript());
			assertEquals(input.getString("desc").trim(), spec.getDescription());
			assertEquals(inputs.stream().filter(candidate -> candidate.optString("type").trim().equals(spec.getType()))
					.map(candidate -> candidate.optString("id").trim()).toList(),
					registry.getByType(spec.getType()).stream().map(BattleNext_OrderSpec::getId).toList());
			assertNotSame(registry.create(spec.getId()), registry.create(spec.getId()));
		}
	}

	@Test
	void invalidRegistrationKeepsPublishedSpecsAndLoader() throws Exception {
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		JSONArray valid = new JSONArray().put(new JSONObject().put("id", "assault")
				.put("type", "starter").put("script", "data.order.starter.AssaultStarter").put("desc", "Assault"));
		registry.load(valid, BattleNext_OrderRegistryTest::instantiate);
		JSONArray wrongInterface = new JSONArray().put(new JSONObject().put("id", "assault")
				.put("type", "filter").put("script", "data.order.starter.AssaultStarter").put("desc", "Wrong"));
		IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
				() -> registry.load(wrongInterface, BattleNext_OrderRegistryTest::instantiate));
		assertTrue(mismatch.getMessage().contains("assault"));
		assertEquals("Assault", registry.get("assault").getDescription());
		assertNotNull(registry.create("assault"));
		JSONArray duplicate = new JSONArray().put(valid.getJSONObject(0)).put(valid.getJSONObject(0));
		IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
				() -> registry.load(duplicate, BattleNext_OrderRegistryTest::instantiate));
		assertTrue(repeated.getMessage().contains("row 2"));
		assertEquals(1, registry.getAll().size());
	}

	@Test
	void factoryValidatesAtLoadAndCreatesFreshInstancesOnDemand() throws Exception {
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		AtomicInteger creations = new AtomicInteger();
		registry.load(new JSONArray().put(row("assault", "starter", "assaultScript")), name -> {
			creations.incrementAndGet();
			return new AssaultStarter();
		});
		assertEquals(1, creations.get());
		assertNotSame(registry.create("assault"), registry.create("assault"));
		assertEquals(3, creations.get());
		assertThrows(IllegalArgumentException.class, () -> registry.load(
				new JSONArray().put(row("broken", "filter", "assaultScript")), name -> new AssaultStarter()));
		assertNotNull(registry.create("assault"));
		assertEquals(4, creations.get());
	}

	@Test
	void parserKeepsCommandStructure() throws Exception {
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		JSONArray rows = new JSONArray();
		rows.put(row("assault", "starter", "data.order.starter.AssaultStarter"));
		rows.put(row("distance", "filter", "data.order.filter.DistanceFilter"));
		rows.put(row("nearest", "picker", "data.order.picker.NearestPicker"));
		rows.put(row("priority", "suffix", "data.order.suffix.PrioritySuffix"));
		registry.load(rows, BattleNext_OrderRegistryTest::instantiate);
		BattleNext_OrderParser.OrderParseResult result = BattleNext_OrderParser.parse(
				"assault.distance(10.5).nearest.priority(2.5)", registry);
		assertTrue(result.isValid());
		BattleNext_OrderParser.ParsedOrderCommand command = result.getCommand();
		assertEquals("assault", command.getStarter());
		assertEquals("nearest", command.getPicker());
		assertEquals("distance", command.getFilters().get(0).getId());
		assertEquals(List.of("10.5"), command.getFilters().get(0).getParams());
		assertEquals("priority", command.getSuffixes().get(0).getId());
		assertEquals(List.of("2.5"), command.getSuffixes().get(0).getParams());
		var located = BattleNext_OrderParser.parseScript("assault.nearest;\nassault.nearest", registry);
		assertEquals(2, located.size());
		assertEquals(2, located.get(1).getInstructionNumber());
		assertEquals(2, located.get(1).getLineNumber());
		List<BattleNext_OrderParser.OrderParseResult> recovered = BattleNext_OrderParser.parseScript(
				"assault.distance(3;assault.nearest", registry);
		assertFalse(recovered.get(0).isValid());
		assertTrue(recovered.get(1).isValid());
		assertFalse(BattleNext_OrderParser.parse("Assault.nearest", registry).isValid());
		assertFalse(BattleNext_OrderParser.parse("assault.nearest.distance(5)", registry).isValid());
	}

	private JSONObject row(String id, String type, String script) throws Exception {
		return new JSONObject().put("id", id).put("type", type).put("script", script).put("desc", id);
	}

}
