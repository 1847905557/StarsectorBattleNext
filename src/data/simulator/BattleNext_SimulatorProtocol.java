package data.simulator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

public class BattleNext_SimulatorProtocol implements AutoCloseable {

	private static final int VERSION = 1;
	private static final int MAX_BYTES = 32 * 1024 * 1024;
	private final Socket socket;
	private final InputStream input;
	private final OutputStream output;
	private final String token;
	private final byte[] header = new byte[4];
	private int headerRead;
	private byte[] payload;
	private int payloadRead;

	public BattleNext_SimulatorProtocol(Socket socket, String token) throws IOException {
		this.socket = socket;
		this.token = token;
		input = socket.getInputStream();
		output = socket.getOutputStream();
	}

	public synchronized void send(String type, JSONObject payload) throws IOException, JSONException {
		byte[] bytes = new JSONObject().put("version", VERSION).put("token", token).put("type", type).put("payload", payload)
				.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_BYTES) throw new IOException("Simulator frame exceeds the byte limit");
		output.write(ByteBuffer.allocate(4).putInt(bytes.length).array());
		output.write(bytes);
		output.flush();
	}

	public JSONObject receive() throws IOException, JSONException {

		while (headerRead < header.length) {
			int count = input.read(header, headerRead, header.length - headerRead);
			if (count < 0) throw new IOException("Simulator channel closed during frame header");
			headerRead += count;
		}
		if (payload == null) {
			int size = ByteBuffer.wrap(header).getInt();
			if (size < 1 || size > MAX_BYTES) throw new IOException("Invalid simulator frame size: " + size);
			payload = new byte[size];
		}
		while (payloadRead < payload.length) {
			int count = input.read(payload, payloadRead, payload.length - payloadRead);
			if (count < 0) throw new IOException("Simulator channel closed during frame payload");
			payloadRead += count;
		}
		JSONObject message = new JSONObject(new String(payload, StandardCharsets.UTF_8));
		headerRead = 0;
		payloadRead = 0;
		payload = null;
		if (message.getInt("version") != VERSION || !token.equals(message.getString("token"))) throw new IOException("Simulator protocol version or session token mismatch");
		message.getString("type");
		message.getJSONObject("payload");
		return message;
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}
}
