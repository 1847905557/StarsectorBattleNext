package data.scripts.plugin;

import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.MutableStat;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import org.boxutil.backends.reflect.BUtil_RefMethod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class BattleNext_DeploymentLimitInjection {

	private final MethodHandle points;
	private final int limit;

	private BattleNext_DeploymentLimitInjection(CombatFleetManagerAPI manager, int limit) throws ReflectiveOperationException {
		points = MethodHandles.publicLookup().findVirtual(manager.getClass(), "getMaxFleetPoints",
				MethodType.methodType(MutableStat.class)).bindTo(manager);
		this.limit = limit;
	}

	@LifecycleEntry
	public static void install(CombatFleetManagerAPI manager, int limit) {

		try {
			BattleNext_DeploymentLimitInjection injection = new BattleNext_DeploymentLimitInjection(manager, limit);
			Class<?> method = Class.forName("java.lang.reflect.Method", false, null);
			Object registration = null;
			for (Object candidate : manager.getClass().getMethods()) {
				if (!BUtil_RefMethod.java_lang_reflect_Method_getName(candidate).equals("addEventListener")) continue;
				registration = candidate;
				break;
			}
			if (registration == null) throw new NoSuchMethodException("addEventListener");
			Class<?>[] parameters = (Class<?>[]) MethodHandles.publicLookup().findVirtual(method, "getParameterTypes",
					MethodType.methodType(Class[].class)).invoke(registration);
			Class<?> contract = parameters[0];
			Class<?> handler = Class.forName("java.lang.reflect.InvocationHandler", false, null);
			MethodHandle dispatch = MethodHandles.lookup().findVirtual(BattleNext_DeploymentLimitInjection.class, "invoke",
					MethodType.methodType(Object.class, Object.class, Object.class, Object[].class)).bindTo(injection);
			Object invocation = MethodHandleProxies.asInterfaceInstance(handler,
					dispatch.asType(MethodType.methodType(Object.class, Object.class, method, Object[].class)));
			Object listener = MethodHandles.lookup().findStatic(Class.forName("java.lang.reflect.Proxy", false, null), "newProxyInstance",
					MethodType.methodType(Object.class, ClassLoader.class, Class[].class, handler))
					.invoke(contract.getClassLoader(), new Class<?>[]{contract}, invocation);
			MethodHandles.publicLookup().findVirtual(manager.getClass(), "addEventListener",
					MethodType.methodType(void.class, contract)).invoke(manager, listener);
		} catch (Throwable exception) {
			throw new IllegalStateException("Unable to inject deployment point limit for side " + manager.getOwner(), exception);
		}
	}

	private Object invoke(Object proxy, Object method, Object[] arguments) throws Throwable {
		if (BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals("fleetPointsAvailableChanged")) {
			((MutableStat) points.invoke()).setBaseValue(limit);
		}
		return null;
	}
}
