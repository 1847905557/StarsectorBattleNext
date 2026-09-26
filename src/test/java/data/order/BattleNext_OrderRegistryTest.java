package data.order;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import data.scripts.tools.BattleNext_CsvLine;
import data.order.filter.*;
import data.order.picker.*;
import data.order.starter.*;
import data.order.suffix.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_OrderRegistryTest {

	@Test
	void mergedRowsPopulateRegistryAndRejectBadTypes() throws Exception {
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		JSONArray rows = new JSONArray();
		rows.put(new JSONObject().put("id", "custom").put("type", "starter")
				.put("script", "data.order.starter.AssaultStarter").put("desc", "Custom starter"));
		registry.load(rows, this::createScript);
		assertEquals("Custom starter", registry.get("custom").getDescription());
		assertEquals(List.of(registry.get("custom")), registry.getByType("starter"));
		assertNotSame(registry.create("custom"), registry.create("custom"));
		rows.put(new JSONObject().put("id", "broken").put("type", "unknown")
				.put("script", "Example").put("desc", "Broken"));
		assertThrows(IllegalArgumentException.class, () -> registry.load(rows, this::createScript));
		assertEquals(1, registry.getAll().size());
		assertNotNull(registry.create("custom"));
	}

	@Test
	void allRegisteredOrdersAreUniqueAndCreatable() throws Exception {
		JSONArray rows = new JSONArray();
		List<String> lines = Files.readAllLines(Path.of(BattleNext_OrderRegistry.PATH), StandardCharsets.UTF_8);
		assertEquals(List.of("id", "type", "script", "desc"), BattleNext_CsvLine.parse(lines.get(0)));
		for (String line : lines.subList(1, lines.size())) {
			List<String> fields = BattleNext_CsvLine.parse(line);
			rows.put(new JSONObject().put("id", fields.get(0)).put("type", fields.get(1))
					.put("script", fields.get(2)).put("desc", fields.get(3)));
		}
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		registry.load(rows, this::createScript);
		assertEquals(26, registry.getAll().size());
		assertEquals(26, registry.getAll().stream().map(BattleNext_OrderSpec::getId).distinct().count());
		assertEquals(12, registry.getByType("starter").size());
		assertEquals(8, registry.getByType("filter").size());
		assertEquals(4, registry.getByType("picker").size());
		assertEquals(2, registry.getByType("suffix").size());
		assertEquals("assault", registry.getAll().get(0).getId());
		assertEquals("assault", registry.getByType("starter").get(0).getId());
		assertNotNull(registry.get("fighter"));
		assertNotNull(registry.get("hullsize"));
		assertNotNull(registry.get("selfFlux"));
		assertNull(registry.get("selfflux"));
		for (BattleNext_OrderSpec spec : registry.getAll()) {
			assertNotSame(registry.create(spec.getId()),
					registry.create(spec.getId()));
		}
	}

	@Test
	void invalidRegistrationKeepsPublishedSpecsAndLoader() throws Exception {
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		JSONArray valid = new JSONArray().put(new JSONObject().put("id", "assault")
				.put("type", "starter").put("script", "data.order.starter.AssaultStarter").put("desc", "Assault"));
		registry.load(valid, this::createScript);
		JSONArray wrongInterface = new JSONArray().put(new JSONObject().put("id", "assault")
				.put("type", "filter").put("script", "data.order.starter.AssaultStarter").put("desc", "Wrong"));
		IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
				() -> registry.load(wrongInterface, this::createScript));
		assertTrue(mismatch.getMessage().contains("assault"));
		assertEquals("Assault", registry.get("assault").getDescription());
		assertNotNull(registry.create("assault"));
		JSONArray duplicate = new JSONArray().put(valid.getJSONObject(0)).put(valid.getJSONObject(0));
		IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
				() -> registry.load(duplicate, this::createScript));
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
		registry.load(rows, this::createScript);
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
		assertEquals(2, BattleNext_OrderParser.parseScript("assault.nearest;\nassault.nearest", registry).size());
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

	private Object createScript(String name) {
		return switch (name) {
			case "data.order.starter.AssaultStarter" -> new AssaultStarter();
			case "data.order.starter.AvoidStarter" -> new AvoidStarter();
			case "data.order.starter.CaptureStarter" -> new CaptureStarter();
			case "data.order.starter.CleanStarter" -> new CleanStarter();
			case "data.order.starter.ControlStarter" -> new ControlStarter();
			case "data.order.starter.DefendStarter" -> new DefendStarter();
			case "data.order.starter.EliminateStarter" -> new EliminateStarter();
			case "data.order.starter.EngageStarter" -> new EngageStarter();
			case "data.order.starter.EscortStarter" -> new EscortStarter();
			case "data.order.starter.FighterStrikeStarter" -> new FighterStrikeStarter();
			case "data.order.starter.HarassStarter" -> new HarassStarter();
			case "data.order.starter.RetreatStarter" -> new RetreatStarter();
			case "data.order.filter.DistanceFilter" -> new DistanceFilter();
			case "data.order.filter.HullSizeFilter" -> new HullSizeFilter();
			case "data.order.filter.HullTypeFilter" -> new HullTypeFilter();
			case "data.order.filter.SelfFluxFilter" -> new SelfFluxFilter();
			case "data.order.filter.SelfHpFilter" -> new SelfHpFilter();
			case "data.order.filter.TargetFluxFilter" -> new TargetFluxFilter();
			case "data.order.filter.TargetHpFilter" -> new TargetHpFilter();
			case "data.order.filter.TargetSpeedFilter" -> new TargetSpeedFilter();
			case "data.order.picker.ConcentratePicker" -> new ConcentratePicker();
			case "data.order.picker.NearestPicker" -> new NearestPicker();
			case "data.order.picker.RandomPicker" -> new RandomPicker();
			case "data.order.picker.SpreadPicker" -> new SpreadPicker();
			case "data.order.suffix.PersistSuffix" -> new PersistSuffix();
			case "data.order.suffix.PrioritySuffix" -> new PrioritySuffix();
			default -> throw new IllegalArgumentException(name);
		};
	}
}
