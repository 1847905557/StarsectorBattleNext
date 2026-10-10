package data.test.fixture;

import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.MutableStat;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_TestDeploymentFixture extends BattleNext_TestGameFixture {

	public interface FleetListener {
		void fleetPointsAvailableChanged(int previous, int current, int delta);
	}

	public interface FleetManager extends CombatFleetManagerAPI {
		MutableStat getMaxFleetPoints();
		void addEventListener(FleetListener listener);
	}

	protected static FleetManager deploymentManager(Handler handler) {
		MutableStat[] points = {new MutableStat(100f)};
		List<FleetListener> listeners = new ArrayList<>();
		return proxy(FleetManager.class, (method, args) -> switch (method) {
			case "getMaxFleetPoints" -> points[0];
			case "getMaxStrength" -> (int) points[0].getModifiedValue();
			case "addEventListener" -> { listeners.add((FleetListener) args[0]); yield null; }
			case "setMaxStrength" -> {
				int before = (int) points[0].getModifiedValue();
				points[0] = new MutableStat((Integer) args[0]);
				for (FleetListener listener : listeners) {
					listener.fleetPointsAvailableChanged(before, (Integer) args[0], (Integer) args[0] - before);
				}
				yield null;
			}
			default -> handler.apply(method, args);
		});
	}
}
