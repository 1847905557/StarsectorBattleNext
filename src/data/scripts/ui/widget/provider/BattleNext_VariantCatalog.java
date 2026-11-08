package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.tools.BattleNext_Logger;

import java.util.*;
import java.io.IOException;
import org.json.JSONException;

public final class BattleNext_VariantCatalog {

	private final BattleNext_PinnedVariantStore pins;
	private List<String> ids;
	private final Map<String, List<OptionChoice>> groups = new TreeMap<>();
	private final Map<String, OptionChoice> hulls = new TreeMap<>();
	private final Map<String, String> icons = new HashMap<>();
	private final List<OptionChoice> standard = new ArrayList<>();
	private List<OptionChoice> pinned = List.of();
	private List<OptionChoice> options = List.of();
	private int position;
	private long pinRevision = -1;
	private boolean ready;

	public BattleNext_VariantCatalog(BattleNext_PinnedVariantStore pins) {
		this.pins = pins;
	}

	public boolean advance(long budgetNanos) {
		if (ids == null) ids = List.copyOf(Global.getSettings().getAllVariantIds());

		long deadlineNanos = System.nanoTime() + budgetNanos;
		while (position < ids.size() && System.nanoTime() < deadlineNanos) {
			String id = ids.get(position++);
			ShipVariantAPI variant = Global.getSettings().getVariant(id);
			if (variant == null || variant.isFighter()) continue;
			ShipHullSpecAPI hull = variant.getHullSpec();
			OptionChoice choice = new OptionChoice(id, hull.getHullName() + " - " + variant.getDisplayName());
			standard.add(choice);
			groups.computeIfAbsent(hull.getHullId(), ignored -> new ArrayList<>()).add(choice);
			hulls.putIfAbsent(hull.getHullId(), new OptionChoice(hull.getHullId(), hull.getHullName()));
			icons.put(id, hull.getSpriteName());
			icons.put(hull.getHullId(), hull.getSpriteName());
		}

		if (position == ids.size() && !ready) {
			standard.sort(Comparator.comparing(OptionChoice::id));
			for (List<OptionChoice> values : groups.values()) values.sort(Comparator.comparing(OptionChoice::id));
			ready = true;
		}

		return ready;
	}

	public List<OptionChoice> options() {
		loadPins();
		while (!advance(2_000_000L)) {

		}
		if (pinRevision != pins.getRevision()) {
			List<OptionChoice> values = new ArrayList<>();
			for (BattleNext_PinnedVariantModel pin : pins.getAllCopy()) {
				String id = BattleNext_VariantSourceProvider.PINNED_PREFIX + pin.getPinId();
				String hullId = pin.getVariantJsonCopy().optString("hullId");
				ShipHullSpecAPI hull;
				try {
					hull = Global.getSettings().getHullSpec(hullId);
				} catch (RuntimeException exception) {
					BattleNext_Logger.warn(getClass(), "variant.pin.hull.unavailable", BattleNext_Logger.fields()
							.put("pin_id", pin.getPinId()).put("hull_id", hullId).build(), exception);
					continue;
				}
				if (hull == null || hull.getHullSize() == HullSize.FIGHTER) continue;
				values.add(new OptionChoice(id, pin.getDisplayName()));
				icons.put(id, hull.getSpriteName());
			}
			values.sort(Comparator.comparing(OptionChoice::id));
			pinned = List.copyOf(values);
			values.addAll(standard);
			options = List.copyOf(values);
			pinRevision = pins.getRevision();
		}
		return options;
	}

	public void resourcesReloaded() {
		ids = null;
		position = 0;
		ready = false;
		pinRevision = -1;
		standard.clear();
		groups.clear();
		hulls.clear();
		icons.clear();
	}

	public List<OptionChoice> hullsCopy() {
		options();
		return List.copyOf(hulls.values());
	}

	public List<OptionChoice> group(String id) {
		options();
		return id.equals("@pinned") ? pinned : groups.getOrDefault(id, List.of());
	}

	public String icon(String id) {
		return icons.getOrDefault(id, "");
	}

	public ShipVariantAPI resolve(String id) {
		if (!id.startsWith(BattleNext_VariantSourceProvider.PINNED_PREFIX)) return Global.getSettings().getVariant(id);
		loadPins();
		return pins.resolve(id.substring(BattleNext_VariantSourceProvider.PINNED_PREFIX.length()));
	}

	private void loadPins() {
		try {
			pins.ensureLoaded();
		} catch (IOException | JSONException exception) {
			throw new IllegalStateException("Unable to read pinned variants", exception);
		}
	}
}
