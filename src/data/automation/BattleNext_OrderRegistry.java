package data.automation;

import com.fs.starfarer.api.Global;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import data.scripts.tools.BattleNext_CsvLine;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BattleNext_OrderRegistry {


	private static final List<String> TYPES = List.of("starter", "filter", "picker", "suffix");
	private static final String ORDERS_CSV_PATH = "data/automation/orders.csv";
	private static final String MOD_ID = "AI_Battles_Next";
	private static final BattleNext_OrderRegistry INSTANCE = new BattleNext_OrderRegistry();

	private final Map<String, Map<String, BattleNext_OrderSpec>> byType = new LinkedHashMap<>();

	private BattleNext_OrderRegistry() {
		for (String type : TYPES) {
			byType.put(type, new LinkedHashMap<>());
		}
	}

	@LifecycleEntry
	public static void loadFromSettings() {
		String orders;
		try {
			orders = Global.getSettings().loadText(ORDERS_CSV_PATH, MOD_ID);
		} catch (IOException | JSONException exception) {
			throw new IllegalArgumentException("Unable to read data/automation/orders.csv", exception);
		}
		List<String> errors = INSTANCE.loadCsv(orders);
		errors.addAll(INSTANCE.validate());
		if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid data/automation/orders.csv: " + String.join("; ", errors));
	}

	public static BattleNext_OrderRegistry getInstance() {
		return INSTANCE;
	}

	public List<String> loadCsv(String csv) {
		List<String> errors = new ArrayList<>();
		if (csv == null) {
			return List.of("orders.csv is null");
		}
		for (String type : TYPES) {
			byType.get(type).clear();
		}
		int lineNumber = 0;
		for (String line : csv.split("\\R")) {
			lineNumber++;
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("id,type,script,desc")) {
				continue;
			}
			List<String> fields = BattleNext_CsvLine.parse(line);
			if (fields.size() < 4) {
				errors.add("line " + lineNumber + ": expected id,type,script,desc");
				continue;
			}
			String type = fields.get(1).toLowerCase(Locale.ROOT);
			if (!byType.containsKey(type)) {
				errors.add("line " + lineNumber + ": unsupported type " + type);
				continue;
			}
			if (fields.get(0).isEmpty() || fields.get(2).isEmpty()) {
				errors.add("line " + lineNumber + ": id and script are required");
				continue;
			}
			byType.get(type).put(fields.get(0), new BattleNext_OrderSpec(fields.get(0), type, fields.get(2), fields.get(3)));
		}
		return errors;
	}

	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		for (Map<String, BattleNext_OrderSpec> values : byType.values()) {
			for (BattleNext_OrderSpec spec : values.values()) {
				try {
					Class<?> scriptClass = Global.getSettings().getScriptClassLoader().loadClass(spec.getScript());
					Class<?> contract = contractFor(spec.getType());
					if (contract != null && !contract.isAssignableFrom(scriptClass)) {
						errors.add(spec.getId() + ": script does not implement " + contract.getSimpleName());
					}
				} catch (Exception exception) {
					errors.add(spec.getId() + ": cannot load " + spec.getScript());
				}
			}
		}
		return errors;
	}

	private Class<?> contractFor(String type) {
		if ("starter".equals(type)) return OrderStarter.class;
		if ("filter".equals(type)) return OrderFilter.class;
		if ("picker".equals(type)) return OrderPicker.class;
		if ("suffix".equals(type)) return OrderSuffix.class;
		return null;
	}

	public Object create(String type, String id) {
		Map<String, BattleNext_OrderSpec> values = byType.get(type == null ? "" : type.toLowerCase(Locale.ROOT));
		if (values == null || !values.containsKey(id)) {
			throw new IllegalArgumentException("Unknown order: " + type + "/" + id);
		}
		BattleNext_OrderSpec spec = values.get(id);
		try {
			return Global.getSettings().getScriptClassLoader().loadClass(spec.getScript()).getDeclaredConstructor().newInstance();
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to instantiate order " + id, exception);
		}
	}

	public Map<String, BattleNext_OrderSpec> get(String type) {
		return Collections.unmodifiableMap(byType.getOrDefault(type == null ? "" : type.toLowerCase(Locale.ROOT), Map.of()));
	}
}
