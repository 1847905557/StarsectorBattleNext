package data.scripts.ui;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseStore;
import data.scripts.tools.BattleNext_ModFileManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Iterator;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.widget.provider.BattleNext_VariantCatalog;

public final class BattleNext_ManagementSession implements AutoCloseable {

	public record Catalogs(BattleNext_FleetStore fleets, BattleNext_QuickBattleStore quickBattles,
			BattleNext_RuleStore rules, BattleNext_TournamentStore tournaments, BattleNext_PinnedVariantStore pins,
			BattleNext_BattleReportStore reports, BattleNext_StartedTournamentStore instances, BattleNext_VariantCatalog variants) {

		public static Catalogs create() {
			return create(Set.copyOf(Global.getSettings().getAllVariantIds()));
		}

		private static Catalogs create(Set<String> registered) {
			BattleNext_PinnedVariantStore pins = new BattleNext_PinnedVariantStore(model -> { });
			return new Catalogs(new BattleNext_FleetStore(registered::contains), new BattleNext_QuickBattleStore(),
					new BattleNext_RuleStore(), new BattleNext_TournamentStore(), pins,
					new BattleNext_BattleReportStore(), new BattleNext_StartedTournamentStore(), new BattleNext_VariantCatalog(pins));
		}

		public BattleNext_BattleSourceProvider source() {
			return new BattleNext_BattleSourceProvider(fleets, quickBattles, tournaments, rules, instances);
		}
	}

	private Catalogs data;
	private final Set<String> registered;
	private final BattleNext_ModFileManager.ReadSession files;
	private final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
	private CompletableFuture<Void> preparation;
	private volatile String loading = "";
	private volatile boolean closed;
	private Iterator<BattleNext_PinnedVariantModel> pinValidation;
	private boolean gameReady;
	private boolean variantsReady;
	private CompletableFuture<BattleNext_BattleReportStore> pendingReports;
	private boolean reportsDirty;

	public BattleNext_ManagementSession() {
		registered = Set.copyOf(Global.getSettings().getAllVariantIds());
		files = new BattleNext_ModFileManager.ReadSession(Global.getSettings().getModManager().getModSpec("AI_Battles_Next").getPath());
	}

	public Catalogs data() {
		return data;
	}

	public void activate() {
		files.activate();
	}

	public boolean isReady() {
		if (!preparation.isDone()) return false;

		preparation.join();
		loading = "variants";
		long deadlineNanos = System.nanoTime() + 2_000_000L;

		if (!gameReady) {
			if (pinValidation == null) pinValidation = data.pins().getAllCopy().iterator();
			while (pinValidation.hasNext() && System.nanoTime() < deadlineNanos) data.pins().validateDefinition(pinValidation.next().getPinId());
			gameReady = !pinValidation.hasNext();
		}

		long remainingNanos = deadlineNanos - System.nanoTime();
		if (!variantsReady && remainingNanos > 0) variantsReady = data.variants().advance(remainingNanos);

		return gameReady && variantsReady;
	}

	public String loading() {
		return loading;
	}

	public List<String> errorsCopy() {
		return List.copyOf(errors);
	}

	public void start() {
		preparation = files.submit(() -> {
			data = Catalogs.create(registered);
			load("fleets", data.fleets());
			load("variants", data.pins());
			load("quick_battles", data.quickBattles());
			load("rules", data.rules());
			load("tournaments", data.tournaments());

			loading = "instances";
			if (!closed) data.instances().reload();

			load("reports", data.reports());
			return null;
		});
	}

	public CompletableFuture<Boolean> persist(Callable<Boolean> write) {
		return files.submit(write);
	}

	public void refreshReports() {
		if (pendingReports != null) {
			reportsDirty = true;
			return;
		}

		pendingReports = files.submit(() -> {
			files.invalidateDirectory("tournament/_reports");
			BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
			reports.reload();
			return reports;
		});
	}

	public boolean advance() {
		if (pendingReports == null || !pendingReports.isDone()) return false;

		try {
			data.reports().publish(pendingReports.join());
			return true;
		} catch (RuntimeException exception) {
			errors.add(exception.toString());
			return false;
		} finally {
			pendingReports = null;
			if (reportsDirty) {
				reportsDirty = false;
				refreshReports();
			}
		}
	}

	private void load(String label, BattleNext_BaseStore<?> store) {
		if (closed) return;

		loading = label;
		try {
			store.reload();
		} catch (Exception exception) {
			errors.add(exception.toString());
		}
	}

	@Override
	public void close() {
		closed = true;
		BattleNext_ModFileManager.ReadSession.deactivate();
	}
}
