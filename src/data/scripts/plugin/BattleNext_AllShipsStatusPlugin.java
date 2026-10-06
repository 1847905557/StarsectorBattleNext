package data.scripts.plugin;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.tools.BattleNext_Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class BattleNext_AllShipsStatusPlugin extends BaseEveryFrameCombatPlugin {

	static final String STATE_CLASS = "com.fs.starfarer.combat.CombatState";
	static final String RENDERER_CLASS = "com.fs.starfarer.renderers.A.C";
	static final String RETICLE_CLASS = "com.fs.starfarer.renderers.A.null";
	static final String SHIP_CLASS = "com.fs.starfarer.combat.entities.Ship";
	private static final float SECONDARY_ALPHA = 0.75f;

	interface Access {
		void bind(Class<?> uiClass) throws Throwable;
		Object renderer(CombatUIAPI ui) throws Throwable;
		List<?> reticles(Object renderer) throws Throwable;
		boolean isSecondary(Object reticle) throws Throwable;
		ShipAPI ship(Object reticle) throws Throwable;
		void refresh(Object renderer, ShipAPI ship, float alpha) throws Throwable;
		void clear();
	}

	private final Access access = new VanillaAccess();
	private CombatEngineAPI engine;
	private boolean bound;

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null) return;
		if (engine.isCombatOver()) {
			cleanup();
		}
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {

		if (engine == null) return;
		if (engine.isCombatOver()) {
			cleanup();
			return;
		}
		if (Global.getCurrentState() != GameState.COMBAT) return;
		CombatUIAPI ui = engine.getCombatUI();
		if (ui == null) return;

		String phase = "bind";
		Object renderer = null;
		ShipAPI currentShip = null;
		try {
			if (!bound) {
				access.bind(ui.getClass());
				bound = true;
			}
			phase = "renderer.lookup";
			renderer = access.renderer(ui);
			if (renderer == null) return;

			phase = "primary.scan";
			Set<ShipAPI> primaryShips = Collections.newSetFromMap(new IdentityHashMap<>());
			for (Object reticle : access.reticles(renderer)) {
				if (access.isSecondary(reticle)) continue;
				primaryShips.add(access.ship(reticle));
			}
			phase = "ship.refresh";
			for (ShipAPI ship : engine.getShips()) {
				currentShip = ship;
				if (!eligible(ship)) continue;
				if (primaryShips.contains(ship)) continue;
				access.refresh(renderer, ship, SECONDARY_ALPHA);
			}
		} catch (Throwable exception) {
			BattleNext_Logger.error(BattleNext_AllShipsStatusPlugin.class, "ship_status.refresh.failed",
					BattleNext_Logger.fields().put("phase", phase).put("ui_class", ui.getClass())
							.put("renderer_class", renderer == null ? RENDERER_CLASS : renderer.getClass())
							.put("state_class", STATE_CLASS).put("reticle_class", RETICLE_CLASS)
							.put("ship_class", SHIP_CLASS).put("alpha", SECONDARY_ALPHA)
							.put("ship_id", currentShip == null ? null : currentShip.getId())
							.put("signatures", List.of("getTargetReticleRenderer(): " + RENDERER_CLASS,
									"super(" + SHIP_CLASS + ",float): void", "\u00D200000(): List",
									"int(): " + SHIP_CLASS, "\u00D800000: boolean")).build(), exception);
			cleanup();
		}
	}

	private boolean eligible(ShipAPI ship) {
		int owner = ship.getOwner();
		return (owner == 0 || owner == 1) && ship.isAlive() && !ship.isExpired()
				&& engine.isEntityInPlay(ship) && !ship.isFighter() && !ship.isDrone()
				&& !ship.isShuttlePod() && !ship.isStation() && !ship.isStationModule();
	}

	private void cleanup() {
		CombatEngineAPI owner = engine;
		engine = null;
		bound = false;
		access.clear();
		owner.removePlugin(this);
	}

	static class VanillaAccess implements Access {
		private MethodHandle rendererGetter;
		private MethodHandle reticlesGetter;
		private MethodHandle secondaryGetter;
		private MethodHandle shipGetter;
		private MethodHandle refresh;

		@Override
		public void bind(Class<?> uiClass) throws ReflectiveOperationException {

			ClassLoader loader = uiClass.getClassLoader();
			Class<?> stateClass = Class.forName(STATE_CLASS, false, loader);
			Class<?> rendererClass = Class.forName(RENDERER_CLASS, false, loader);
			Class<?> reticleClass = Class.forName(RETICLE_CLASS, false, loader);
			Class<?> shipClass = Class.forName(SHIP_CLASS, false, loader);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			rendererGetter = lookup.findVirtual(stateClass, "getTargetReticleRenderer", MethodType.methodType(rendererClass));
			reticlesGetter = lookup.findVirtual(rendererClass, "\u00D200000", MethodType.methodType(List.class));
			secondaryGetter = MethodHandles.privateLookupIn(reticleClass, MethodHandles.lookup())
					.findGetter(reticleClass, "\u00D800000", boolean.class);
			shipGetter = lookup.findVirtual(reticleClass, "int", MethodType.methodType(shipClass));
			refresh = lookup.findVirtual(rendererClass, "super", MethodType.methodType(void.class, shipClass, float.class));
		}

		@Override
		public Object renderer(CombatUIAPI ui) throws Throwable {
			return rendererGetter.invoke(ui);
		}

		@Override
		public List<?> reticles(Object renderer) throws Throwable {
			return (List<?>) reticlesGetter.invoke(renderer);
		}

		@Override
		public boolean isSecondary(Object reticle) throws Throwable {
			return (boolean) secondaryGetter.invoke(reticle);
		}

		@Override
		public ShipAPI ship(Object reticle) throws Throwable {
			return (ShipAPI) shipGetter.invoke(reticle);
		}

		@Override
		public void refresh(Object renderer, ShipAPI ship, float alpha) throws Throwable {
			refresh.invoke(renderer, ship, alpha);
		}

		@Override
		public void clear() {
			rendererGetter = null;
			reticlesGetter = null;
			secondaryGetter = null;
			shipGetter = null;
			refresh = null;
		}
	}
}
