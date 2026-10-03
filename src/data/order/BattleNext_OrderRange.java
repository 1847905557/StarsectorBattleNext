package data.order;

import java.util.List;

public record BattleNext_OrderRange(Bound lower, Bound upper) {

	public enum Unit {
		VALUE, PERCENT, SELF
	}

	public record Bound(float value, Unit unit) {

		public float resolve(float absolute, float ratio, float self) {
			return switch (unit) {
				case VALUE -> absolute;
				case PERCENT -> ratio;
				case SELF -> absolute;
			};
		}

		public float limit(float self) {
			return unit == Unit.SELF ? self : value;
		}
	}

	public static BattleNext_OrderRange compile(List<String> params, boolean percent, boolean self) {
		BattleNext_OrderUtils.requireCount(params, 1, 2);
		Bound lower = params.size() == 1 ? new Bound(0f, Unit.VALUE) : parse(params.get(0), percent, self);
		Bound upper = parse(params.get(params.size() - 1), percent, self);
		if (lower.unit() == upper.unit() && lower.unit() != Unit.SELF && lower.value() > upper.value()) {
			throw new IllegalArgumentException("Lower bound exceeds upper bound");
		}
		return new BattleNext_OrderRange(lower, upper);
	}

	private static Bound parse(String text, boolean percent, boolean self) {
		if (self && text.equals(BattleNext_OrderUtils.SELF)) return new Bound(0f, Unit.SELF);
		boolean percentage = text.endsWith("%") || text.endsWith("﹪");
		if (percentage && !percent) throw new IllegalArgumentException("Percentage is not supported: " + text);
		float value = BattleNext_OrderUtils.number(percentage ? text.substring(0, text.length() - 1) : text);
		if (value < 0f) throw new IllegalArgumentException("Expected a nonnegative bound: " + text);
		return new Bound(percentage ? value / 100f : value, percentage ? Unit.PERCENT : Unit.VALUE);
	}

	public boolean contains(float absolute, float ratio, float self) {
		return lower.resolve(absolute, ratio, self) >= lower.limit(self)
				&& upper.resolve(absolute, ratio, self) <= upper.limit(self);
	}
}
