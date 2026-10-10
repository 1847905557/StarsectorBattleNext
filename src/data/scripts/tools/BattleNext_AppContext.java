package data.scripts.tools;

import org.boxutil.backends.reflect.BUtil_RefMethod;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

public class BattleNext_AppContext {

	private BattleNext_AppContext() {

	}

	public static Object currentState() {
		return read("getCurrentState");
	}

	public static Map<?, ?> session() {
		return (Map<?, ?>) read("getSession");
	}

	private static Object read(String name) {
		try {
			Class<?> driver = Class.forName("com.fs.state.AppDriver");
			Object instance = MethodHandles.publicLookup().findStatic(driver, "getInstance",
					MethodType.methodType(driver)).invoke();
			for (Object method : driver.getMethods()) {
				if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals(name)) continue;
				return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, instance);
			}
			throw new NoSuchMethodException(name);
		} catch (Throwable exception) {
			throw new IllegalStateException("Unable to read application context " + name, exception);
		}
	}
}
