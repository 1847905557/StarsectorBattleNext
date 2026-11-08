package data.test.fixture;

import com.sun.management.ThreadMXBean;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class BattleNext_TestPerformance {

	private BattleNext_TestPerformance() {
	}

	@FunctionalInterface
	public interface Work {
		void run() throws Exception;
	}

	public static JSONObject measure(int count, Work work) throws Exception {
		ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long thread = Thread.currentThread().getId();
		JSONArray samples = new JSONArray();
		double[] times = new double[count];
		for (int index = 0; index < count; index++) {
			long before = allocations.getThreadAllocatedBytes(thread);
			long start = System.nanoTime();
			work.run();
			times[index] = (System.nanoTime() - start) / 1_000_000d;
			long allocated = allocations.getThreadAllocatedBytes(thread) - before;
			samples.put(new JSONObject().put("milliseconds", times[index]).put("allocated_bytes", allocated));
		}
		Arrays.sort(times);
		return new JSONObject().put("p95_ms", times[(int) Math.ceil(count * 0.95d) - 1]).put("samples", samples);
	}

	public static void write(String name, JSONObject measurements) throws Exception {
		Path directory = Path.of("build/reports/performance");
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(name + ".json"), measurements.toString(2), StandardCharsets.UTF_8);
	}
}
