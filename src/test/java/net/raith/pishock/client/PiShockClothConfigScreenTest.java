package net.raith.pishock.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PiShockClothConfigScreenTest {
    @Test
    void resolveShockerIdValueUsesApiFieldWhenOnlyApiChanged() {
        assertEquals("200", PiShockConfigLogic.resolveShockerIdValue("100", "200", "100"));
    }

    @Test
    void resolveShockerIdValueUsesSerialFieldWhenSerialChanged() {
        assertEquals("300", PiShockConfigLogic.resolveShockerIdValue("100", "100", "300"));
    }

    @Test
    void resolveShockerIdValueLetsSerialFieldWinWhenBothChanged() {
        assertEquals("300", PiShockConfigLogic.resolveShockerIdValue("100", "200", "300"));
    }

    @Test
    void resolveShockerIdValueTrimsValuesBeforeComparing() {
        assertEquals("200", PiShockConfigLogic.resolveShockerIdValue(" 100 ", " 200 ", "100"));
    }

    @Test
    void resolveShockerIdValuePreservesBlankAutoValue() {
        assertEquals("", PiShockConfigLogic.resolveShockerIdValue(null, " ", ""));
    }

    @Test
    void toHubShockerDisplayListGroupsShockersByHubAndRemovesDuplicates() {
        List<String> display = PiShockConfigLogic.toHubShockerDisplayList(List.of(
                new PiShockConfigLogic.DevicePair(10, 1),
                new PiShockConfigLogic.DevicePair(10, 2),
                new PiShockConfigLogic.DevicePair(10, 1),
                new PiShockConfigLogic.DevicePair(20, 5)
        ));

        assertEquals(List.of(
                "Hub 10 -> [1, 2]",
                "Hub 20 -> [5]"
        ), display);
    }

    @Test
    void toHubShockerDisplayListReturnsEmptyListWhenNoRoutesExist() {
        assertEquals(List.of(), PiShockConfigLogic.toHubShockerDisplayList(List.of()));
    }
}
