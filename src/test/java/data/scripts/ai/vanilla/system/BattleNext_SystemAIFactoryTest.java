package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_SystemAIFactoryTest {

	@Test
	void allVanillaTypesHaveARegisteredBranch() throws JSONException {
		ShipAPI ship = proxy(ShipAPI.class, (method, args) -> null);
		List<String> types = List.of("FLARE", "FLARE_JAMMER", "CANISTER_FLAK", "BURN_DRIVE",
				"BURN_DRIVE_TOGGLE", "MANEUVERING_JETS", "FAST_RELOAD", "AMMO_RELOAD",
				"FORTRESS_SHIELD", "DAMPER_FIELD", "HIGH_ENERGY_FOCUS", "PHASE_TELEPORTER",
				"PHASE_TELEPORTER_2", "PHASE_DISPLACER", "DRONE_LAUNCHER_PD",
				"DRONE_LAUNCHER_STANDARD", "WEAPON_BOOST", "DYNAMIC_STABILIZER",
				"LIDAR_ARRAY", "TEMPORAL_SHELL", "ACAUSAL_DISRUPTOR", "MOTE_ATTRACTOR",
				"ENTROPY_AMPLIFIER", "INTERDICTOR", "MINE_STRIKE", "DRONE_STRIKE",
				"EMP", "PHASE_CLOAK", "NONE", "TARGETING_FEED", "RESERVE_DEPLOYMENT",
				"RECALL_DEVICE", "CUSTOM");
		for (String type : types) {
			try {
				BattleNext_SystemAIFactory.create(ship, system(type), null, null, null, null, null);
			} catch (IllegalStateException exception) {
				assertFalse(exception.getCause() instanceof IllegalArgumentException
						&& exception.getCause().getMessage().startsWith("Unknown aiType"), type);
			}
		}
	}

	@Test
	void empTypeConstructsDedicatedAi() throws JSONException {
		ShipSystemAPI system = system("EMP");
		ShipAPI ship = proxy(ShipAPI.class, (method, args) -> "getSystem".equals(method) ? system : null);

		BattleNext_SystemAI result = BattleNext_SystemAIFactory.create(ship, system,
				new ShipwideAIFlags(), null, null, null, null);

		assertInstanceOf(BattleNext_EmpSystemAI.class, result);
	}

	@Test
	void noneHasNoSystemAi() throws JSONException {
		assertNull(BattleNext_SystemAIFactory.create(null, system("NONE"), null, null, null, null, null));
	}

	@Test
	void missingAndUnknownTypesFailWithSystemIdentity() throws JSONException {
		assertThrows(IllegalStateException.class,
				() -> BattleNext_SystemAIFactory.create(null, system(null), null, null, null, null, null));
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> BattleNext_SystemAIFactory.create(null, system("NOT_REGISTERED"), null, null, null, null, null));
		assertTrue(error.getMessage().contains("test_system"));
	}

	private ShipSystemAPI system(String type) throws JSONException {
		JSONObject data = new JSONObject();
		if (type != null) data.put("aiType", type);
		ShipSystemSpecAPI spec = proxy(ShipSystemSpecAPI.class, (method, args) -> switch (method) {
			case "getSpecJson" -> data;
			case "getId" -> "test_system";
			default -> null;
		});
		return proxy(ShipSystemAPI.class, (method, args) -> "getSpecAPI".equals(method) ? spec : null);
	}

	private interface Call {
		Object invoke(String method, Object[] args);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> call.invoke(method.getName(), args)));
	}
}
