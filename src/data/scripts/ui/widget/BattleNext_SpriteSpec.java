package data.scripts.ui.widget;

import com.fs.starfarer.api.combat.ShipHullSpecAPI;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public record BattleNext_SpriteSpec(float width, float height, float centerX, float centerY) {

	public static BattleNext_SpriteSpec read(ShipHullSpecAPI hull) {
		try {
			Class<?> spriteType = Class.forName("com.fs.starfarer.loading.specs.Q", false, hull.getClass().getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Object spec = lookup.findVirtual(hull.getClass(), "getSpriteSpec", MethodType.methodType(spriteType)).invoke(hull);
			MethodType scalar = MethodType.methodType(float.class);
			return new BattleNext_SpriteSpec(
					(float) lookup.findVirtual(spriteType, "null", scalar).invoke(spec),
					(float) lookup.findVirtual(spriteType, "o00000", scalar).invoke(spec),
					(float) lookup.findVirtual(spriteType, "class", scalar).invoke(spec),
					(float) lookup.findVirtual(spriteType, "new", scalar).invoke(spec));
		} catch (Throwable exception) {
			throw new IllegalStateException("Could not read loaded sprite spec for hull " + hull.getHullId(), exception);
		}
	}
}
