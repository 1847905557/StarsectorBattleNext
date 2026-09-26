package data.scripts.battlesnext.util;
/// 20260927

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.boxutil.base.BaseControlData;
import org.boxutil.base.SimpleParticleControlData;
import org.boxutil.base.api.InstanceDataAPI;
import org.boxutil.base.api.RenderDataAPI;
import org.boxutil.config.BoxConfigs;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.attribute.NodeData;
import org.boxutil.units.standard.entity.*;
import org.boxutil.util.CalculateUtil;
import org.boxutil.util.CurveUtil;
import org.boxutil.util.CurveUtil.DealtController;
import org.boxutil.util.ShaderUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Matrix2f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.*;

public class BattleNext_BoxBasedUtils {

	public static final String KEY_PREFIX = BattleNext_BoxBasedUtils.class.getSimpleName();

	public static final Random RANDOM = new Random();
	public static final Color EMPTY = new Color(0, 0, 0, 0);
	public static final Vector2f ZERO = new Vector2f();

	public static final int DEFAULT_MAX_PARTICLES = 5000;
	public static final int DEFAULT_MAX_DURATION = 100;

	public static SpriteEntity addSprite(SpriteAPI sprite, CombatEngineLayers layer) {
		SpriteEntity spriteEntity = new SpriteEntity(sprite);
		spriteEntity.setControlData(new StaticControlData());
		spriteEntity.setLayer(layer);
		CombatRenderingManager.addEntity(spriteEntity);
		return spriteEntity;
	}

	public static SpriteEntity addSpriteToTarget(SpriteAPI sprite, CombatEntityAPI target, float in, float full, float out, CombatEngineLayers layer) {
		SpriteEntity spriteEntity = new SpriteEntity(sprite);
		spriteEntity.setControlData(new EntityBasedControlData(target));
		spriteEntity.setLayer(layer);
		spriteEntity.setGlobalTimer(in, full, out);
		CombatRenderingManager.addEntity(spriteEntity);
		return spriteEntity;
	}

	public static SpriteEntity addQuarterSprite(SpriteAPI sprite, CombatEngineLayers layer) {

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, sprite.getTextureId());
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

		SpriteEntity spriteEntity = addSprite(sprite, layer);
		spriteEntity.setUVStart(-1, -1);
		return spriteEntity;
	}

	public static Instance2Data addBasicInstanceData(SpriteEntity spriteEntity, float in, float full, float out, float facing, float turnRate, float growth) {

		List<InstanceDataAPI> particleList = new ArrayList<>();
		Instance2Data data = new Instance2Data();
		data.setFacing(facing);
		data.setTurnRate(turnRate);
		data.setScale(1, 1);
		data.setScaleRate(growth, growth);
		data.setTimer(in, full, out);
		particleList.add(data);

		spriteEntity.setInstanceData(particleList);
		spriteEntity.setInstanceDataRefreshAllFromCurrentIndex();
		spriteEntity.submitInstanceData();
		spriteEntity.setRenderingCount(1);
		spriteEntity.setAlwaysRefreshInstanceData(true);

		return data;
	}

	public static SpriteEntity addSingleParticle(String key, Vector2f loc, Vector2f vel, float size, float brightness, float in, float full, float out, Color color, String sprite, float facing, float turnRate, float growth) {

		if (key == null) {
			key = KEY_PREFIX + sprite;
		} else {
			key = KEY_PREFIX + key + sprite;
		}

		CombatEngineAPI engine = Global.getCombatEngine();
		if (!engine.getCustomData().containsKey(key)) {

			SpriteEntity spriteEntity = new SpriteEntity(sprite);
			spriteEntity.getMaterialData().setColor(Color.WHITE);
			spriteEntity.getMaterialData().setAlphaToEmissive(0f);
			spriteEntity.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
			spriteEntity.setAdditiveBlend();
			SimpleParticleControlData data = new SimpleParticleControlData(DEFAULT_MAX_PARTICLES, DEFAULT_MAX_DURATION, -9999f, false);
			spriteEntity.setControlData(data);
			CombatRenderingManager.addEntity(spriteEntity);

			engine.getCustomData().put(key, spriteEntity);
		}

		SpriteEntity spriteEntity = (SpriteEntity)engine.getCustomData().get(key);
		SimpleParticleControlData data = (SimpleParticleControlData)spriteEntity.getControlData();

		color = Misc.scaleAlpha(color, brightness);
		size *= 0.5f;
		data.addParticle(loc, facing, turnRate, vel, new Vector2f(size, size), new Vector2f(growth, growth), color, EMPTY, in, full, out);

		return spriteEntity;
	}

	public static SpriteEntity addMultipleParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color, String sprite) {

		SpriteEntity spriteEntity = new SpriteEntity(sprite);
		spriteEntity.getMaterialData().setColor(color);
		spriteEntity.getMaterialData().setColorAlpha(color.getAlpha() * brightness / 255.0f);
		spriteEntity.getMaterialData().setAlphaToEmissive(0f);

		spriteEntity.setLocation(loc);
		spriteEntity.setBaseSizePerTiles(size * 0.5f, size * 0.5f); // basically size
		spriteEntity.setAdditiveBlend();

		spriteEntity.setInstanceData(particleList);
		spriteEntity.setInstanceDataRefreshAllFromCurrentIndex();
		spriteEntity.submitInstanceData();
		spriteEntity.setRenderingCount(particleList.size());
		spriteEntity.setAlwaysRefreshInstanceData(true);

		spriteEntity.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
		spriteEntity.setGlobalTimer(in, 0f, out);
		CombatRenderingManager.addEntity(spriteEntity);

		return spriteEntity;
	}

	public static SpriteEntity addSmoothParticle(Vector2f loc, Vector2f vel, float size, float brightness, float duration, Color color) {
		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().addSmoothParticle(loc, vel, size, brightness, duration, color);
			return null;
		}
		return addSingleParticle(null, loc, vel, size, brightness, 0f, 0f, duration, color, "graphics/fx/hit_glow.png", 0f, 0f, 0f);
	}

	public static SpriteEntity addSmoothParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color) {
		return addMultipleParticle(particleList, loc, size, brightness, in, out, color, "graphics/fx/hit_glow.png");
	}

	public static SpriteEntity addSmokeParticle(Vector2f loc, Vector2f vel, float size, float brightness, float duration, Color color) {
		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().addSmokeParticle(loc, vel, size, brightness, duration, color);
			return null;
		}
		return addSingleParticle(null, loc, vel, size, brightness, 0f, 0f, duration, color, "graphics/fx/smoke32.png", RANDOM.nextFloat() * 360f, 60f, 0f);
	}

	public static SpriteEntity addSmokeParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color) {
		return addMultipleParticle(particleList, loc, size, brightness, in, out, color, "graphics/fx/smoke32.png");
	}

	public static final String[] EXPLOSION_PICKER = new String[5];
	static {
		EXPLOSION_PICKER[0] = "graphics/fx/explosion0.png";
		EXPLOSION_PICKER[1] = "graphics/fx/explosion1.png";
		EXPLOSION_PICKER[2] = "graphics/fx/explosion2.png";
		EXPLOSION_PICKER[3] = "graphics/fx/explosion_ring0.png";
		EXPLOSION_PICKER[4] = "graphics/fx/particlealpha32sq.png";
	}

	public static List<SpriteEntity> spawnExplosion(Vector2f loc, Vector2f vel, Color color, float size, float maxDuration) {

		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().spawnExplosion(loc, vel, color, size, maxDuration);
			return null;
		}

		float baseSize = 20f + ((60f * size) / 500f);
		float smallerSize = (20f * size) / 500f;
		float count = (((((size / 2f) * size) / 2f) * 3.1415927f) / ((((baseSize * 0.66f) * baseSize) * 0.66f) * 3.1415927f)) * 6f;
		count = Math.max(count, 5f);

		List<SpriteEntity> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {

			// do not fix index bug
			int textureIndex = (int) (RANDOM.nextDouble() * (EXPLOSION_PICKER.length - 1));
			if (textureIndex == 3) {
				textureIndex = (int) (RANDOM.nextDouble() * (EXPLOSION_PICKER.length - 1));
			}
			boolean isLast = false;
			if (textureIndex >= EXPLOSION_PICKER.length - 1) {
				textureIndex = EXPLOSION_PICKER.length - 1;
				isLast = true;
			}

			float originalSize = baseSize + (baseSize * RANDOM.nextFloat());
			float endSize = originalSize * 1.25f;
			if (textureIndex == 3) {
				endSize = originalSize * 3f;
				originalSize = endSize / 10f;
			} else if (isLast) {
				originalSize *= 1.5f;
				endSize = originalSize;
			}

			float extraAngle = RANDOM.nextFloat() * 360f;
			float cos = (float) Math.cos(extraAngle);
			float sin = (float) Math.sin(extraAngle);
			float extraDistance = (size / 4f) * RANDOM.nextFloat();
			if (textureIndex == 3 || i < 5f) {
				extraDistance = 0f;
			}

			Vector2f newVelocity = new Vector2f();
			if (textureIndex == 3) {
				newVelocity.set(vel.x, vel.y);
			} else {
				float extraSpeed = 10f + (RANDOM.nextFloat() * smallerSize);
				newVelocity.set((cos * extraSpeed) + vel.x, (sin * extraSpeed) + vel.y);
				newVelocity.set(vel.x, vel.y); // do not provide extra speed
			}

			Vector2f newLocation = new Vector2f(loc.x + (cos * extraDistance * 1f), loc.y + (sin * extraDistance * 1f));
			float growth = (endSize - originalSize) / maxDuration;

			SpriteEntity entity = addSingleParticle(null, newLocation, newVelocity, originalSize, 0.2f, 0f, 0f, maxDuration, color, EXPLOSION_PICKER[textureIndex], RANDOM.nextFloat() * 360f, 0f, growth);
			list.add(entity);
		}

		return list;
	}

	public static float[] rampToTimer(float totalDuration, float rampUpFraction) {
		float in = Math.max(totalDuration * rampUpFraction, 0f);
		float out = Math.max(totalDuration * (1f - rampUpFraction), 0f);
		return new float[]{in, out};
	}

	public static float endSizeMultToInstanceGrowth(float scale, float endSizeMult, float totalDuration) {
		return scale * (endSizeMult - 1f) / totalDuration;
	}

	public static float getNumberInRange(float[] number) {
		if (number.length == 1) return number[0];
		return MathUtils.getRandomNumberInRange(number[0], number[1]);
	}

	public static float getAngleInRange(float[] angle) {
		if (angle.length == 1) return angle[0];
		float rotation = MathUtils.getShortestRotation(angle[0], angle[1]);
		return angle[0] + MathUtils.getRandomNumberInRange(0f, rotation);
	}

	public static List<InstanceDataAPI> createBurstOutInstanceData(int count, @Nullable float[] range, @Nullable float[] facing, @Nullable float[] angle, @Nullable float[] speed, float[] size, @Nullable float[] sizeMult, @Nullable Color[] color, float in, float out) {

		List<InstanceDataAPI> list = new ArrayList<>(count);
		float anglePerPoint = 360f / count;
		for (int i = 0; i < count; i++) {
			Instance2Data data = new Instance2Data();

			if (facing != null) data.setFacing(getAngleInRange(facing));
			else data.setFacing(RANDOM.nextFloat() * 360f);

			float a;
			if (angle == null) a = RANDOM.nextFloat() * 360f;
			else if (angle.length <= 2) a = getAngleInRange(angle);
			else a = anglePerPoint * i + getAngleInRange(angle) + angle[2];

			Vector2f baseLocation = new Vector2f(ZERO);
			if (range != null) {
				float locationRange = getNumberInRange(range);
				baseLocation = MathUtils.getPointOnCircumference(ZERO, locationRange, a);
				data.setLocation(baseLocation);
			}

			if (speed != null) {
				float s = getNumberInRange(speed);
				Vector2f velocity;
				if (speed.length <= 2) velocity = MathUtils.getPoint(ZERO, s, a);
				else velocity = MathUtils.getPoint((Vector2f)baseLocation.scale(speed[2]), s, a);
				data.setVelocity(velocity.getX(), velocity.getY());
			}

			float scale = 1f;
			if (size != null) scale = getNumberInRange(size);
			data.setScale(scale, scale);
			data.setTimer(in, 0f, out);

			if (color != null) {
				if (color.length == 1) data.setColor(color[0]);
				else if (color.length == 2) data.setColor(Misc.interpolateColor(color[0], color[1], RANDOM.nextFloat()));
				else data.setColor(Misc.interpolateColor(color[0], color[1], color.length * 0.1f * RANDOM.nextFloat()));
			}

			if (sizeMult != null) {
				float growth = endSizeMultToInstanceGrowth(scale, getNumberInRange(sizeMult), in + out);
				data.setScaleRate(growth, growth);
			}

			list.add(data);
		}
		return list;
	}

	public static SpriteEntity addNebulaParticle(Vector2f loc, Vector2f vel, float size, float endSizeMult, float rampUpFraction, float fullBrightnessFraction, float totalDuration, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().addNebulaParticle(loc, vel, size, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration, color);
			return null;
		}

		float[] timer = rampToTimer(totalDuration, rampUpFraction);
		float in = timer[0];
		float out = Math.max(totalDuration * (1f - rampUpFraction), 0f);

		SpriteEntity spriteEntity = addSingleParticle(null, loc, vel, size, fullBrightnessFraction, in, 0f, out, color, "graphics/fx/nebula_colorless.png", RANDOM.nextFloat() * 360f, 0f, endSizeMultToInstanceGrowth(size, endSizeMult, totalDuration));
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		return spriteEntity;
	}

	public static SpriteEntity addNebulaParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			for (InstanceDataAPI data :  particleList) {
				if (!(data instanceof Instance2Data data2)) continue;
				Vector2f location = Vector2f.add(loc, data2.getLocation(), null);
				Vector2f velocity = data2.getVelocity();
				float totalDuration = in + out;
				float size2 = size * data2.getScale().getX();
				float endSizeMult = 1f + data2.getScaleRate().getX() / data2.getScale().getX() * totalDuration;
				float rampUpFraction = in / totalDuration;
				Global.getCombatEngine().addNebulaParticle(location, velocity, size2, endSizeMult, rampUpFraction, brightness, totalDuration, color);
			}
			return null;
		}

		SpriteEntity spriteEntity = addMultipleParticle(particleList, loc, size, brightness, in, out, color, "graphics/fx/nebula_colorless.png");
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		return spriteEntity;
	}

	public static SpriteEntity addNebulaSmoothParticle(Vector2f loc, Vector2f vel, float size, float endSizeMult, float rampUpFraction, float fullBrightnessFraction, float totalDuration, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().addNebulaSmoothParticle(loc, vel, size, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration, color);
			return null;
		}

		float in = Math.max(totalDuration * rampUpFraction, 0f);
		float out = Math.max(totalDuration * (1f - rampUpFraction), 0f);

		SpriteEntity spriteEntity = addSingleParticle(null, loc, vel, size, fullBrightnessFraction, in, 0f, out, color, "graphics/fx/cleaner_clouds00.png", RANDOM.nextFloat() * 360f, 0f, endSizeMultToInstanceGrowth(size, endSizeMult, totalDuration));
		spriteEntity.setTileSize(2, 2);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		return spriteEntity;
	}

	public static SpriteEntity addNebulaSmoothParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			for (InstanceDataAPI data :  particleList) {
				if (!(data instanceof Instance2Data data2)) continue;
				Vector2f location = Vector2f.add(loc, data2.getLocation(), null);
				Vector2f velocity = data2.getVelocity();
				float totalDuration = in + out;
				float size2 = size * data2.getScale().getX();
				float endSizeMult = 1f + data2.getScaleRate().getX() / data2.getScale().getX() * totalDuration;
				float rampUpFraction = in / totalDuration;
				Global.getCombatEngine().addNebulaSmoothParticle(location, velocity, size2, endSizeMult, rampUpFraction, brightness, totalDuration, color);
			}
			return null;
		}

		SpriteEntity spriteEntity = addMultipleParticle(particleList, loc, size, brightness, in, out, color, "graphics/fx/cleaner_clouds00.png");
		spriteEntity.setTileSize(2, 2);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		return spriteEntity;
	}

	public static SpriteEntity addSwirlyNebulaParticle(Vector2f loc, Vector2f vel, float size, float endSizeMult, float rampUpFraction, float fullBrightnessFraction, float totalDuration, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().addSwirlyNebulaParticle(loc, vel, size, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration, color, false);
			return null;
		}

		float in = Math.max(totalDuration * rampUpFraction, 0f);
		float out = Math.max(totalDuration * (1f - rampUpFraction), 0f);

		SpriteEntity spriteEntity = addSingleParticle(null, loc, vel, size, fullBrightnessFraction, in, 0f, out, color, "graphics/fx/fx_clouds01.png", RANDOM.nextFloat() * 360f, 0f, endSizeMultToInstanceGrowth(size, endSizeMult, totalDuration));
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		return spriteEntity;
	}

	public static SpriteEntity addSwirlyNebulaParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			for (InstanceDataAPI data :  particleList) {
				if (!(data instanceof Instance2Data data2)) continue;
				Vector2f location = Vector2f.add(loc, data2.getLocation(), null);
				Vector2f velocity = data2.getVelocity();
				float totalDuration = in + out;
				float size2 = size * data2.getScale().getX();
				float endSizeMult = 1f + data2.getScaleRate().getX() / data2.getScale().getX() * totalDuration;
				float rampUpFraction = in / totalDuration;
				Global.getCombatEngine().addSwirlyNebulaParticle(location, velocity, size2, endSizeMult, rampUpFraction, brightness, totalDuration, color, false);
			}
			return null;
		}

		SpriteEntity spriteEntity = addMultipleParticle(particleList, loc, size, brightness, in, out, color, "graphics/fx/fx_clouds01.png");
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		return spriteEntity;
	}

	public static SpriteEntity addNebulaSmokeParticle(Vector2f loc, Vector2f vel, float size, float endSizeMult, float rampUpFraction, float fullBrightnessFraction, float totalDuration, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().addNebulaSmokeParticle(loc, vel, size, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration, color);
			return null;
		}

		float in = Math.max(totalDuration * rampUpFraction, 0f);
		float out = Math.max(totalDuration * (1f - rampUpFraction), 0f);

		SpriteEntity spriteEntity = addSingleParticle("smoke", loc, vel, size, fullBrightnessFraction, in, 0f, out, color, "graphics/fx/nebula_colorless.png", RANDOM.nextFloat() * 360f, 0f, endSizeMultToInstanceGrowth(size, endSizeMult, totalDuration));
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		spriteEntity.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		return spriteEntity;
	}

	public static SpriteEntity addNebulaSmokeParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			for (InstanceDataAPI data :  particleList) {
				if (!(data instanceof Instance2Data data2)) continue;
				Vector2f location = Vector2f.add(loc, data2.getLocation(), null);
				Vector2f velocity = data2.getVelocity();
				float totalDuration = in + out;
				float size2 = size * data2.getScale().getX();
				float endSizeMult = 1f + data2.getScaleRate().getX() / data2.getScale().getX() * totalDuration;
				float rampUpFraction = in / totalDuration;
				Global.getCombatEngine().addNebulaSmokeParticle(location, velocity, size2, endSizeMult, rampUpFraction, brightness, totalDuration, color);
			}
			return null;
		}

		SpriteEntity spriteEntity = addMultipleParticle(particleList, loc, size, brightness, in, out, color, "graphics/fx/nebula_colorless.png");
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		spriteEntity.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		return spriteEntity;
	}

	public static SpriteEntity addNegativeNebulaParticle(Vector2f loc, Vector2f vel, float size, float endSizeMult, float rampUpFraction, float fullBrightnessFraction, float totalDuration, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			Global.getCombatEngine().addNegativeNebulaParticle(loc, vel, size, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration, color);
			return null;
		}

		float in = Math.max(totalDuration * rampUpFraction, 0f);
		float out = Math.max(totalDuration * (1f - rampUpFraction), 0f);

		SpriteEntity spriteEntity = addSingleParticle("negative", loc, vel, size, fullBrightnessFraction, in, 0f, out, color, "graphics/fx/nebula_colorless.png", RANDOM.nextFloat() * 360f, 0f, endSizeMultToInstanceGrowth(size, endSizeMult, totalDuration));
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		spriteEntity.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		spriteEntity.setBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);

		return spriteEntity;
	}

	public static SpriteEntity addNegativeNebulaParticle(List<InstanceDataAPI> particleList, Vector2f loc, float size, float brightness, float in, float out, Color color) {

		if (!BoxConfigs.isShaderEnable()) {
			for (InstanceDataAPI data :  particleList) {
				if (!(data instanceof Instance2Data data2)) continue;
				Vector2f location = Vector2f.add(loc, data2.getLocation(), null);
				Vector2f velocity = data2.getVelocity();
				float totalDuration = in + out;
				float size2 = size * data2.getScale().getX();
				float endSizeMult = 1f + data2.getScaleRate().getX() / data2.getScale().getX() * totalDuration;
				float rampUpFraction = in / totalDuration;
				Global.getCombatEngine().addNegativeNebulaParticle(location, velocity, size2, endSizeMult, rampUpFraction, brightness, totalDuration, color);
			}
			return null;
		}

		SpriteEntity spriteEntity = addMultipleParticle(particleList, loc, size, brightness, in, out, color, "graphics/fx/nebula_colorless.png");
		spriteEntity.setTileSize(4, 4);
		spriteEntity.setRandomTile(true);
		spriteEntity.setRandomTileEachInstance(true);

		spriteEntity.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		spriteEntity.setBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);

		return spriteEntity;
	}

	public static TrailEntity createFakeBeam(SpriteAPI core, Color coreColor, SpriteAPI fringe, Color fringeColor, float width, float textureSpeed) {

		TrailEntity fakeBeam = new TrailEntity();
		fakeBeam.getMaterialData().setDiffuse(core);
		fakeBeam.getMaterialData().setEmissive(fringe);
		fakeBeam.setStartColor(coreColor);
		fakeBeam.setEndColor(coreColor);
		fakeBeam.setStartEmissive(fringeColor);
		fakeBeam.setEndEmissive(fringeColor);

		fakeBeam.setStartWidth(width);
		fakeBeam.setEndWidth(width);

		fakeBeam.setTexturePixels(256f);
		fakeBeam.setTextureSpeed(textureSpeed);
		fakeBeam.setAdditiveBlend();

		fakeBeam.addNode(new Vector2f(0, 0));
		fakeBeam.addNode(new Vector2f(0, 0));
		fakeBeam.setFillStartAlpha(0f);
		fakeBeam.setFillEndAlpha(0f);

		fakeBeam.setNodeRefreshAllFromCurrentIndex();
		fakeBeam.submitNodes();

		fakeBeam.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);

		CombatRenderingManager.addEntity(fakeBeam);
		return fakeBeam;
	}

	public static void updateFakeBeam(TrailEntity fakeBeam, CombatEngineAPI engine, Vector2f start, Vector2f end, DealtController controller) {

		if (fakeBeam.hasDelete()) return;

		Pair<Vector3f, Matrix2f> origResult = CurveUtil.spawnDirectBeam(engine, start, end, 10f, controller);
		Vector3f beamEndPoint = origResult.one;
		Matrix2f beamRotation = origResult.two;

		fakeBeam.setLocation(start);
		fakeBeam.getModelMatrix().m00 = beamRotation.m00;
		fakeBeam.getModelMatrix().m01 = beamRotation.m01;
		fakeBeam.getModelMatrix().m10 = beamRotation.m10;
		fakeBeam.getModelMatrix().m11 = beamRotation.m11;
		beamRotation.m10 = -beamRotation.m10;
		beamRotation.m01 = -beamRotation.m01;

		Vector3f realEnd = new Vector3f();

		Vector2f endNode = fakeBeam.getNodes().get(0);
		if (beamEndPoint == null) {
			endNode.set(end.x - start.x, end.y - start.y);
			realEnd.z = endNode.lengthSquared();
		} else {
			endNode.set(beamEndPoint.x - start.x, beamEndPoint.y - start.y);
			realEnd.set(beamEndPoint);
		}
		Matrix2f.transform(beamRotation, endNode, endNode);

		realEnd.z = (float)Math.sqrt(realEnd.z);
		float smoothFactor = Math.max(realEnd.z - 10f, 0f) / realEnd.z;
		fakeBeam.setFillStartAlpha(0f);
		fakeBeam.setFillStartFactor(smoothFactor);
		fakeBeam.setFillEndAlpha(0f);

		fakeBeam.setNodeRefreshAllFromCurrentIndex();
		fakeBeam.submitNodes();
	}

	public static void startFakeBeam(TrailEntity fakeBeam, float in) {
		if (fakeBeam.getGlobalTimerState() == BoxEnum.TIMER_IN) return;
		if (fakeBeam.getGlobalTimerState() == BoxEnum.TIMER_FULL) return;
		fakeBeam.setControlData(new SelfRenewingControlData());
		fakeBeam.setGlobalTimer(in, 1f, in);
	}

	public static void endFakeBeam(TrailEntity fakeBeam, float out) {
		if (fakeBeam.getGlobalTimerState() == BoxEnum.TIMER_OUT) return;
		if (fakeBeam.getGlobalTimerState() == BoxEnum.TIMER_INVALID) return;
		if (fakeBeam.getGlobalTimerState() == BoxEnum.TIMER_ONCE) return;
		if (fakeBeam.getControlData() instanceof SelfRenewingControlData) {
			fakeBeam.setControlData(null);
		}
		fakeBeam.setGlobalTimer(0f, 0f, out);
	}

	public static CurveEntity createCurveLine(SpriteAPI core, Color coreColorStart, Color coreColorEnd, SpriteAPI fringe, Color fringeColorStart, Color fringeColorEnd, float widthStart, float widthEnd, float textureSpeed) {
		return createCurveLine(core, coreColorStart, coreColorEnd, fringe, fringeColorStart, fringeColorEnd, widthStart, widthEnd, textureSpeed, (short)64, CombatEngineLayers.ABOVE_PARTICLES_LOWER);
	}

	public static CurveEntity createCurveLine(SpriteAPI core, Color coreColorStart, Color coreColorEnd, SpriteAPI fringe, Color fringeColorStart, Color fringeColorEnd, float widthStart, float widthEnd, float textureSpeed, short interpolation, CombatEngineLayers layer) {

		CurveEntity curveEntity = new CurveEntity();

		List<NodeData> line = new ArrayList<>(2);
		NodeData start = new NodeData(0f, 0f, 0f, 0f, 0f, 0f);
		start.setColor(coreColorStart);
		start.setEmissiveColor(fringeColorStart);
		start.setWidth(widthStart);
		NodeData end = new NodeData(0f, 0f, 0f, 0f, 0f, 0f);
		end.setColor(coreColorEnd);
		end.setEmissiveColor(fringeColorEnd);
		end.setWidth(widthEnd);
		line.add(start);
		line.add(end);
		curveEntity.setNodes(line);
		curveEntity.setNodeRefreshAllFromCurrentIndex();
		curveEntity.submitNodes();

		curveEntity.getMaterialData().setDiffuse(core);
		curveEntity.getMaterialData().setEmissive(fringe);
		curveEntity.setInterpolation(interpolation);
		curveEntity.setTexturePixels(256f);
		curveEntity.setTextureSpeed(textureSpeed);
		curveEntity.setLayer(layer);
		curveEntity.setAdditiveBlend();
		curveEntity.setFillStartAlpha(0f);
		curveEntity.setFillEndAlpha(0f);
		curveEntity.setFillStartFactor(0.95f);
		curveEntity.setFillEndFactor(0.05f);
		CombatRenderingManager.addEntity(curveEntity);

		return curveEntity;
	}

	public static void updateCurveLine(CurveEntity curveEntity, Vector2f start, Vector2f end, float mainFacing, float startFactor, float endFactor, int sig) {

		if (curveEntity.hasDelete()) return;

		NodeData startNode = curveEntity.getNodes().get(0);
		NodeData endNode = curveEntity.getNodes().get(1);
		startNode.setLocation(start);
		endNode.setLocation(end);

		float nodeAngle = VectorUtils.getAngle(startNode.getLocation(), endNode.getLocation());
		if (sig == 0) sig = (int)Math.signum(MathUtils.getShortestRotation(mainFacing, nodeAngle));
		float anglePlus90 = mainFacing + 90f * sig;

		startNode.setTangentLeft(0f, 0f);
		startNode.setTangentRight(MathUtils.getPoint(null, startFactor, anglePlus90));

		endNode.setTangentLeft(MathUtils.getPoint(null, endFactor, mainFacing + 180f));
		endNode.setTangentRight(0f, 0f);

		curveEntity.submitNodes();
	}

	public static void updateCurveLine(CurveEntity curveEntity, Vector2f start, Vector2f end) {

		if (curveEntity.hasDelete()) return;

		NodeData startNode = curveEntity.getNodes().get(0);
		NodeData endNode = curveEntity.getNodes().get(1);
		startNode.setLocation(start);
		endNode.setLocation(end);

		curveEntity.submitNodes();
	}

	public static void startCurveLine(CurveEntity curveEntity, float in) {
		if (curveEntity.getGlobalTimerState() == BoxEnum.TIMER_IN) return;
		if (curveEntity.getGlobalTimerState() == BoxEnum.TIMER_FULL) return;
		curveEntity.setControlData(new SelfRenewingControlData());
		curveEntity.setGlobalTimer(in, 1f, in);
	}

	public static void endCurveLine(CurveEntity curveEntity, float out) {
		if (curveEntity.getGlobalTimerState() == BoxEnum.TIMER_OUT) return;
		if (curveEntity.getGlobalTimerState() == BoxEnum.TIMER_INVALID) return;
		if (curveEntity.getGlobalTimerState() == BoxEnum.TIMER_ONCE) return;
		if (curveEntity.getControlData() instanceof SelfRenewingControlData) {
			curveEntity.setControlData(null);
		}
		curveEntity.setGlobalTimer(0f, 0f, out);
	}

	public static FlareEntity addSharpFlare(Vector2f location, float facing, float width, float height, Color coreColor, Color fringeColor, float in, float full, float out, float glowPower, float noisePower) {

		FlareEntity flareEntity = new FlareEntity();
		flareEntity.setLocation(location);
		flareEntity.setSize(width, height);
		flareEntity.setFacingScale(MathUtils.clampAngle(facing), 1f, 1f);

		flareEntity.setCoreColor(coreColor);
		flareEntity.setFringeColor(fringeColor);
		flareEntity.setAdditiveBlend();

		flareEntity.setSharpDisc();
		flareEntity.autoAspect();
		flareEntity.setNoisePower(noisePower);
		flareEntity.setGlowPower(glowPower);
		flareEntity.setGlobalTimer(in, full, out);

		flareEntity.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
		CombatRenderingManager.addEntity(flareEntity);

		return flareEntity;
	}

	public static FlareEntity addBrightCross(Vector2f location, float facing, float width, float height, Color coreColor, Color fringeColor, float in, float full, float out, float noisePower) {

		FlareEntity flareEntity = new FlareEntity();
		flareEntity.setLocation(location);
		flareEntity.setSize(width, height);

		flareEntity.setCoreColor(coreColor);
		flareEntity.setFringeColor(fringeColor);
		flareEntity.setAdditiveBlend();

		flareEntity.setSmooth();
		flareEntity.setFlick(false);
		flareEntity.setNoisePower(noisePower);

		List<InstanceDataAPI> dataList = new ArrayList<>();
		for (int ix = 0; ix < 2; ix++) {
			Instance2Data data = new Instance2Data();
			data.setLocation(0f, 0f);
			data.setScale(1f, 1f);
			data.setFacing(facing + ix * 90f);
			data.setColor(coreColor);
			data.setEmissiveColor(fringeColor);
			data.setTimer(0, in + full + out, 0);
			dataList.add(data);
		}
		flareEntity.setInstanceData(dataList);
		flareEntity.setInstanceDataRefreshAllFromCurrentIndex();
		flareEntity.submitInstanceData();
		flareEntity.setRenderingCount(dataList.size());
		flareEntity.setAlwaysRefreshInstanceData(true);

		flareEntity.setControlData(new SizeBasedFlaredControlData());

		flareEntity.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
		flareEntity.setGlobalTimer(in, full, out);
		CombatRenderingManager.addEntity(flareEntity);

		return flareEntity;
	}

	public static final Color DEFAULT_CROSS_CORE_COLOR = Color.WHITE;
	public static final Color DEFAULT_CROSS_FRINGE_COLOR = new Color(174, 233, 255);
	public static final Color DEFAULT_CROSS_GLOW_COLOR = new Color(38, 175, 226);
	public static final Color DEFAULT_CROSS_NEBULA_COLOR = new Color(80, 170, 240, 100);

	public static void addBrightCrossExplosion(Vector2f location, float radius, Color coreColor, Color fringeColor, Color glowColor, Color nebulaColor, float time) {

		radius *= 4f;

		addBrightCross(location, RANDOM.nextFloat() * 360f, radius, radius * 0.0667f, coreColor, fringeColor, time * 0.2f, 0f, time * 0.8f, 0.25f);

		float glowSize = radius * 0.6f;
		float coreSize = radius * 0.3f;
		addSmoothParticle(location, ZERO, glowSize, 1f, time, glowColor);
		addSmoothParticle(location, ZERO, coreSize, 1f, time, coreColor);

		float nebulaRadius = radius * 0.2f;
		float nebulaSize = nebulaRadius * MathUtils.getRandomNumberInRange(0.75f, 1.5f);
		float nebulaTime = time * 2f;
		float[] nebulaTimer = rampToTimer(nebulaTime, 0f);
		int nebulaCount = (int)(radius / 50) + 5;
		float nebulaSizeMult = 1.5f;

		List<InstanceDataAPI> list = new ArrayList<>();
		for (int i = 0; i < nebulaCount; i++) {

			Instance2Data data = new Instance2Data();
			data.setLocation(MathUtils.getRandomPointInCircle(ZERO, nebulaRadius));
			data.setFacing(RANDOM.nextFloat() * 360f);
			data.setVelocity(0f, 0f);

			data.setScale(1f, 1f);
			data.setTimer(nebulaTimer[0], 0f, nebulaTimer[1]);

			float growth = endSizeMultToInstanceGrowth(1f, nebulaSizeMult, nebulaTime);
			data.setScaleRate(growth, growth);

			list.add(data);
		}

		addNebulaParticle(list, location, nebulaSize, 1f, nebulaTimer[0], nebulaTimer[1], nebulaColor);
	}

	public static DistortionEntity addDistortion(Vector2f location, float radius, float power, float edgeHardness, float in, float full, float out) {

		DistortionEntity distortionEntity = new DistortionEntity();
		distortionEntity.setLocation(location);
		distortionEntity.setSizeIn(0f, 0f);
		distortionEntity.setSizeFull(radius, radius);
		distortionEntity.setSizeOut(radius * 2f, radius * 2f);
		distortionEntity.setPowerIn(0f);
		distortionEntity.setPowerFull(power);
		distortionEntity.setPowerOut(0f);
		distortionEntity.setInnerHardness(0f);
		distortionEntity.setInnerFull(0.1f, 0.1f);
		distortionEntity.setRingHardness(edgeHardness);

		distortionEntity.setGlobalTimer(in, full, out);
		CombatRenderingManager.addEntity(distortionEntity);

		return distortionEntity;
	}

	public static class StaticControlData extends BaseControlData {

		@Override
		public boolean controlAlphaBasedTimer(@NotNull RenderDataAPI renderEntity) {
			return false;
		}

		@Override
		public boolean controlRemoveBasedTimer(@NotNull RenderDataAPI renderEntity) {
			return false;
		}
	}

	public static class EntityBasedControlData extends BaseControlData {

		private final CombatEntityAPI entity;
		private boolean withFacing = true;

		public EntityBasedControlData(CombatEntityAPI entity) {
			this.entity = entity;
		}

		public void setWithFacing(boolean withFacing) {
			this.withFacing = withFacing;
		}

		@Override
		public void controlAdvance(@NotNull RenderDataAPI renderEntity, float amount) {
			if (entity != null) {

				renderEntity.setLocation(entity.getLocation());
				if (withFacing) renderEntity.setFacingScale(MathUtils.clampAngle(entity.getFacing()), 1f, 1f);

				CombatEngineAPI engine = Global.getCombatEngine();
				if (engine == null || !isEntityValid(engine, entity)) {
					float[] timer = renderEntity.getGlobalTimer();
					timer[0] = Math.min(timer[0], 1);
				}
			}
		}

		public static boolean isEntityValid(CombatEngineAPI engine, CombatEntityAPI entity) {
			if (!engine.isEntityInPlay(entity)) return false;
			if (entity.isExpired()) return false;

			if (entity instanceof ShipAPI ship) {
				if (!ship.isAlive()) return false;
			}
			if (entity instanceof DamagingProjectileAPI projectile) {
				if (projectile.isFading()) return false;
				if (projectile instanceof MissileAPI missile) {
					if (missile.didDamage()) return false;
				}
			}

			return true;
		}
	}

	public static class SelfRenewingControlData extends BaseControlData {

		@Override
		public void controlAdvance(@NotNull RenderDataAPI renderEntity, float amount) {
			float[] timer = renderEntity.getGlobalTimer();
			if (timer[0] >= 1 && timer[0] < 2) {
				timer[0] = 2;
			} else if (timer[0] >= 0 && timer[0] < 1) {
				timer[0] = 2 + (1 - timer[0]);
			}
		}
	}

	public static class SizeBasedFlaredControlData extends BaseControlData {

		private float[] wh = null;

		@Override
		public void controlInit(@NotNull RenderDataAPI renderEntity) {
			if (renderEntity instanceof FlareEntity flareEntity && wh == null) {
				wh = new float[2];
				wh[0] = flareEntity.getWidth();
				wh[1] = flareEntity.getHeight();
			}
		}

		@Override
		public void controlAdvance(@NotNull RenderDataAPI renderEntity, float amount) {
			if (renderEntity instanceof FlareEntity flareEntity && wh != null) {
				float[] globalTimer = flareEntity.getGlobalTimer();
				float effectLevel = 1.0f;
				if (globalTimer[0] > 2.0f) effectLevel = Math.abs(globalTimer[0] - 3.0f);
				if (globalTimer[0] < 1.0f && globalTimer[0] > -500.0f) effectLevel = globalTimer[0];

				flareEntity.setSize(wh[0] * effectLevel, wh[1] * effectLevel);
			}
		}

		@Override
		public boolean controlAlphaBasedTimer(@NotNull RenderDataAPI renderEntity) {
			return false;
		}
	}

	// just keep the sdf all over the time
	private static final Map<String, int[]> SAVED_SDF = new HashMap<>();
	public static int[] genSDF(SpriteAPI sprite, int stroke) {
		String key = sprite.getTextureId() + "_" + stroke;
		if (SAVED_SDF.containsKey(key)) return SAVED_SDF.get(key);
		float strokeDiv = 1f / stroke;
		int localWidth = (int) sprite.getWidth();
		int localHeight = (int) sprite.getHeight();
		int[] sdfs = ShaderUtil.genSDF(sprite.getTextureId(), GL11.GL_ALPHA, localWidth, localHeight, stroke, stroke, 0.5f,  CalculateUtil.getExponentPOTMin(Math.max(localWidth, localHeight)), strokeDiv, strokeDiv);
		SAVED_SDF.put(key, sdfs);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, sdfs[0]);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		return sdfs;
	}

	public static FloatBuffer maintainAnchoredFloatBuffer(Vector2f location, float angle, FloatBuffer buffer) {

		if (buffer == null || buffer.capacity() < 16) {
			buffer = BufferUtils.createFloatBuffer(16);
		}

		float radians = (float)Math.toRadians(MathUtils.clampAngle(angle));
		float sin = (float)Math.sin(radians);
		float cos = (float)Math.cos(radians);

		buffer.clear();

		buffer.put(cos);
		buffer.put(sin);
		buffer.put(0f);
		buffer.put(0f);

		buffer.put(-sin);
		buffer.put(cos);
		buffer.put(0f);
		buffer.put(0f);

		buffer.put(0f);
		buffer.put(0f);
		buffer.put(1f);
		buffer.put(0f);

		buffer.put(location.x);
		buffer.put(location.y);
		buffer.put(0f);
		buffer.put(1f);

		buffer.flip();

		return buffer;
	}

	public static FloatBuffer maintainScreenFloatBuffer(FloatBuffer buffer) {

		if (buffer == null || buffer.capacity() < 16) {
			buffer = BufferUtils.createFloatBuffer(16);
		}

		float width = ShaderCore.getScreenWidth();
		float height = ShaderCore.getScreenHeight();

		buffer.clear();

		buffer.put(2f / width);
		buffer.put(0f);
		buffer.put(0f);
		buffer.put(0f);

		buffer.put(0f);
		buffer.put(2f / height);
		buffer.put(0f);
		buffer.put(0f);

		buffer.put(0f);
		buffer.put(0f);
		buffer.put(1f);
		buffer.put(0f);

		buffer.put(-1f);
		buffer.put(-1f);
		buffer.put(0f);
		buffer.put(1f);

		buffer.flip();

		return buffer;
	}
}
