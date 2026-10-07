package data.simulator;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorBudgetServiceTest extends BattleNext_SimulatorServiceFixture {

	@Test
	void executionWatchdogCompletesADrawWithoutStatisticsAndRetainsTheTaskInMemory() throws Exception {
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(5000);
			OwnedProcess worker = new OwnedProcess();
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), worker);
			var config = new BattleNext_SimulationConfig(BattleNext_SimulationConfig.Strategy.AGGRESSIVE,
					12000f, 12000f, 500f, 3600f, 0.01f, 32, true, false);
			var task = service.submit(BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), config));
			assertEquals(task.getRequest().id(), peer.receive().getJSONObject("payload").getString("id"));
			peer.send("STATUS", new JSONObject().put("id", task.getRequest().id()).put("state", "RUNNING"));
			long deadline = System.nanoTime() + 12000000000L;
			while (readField("active") != null && System.nanoTime() < deadline) {
				Thread.sleep(10L);
			}
			assertEquals(BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED, task.getResult().outcome());
			assertEquals("Worker exceeded execution deadline", task.getResult().diagnostic());
			assertTrue(task.getResult().reportJson().isEmpty());
			assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
			assertSame(task, BattleNext_SimulatorService.task(task.getRequest().id()));
			assertFalse(worker.isAlive());
			assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + task.getRequest().id() + ".json")));
		}
	}

}
