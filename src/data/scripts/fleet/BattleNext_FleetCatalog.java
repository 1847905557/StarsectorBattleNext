package data.scripts.fleet;

import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BattleNext_FleetCatalog {

	private static final Set<String> PREREQUISITE_PREFIXES = Set.of(
			"magiclib", "lunalib", "shaderlib", "graphicslib", "boxutil");
	private final List<String> variantIds = new ArrayList<>();

	public void refresh() {
		variantIds.clear();
		variantIds.addAll(Global.getSettings().getAllVariantIds());
		variantIds.removeIf(this::isPrerequisiteVariant);
		variantIds.sort(String::compareTo);
	}

	public List<String> getVariantIds() {
		return List.copyOf(variantIds);
	}

	public List<String> filter(Collection<String> allowedSources, int minimumLevel, int maximumLevel) {
		Set<String> sources = new LinkedHashSet<>();
		if (allowedSources != null) {
			for (String source : allowedSources) if (source != null) sources.add(source.toLowerCase(Locale.ROOT));
		}
		List<String> result = new ArrayList<>();
		for (String variantId : variantIds) {
			String source = sourceOf(variantId);
			int level = levelOf(variantId);
			if ((!sources.isEmpty() && !sources.contains(source)) || level < minimumLevel || level > maximumLevel) continue;
			result.add(variantId);
		}
		return result;
	}

	private String sourceOf(String variantId) {
		int separator = variantId.indexOf(':');
		return separator > 0 ? variantId.substring(0, separator).toLowerCase(Locale.ROOT) : "base";
	}

	private int levelOf(String variantId) {
		String normalized = variantId.toLowerCase(Locale.ROOT);
		if (normalized.contains("_elite") || normalized.contains("_high")) return 3;
		if (normalized.contains("_mid") || normalized.contains("_medium")) return 2;
		return 1;
	}

	private boolean isPrerequisiteVariant(String variantId) {
		String normalized = variantId.toLowerCase(Locale.ROOT);
		for (String prefix : PREREQUISITE_PREFIXES) {
			if (normalized.startsWith(prefix + ":") || normalized.startsWith(prefix + "_")) return true;
		}
		return false;
	}
}
