package data.scripts.ui;

import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.widget.BattleNext_SaveErrors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class BattleNext_View {

	protected BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	protected final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	protected final BattleNext_SaveErrors saveErrors = new BattleNext_SaveErrors();
	private Function<Callable<Boolean>, CompletableFuture<Boolean>> persistence;
	private Pending pending;

	private record Pending(CompletableFuture<Boolean> result, Runnable publish, String failure, boolean commit) { }

	public void setPersistence(Function<Callable<Boolean>, CompletableFuture<Boolean>> persistence) {
		this.persistence = persistence;
	}

	protected final Function<Callable<Boolean>, CompletableFuture<Boolean>> persistence() {
		return persistence;
	}

	public boolean isSaving() {
		return pending != null;
	}

	protected final boolean persist(Callable<Boolean> write, Runnable publish) throws Exception {
		return perform(write, publish, strings.get("saving"), strings.get("save_failed"), true);
	}

	public final boolean perform(Callable<Boolean> write, Runnable publish, String progress, String failure, boolean commit) throws Exception {
		saveErrors.clear();
		if (persistence == null) {
			boolean success = write.call();
			if (!commit) operationStatus("");
			if (success) publish.run();
			return success;
		}
		pending = new Pending(persistence.apply(write), publish, failure, commit);
		operationStatus(progress);
		return false;
	}

	protected final Boolean completeOperation() {
		Pending completed = pending;
		if (completed == null || !completed.result().isDone()) return null;
		pending = null;
		try {
			if (!completed.result().join()) return completed.commit() ? false : null;
			if (!completed.commit()) operationStatus("");
			completed.publish().run();
			if (completed.commit()) commitPublished();
			return completed.commit() ? true : null;
		} catch (RuntimeException exception) {
			saveErrors.failure(completed.failure(), "", exception);
			operationStatus(saveErrors.summary());
			return completed.commit() ? false : null;
		}
	}

	protected final void clearOperation() {
		pending = null;
	}

	protected void commitPublished() {
	}

	protected abstract void operationStatus(String value);
}
