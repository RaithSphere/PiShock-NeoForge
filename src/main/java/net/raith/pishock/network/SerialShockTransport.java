package net.raith.pishock.network;

import com.fazecast.jSerialComm.SerialPort;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.raith.pishock.PiShock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class SerialShockTransport {
    private static final int PISHOCK_VENDOR_ID = 0x1A86;
    private static final int PISHOCK_NEXT_PRODUCT_ID = 0x7523;
    private static final int PISHOCK_LITE_PRODUCT_ID = 0x55D4;
    private static final int DATA_BITS = 8;
    private static final int READ_BUFFER_SIZE = 256;
    private static final int WRITE_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 2500;
    private static final String TERMINAL_INFO_PREFIX = "TERMINALINFO:";
    private static final String LINE_ENDING = "\n";
    private static final String CMD_INFO = "info";
    private static final String CMD_OPERATE = "operate";

    private SerialShockTransport() {
    }

    static boolean send(String configuredPortName, int baudRate, int shockerId, PiShock.PiShockMode mode, int intensity, int durationMs) {
        JsonObject command = buildOperateCommand(shockerId, mode, intensity, durationMs);
        return sendCommand(configuredPortName, baudRate, command.toString());
    }

    static boolean check(String configuredPortName, int baudRate) {
        return info(configuredPortName, baudRate) != null;
    }

    static Info info(String configuredPortName, int baudRate) {
        JsonObject command = buildInfoCommand();
        String response = queryCommand(configuredPortName, baudRate, command.toString());
        if (response == null || response.isBlank()) {
            return null;
        }
        return parseInfo(response);
    }

    static List<PortInfo> listPorts() {
        List<PortInfo> ports = new ArrayList<>();
        for (SerialPort port : SerialPort.getCommPorts()) {
            ports.add(new PortInfo(
                    port.getSystemPortName(),
                    port.getDescriptivePortName(),
                    port.getVendorID(),
                    port.getProductID(),
                    isPiShockPort(port)
            ));
        }
        return ports;
    }

    static JsonObject buildInfoCommand() {
        JsonObject command = new JsonObject();
        command.addProperty("cmd", CMD_INFO);
        return command;
    }

    static JsonObject buildOperateCommand(int shockerId, PiShock.PiShockMode mode, int intensity, int durationMs) {
        JsonObject value = new JsonObject();
        value.addProperty("id", shockerId);
        value.addProperty("op", toSerialOperation(mode));
        value.addProperty("duration", durationMs);
        if (mode != PiShock.PiShockMode.Beep) {
            value.addProperty("intensity", intensity);
        }

        JsonObject command = new JsonObject();
        command.addProperty("cmd", CMD_OPERATE);
        command.add("value", value);
        return command;
    }

    private static boolean sendCommand(String configuredPortName, int baudRate, String json) {
        SerialPort port = openPort(configuredPortName, baudRate, SerialPort.TIMEOUT_WRITE_BLOCKING);
        if (port == null) {
            return false;
        }

        try {
            return writeJsonLine(port, json);
        } finally {
            closeQuietly(port);
        }
    }

    private static String queryCommand(String configuredPortName, int baudRate, String json) {
        SerialPort port = openPort(
                configuredPortName,
                baudRate,
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING
        );
        if (port == null) {
            return null;
        }

        try {
            // Old terminal output can sit in the buffer after connect; don't parse that as today's reply.
            port.flushIOBuffers();
            return writeJsonLine(port, json) ? readUntilTerminalInfo(port) : null;
        } finally {
            closeQuietly(port);
        }
    }

    private static SerialPort openPort(String configuredPortName, int baudRate, int timeoutMode) {
        SerialPort port = findPort(configuredPortName);
        if (port == null) {
            return null;
        }

        configurePort(port, baudRate, timeoutMode);
        return port.openPort() ? port : null;
    }

    private static void configurePort(SerialPort port, int baudRate, int timeoutMode) {
        port.setComPortParameters(baudRate, DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(timeoutMode, READ_TIMEOUT_MS, WRITE_TIMEOUT_MS);
    }

    private static boolean writeJsonLine(SerialPort port, String json) {
        byte[] payload = (json + LINE_ENDING).getBytes(StandardCharsets.UTF_8);
        return port.writeBytes(payload, payload.length) == payload.length;
    }

    private static String readUntilTerminalInfo(SerialPort port) {
        long deadline = System.currentTimeMillis() + READ_TIMEOUT_MS;
        StringBuilder response = new StringBuilder();
        byte[] buffer = new byte[READ_BUFFER_SIZE];

        while (System.currentTimeMillis() < deadline) {
            int bytesRead = port.readBytes(buffer, buffer.length);
            if (bytesRead <= 0) {
                continue;
            }

            response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            int terminalInfoIndex = response.indexOf(TERMINAL_INFO_PREFIX);
            if (terminalInfoIndex >= 0 && response.indexOf(LINE_ENDING, terminalInfoIndex) > terminalInfoIndex) {
                break;
            }
        }

        return response.toString();
    }

    private static void closeQuietly(SerialPort port) {
        if (port.isOpen()) {
            port.closePort();
        }
    }

    private static SerialPort findPort(String configuredPortName) {
        String trimmedPortName = configuredPortName == null ? "" : configuredPortName.trim();
        SerialPort[] ports = SerialPort.getCommPorts();
        if (!trimmedPortName.isEmpty()) {
            for (SerialPort port : ports) {
                if (trimmedPortName.equalsIgnoreCase(port.getSystemPortName())
                        || trimmedPortName.equalsIgnoreCase(port.getSystemPortPath())) {
                    return port;
                }
            }
            return SerialPort.getCommPort(trimmedPortName);
        }

        // Auto-detect only when there is exactly one matching hub.
        List<SerialPort> matches = new ArrayList<>();
        for (SerialPort port : ports) {
            if (isPiShockPort(port)) {
                matches.add(port);
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean isPiShockPort(SerialPort port) {
        return port.getVendorID() == PISHOCK_VENDOR_ID
                && (port.getProductID() == PISHOCK_NEXT_PRODUCT_ID || port.getProductID() == PISHOCK_LITE_PRODUCT_ID);
    }

    private static String toSerialOperation(PiShock.PiShockMode mode) {
        return switch (mode) {
            case Shock -> "shock";
            case Vibrate -> "vibrate";
            case Beep -> "beep";
        };
    }

    static Info parseInfo(String response) {
        String json = extractTerminalInfoJson(response);
        if (json == null) {
            return null;
        }

        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String version = getString(object, "version");
            Integer type = firstPresentInt(object, "type", "Type");
            Integer clientId = firstPresentInt(object, "clientId", "clientID", "HubId", "hubId");
            return new Info(version, type, clientId, parseShockers(object));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractTerminalInfoJson(String response) {
        int prefixIndex = response.indexOf(TERMINAL_INFO_PREFIX);
        if (prefixIndex < 0) {
            return null;
        }

        String tail = response.substring(prefixIndex + TERMINAL_INFO_PREFIX.length()).trim();
        int start = tail.indexOf('{');
        int end = tail.lastIndexOf('}');
        return start >= 0 && end > start ? tail.substring(start, end + 1) : null;
    }

    private static List<ShockerInfo> parseShockers(JsonObject object) {
        List<ShockerInfo> shockers = new ArrayList<>();
        if (!object.has("shockers") || !object.get("shockers").isJsonArray()) {
            return shockers;
        }

        for (var element : object.getAsJsonArray("shockers")) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject shocker = element.getAsJsonObject();
            Integer id = firstPresentInt(shocker, "id", "shockerId", "ShockerId");
            if (id != null) {
                shockers.add(new ShockerInfo(id, getBoolean(shocker, "paused")));
            }
        }
        return shockers;
    }

    private static Integer firstPresentInt(JsonObject object, String... keys) {
        for (String key : keys) {
            Integer value = getInt(object, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer getInt(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean getBoolean(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return false;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    record Info(String firmwareVersion, Integer type, Integer clientId, List<ShockerInfo> shockers) {
        Integer firstShockerId() {
            return shockers.isEmpty() ? null : shockers.get(0).id();
        }

        String shockerSummary() {
            return shockers.stream()
                    .map(shocker -> {
                        StringBuilder summary = new StringBuilder(Integer.toString(shocker.id()));
                        if (shocker.paused()) {
                            summary.append(" paused");
                        }
                        return summary.toString();
                    })
                    .collect(Collectors.joining(", "));
        }
    }

    record ShockerInfo(int id, boolean paused) {
    }

    record PortInfo(String systemName, String description, int vendorId, int productId, boolean piShock) {
        String label() {
            StringBuilder label = new StringBuilder(systemName);
            if (description != null && !description.isBlank() && !description.equals(systemName)) {
                label.append(" - ").append(description);
            }
            if (piShock) {
                label.append(" (PiShock)");
            }
            return label.toString();
        }
    }
}
