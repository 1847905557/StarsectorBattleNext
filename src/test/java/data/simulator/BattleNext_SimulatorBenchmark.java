package data.simulator;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_SimulatorBenchmark {

	public static void main(String[] arguments) throws Exception {

		Map<String, List<String>> profiles = new LinkedHashMap<>();
		profiles.put("g1", List.of("-XX:+UseG1GC"));
		profiles.put("parallel", List.of("-XX:+UseParallelGC"));
		profiles.put("shenandoah_compact", List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC",
				"-XX:ShenandoahGCMode=iu", "-XX:ShenandoahGCHeuristics=compact"));
		profiles.put("g1_dedup", List.of("-XX:+UseG1GC", "-XX:+UseStringDeduplication"));
		profiles.put("shenandoah_compact_dedup", List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC",
				"-XX:ShenandoahGCMode=iu", "-XX:ShenandoahGCHeuristics=compact", "-XX:+UseStringDeduplication"));
		profiles.put("g1_huge_methods", List.of("-XX:+UseG1GC", "-XX:-DontCompileHugeMethods"));
		JSONObject summaries = new JSONObject();
		boolean failed = false;
		for (var profile : profiles.entrySet()) {
			try {
				summaries.put(profile.getKey(), BattleNext_SimulatorSmoke.runSession(profile.getKey(), profile.getValue(), 5));
			} catch (Exception exception) {
				failed = true;
				summaries.put(profile.getKey(), new JSONObject().put("failure", exception.getClass().getName() + ": " + exception.getMessage()));
				System.out.println("Benchmark failed: " + profile.getKey() + ": " + exception);
			}
			System.out.println(summaries.toString(2));
		}
		if (failed) throw new IllegalStateException("Simulator benchmark contains failed profiles; inspect the process output");
	}
}
