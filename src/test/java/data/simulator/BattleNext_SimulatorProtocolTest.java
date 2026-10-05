package data.simulator;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorProtocolTest {

	@Test
	void unicodePercentAndConsecutiveFramesRoundTrip() throws Exception {
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				BattleNext_SimulatorProtocol sender = new BattleNext_SimulatorProtocol(client, "session");
				BattleNext_SimulatorProtocol receiver = new BattleNext_SimulatorProtocol(listener.accept(), "session")) {
			for (String value : new String[]{"中文 50% 😀", "Another task"}) {
				sender.send("REQUEST", new JSONObject().put("name", value));
				assertEquals(value, receiver.receive().getJSONObject("payload").getString("name"));
			}
		}
	}

	@Test
	void partialHeadersAndPayloadsSurviveSocketTimeouts() throws Exception {
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol receiver = new BattleNext_SimulatorProtocol(socket, "session")) {
			socket.setSoTimeout(20);
			byte[] payload = new JSONObject().put("version", 1).put("token", "session").put("type", "READY")
					.put("payload", new JSONObject().put("name", "中文 50%")).toString().getBytes(StandardCharsets.UTF_8);
			byte[] header = ByteBuffer.allocate(4).putInt(payload.length).array();
			client.getOutputStream().write(header, 0, 2);
			assertThrows(SocketTimeoutException.class, receiver::receive);
			client.getOutputStream().write(header, 2, 2);
			client.getOutputStream().write(payload, 0, 3);
			assertThrows(SocketTimeoutException.class, receiver::receive);
			client.getOutputStream().write(payload, 3, payload.length - 3);
			assertEquals("中文 50%", receiver.receive().getJSONObject("payload").getString("name"));
		}
	}

	@Test
	void wrongSessionAndInvalidFrameSizeAreRejected() throws Exception {
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				BattleNext_SimulatorProtocol sender = new BattleNext_SimulatorProtocol(client, "other");
				BattleNext_SimulatorProtocol receiver = new BattleNext_SimulatorProtocol(listener.accept(), "session")) {
			sender.send("READY", new JSONObject());
			assertThrows(IOException.class, receiver::receive);
			client.getOutputStream().write(ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array());
			assertThrows(IOException.class, receiver::receive);
		}
	}
}
