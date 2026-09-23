package data.scripts.kratogen.util;

import org.boxutil.base.BaseShaderData;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.FontDataManager;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.attribute.FontMapData;
import org.boxutil.units.standard.entity.TextFieldEntity;
import org.boxutil.units.standard.misc.TextFieldObject;
import org.boxutil.util.TransformUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;

public final class Kratogen_FontData {

	private static final float TEXT_FIELD_SIZE = 8192f;
	private static final Vector2f COMPATIBLE_ORIGIN = new Vector2f();

	private static final FontStore fonts = new FontStore();

	public static void init() {
		fonts.orbitronNormal = loadFont("graphics/fonts/orbitron20aa.fnt");
		fonts.orbitronBold = loadFont("graphics/fonts/orbitron20aabold.fnt");
		fonts.insigniaNormal = loadFont("graphics/fonts/insignia21LTaa.fnt");
	}

	public static TextObject getOrbitronNormal() {
		return new TextObject(fonts.orbitronNormal);
	}

	public static TextObject getOrbitronBold() {
		return new TextObject(fonts.orbitronBold);
	}

	public static TextObject getInsigniaNormal() {
		return new TextObject(fonts.insigniaNormal);
	}

	private static FontMapData loadFont(String path) {
		FontMapData font = FontDataManager.tryFont(path);
		if (font == null || !font.isValid()) throw new IllegalStateException("Error font loading: " + path);
		if (Math.abs(font.getFontSize()) <= 0f) throw new IllegalArgumentException("Error font loading (negative size): " + path);
		return font;
	}

	private Kratogen_FontData() {}

	public static final class TextObject {

		private final TextFieldEntity entity;
		private final TextFieldObject compatible;
		private final float nativeFontSize;

		private Color baseColor = Color.WHITE;
		private float fontScale = 1f;

		private TextObject(FontMapData font) {

			this.nativeFontSize = Math.abs(font.getFontSize());

			this.entity = createPreferredEntity(font);
			this.compatible = entity == null ? new TextFieldObject(font) : null;

			if (entity != null) {
				entity.setFieldSize(TEXT_FIELD_SIZE, TEXT_FIELD_SIZE);
				entity.addText("", 0f, Color.WHITE, false, false, false, false, 0);
				entity.setMappingModeSubmitData(false);
				entity.setCustomPrimeMatrix();
				entity.setPrimeMatrix(TransformUtil.createWindowOrthoMatrix(null));
				entity.setGlobalColor(baseColor);
				return;
			}

			compatible.setFieldSize(TEXT_FIELD_SIZE, TEXT_FIELD_SIZE);
			compatible.addText("", 0f, Color.WHITE);
		}

		public void setText(String text) {

			String value = String.valueOf(text);

			List<TextFieldEntity.TextData> textDataList = getTextDataList();
			if (textDataList == null || textDataList.isEmpty()) return;

			TextFieldEntity.TextData message = textDataList.get(0);
			if (value.equals(message.text)) return;

			message.text = value;
			setTextDataRefreshIndex(0);
			setTextDataRefreshAllFromCurrentIndex();
			submitText();
		}

		public void setFontSize(float size) {
			fontScale = size / nativeFontSize;
		}

		public void setBaseColor(Color color) {
			baseColor = color;
			if (entity != null) entity.setGlobalColor(color);
		}

		public float getWidth() {
			if (!isValidRenderingTextField()) return 0f;
			if (entity != null) return entity.getCurrentVisualWidth() * fontScale;
			return compatible.getCurrentVisualWidth() * fontScale;
		}

		public float getHeight() {
			if (!isValidRenderingTextField()) return 0f;
			if (entity != null) return entity.getCurrentVisualHeight() * fontScale;
			return compatible.getCurrentVisualHeight() * fontScale;
		}

		public void draw(float x, float y) {

			if (entity != null) {
				if (!entity.hasDelete()) {
					entity.setLocation(x, y);
					entity.setFacingScale(0f, fontScale, fontScale);
					entity.directDraw();
				}
				return;
			}

			GL11.glPushMatrix();
			GL11.glTranslatef(x, y, 0f);
			GL11.glScalef(fontScale, fontScale, 1f);
			compatible.render(COMPATIBLE_ORIGIN, 0f, false, baseColor);
			GL11.glPopMatrix();
		}

		public List<TextFieldEntity.TextData> getTextDataList() {
			if (entity != null) return entity.getTextDataList();
			return compatible.getTextDataList();
		}

		public void setTextDataRefreshIndex(int index) {
			if (entity != null) entity.setTextDataRefreshIndex(index);
			else compatible.setTextDataRefreshIndex(index);
		}

		public void setTextDataRefreshSize(int size) {
			if (entity != null) entity.setTextDataRefreshSize(size);
			else compatible.setTextDataRefreshSize(size);
		}

		public void setTextDataRefreshAllFromCurrentIndex() {
			if (entity != null) entity.setTextDataRefreshAllFromCurrentIndex();
			else compatible.setTextDataRefreshAllFromCurrentIndex();
		}

		public byte submitText() {
			byte result = entity != null ? entity.submitText() : compatible.submitText();
			if (result != BoxEnum.STATE_SUCCESS) setShouldRenderingCharCount(0);
			return result;
		}

		public boolean isUsingEntity() {
			return entity != null;
		}

		public void dispose() {
			if (entity != null) {
				entity.delete();
				return;
			}
			compatible.resetText();
		}

		private static TextFieldEntity createPreferredEntity(FontMapData font) {

			if (!ShaderCore.isInitialized()) return null;
			BaseShaderData program = ShaderCore.getTextProgram();
			if (program == null || !program.isValid()) return null;

			TextFieldEntity entity = new TextFieldEntity(font, true);
			if (entity.isValid()) return entity;

			entity.delete();
			return null;
		}

		private boolean isValidRenderingTextField() {
			if (entity != null) return entity.isValidRenderingTextField();
			return compatible.isValidRenderingTextField();
		}

		private void setShouldRenderingCharCount(int count) {
			if (entity != null) entity.setShouldRenderingCharCount(count);
			else compatible.setShouldRenderingCharCount(count);
		}
	}

	private static final class FontStore {
		private FontMapData orbitronNormal;
		private FontMapData orbitronBold;
		private FontMapData insigniaNormal;
	}
}
