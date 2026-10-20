package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_SaveErrors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class BattleNext_FleetVariantFormatter {

	@FunctionalInterface
	public interface SourceResolver {
		BattleNext_VariantBuilder resolve(String id) throws JSONException;
	}

	public record Result(BattleNext_FleetModel candidate, Map<String, String> mapping, List<String> errors,
			int renamedIds, int affectedMembers) { }

	private record Variant(JSONObject json, Set<String> dependencies) { }

	private final BattleNext_FleetModel source;
	private final String prefix;
	private final SourceResolver resolver;
	private final BattleNext_SaveErrors errors = new BattleNext_SaveErrors();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final Map<String, Variant> roots = new LinkedHashMap<>();
	private final Map<String, Variant> local = new LinkedHashMap<>();
	private final Map<String, String> hulls = new LinkedHashMap<>();
	private final Map<String, String> contexts = new LinkedHashMap<>();
	private final Map<String, String> mapping = new LinkedHashMap<>();
	private final List<JSONObject> nodes = new ArrayList<>();

	private BattleNext_FleetVariantFormatter(BattleNext_FleetModel source, String prefix,
			SourceResolver resolver) {
		this.source = source;
		this.prefix = BattleNext_EntryId.trimWhitespace(prefix);
		this.resolver = resolver;
	}

	public static Result preview(BattleNext_FleetModel source, String prefix,
			SourceResolver resolver) {
		return new BattleNext_FleetVariantFormatter(source, prefix, resolver).preview();
	}

	private Result preview() {

		errors.id(strings.get("details_heading"), strings.get("variant_id_prefix"), prefix, BattleNext_EntryId.problem(prefix));
		if (!errors.isEmpty()) return failure();
		for (int index = 0; index < source.getMembers().size(); index++) {
			BattleNext_MemberModel member = source.getMembers().get(index);
			String context = strings.format("format_member", index + 1, member.getName());
			if (!roots.containsKey(member.getVariantId())) {
				roots.put(member.getVariantId(), read(member.getVariantId(), context));
			}
		}
		Set<String> processed = new HashSet<>();
		boolean added;
		do {
			added = false;
			for (BattleNext_VariantBuilder variant : source.getVariants()) {
				if (!hulls.containsKey(variant.getId()) || !processed.add(variant.getId())) continue;
				local.put(variant.getId(), roots.containsKey(variant.getId()) ? roots.get(variant.getId())
						: read(variant.getId(), contexts.get(variant.getId())));
				added = true;
			}
		} while (added);
		allocate();
		if (!errors.isEmpty()) return failure();
		try {
			return apply();
		} catch (JSONException exception) {
			reject(strings.get("details_heading"), source.getId(), exception);
			return failure();
		}
	}

	private Variant read(String id, String context) {
		Set<String> dependencies = new LinkedHashSet<>();
		try {
			JSONObject json = resolve(id);
			if (json == null) {
				errors.field(context, "variantId", id, strings.format("format_missing_variant", id));
				return null;
			}
			visit(json, context, dependencies, new LinkedHashSet<>());
			return new Variant(json, dependencies);
		} catch (JSONException | RuntimeException exception) {
			reject(context, id, exception);
			return null;
		}
	}

	private JSONObject resolve(String id) throws JSONException {
		BattleNext_VariantBuilder variant = source.getVariant(id);
		if (variant == null) {
			variant = resolver.resolve(id);
		}
		return variant == null ? null : variant.toJson();
	}

	private void visit(JSONObject json, String context, Set<String> dependencies, Set<String> visiting) throws JSONException {

		String id = json.getString("variantId");
		String hull = json.getString("hullId");
		if (id.isEmpty()) {
			errors.field(context, "variantId", id, strings.get("format_missing_id"));
			return;
		}
		if (hull.isEmpty() || Global.getSettings().getHullSpec(hull) == null) {
			errors.field(context + " / " + id, "hullId", hull, strings.format("format_missing_hull", hull));
			return;
		}
		String previous = hulls.putIfAbsent(id, hull);
		if (previous != null && !previous.equals(hull)) {
			errors.field(context, "variantId", id, strings.format("format_hull_conflict", id, previous, hull));
			return;
		}
		if (!visiting.add(id)) {
			errors.field(context, "variantId", id, strings.format("format_cycle", String.join(" -> ", visiting) + " -> " + id));
			return;
		}
		contexts.putIfAbsent(id, context);
		dependencies.add(id);
		nodes.add(json);
		Object modules = json.opt("modules");
		if (modules instanceof JSONObject object) {
			visitModules(object, context, dependencies, visiting);
		} else if (modules instanceof JSONArray array) {
			for (int index = 0; index < array.length(); index++) {
				Object entry = array.get(index);
				if (entry instanceof JSONObject object) {
					visitModules(object, context + " / " + (index + 1), dependencies, visiting);
				} else {
				errors.field(context + " / " + id, "modules", String.valueOf(entry), strings.get("format_invalid_modules"));
				}
			}
		} else if (modules != null) {
			errors.field(context + " / " + id, "modules", String.valueOf(modules), strings.get("format_invalid_modules"));
		}
		visiting.remove(id);
	}

	private void visitModules(JSONObject modules, String context, Set<String> dependencies, Set<String> visiting) {

		if (modules.length() == 0) return;
		List<String> slots = new ArrayList<>(Arrays.asList(JSONObject.getNames(modules)));
		Collections.sort(slots);
		for (String slot : slots) {
			String path = context + " / " + slot;
			String id = "";
			Set<String> branch = new LinkedHashSet<>(visiting);
			try {
				Object value = modules.get(slot);
				JSONObject child;
				if (value instanceof String reference) {
					id = reference;
					child = resolve(reference);
					if (child == null) {
						errors.field(path, "variantId", id, strings.format("format_missing_variant", id));
						continue;
					}
					modules.put(slot, child);
				} else if (value instanceof JSONObject object) {
					child = object;
					id = object.optString("variantId", "");
				} else {
					errors.field(path, "variantId", String.valueOf(value), strings.get("format_invalid_modules"));
					continue;
				}
				visit(child, path, dependencies, branch);
			} catch (JSONException | RuntimeException exception) {
				reject(path, id, exception);
			}
		}
	}

	private void allocate() {

		Set<String> occupied = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		occupied.addAll(hulls.keySet());
		for (BattleNext_VariantBuilder variant : source.getVariants()) {
			occupied.add(variant.getId());
		}
		Map<String, Integer> next = new HashMap<>();
		for (var entry : hulls.entrySet()) {
			String id = entry.getKey();
			if (id.startsWith(prefix + "_")) {
				mapping.put(id, id);
				continue;
			}
			String hull = entry.getValue();
			int number = next.getOrDefault(hull, 1);
			String target;
			do {
				target = prefix + "_" + hull + "_" + number++;
			} while (occupied.contains(target));
			next.put(hull, number);
			occupied.add(target);
			mapping.put(id, target);
			errors.id(contexts.get(id) + " / " + id, strings.get("format_target_id"), target, BattleNext_EntryId.problem(target));
		}
	}

	private Result apply() throws JSONException {

		for (JSONObject node : nodes) {
			node.put("variantId", mapping.get(node.getString("variantId")));
		}
		List<BattleNext_VariantBuilder> variants = new ArrayList<>();
		for (BattleNext_VariantBuilder variant : source.getVariants()) {
			Variant replacement = local.get(variant.getId());
			variants.add(replacement != null && changed(replacement)
					? BattleNext_VariantBuilder.fromJson(replacement.json(), "") : variant);
		}
		BattleNext_FleetModel candidate = new BattleNext_FleetModel();
		candidate.fromJson(source.toJson());
		candidate.setFolderName(source.getFolderName());
		candidate.setSourceType(source.getSourceType());
		candidate.setSourcePath(source.getSourcePath());
		for (String error : source.getLoadErrors()) {
			if (!candidate.getLoadErrors().contains(error)) candidate.addLoadError(error);
		}
		candidate.setVariants(variants);
		int affected = 0;
		for (int index = 0; index < source.getMembers().size(); index++) {
			BattleNext_MemberModel member = source.getMembers().get(index);
			Variant root = roots.get(member.getVariantId());
			String target = root.json().getString("variantId");
			if (!member.getVariantId().equals(target) || changed(root)) {
				candidate.getMembers().get(index).setVariantId(target);
				if (candidate.getVariant(target) == null) candidate.putVariant(BattleNext_VariantBuilder.fromJson(root.json(), target));
				affected++;
			}
		}
		Set<String> filenames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (BattleNext_VariantBuilder variant : candidate.getVariants()) {
			if (!filenames.add(variant.getId())) {
				errors.field(strings.get("format_ids"), strings.get("format_target_id"), variant.getId(), strings.get("format_file_conflict"));
			}
		}
		if (!errors.isEmpty()) return failure();
		int renamed = (int) mapping.entrySet().stream().filter(entry -> !entry.getKey().equals(entry.getValue())).count();
		return new Result(candidate, Collections.unmodifiableMap(new LinkedHashMap<>(mapping)), List.of(), renamed, affected);
	}

	private boolean changed(Variant variant) {
		return variant.dependencies().stream().anyMatch(id -> !id.equals(mapping.get(id)));
	}

	private void reject(String context, String id, Exception exception) {
		errors.failure(strings.get("format_ids"), context + " / " + id, exception);
		BattleNext_Logger.warn(BattleNext_FleetVariantFormatter.class, "fleet.variant.format.rejected",
				BattleNext_Logger.fields().put("context", context).put("variant_id", id).build(), exception);
	}

	private Result failure() {
		return new Result(null, Collections.unmodifiableMap(new LinkedHashMap<>(mapping)), errors.getMessages(), 0, 0);
	}
}
