package net.raith.pishock.network;

import com.fazecast.jSerialComm.SerialPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SerialShockTransportTest {
    @Test
    void jSerialCommIsAvailableOnTheTestClasspath() {
        assertNotNull(SerialPort.class);
    }

    @Test
    void listPortsDoesNotThrowWhenNoHubIsAttached() {
        List<SerialShockTransport.PortInfo> ports = assertDoesNotThrow(SerialShockTransport::listPorts);
        assertNotNull(ports);
    }
}
