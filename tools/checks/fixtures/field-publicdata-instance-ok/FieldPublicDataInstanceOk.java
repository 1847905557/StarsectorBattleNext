@PublicData
public class FieldPublicDataInstanceOk {
	public static final FieldPublicDataInstanceOk DEFAULT = new FieldPublicDataInstanceOk(0) {
		private int detail;
	};
	public int state;

	private FieldPublicDataInstanceOk(int state) {
		int initialState = Math.max(0, state);
		this.state = initialState;
	}

	private static class Nested {
		private int detail;
	}

	private record Snapshot(int state) {
	}
}
