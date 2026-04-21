package net.raith.pishock.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ShockCore {
    public static final int HARD_MIN_DURATION_MS = 100;
    public static final int HARD_MAX_DURATION_MS = 15000;
    public static final int HARD_MIN_INTENSITY = 1;
    public static final int HARD_MAX_INTENSITY = 100;

    private ShockCore() {
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int computeIntensityFromDamageRatio(float damageRatio, int intensityLimit) {
        int clampedLimit = Math.max(0, intensityLimit);
        int rawShockPower = Math.round(Math.max(0.0F, damageRatio) * clampedLimit);
        return Math.min(clampedLimit, Math.max(0, rawShockPower));
    }

    public static OperationLimits enforceOperationLimits(
            int requestedIntensity,
            int requestedDurationMs,
            boolean respectConfiguredLimits,
            int configuredMaxIntensity,
            int configuredMaxDurationMs
    ) {
        int maxIntensity = HARD_MAX_INTENSITY;
        int maxDuration = HARD_MAX_DURATION_MS;

        if (respectConfiguredLimits) {
            maxIntensity = clamp(configuredMaxIntensity, HARD_MIN_INTENSITY, HARD_MAX_INTENSITY);
            maxDuration = clamp(configuredMaxDurationMs, HARD_MIN_DURATION_MS, HARD_MAX_DURATION_MS);
        }

        int safeIntensity = clamp(requestedIntensity, HARD_MIN_INTENSITY, maxIntensity);
        int safeDurationMs = clamp(requestedDurationMs, HARD_MIN_DURATION_MS, maxDuration);
        return new OperationLimits(safeIntensity, safeDurationMs);
    }

    public static DeviceRouting selectRouting(JsonArray shockers, Integer configuredHubId, Integer configuredShockerId) {
        for (JsonElement element : shockers) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            Integer hubId = getInt(candidate, "HubId");
            Integer shockerId = getInt(candidate, "ShockerId");
            if (hubId == null || shockerId == null) {
                continue;
            }

            if (configuredShockerId != null && configuredShockerId.intValue() != shockerId.intValue()) {
                continue;
            }

            if (configuredHubId != null && configuredHubId.intValue() != hubId.intValue()) {
                continue;
            }

            return new DeviceRouting(hubId, shockerId);
        }

        if (configuredShockerId != null || configuredHubId != null) {
            return null;
        }

        for (JsonElement element : shockers) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            Integer hubId = getInt(candidate, "HubId");
            Integer shockerId = getInt(candidate, "ShockerId");
            if (hubId != null && shockerId != null) {
                return new DeviceRouting(hubId, shockerId);
            }
        }

        return null;
    }

    public static AckDetails extractAckDetails(String originalCommand, Gson gson) {
        if (originalCommand == null || originalCommand.isBlank()) {
            return null;
        }

        try {
            JsonElement parsed = gson.fromJson(originalCommand, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                return null;
            }
            return extractAckDetails(parsed.getAsJsonObject());
        } catch (Exception ignored) {
            return null;
        }
    }

    public static AckDetails extractAckDetails(JsonObject commandObj) {
        JsonElement publishCommandsElement = commandObj.get("PublishCommands");
        if (publishCommandsElement == null || !publishCommandsElement.isJsonArray()) {
            return null;
        }

        JsonArray publishCommands = publishCommandsElement.getAsJsonArray();
        if (publishCommands.isEmpty() || !publishCommands.get(0).isJsonObject()) {
            return null;
        }

        JsonObject firstPublish = publishCommands.get(0).getAsJsonObject();
        JsonElement bodyElement = firstPublish.get("Body");
        if (bodyElement == null || !bodyElement.isJsonObject()) {
            return null;
        }

        JsonObject body = bodyElement.getAsJsonObject();
        Integer intensity = getInt(body, "i");
        Integer duration = getInt(body, "d");
        if (intensity == null || duration == null) {
            return null;
        }
        return new AckDetails(intensity, duration);
    }

    private static Integer getInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    public record DeviceRouting(int hubId, int shockerId) {
    }

    public record OperationLimits(int intensity, int durationMs) {
    }

    public record AckDetails(int intensity, int durationMs) {
    }
}
