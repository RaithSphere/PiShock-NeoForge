package net.raith.pishock.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShockCoreTest {
    private static final Gson GSON = new Gson();

    @Test
    void enforceOperationLimitsRespectsConfiguredMaximums() {
        ShockCore.OperationLimits limits = ShockCore.enforceOperationLimits(120, 5000, true, 80, 1000);

        assertEquals(80, limits.intensity());
        assertEquals(1000, limits.durationMs());
    }

    @Test
    void enforceOperationLimitsFallsBackToHardBoundsWhenConfigNotRespected() {
        ShockCore.OperationLimits limits = ShockCore.enforceOperationLimits(0, 20000, false, 1, 1);

        assertEquals(ShockCore.HARD_MIN_INTENSITY, limits.intensity());
        assertEquals(ShockCore.HARD_MAX_DURATION_MS, limits.durationMs());
    }

    @Test
    void computeIntensityFromDamageRatioScalesAndClamps() {
        assertEquals(20, ShockCore.computeIntensityFromDamageRatio(0.25F, 80));
        assertEquals(0, ShockCore.computeIntensityFromDamageRatio(-1.0F, 80));
        assertEquals(80, ShockCore.computeIntensityFromDamageRatio(2.0F, 80));
    }

    @Test
    void selectRoutingPrefersConfiguredPair() {
        JsonArray shockers = new JsonArray();
        shockers.add(route(5, 9));
        shockers.add(route(7, 11));

        ShockCore.DeviceRouting result = ShockCore.selectRouting(shockers, 7, 11);

        assertNotNull(result);
        assertEquals(7, result.hubId());
        assertEquals(11, result.shockerId());
    }

    @Test
    void selectRoutingReturnsNullWhenConfiguredPairMissing() {
        JsonArray shockers = new JsonArray();
        shockers.add(route(5, 9));

        ShockCore.DeviceRouting result = ShockCore.selectRouting(shockers, 7, 11);

        assertNull(result);
    }

    @Test
    void selectRoutingUsesFirstAvailableWhenNoConfigProvided() {
        JsonArray shockers = new JsonArray();
        shockers.add(new JsonObject()); // invalid
        shockers.add(route(8, 12));
        shockers.add(route(9, 13));

        ShockCore.DeviceRouting result = ShockCore.selectRouting(shockers, null, null);

        assertNotNull(result);
        assertEquals(8, result.hubId());
        assertEquals(12, result.shockerId());
    }

    @Test
    void extractAckDetailsParsesOriginalCommand() {
        String originalCommand = """
                {
                  "PublishCommands": [
                    {
                      "Body": { "i": 42, "d": 900 }
                    }
                  ]
                }
                """;

        ShockCore.AckDetails details = ShockCore.extractAckDetails(originalCommand, GSON);

        assertNotNull(details);
        assertEquals(42, details.intensity());
        assertEquals(900, details.durationMs());
    }

    @Test
    void extractAckDetailsReturnsNullForInvalidPayload() {
        ShockCore.AckDetails details = ShockCore.extractAckDetails("{\"PublishCommands\":[]}", GSON);
        assertNull(details);
    }

    private static JsonObject route(int hubId, int shockerId) {
        JsonObject object = new JsonObject();
        object.addProperty("HubId", hubId);
        object.addProperty("ShockerId", shockerId);
        return object;
    }
}
