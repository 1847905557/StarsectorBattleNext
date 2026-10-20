package data.scripts.ui.widget;

import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.BattleNext_BaseStore.EditRejection;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_EntryId.Problem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_SaveErrors {

	private static final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final List<String> messages = new ArrayList<>();

	public void clear() {
		messages.clear();
	}

	public boolean isEmpty() {
		return messages.isEmpty();
	}

	public List<String> getMessages() {
		return List.copyOf(messages);
	}

	public String summary() {
		return messages.isEmpty() ? "" : strings.format("errors_summary", messages.size(), messages.get(0));
	}

	public void add(String message) {
		messages.add(message);
	}

	public void field(String subject, String field, String input, String reason) {
		StringBuilder visible = new StringBuilder();
		input.codePoints().forEach(letter -> {
			if (Character.isISOControl(letter)) {
				visible.append(String.format("U+%04X", letter));
			} else {
				visible.appendCodePoint(letter);
			}
		});
		messages.add(strings.format("field_error", subject, field, visible.toString(), reason));
	}

	public void id(String subject, String field, String input) {
		String value = input.trim();
		Problem problem = BattleNext_EntryId.problem(value.isEmpty() ? input : value);
		if (problem == null) return;
		int position = problem.position();
		if (position > 0 && !value.isEmpty()) {
			position += input.codePointCount(0, input.indexOf(value));
		}
		id(subject, field, input, new Problem(problem.reason(), position, problem.codePoint()));
	}

	public void id(String subject, String field, String input, Problem problem) {
		if (problem == null) return;
		int position = problem.position();
		String character = String.format("U+%04X", problem.codePoint());
		String reason = switch (problem.reason()) {
			case REQUIRED -> strings.get("error_required");
			case NUMERIC -> strings.get("error_id_numeric");
			case CONTROL -> strings.format("error_id_control", position, character);
			case WHITESPACE -> strings.format("error_id_whitespace", position, character);
			case CHARACTER -> strings.format("error_id_character", position, character);
			case TRAILING_DOT -> strings.get("error_id_trailing_dot");
			case RESERVED -> strings.get("error_id_reserved");
		};
		field(subject, field, input, reason);
	}

	public void rejection(String subject, String field, EditRejection rejection) {
		field(subject, field, rejection.getIdentity(), rejectionReason(rejection));
	}

	private String rejectionReason(EditRejection rejection) {
		return switch (rejection.getReason()) {
			case DUPLICATE_ID -> strings.get("duplicate_id");
			case SOURCE_UNAVAILABLE -> strings.get("source_unavailable");
			case DUPLICATE_FOLDER -> BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_").get("duplicate_folder");
		};
	}

	public Integer integer(String subject, String field, String input, int minimum, int maximum) {
		int result;
		try {
			result = Integer.parseInt(input.trim());
		} catch (NumberFormatException exception) {
			field(subject, field, input, strings.get("error_integer"));
			return null;
		}
		if (result < minimum || result > maximum) {
			field(subject, field, input, strings.format("error_integer_range", minimum, maximum));
			return null;
		}
		return result;
	}

	public Float number(String subject, String field, String input, boolean positive) {
		float result;
		String reason = strings.get(positive ? "error_positive" : "error_nonnegative");
		try {
			result = Float.parseFloat(input.trim());
		} catch (NumberFormatException exception) {
			field(subject, field, input, reason);
			return null;
		}
		if (!Float.isFinite(result) || (positive ? result <= 0f : result < 0f)) {
			field(subject, field, input, reason);
			return null;
		}
		return result;
	}

	public void failure(String operation, String target, Throwable exception) {
		String reason;
		if (exception instanceof EditRejection rejection) {
			reason = rejectionReason(rejection) + " / " + rejection.getIdentity();
		} else {
			reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
		}
		Throwable cause = exception.getCause();
		while (cause != null) {
			String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
			if (!reason.contains(detail)) reason += " / " + detail;
			cause = cause.getCause();
		}
		messages.add(strings.format("operation_error", operation, target, reason));
	}

	public static BaseTooltipCreator tooltip(Supplier<List<String>> source) {
		return new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return 480f;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				List<String> errors = source.get();
				for (int index = 0; index < errors.size(); index++) {
					tooltip.addPara((index + 1) + ". " + errors.get(index), index == 0 ? 0f : 5f);
				}
			}
		};
	}
}
