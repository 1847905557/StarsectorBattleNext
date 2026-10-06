package data.scripts.ui.widget.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_TournamentTemplate {

	private final List<String> fleetIds = new ArrayList<>();

	public BattleNext_TournamentTemplate(int capacity) {
		resize(capacity);
	}

	public void resize(int capacity) {
		if (fleetIds.size() > capacity) {
			fleetIds.subList(capacity, fleetIds.size()).clear();
		}
		while (fleetIds.size() < capacity) {
			fleetIds.add("");
		}
	}

	public List<String> getFleetIds() {
		return Collections.unmodifiableList(fleetIds);
	}

	public void select(int slot, String fleetId) {
		fleetIds.set(slot, fleetId);
	}
}
