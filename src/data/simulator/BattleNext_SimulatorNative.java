package data.simulator;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.state.AppDriver;
import org.boxutil.config.BoxConfigs;
import org.dark.shaders.util.GraphicsLibSettings;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;

public class BattleNext_SimulatorNative {

	private final MethodHandle missionConstructor;
	private final MethodHandle missionAccepted;
	private final MethodHandle deploymentDialog;
	private final MethodHandle returnToTitle;
	private final MethodHandle endProcessing;
	private final MethodHandle soundAdvance;
	private final MethodHandle resultGetter;
	private final MethodHandle exitState;
	private final MethodHandle faderGetter;
	private final MethodHandle forceOut;

	public BattleNext_SimulatorNative(ClassLoader loader) throws ReflectiveOperationException {

		Class<?> engine = Class.forName("com.fs.starfarer.combat.CombatEngine", false, loader);
		Class<?> state = Class.forName("com.fs.starfarer.combat.CombatState", false, loader);
		Class<?> title = Class.forName("com.fs.starfarer.title.TitleScreenState", false, loader);
		Class<?> mission = Class.forName("com.fs.starfarer.title.Object.L", false, loader);
		Class<?> sounds = Class.forName("com.fs.starfarer.D.M", false, loader);
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		missionConstructor = lookup.findConstructor(mission, MethodType.methodType(void.class));
		missionAccepted = lookup.findVirtual(title, "missionAccepted", MethodType.methodType(void.class, mission));
		deploymentDialog = lookup.findVirtual(engine, "setShowDeploymentDialog", MethodType.methodType(void.class, boolean.class));
		returnToTitle = lookup.findVirtual(state, "goToState", MethodType.methodType(void.class, String.class));
		endProcessing = lookup.findVirtual(engine, "doEndOfCombatProcessing", MethodType.methodType(void.class));
		resultGetter = bindGetter(lookup, engine, "getResults");
		soundAdvance = lookup.findStatic(sounds, "o00000", MethodType.methodType(void.class,
				float.class, float.class, float.class, float.class, float.class, int.class));
		exitState = lookup.findVirtual(title, "goToState", MethodType.methodType(void.class, String.class));
		faderGetter = bindGetter(lookup, title, "getFader");
		forceOut = lookup.findVirtual(faderGetter.type().returnType(), "forceOut", MethodType.methodType(void.class));
	}

	private MethodHandle bindGetter(MethodHandles.Lookup lookup, Class<?> owner, String name) throws ReflectiveOperationException {
		try {
			Class<?> methodType = Class.forName("java.lang.reflect.Method", false, null);
			Object method = MethodHandles.lookup().findVirtual(Class.class, "getMethod", MethodType.methodType(methodType, String.class, Class[].class))
					.invoke(owner, name, new Class<?>[0]);
			Class<?> returnType = (Class<?>) lookup.findVirtual(methodType, "getReturnType", MethodType.methodType(Class.class)).invoke(method);
			return lookup.findVirtual(owner, name, MethodType.methodType(returnType));
		} catch (ReflectiveOperationException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new ReflectiveOperationException("Unable to bind simulator native getter " + owner.getName() + "." + name, exception);
		}
	}

	public MissionDefinitionAPI createMission() throws Throwable {
		return (MissionDefinitionAPI) missionConstructor.invoke();
	}

	public void configureGraphics() throws ReflectiveOperationException {
		for (String name : List.of("shadersAllowed", "buffersAllowed")) {
			Field field = ShaderLib.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setBoolean(null, false);
		}
		Field luna = GraphicsLibSettings.class.getDeclaredField("useLunaLib");
		luna.setAccessible(true);
		luna.setBoolean(null, false);
		for (String name : List.of("enableLights", "enableBloom", "enableNormal", "autoGenNormals", "loadMaterial",
				"loadSurface", "enableDistortion", "enablePostProcess", "preloadAllMaps")) {
			Field field = GraphicsLibSettings.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setBoolean(null, false);
		}
		for (String name : List.of("BUtil_EnableShader", "BUtil_EnableCL", "BUtil_EnableDistortion", "BUtil_EnableDebug")) {
			Field field = BoxConfigs.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setBoolean(null, false);
		}
	}

	public void submitMission(MissionDefinitionAPI mission) throws Throwable {
		missionAccepted.invoke(AppDriver.getInstance().getCurrentState(), mission);
	}

	public void prepareDeployment(CombatEngineAPI engine) throws Throwable {
		deploymentDialog.invoke(engine, false);
		engine.setDoNotEndCombat(true);
	}

	public void maintainWindowAndSound(CombatEngineAPI engine, float elapsed) throws Throwable {
		Display.processMessages();
		Keyboard.poll();
		Vector2f center = engine.getViewport().getCenter();
		soundAdvance.invoke(elapsed, center.x, center.y, 0f, 0f, 1800);
	}

	public void finish(CombatEngineAPI engine) throws Throwable {
		endProcessing.invoke(engine);
		AppDriver.getInstance().getSession().remove("missionPlayed");
		AppDriver.getInstance().getSession().remove("missionResults");
		resultGetter.invoke(engine);
		CombatUIAPI ui = engine.getCombatUI();
		returnToTitle.invoke(ui, "Title Screen State");
		engine.setPaused(false);
	}

	public void exit() throws Throwable {
		Object title = AppDriver.getInstance().getCurrentState();
		exitState.invoke(title, "AppDriver.EXIT_STATE");
		forceOut.invoke(faderGetter.invoke(title));
	}
}
