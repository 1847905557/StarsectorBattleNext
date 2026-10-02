package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

public abstract class BattleNext_BaseSourceProvider implements BattleNext_OptionPicker.OptionChoiceSource {

	@Override
	public boolean expectsIcon() {
		return true;
	}

	private static final float KEY_GAP = 5f;
	private static final float BODY_GAP = 8f;
	private static final float BOTTOM_INSET = 8f;
	private static final String ELLIPSIS = "...";

	protected void addHeader(TooltipMakerAPI tooltip, String title, String key) {
		tooltip.addTitle(title);
		BattleNext_PageLayout.addMutedIdLine(tooltip, key, KEY_GAP);
	}

	protected void addExcerpt(TooltipMakerAPI tooltip, String text) {
		addExcerpt(tooltip, text, 0f);
	}

	protected void addExcerpt(TooltipMakerAPI tooltip, String text, float reservedHeight) {
		if (text == null || text.isBlank()) return;
		float available = tooltip.getPosition().getHeight() - tooltip.getHeightSoFar()
				- BODY_GAP - BOTTOM_INSET - reservedHeight;
		if (available <= 0f) return;
		float width = tooltip.getPosition().getWidth();
		String firstParagraph = text.strip().split("\\R", 2)[0].replaceAll("\\s+", " ").trim();
		String excerpt = fitExcerpt(firstParagraph, available, candidate -> {
			LabelAPI label = tooltip.createLabel(candidate, BattleNext_UiTheme.TEXT);
			return label.autoSizeToWidth(width).getHeight();
		});
		if (!excerpt.isEmpty()) tooltip.addPara(excerpt, BODY_GAP);
	}

	static String fitExcerpt(String text, float availableHeight, ToDoubleFunction<String> heightOf) {
		if (text == null || text.isBlank() || availableHeight <= 0f) return "";
		String normalized = text.strip();
		if (heightOf.applyAsDouble(normalized) <= availableHeight) return normalized;
		BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ROOT);
		sentences.setText(normalized);
		int end = sentences.first();
		String best = "";
		while ((end = sentences.next()) != BreakIterator.DONE) {
			String candidate = normalized.substring(0, end).stripTrailing();
			if (end < normalized.length()) {
				if (candidate.endsWith(".")) candidate = candidate.substring(0, candidate.length() - 1);
				candidate += ELLIPSIS;
			}
			if (heightOf.applyAsDouble(candidate) > availableHeight) break;
			best = candidate;
		}
		if (!best.isEmpty()) return best;
		int length = normalized.length();
		while (length > 0) {
			length = normalized.offsetByCodePoints(0, normalized.codePointCount(0, length) - 1);
			String candidate = normalized.substring(0, length).stripTrailing() + ELLIPSIS;
			if (heightOf.applyAsDouble(candidate) <= availableHeight) return candidate;
		}
		return "";
	}
}
