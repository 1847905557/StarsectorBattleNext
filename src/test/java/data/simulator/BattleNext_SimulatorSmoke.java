package data.simulator;

import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.log4j.Level;
import data.missions.BattleNext_MissionBattleMap;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.member.BattleNext_MemberModel;
import data.model.tournament.BattleNext_TournamentModel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class BattleNext_SimulatorSmoke {

	public static void main(String[] arguments) throws Exception {
		runSession("smoke", List.of(), 0);
	}

	static JSONObject runSession(String profile, List<String> vmOptions, int measurements) throws Exception {

		Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
		Path logs = (measurements == 0 ? Path.of("build/reports/simulator-smoke")
				: Path.of("build/reports/simulator-benchmark", profile + "-" + UUID.randomUUID())).toAbsolutePath();
		Files.createDirectories(logs);
		Path root = Files.createTempDirectory("BattleNext-simulator-smoke-");
		BattleNext_SimulatorStorage storage = new BattleNext_SimulatorStorage(root);
		Path runtime = storage.directory("runtime/session");
		storage.snapshotCommon(core.getParent().resolve("saves"), runtime.resolve("saves"));
		storage.configureGraphics(runtime.resolve("saves"));
		String token = UUID.randomUUID().toString();
		Path workerLog = logs.resolve("worker-" + token + ".log");
		Process process = null;
		BattleNext_SimulatorProtocol channel = null;
		JSONArray samples = new JSONArray();
		List<Double> rates = new ArrayList<>();
		double startupSeconds;
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			server.setSoTimeout(1000);
			List<String> command = BattleNext_SimulatorLaunch.javaCommand(core, logs);
			if (measurements > 0) {
				command.removeIf(Set.of("-XX:+UseG1GC", "-XX:+UseParallelGC", "-XX:+UseShenandoahGC", "-XX:+UseStringDeduplication", "-XX:-DontCompileHugeMethods")::contains);
				command.add("-Xlog:gc*:file=" + core.relativize(logs.resolve("gc-%p.log")).toString().replace('\\', '/') + ":time,level,tags");
			}
			command.addAll(vmOptions);
			command.addAll(List.of(
					"-Dcom.fs.starfarer.settings.paths.saves=" + runtime.resolve("saves"),
					"-Dcom.fs.starfarer.settings.paths.screenshots=" + runtime.resolve("screenshots"),
					"-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"),
					"-Dcom.fs.starfarer.settings.paths.logs=" + logs,
					"-cp", System.getProperty("java.class.path"), BattleNext_SimulatorWorker.class.getName(), Integer.toString(server.getLocalPort()), token));
			long launching = System.nanoTime();
			process = BattleNext_SimulatorLaunch.start(command, core, workerLog);
			long deadline = System.nanoTime() + 180000000000L;
			Socket socket = null;
			while (socket == null) {
				if (!process.isAlive() || System.nanoTime() > deadline) throw new IOException("Smoke worker startup failed; see " + logs);
				try {
					socket = server.accept();
				} catch (SocketTimeoutException exception) {
					continue;
				}
			}
			socket.setSoTimeout(1000);
			channel = new BattleNext_SimulatorProtocol(socket, token);
			JSONObject ready = awaitReady(channel, process, deadline);
			JSONObject display = ready.getJSONObject("payload").getJSONObject("display");
			if (display.getInt("width") != 800 || display.getInt("height") != 600) {
				throw new IOException("Worker display must use the default 800x600 resolution");
			}
			startupSeconds = (System.nanoTime() - launching) / 1000000000d;
			double activeFps = ready.getJSONObject("payload").getDouble("active_fps");
			long idleStarted = System.nanoTime();
			long cpuStarted = process.info().totalCpuDuration().orElseThrow().toNanos();
			Thread.sleep(3000L);
			double idleElapsed = (System.nanoTime() - idleStarted) / 1000000000d;
			double idleCpu = (process.info().totalCpuDuration().orElseThrow().toNanos() - cpuStarted) / 1000000000d;
			System.out.println("Idle worker: fps=5, wall=" + idleElapsed + "s, cpu=" + idleCpu + "s, oneCorePercent=" + idleCpu / idleElapsed * 100d);
			List<BattleNext_SimulationConfig.Strategy> strategies = measurements == 0 ? Arrays.asList(BattleNext_SimulationConfig.Strategy.values())
					: List.of(BattleNext_SimulationConfig.Strategy.AGGRESSIVE);
			for (BattleNext_SimulationConfig.Strategy strategy : strategies) {
				for (int iteration = 0; iteration < (measurements == 0 ? 2 : measurements + 1); iteration++) {
					BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(strategy, 12000f, 12000f, 500f, 3600f, 120f, 32, true, false);
					List<String> wolves = List.of("wolf_CS", "wolf_CS", "wolf_CS", "wolf_CS");
					BattleNext_SimulationRequest request = measurements == 0 && iteration == 1 ? matchRequest(core, config)
							: BattleNext_SimulationRequest.fromVariants(wolves, wolves, config);
					channel.send("REQUEST", request.toJson());
					JSONObject preparing = await(channel, process, "STATUS", System.nanoTime() + 30000000000L).getJSONObject("payload");
					if (!preparing.getString("state").equals("PREPARING") || preparing.getDouble("fps") != activeFps) {
						throw new IOException("Smoke preparation must restore the active frame rate");
					}
					JSONObject running = await(channel, process, "STATUS", System.nanoTime() + 30000000000L).getJSONObject("payload");
					if (!running.getString("state").equals("RUNNING") || running.getDouble("fps") != activeFps) {
						throw new IOException("Smoke simulation must retain the active frame rate");
					}
					JSONObject message = await(channel, process, "RESULT", System.nanoTime() + 180000000000L);
					BattleNext_SimulationResult result = BattleNext_SimulationResult.fromJson(message.getJSONObject("payload"));
					Files.writeString(logs.resolve(strategy + "-" + request.kind() + "-" + request.id() + ".json"),
							new JSONObject().put("request", request.toJson()).put("result", result.toJson()).toString(), StandardCharsets.UTF_8);
					System.out.println(strategy + " " + request.kind() + ": " + result.outcome() + ", simulated=" + result.simulationSeconds()
							+ "s, elapsed=" + result.executionNanos() / 1000000000d + "s, steps=" + result.steps()
							+ ", peakHeap=" + result.peakHeapBytes());
					if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) throw new IOException("Smoke simulation did not complete: " + result.diagnostic());
					if (result.report().getPlayerMembers().size() != wolves.size() || result.report().getEnemyMembers().size() != wolves.size()) throw new IOException("Smoke report member count differs from the constructed input");
					String expectedHull = new JSONObject(Files.readString(core.resolve("data/variants/wolf/wolf_CS.variant"), StandardCharsets.UTF_8)).getString("hullId");
					var report = result.report();
					for (var members : List.of(report.getPlayerMembers(), report.getEnemyMembers())) {
						for (var member : members) {
							if (!expectedHull.equals(member.getHullId())) throw new IOException("Smoke report hull identity differs from the constructed variant: " + member.getHullId());
						}
					}
					if (measurements == 0 || iteration > 0) {
						samples.put(result.toJson());
						rates.add(result.simulationSeconds() / (result.executionNanos() / 1000000000d));
					}
					awaitReady(channel, process, System.nanoTime() + 30000000000L);
				}
			}
			for (String scenario : List.of("unknown", "budget", "cancel")) {
				BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(BattleNext_SimulationConfig.Strategy.AGGRESSIVE,
						12000f, 12000f, 500f, scenario.equals("budget") ? 1f : 3600f, 120f, 32, true, false);
				BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromVariants(List.of(scenario.equals("unknown") ? "BattleNext_unknown_smoke_variant" : "wolf_CS"), List.of("wolf_CS"), config);
				channel.send("REQUEST", request.toJson());
				if (scenario.equals("cancel")) {
					await(channel, process, "STATUS", System.nanoTime() + 30000000000L);
					channel.send("CANCEL", new JSONObject().put("id", request.id()));
				}
				BattleNext_SimulationResult result = BattleNext_SimulationResult.fromJson(await(channel, process, "RESULT",
						System.nanoTime() + 150000000000L).getJSONObject("payload"));
				BattleNext_SimulationResult.Outcome expected = switch (scenario) {
					case "unknown" -> BattleNext_SimulationResult.Outcome.FAILED;
					case "budget" -> BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED;
					default -> BattleNext_SimulationResult.Outcome.CANCELLED;
				};
				if (result.outcome() != expected) throw new IOException("Smoke terminal outcome: " + scenario + " expected " + expected + ", actual " + result.outcome());
				System.out.println(scenario + ": " + result.outcome());
				awaitReady(channel, process, System.nanoTime() + 30000000000L);
			}
			if (measurements == 0) {
				BattleNext_SimulationRequest matchInput = matchRequest(core, BattleNext_SimulationConfig.defaults());
				BattleNext_TournamentModel model = new BattleNext_TournamentModel("smoke_cup", "Smoke Cup",
						BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 3);
				model.setFleetIds(List.of("player", "enemy", "third"));
				JSONObject fleets = new JSONObject().put("player", matchInput.source().getJSONObject("player"))
						.put("enemy", matchInput.source().getJSONObject("enemy"));
				JSONObject third = new JSONObject(matchInput.source().getJSONObject("enemy").toString());
				third.getJSONObject("definition").put("id", "third").put("name", "Third fleet");
				fleets.put("third", third);
				BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromJson(new JSONObject().put("id", UUID.randomUUID().toString())
						.put("kind", "TOURNAMENT").put("config", BattleNext_SimulationConfig.defaults().toJson())
						.put("source", new JSONObject().put("tournament", model.toJson()).put("fleets", fleets)
								.put("sourceType", BattleNext_LoadSource.CREATED.name()).put("sourcePath", "smoke/tournament.json")));
				BattleNext_TournamentSnapshot snapshot = BattleNext_TournamentSnapshot.create(request);
				int rounds = 0;
				while (!snapshot.getTournament().isCompleted()) {
					BattleNext_SimulationRequest round = snapshot.prepareRound();
					channel.send("REQUEST", round.toJson());
					BattleNext_SimulationResult result = BattleNext_SimulationResult.fromJson(await(channel, process, "RESULT",
							System.nanoTime() + 180000000000L).getJSONObject("payload"));
					if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) throw new IOException("Tournament smoke round failed: " + result.diagnostic());
					snapshot.record(result);
					BattleNext_TournamentSnapshot checkpoint = snapshot.copy();
					if (checkpoint.getVersion() != snapshot.getVersion()) throw new IOException("Tournament smoke checkpoint version mismatch");
					Files.writeString(logs.resolve("TOURNAMENT-" + snapshot.getId() + ".json"), snapshot.toJson().toString(), StandardCharsets.UTF_8);
					System.out.println("Tournament round " + (++rounds) + ": " + result.outcome() + ", simulated=" + result.simulationSeconds()
							+ "s, elapsed=" + result.executionNanos() / 1000000000d + "s");
					awaitReady(channel, process, System.nanoTime() + 30000000000L);
				}
				snapshot.transition(BattleNext_SimulationTask.State.FINISHED, "");
				Files.writeString(logs.resolve("TOURNAMENT-" + snapshot.getId() + ".json"), snapshot.toJson().toString(), StandardCharsets.UTF_8);
				BattleNext_SimulationResult completed = BattleNext_SimulationResult.fromJson(snapshot.result().toJson());
				System.out.println("Tournament completed: rounds=" + rounds + ", champion=" + snapshot.getTournament().getChampionFleetId()
						+ ", simulated=" + completed.simulationSeconds() + "s, elapsed=" + completed.executionNanos() / 1000000000d + "s");
			}
		} finally {
			if (channel != null) {
				try {
					channel.send("SHUTDOWN", new JSONObject());
				} catch (Exception exception) {
					System.out.println("Smoke shutdown channel: " + exception.getMessage());
				}
				channel.close();
			}
			if (process != null && process.isAlive() && !process.waitFor(5L, TimeUnit.SECONDS)) {
				process.destroy();
				if (!process.waitFor(5L, TimeUnit.SECONDS)) {
					process.destroyForcibly().waitFor();
				}
			}
			storage.cleanup(runtime);
			Files.delete(root.resolve("runtime"));
			Files.delete(root);
			System.out.println("Smoke runtime cleaned; diagnostics: " + logs);
		}
		Pattern header = Pattern.compile("^\\d+\\s+\\[[^\\]]*\\]\\s+(\\S+)\\s+");
		List<String> lines = Files.readAllLines(workerLog, StandardCharsets.UTF_8);
		int events = 0;
		for (String line : lines) {
			var match = header.matcher(line);
			if (!match.find()) continue;
			if (!Level.toLevel(match.group(1)).isGreaterOrEqual(Level.WARN)) throw new IOException("Worker wrote a log below WARN: " + line);
			events++;
		}
		if (lines.stream().noneMatch(line -> line.contains("simulator.task.prepare.failed"))) throw new IOException("Worker must retain task failure diagnostics");
		System.out.println("Worker WARN+ log verified: events=" + events + ", bytes=" + Files.size(workerLog));
		rates.sort(Double::compareTo);
		JSONObject summary = new JSONObject().put("profile", profile).put("vm_options", new JSONArray(vmOptions))
				.put("startup_seconds", startupSeconds).put("samples", samples).put("diagnostics", logs.toString());
		if (measurements > 0 && !rates.isEmpty()) {
			double median = rates.get(rates.size() / 2);
			summary.put("median_speed", median).put("min_speed", rates.get(0)).put("max_speed", rates.get(rates.size() - 1));
			System.out.println("Benchmark " + profile + ": samples=" + rates.size() + ", median=" + median + "x, startup=" + startupSeconds + "s");
		}
		return summary;
	}

	private static BattleNext_SimulationRequest matchRequest(Path core, BattleNext_SimulationConfig config) throws Exception {

		JSONObject source = new JSONObject();
		for (String side : List.of("player", "enemy")) {
			List<BattleNext_MemberModel> members = new ArrayList<>();
			JSONObject variants = new JSONObject();
			for (int index = 0; index < 4; index++) {
				members.add(new BattleNext_MemberModel("smoke_local_wolf", "Smoke " + side + " " + index, "aggressive",
						List.of("helmsmanship", "helmsmanship", "combat_endurance"), "", "", List.of()));
				variants.put(Integer.toString(index), new JSONObject(Files.readString(core.resolve("data/variants/wolf/wolf_CS.variant"),
						StandardCharsets.UTF_8)).put("variantId", "smoke_local_wolf"));
			}
			BattleNext_FleetModel fleet = new BattleNext_FleetModel(side, "Smoke " + side, List.of("crew_training"),
					List.of(side.equals("player") ? "full_assault" : "default_ai"), members);
			source.put(side, new JSONObject().put("definition", fleet.toJson()).put("variants", variants)
					.put("folderName", side).put("sourceType", BattleNext_LoadSource.CREATED.name()).put("sourcePath", "smoke/" + side));
		}
		BattleNext_Match match = new BattleNext_Match("smoke_match", "player", "enemy", -1, 1.5f);
		source.put("match", match.toJson()).put("actualMap", BattleNext_MissionBattleMap.resolve(-1))
				.put("sourceType", BattleNext_LoadSource.CREATED.name()).put("sourcePath", "smoke/quick_battle.csv");
		return BattleNext_SimulationRequest.fromJson(new JSONObject().put("id", UUID.randomUUID().toString())
				.put("kind", BattleNext_SimulationRequest.Kind.MATCH.name()).put("source", source).put("config", config.toJson()));
	}

	private static JSONObject awaitReady(BattleNext_SimulatorProtocol channel, Process process, long deadline) throws Exception {
		JSONObject message = await(channel, process, "READY", deadline);
		if (message.getJSONObject("payload").getDouble("fps") != 5d) throw new IOException("Smoke idle frame rate must be 5 FPS");
		JSONObject graphics = message.getJSONObject("payload").getJSONObject("graphics");
		for (String key : List.of("lights", "bloom", "normal", "auto_normals", "material", "surface", "distortion", "post_process", "preload_all_maps",
				"box_shader", "box_distortion", "box_debug", "box_opencl")) {
			if (graphics.getBoolean(key)) throw new IOException("Worker graphics option remains enabled: " + key);
		}
		JSONObject display = message.getJSONObject("payload").getJSONObject("display");
		if (!display.getBoolean("mouse_isolated")) throw new IOException("Worker mouse must be isolated");
		if (display.getInt("aa_samples") != 0 || display.getInt("gl_samples") != 0) throw new IOException("Worker actual antialiasing must be zero");
		for (String key : List.of("shield_jitter", "version_overlay", "planet_backgrounds")) {
			if (display.getBoolean(key)) throw new IOException("Worker vanilla display option remains enabled: " + key);
		}
		System.out.println("Worker display: " + display);
		System.out.println("Worker graphics: " + graphics);
		return message;
	}

	private static JSONObject await(BattleNext_SimulatorProtocol channel, Process process, String type, long deadline) throws Exception {
		while (true) {
			if (!process.isAlive() || System.nanoTime() > deadline) throw new IOException("Smoke worker exited or timed out while awaiting " + type);
			try {
				JSONObject message = channel.receive();
				if (type.equals(message.getString("type"))) return message;
				System.out.println("Worker frame: " + message.getString("type") + " " + message.getJSONObject("payload"));
			} catch (SocketTimeoutException exception) {
				continue;
			}
		}
	}
}
