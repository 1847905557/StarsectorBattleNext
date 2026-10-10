package data.simulator;

import data.missions.BattleNext_MissionBattleMap;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class BattleNext_SimulatorSmoke {

	public static void main(String[] arguments) throws Exception {
		BattleNext_DeploymentOpeningSmoke.run();
		runSession("smoke", List.of(), 0);
	}

	static JSONObject runSession(String profile, List<String> vmOptions, int measurements) throws Exception {

		Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
		Path logs = Path.of("tournament/_simulations", UUID.randomUUID().toString()).toAbsolutePath();
		Files.createDirectories(logs);
		String token = UUID.randomUUID().toString();
		StringBuilder workerOutput = new StringBuilder();
		Thread outputReader = null;
		Process process = null;
		BattleNext_SimulatorProtocol channel = null;
		JSONArray samples = new JSONArray();
		List<Double> rates = new ArrayList<>();
		double startupSeconds;
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			server.setSoTimeout(1000);
			List<String> command = BattleNext_SimulatorLaunch.javaCommand(core);
			command.add("-XX:ErrorFile=" + logs.resolve("hs_err_pid%p.log"));
			if (measurements > 0) {
				command.removeIf(Set.of("-XX:+UseG1GC", "-XX:+UseParallelGC", "-XX:+UseShenandoahGC", "-XX:+UseStringDeduplication", "-XX:-DontCompileHugeMethods")::contains);
				command.add("-Xlog:gc*:file=" + core.relativize(logs.resolve("gc-%p.log")).toString().replace('\\', '/') + ":time,level,tags");
			}
			command.addAll(vmOptions);
			command.addAll(List.of(
					"-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"),
					"-Dcom.fs.starfarer.settings.paths.logs=" + logs,
					"-cp", System.getProperty("java.class.path"), BattleNext_SimulatorWorker.class.getName(), Integer.toString(server.getLocalPort()), token));
			long launching = System.nanoTime();
			process = BattleNext_SimulatorLaunch.start(command, core);
			Process worker = process;
			outputReader = new Thread(() -> {
				try (var reader = new InputStreamReader(worker.getInputStream(), StandardCharsets.UTF_8)) {
					char[] buffer = new char[4096];
					int count;
					while ((count = reader.read(buffer)) != -1) {
						synchronized (workerOutput) {
							workerOutput.append(buffer, 0, count);
						}
					}
				} catch (IOException exception) {
					throw new IllegalStateException("Unable to consume smoke worker output", exception);
				}
			}, "Simulator smoke output");
			outputReader.start();
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
				for (int iteration = 0; iteration < (measurements == 0 ? 5 : measurements + 1); iteration++) {
					int deploymentPointLimit = measurements == 0 && iteration >= 2 ? 10 : 0;
					BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(strategy, 12000f, 12000f, 500f, 3600f, 120f, 32, true, false, deploymentPointLimit);
					List<String> wolves = List.of("wolf_CS", "wolf_CS", "wolf_CS", "wolf_CS");
					BattleNext_SimulationRequest request = measurements == 0 && (iteration % 2 == 1 || iteration == 4) ? matchRequest(core, config)
							: BattleNext_SimulationRequest.fromVariants(wolves, wolves, config);
					if (measurements == 0 && iteration == 4) {
						JSONObject source = request.source();
						source.getJSONObject("player").getJSONObject("definition").put("tags", new JSONArray());
						request = new BattleNext_SimulationRequest(request.id(), request.kind(), source.toString(), config);
					}
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
					System.out.println(strategy + " " + request.kind() + " DP=" + deploymentPointLimit + ": " + result.outcome() + ", simulated=" + result.simulationSeconds()
							+ "s, elapsed=" + result.executionNanos() / 1000000000d + "s, steps=" + result.steps()
							+ ", peakHeap=" + result.peakHeapBytes());
					if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) throw new IOException("Smoke simulation did not complete: " + result.diagnostic());
					if (result.report().getPlayerMembers().size() != wolves.size() || result.report().getEnemyMembers().size() != wolves.size()) throw new IOException("Smoke report member count differs from the constructed input");
					String expectedHull = new JSONObject(Files.readString(core.resolve("data/variants/wolf/wolf_CS.variant"), StandardCharsets.UTF_8)).getString("hullId");
					var report = result.report();
					if (result.simulationSeconds() <= 1f) throw new IOException("Battle settled during deployment");
					if (deploymentPointLimit > 0 && report.getWinner().equals("draw")) throw new IOException("Limited deployment must resolve a winner");
					if (deploymentPointLimit > 0 && request.kind() == BattleNext_SimulationRequest.Kind.VARIANTS) {
						var losers = report.getWinner().equals("player") ? report.getEnemyMembers() : report.getPlayerMembers();
						if (losers.stream().filter(member -> member.getStatus().equals("destroyed")).count() <= 2) throw new IOException("Losses must include vanilla reinforcements beyond the initial two Wolves");
					}
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
			if (measurements == 0) {
				for (BattleNext_SimulationConfig.Strategy strategy : strategies) {
					BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(strategy, 12000f, 12000f, 500f, 1f, 120f, 32, true, false, 10);
					var request = BattleNext_SimulationRequest.fromVariants(Collections.nCopies(4, "wolf_CS"), Collections.nCopies(4, "wolf_CS"), config);
					channel.send("REQUEST", request.toJson());
					var result = BattleNext_SimulationResult.fromJson(await(channel, process, "RESULT", System.nanoTime() + 180000000000L).getJSONObject("payload"));
					if (result.outcome() != BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED) throw new IOException("Initial deployment probe must exhaust its one-second budget: " + result.diagnostic());
					for (var members : List.of(result.report().getPlayerMembers(), result.report().getEnemyMembers())) {
						long alive = members.stream().filter(member -> member.getStatus().equals("alive")).count();
						long undeployed = members.stream().filter(member -> member.getStatus().equals("undeployed")).count();
						if (alive < 1 || alive > 2 || alive + undeployed != 4) throw new IOException("10 DP initial deployment must retain reserves for both sides: " + alive + "/" + undeployed);
					}
					System.out.println(strategy + " initial 10 DP deployment and reserves verified");
					awaitReady(channel, process, System.nanoTime() + 30000000000L);
				}
			}
			for (String scenario : List.of("unknown", "budget", "execution_budget", "cancel", "empty_deployment")) {
				BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(BattleNext_SimulationConfig.Strategy.AGGRESSIVE,
						12000f, 12000f, 500f, scenario.equals("budget") ? 1f : scenario.equals("empty_deployment") ? 20f : 3600f, scenario.equals("execution_budget") ? 0.01f : 120f, 32, true, false, scenario.equals("empty_deployment") ? 1 : 0);
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
					case "budget", "execution_budget", "empty_deployment" -> BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED;
					default -> BattleNext_SimulationResult.Outcome.CANCELLED;
				};
				if (result.outcome() != expected) throw new IOException("Smoke terminal outcome: " + scenario + " expected " + expected + ", actual " + result.outcome());
				if (expected == BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED && !result.report().getWinner().equals("draw")) throw new IOException("Budget exhaustion must freeze a draw report");
				if (scenario.equals("empty_deployment")) {
					for (var members : List.of(result.report().getPlayerMembers(), result.report().getEnemyMembers())) {
						if (!members.get(0).getStatus().equals("undeployed")) throw new IOException("One DP must leave both Wolves undeployed");
					}
				}
				System.out.println(scenario + ": " + result.outcome());
				awaitReady(channel, process, System.nanoTime() + 30000000000L);
			}
			if (measurements == 0) {
				BattleNext_SimulationRequest matchInput = matchRequest(core, BattleNext_SimulationConfig.defaults());
				BattleNext_TournamentModel model = new BattleNext_TournamentModel("smoke_cup", "Smoke Cup",
						BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 7);
				List<String> slots = List.of("", "player", "", "enemy", "third", "", "");
				JSONObject fleets = new JSONObject().put("player", matchInput.source().getJSONObject("player"))
						.put("enemy", matchInput.source().getJSONObject("enemy"));
				JSONObject third = new JSONObject(matchInput.source().getJSONObject("enemy").toString());
				third.getJSONObject("definition").put("id", "third").put("name", "Third fleet");
				fleets.put("third", third);
				BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromJson(new JSONObject().put("id", UUID.randomUUID().toString())
						.put("kind", "TOURNAMENT").put("config", BattleNext_SimulationConfig.defaults().toJson())
						.put("source", new JSONObject().put("sourceTournamentId", model.getId()).put("name", model.getName()).put("fleetIds", new JSONArray(slots))));
				BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
				BattleNext_StartedTournament started = new BattleNext_StartedTournament(request.id(), model.getId(), BattleNext_StartedTournament.ExecutionKind.SIMULATION, slots);
				engine.initialize(model, started);
				int rounds = 0;
				float simulated = 0f;
				long elapsed = 0L;
				while (!started.isCompleted()) {
					BattleNext_Match match = started.getNextPendingMatch();
					JSONObject roundSource = new JSONObject(matchInput.source().toString());
					roundSource.put("player", fleets.getJSONObject(match.getPlayer())).put("enemy", fleets.getJSONObject(match.getEnemy()));
					roundSource.put("match", match.toJson()).put("actualMap", BattleNext_MissionBattleMap.resolve(model.getMapId()));
					BattleNext_SimulationRequest round = new BattleNext_SimulationRequest(UUID.randomUUID().toString(),
							BattleNext_SimulationRequest.Kind.MATCH, roundSource.toString(), request.config());
					channel.send("REQUEST", round.toJson());
					BattleNext_SimulationResult result = BattleNext_SimulationResult.fromJson(await(channel, process, "RESULT",
							System.nanoTime() + 180000000000L).getJSONObject("payload"));
					if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) throw new IOException("Tournament smoke round failed: " + result.diagnostic());
					String winner = result.report().getWinner().equals("player") ? match.getPlayer()
							: result.report().getWinner().equals("enemy") ? match.getEnemy() : "";
					engine.recordRound(model, started, match.getId(), winner, result.report().getId());
					simulated += result.simulationSeconds();
					elapsed += result.executionNanos();
					System.out.println("Tournament round " + (++rounds) + ": " + result.outcome());
					awaitReady(channel, process, System.nanoTime() + 30000000000L);
				}
				System.out.println("Tournament completed: rounds=" + rounds + ", champion=" + started.getChampionFleetId()
						+ ", simulated=" + simulated + "s, elapsed=" + elapsed / 1000000000d + "s");
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
			if (outputReader != null) {
				outputReader.join();
			}
			try (var paths = Files.walk(logs)) {
				if (paths.filter(Files::isRegularFile).anyMatch(path -> !path.toString().endsWith(".log"))) {
					throw new IOException("Worker session contains a non-log file: " + logs);
				}
			}
			System.out.println("Smoke log directory retained: " + logs);
		}
		Pattern header = Pattern.compile("^\\d+\\s+\\[[^\\]]*\\]\\s+(\\S+)\\s+");
		List<String> lines = workerOutput.toString().lines().toList();
		int events = 0;
		for (String line : lines) {
			var match = header.matcher(line);
			if (!match.find()) continue;
			if (!Level.toLevel(match.group(1)).isGreaterOrEqual(Level.WARN)) throw new IOException("Worker wrote a log below WARN: " + line);
			events++;
		}
		if (lines.stream().noneMatch(line -> line.contains("simulator.task.prepare.failed"))) throw new IOException("Worker must retain task failure diagnostics");
		System.out.println("Worker WARN+ output verified: events=" + events + ", characters=" + workerOutput.length());
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
