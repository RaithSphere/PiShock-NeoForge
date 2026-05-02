package net.raith.pishock.network;

import com.google.gson.JsonObject;
import net.raith.pishock.PiShock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SerialShockTransportTest {
    @Test
    void buildInfoCommandUsesExpectedWireShape() {
        JsonObject command = SerialShockTransport.buildInfoCommand();

        assertEquals("info", command.get("cmd").getAsString());
        assertEquals(1, command.size());
    }

    @Test
    void buildOperateCommandIncludesIntensityForShock() {
        JsonObject command = SerialShockTransport.buildOperateCommand(42, PiShock.PiShockMode.Shock, 30, 1200);
        JsonObject value = command.getAsJsonObject("value");

        assertEquals("operate", command.get("cmd").getAsString());
        assertEquals(42, value.get("id").getAsInt());
        assertEquals("shock", value.get("op").getAsString());
        assertEquals(30, value.get("intensity").getAsInt());
        assertEquals(1200, value.get("duration").getAsInt());
    }

    @Test
    void buildOperateCommandOmitsIntensityForBeep() {
        JsonObject command = SerialShockTransport.buildOperateCommand(42, PiShock.PiShockMode.Beep, 30, 1200);
        JsonObject value = command.getAsJsonObject("value");

        assertEquals("beep", value.get("op").getAsString());
        assertFalse(value.has("intensity"));
    }

    @Test
    void parseInfoReadsTerminalInfoPayload() {
        SerialShockTransport.Info info = SerialShockTransport.parseInfo("""
                boot noise
                TERMINALINFO:{"version":"1.2.3.4","type":3,"clientId":88,"shockers":[{"id":101},{"id":102,"paused":true}]}
                """);

        assertNotNull(info);
        assertEquals("1.2.3.4", info.firmwareVersion());
        assertEquals(3, info.type());
        assertEquals(88, info.clientId());
        assertEquals(101, info.firstShockerId());
        assertEquals("101, 102 paused", info.shockerSummary());
    }

    @Test
    void parseInfoAcceptsAlternateFieldNames() {
        SerialShockTransport.Info info = SerialShockTransport.parseInfo(
                "TERMINALINFO:{\"Type\":4,\"HubId\":12,\"shockers\":[{\"ShockerId\":55}]}\n"
        );

        assertNotNull(info);
        assertEquals(4, info.type());
        assertEquals(12, info.clientId());
        assertEquals(55, info.firstShockerId());
    }

    @Test
    void parseInfoReturnsNullForMissingTerminalInfo() {
        assertNull(SerialShockTransport.parseInfo("{\"version\":\"1.2.3\"}"));
    }

    @Test
    void portLabelMarksPiShockPorts() {
        SerialShockTransport.PortInfo port = new SerialShockTransport.PortInfo(
                "COM7",
                "USB Serial",
                0x1A86,
                0x7523,
                true
        );

        assertEquals("COM7 - USB Serial (PiShock)", port.label());
    }
}
