package net.raith.pishock.network;

import com.google.gson.Gson;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import net.raith.pishock.PiShock;
import net.raith.pishock.PiShockConfig;
import net.raith.pishock.Utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class ShockHandler {
    private static final URI WS_API_URI_BASE = URI.create("wss://broker.pishock.com/v2");
    private static final String REST_API_BASE = "https://api.pishock.com";
    private static final int HARD_MIN_DURATION_MS = 100;
    private static final int HARD_MAX_DURATION_MS = 15000;
    private static final int HARD_MIN_INTENSITY = 1;
    private static final int HARD_MAX_INTENSITY = 100;
    private static final long FAILSAFE_MIN_DISPATCH_GAP_MS = 100L;
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            .create();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ReadWriteLock HTTP_LOCK = new ReentrantReadWriteLock();
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pishock-ws");
        t.setDaemon(true);
        return t;
    });

    private static HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private static CompletableFuture<WebSocket> webSocketFuture;
    private static volatile DiscoveryCache discoveryCache;
    private static volatile boolean debugEnabled = false;
    private static volatile long lastIssueChatAtMs = 0L;
    private static volatile String lastIssueChatKey = "";
    private static volatile long lastSuccessChatAtMs = 0L;
    private static volatile long lastDispatchAtMs = 0L;
    private static final long ISSUE_CHAT_COOLDOWN_MS = 15_000L;
    private static final long SUCCESS_CHAT_COOLDOWN_MS = 1_000L;

    public static void shock(float requestedIntensity) {
        shock(requestedIntensity, PiShockConfig.PISHOCK_DURATION.get());
    }

    public static void shock(float requestedIntensity, int requestedDurationMs) {
        sendOperation(PiShockConfig.PISHOCK_MODE.get(), requestedIntensity, requestedDurationMs, true);
    }

    public static CompletableFuture<String> sendVibrationTestAsync() {
        return CompletableFuture.supplyAsync(() -> {
            boolean sent = sendOperation(PiShock.PiShockMode.Vibrate, 100, 1000, false);
            return sent
                    ? "[PiShock] Vibration test sent."
                    : "[PiShock] Vibration test failed.";
        }, EXECUTOR);
    }

    private static boolean sendOperation(PiShock.PiShockMode mode, float requestedIntensity, int requestedDurationMs, boolean respectConfiguredLimits) {
        debug("Shock request received: requestedIntensity=" + requestedIntensity + ", requestedDurationMs=" + requestedDurationMs);
        String username = safeTrim(PiShockConfig.PISHOCK_USERNAME.get());
        String apiKey = safeTrim(PiShockConfig.PISHOCK_APIKEY.get());

        if (username.isEmpty() || apiKey.isEmpty()) {
            Utils.unilog("PiShock username/API key missing; cannot send websocket operation.");
            reportIssueToChat("missing-credentials", "[PiShock] Missing username/API key in config.");
            return false;
        }

        Integer configuredShockerId = parseInteger(PiShockConfig.PISHOCK_SHOCKER_ID.get());
        Integer configuredHubId = PiShockConfig.PISHOCK_HUB_ID.get() >= 0 ? PiShockConfig.PISHOCK_HUB_ID.get() : null;
        Integer configuredUserId = PiShockConfig.PISHOCK_USER_ID.get() >= 0 ? PiShockConfig.PISHOCK_USER_ID.get() : null;

        DiscoveryResult discovery = resolveDiscovery(username, apiKey, configuredUserId, configuredHubId, configuredShockerId);
        if (discovery == null) {
            reportIssueToChat("discovery-failed", "[PiShock] Failed to resolve User/Hub/Shocker IDs.");
            return false;
        }

        OperationLimits limits = enforceOperationLimits(Math.round(requestedIntensity), requestedDurationMs, respectConfiguredLimits);
        int intensity = limits.intensity();
        int durationMs = limits.durationMs();
        debug("Resolved routing and clamped values: userId=" + discovery.userId + ", hubId=" + discovery.hubId
                + ", shockerId=" + discovery.shockerId + ", intensity=" + intensity + ", durationMs=" + durationMs);

        if (isDispatchGapTooSmall()) {
            reportIssueToChat("failsafe-dispatch-gap", "[PiShock] Failsafe blocked rapid consecutive command.");
            debug("Failsafe blocked command due to minimum dispatch gap.");
            return false;
        }

        String configuredLogIdentifier = safeTrim(PiShockConfig.PISHOCK_LOG_IDENTIFIER.get());
        LogMetadata metadata = new LogMetadata(
                discovery.userId,
                AccessType.API,
                false,
                false,
                configuredLogIdentifier.isEmpty() ? "Minecraft" : configuredLogIdentifier
        );

        PublishMessage message = new PublishMessage(
                "c" + discovery.hubId + "-ops",
                new ShockerCommand(
                        discovery.shockerId,
                        toWsMode(mode),
                        intensity,
                        durationMs,
                        true,
                        metadata
                )
        );

        doApiCall(new PublishCommand(List.of(message)), username, apiKey, durationMs);
        return true;
    }

    private static DiscoveryResult resolveDiscovery(
            String username,
            String apiKey,
            Integer configuredUserId,
            Integer configuredHubId,
            Integer configuredShockerId
    ) {
        debug("Resolving discovery values: configuredUserId=" + configuredUserId + ", configuredHubId=" + configuredHubId
                + ", configuredShockerId=" + configuredShockerId);
        Integer userId = configuredUserId;
        Integer hubId = configuredHubId;
        Integer shockerId = configuredShockerId;

        DiscoveryCache cache = discoveryCache;
        if (cache != null && cache.matches(username, apiKey)) {
            debug("Using cached discovery values.");
            if (userId == null) {
                userId = cache.userId;
            }
            if (hubId == null) {
                hubId = cache.hubId;
            }
            if (shockerId == null) {
                shockerId = cache.shockerId;
            }
        }

        try {
            if (userId == null) {
                debug("Requesting /Account to resolve UserId.");
                HttpResponse<String> accountResponse = sendJsonRequest("/Account", "GET", null, apiKey);
                if (accountResponse.statusCode() != 200) {
                    Utils.unilog("Failed to resolve UserId from /Account. Status: " + accountResponse.statusCode());
                    return null;
                }

                JsonElement accountParsed = GSON.fromJson(accountResponse.body(), JsonElement.class);
                if (accountParsed == null || !accountParsed.isJsonObject()) {
                    Utils.unilog("Unexpected /Account response while resolving UserId.");
                    return null;
                }

                userId = getInt(accountParsed.getAsJsonObject(), "UserId");
                if (userId == null) {
                    Utils.unilog("/Account response missing UserId.");
                    return null;
                }
            }

            if (hubId == null || shockerId == null) {
                debug("Requesting /Shockers to resolve HubId/ShockerId.");
                HttpResponse<String> listResponse = sendJsonRequest("/Shockers", "GET", null, apiKey);
                if (listResponse.statusCode() != 200) {
                    Utils.unilog("Failed to list shockers for websocket routing. Status: " + listResponse.statusCode());
                    return null;
                }

                JsonElement parsed = GSON.fromJson(listResponse.body(), JsonElement.class);
                if (parsed == null || !parsed.isJsonArray()) {
                    Utils.unilog("Unexpected /Shockers response format while resolving routing.");
                    return null;
                }

                JsonArray shockers = parsed.getAsJsonArray();
                if (shockers.isEmpty()) {
                    Utils.unilog("No shockers found on account.");
                    return null;
                }

                DeviceRouting routing = selectRouting(shockers, configuredHubId, configuredShockerId);
                if (routing == null) {
                    Utils.unilog("Could not select a valid HubId/ShockerId from /Shockers.");
                    return null;
                }

                if (hubId == null) {
                    hubId = routing.hubId;
                }
                if (shockerId == null) {
                    shockerId = routing.shockerId;
                }
            }
        } catch (Exception ex) {
            Utils.unilog("Failed to resolve discovery values: " + ex.getMessage());
            return null;
        }

        if (userId == null || hubId == null || shockerId == null) {
            debug("Discovery failed after resolution attempt.");
            return null;
        }

        discoveryCache = new DiscoveryCache(username, apiKey, userId, hubId, shockerId);
        debug("Discovery resolved and cached.");
        return new DiscoveryResult(userId, hubId, shockerId);
    }

    private static DeviceRouting selectRouting(JsonArray shockers, Integer configuredHubId, Integer configuredShockerId) {
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

            Utils.unilog("Selected routing HubId=" + hubId + " ShockerId=" + shockerId);
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
                Utils.unilog("Selected first available routing HubId=" + hubId + " ShockerId=" + shockerId);
                return new DeviceRouting(hubId, shockerId);
            }
        }

        return null;
    }

    private static URI getApiUri(String username, String apiKey) {
        String query = "Username=" + urlEncode(username) + "&ApiKey=" + urlEncode(apiKey);
        return URI.create(WS_API_URI_BASE + "?" + query);
    }

    private static CompletableFuture<WebSocket> connectToApi(String username, String apiKey) {
        URI uri = getApiUri(username, apiKey);
        debug("Opening websocket connection to broker.");
        return httpClient.newWebSocketBuilder().buildAsync(uri, new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                debug("Websocket opened.");
                WebSocket.Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                if (last) {
                    debug("Websocket message received: " + data);
                    handleBrokerMessage(data.toString());
                }
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                debug("Websocket listener error: " + error.getMessage());
                WebSocket.Listener.super.onError(webSocket, error);
            }
        });
    }

    private static CompletableFuture<WebSocket> getWebSocketFuture(String username, String apiKey) {
        var readLock = HTTP_LOCK.readLock();
        readLock.lock();
        CompletableFuture<WebSocket> wsFuture;
        try {
            wsFuture = webSocketFuture;
        } finally {
            readLock.unlock();
        }

        if (wsFuture == null) {
            var writeLock = HTTP_LOCK.writeLock();
            writeLock.lock();
            try {
                wsFuture = webSocketFuture;
                if (wsFuture == null) {
                    webSocketFuture = wsFuture = connectToApi(username, apiKey);
                } else {
                    debug("Reusing existing websocket future.");
                }
            } finally {
                writeLock.unlock();
            }
        }

        return wsFuture;
    }

    private static void whenConnected(String username, String apiKey, Consumer<WebSocket> fn, int retries) {
        CompletableFuture<WebSocket> wsFuture = getWebSocketFuture(username, apiKey);
        wsFuture.handleAsync((socket, ex) -> {
            if (ex != null || socket == null || socket.isOutputClosed()) {
                debug("Websocket not ready (retries left: " + retries + "). Reason: "
                        + (ex != null ? ex.getMessage() : "socket null/closed"));
                if (socket != null) {
                    socket.abort();
                }

                var writeLock = HTTP_LOCK.writeLock();
                writeLock.lock();
                try {
                    if (wsFuture == webSocketFuture) {
                        webSocketFuture = null;
                    }
                } finally {
                    writeLock.unlock();
                }

                if (retries > 0) {
                    whenConnected(username, apiKey, fn, retries - 1);
                } else {
                    Utils.unilog("WebSocket unavailable after retry.");
                    reportIssueToChat("websocket-unavailable", "[PiShock] WebSocket unavailable.");
                }
            } else {
                debug("Websocket ready; executing send.");
                fn.accept(socket);
            }
            return null;
        }, EXECUTOR);
    }

    private static void doApiCall(Object payload, String username, String apiKey, int durationMs) {
        whenConnected(username, apiKey, socket -> {
            String json = GSON.toJson(payload);
            debug("Sending websocket payload: " + json);
            socket.sendText(json, true)
                    .thenAccept(ignored -> {
                        debug("Websocket payload send completed.");
                        PiShock.markShockVisual(durationMs);
                    })
                    .exceptionally(ex -> {
                        Utils.unilog("Failed sending websocket payload: " + ex.getMessage());
                        reportIssueToChat("send-failed", "[PiShock] Failed to send websocket payload.");
                        return null;
                    });
        }, 1);
    }

    public static CompletableFuture<String> checkConnectivityAsync() {
        return CompletableFuture.supplyAsync(ShockHandler::checkConnectivity, EXECUTOR);
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        Utils.unilog("[PiShock Debug] " + (enabled ? "Enabled" : "Disabled"));
    }

    public static boolean isDebugEnabled() {
        return isDebugActive();
    }

    private static String checkConnectivity() {
        String username = safeTrim(PiShockConfig.PISHOCK_USERNAME.get());
        String apiKey = safeTrim(PiShockConfig.PISHOCK_APIKEY.get());

        if (username.isEmpty() || apiKey.isEmpty()) {
            String msg = "[PiShock] Connectivity check failed: missing username/API key.";
            reportIssueToChat("check-missing-credentials", msg);
            return msg;
        }

        try {
            HttpResponse<String> accountResponse = sendJsonRequest("/Account", "GET", null, apiKey);
            if (accountResponse.statusCode() != 200) {
                String msg = "[PiShock] Connectivity check failed: /Account returned " + accountResponse.statusCode() + ".";
                reportIssueToChat("check-account-status", msg);
                return msg;
            }
        } catch (Exception ex) {
            String msg = "[PiShock] Connectivity check failed: cannot reach PiShock API.";
            reportIssueToChat("check-api-unreachable", msg);
            return msg;
        }

        try {
            WebSocket socket = connectToApi(username, apiKey)
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
            if (socket == null || socket.isOutputClosed()) {
                String msg = "[PiShock] Connectivity check failed: websocket connection closed.";
                reportIssueToChat("check-websocket-closed", msg);
                return msg;
            }
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "check").join();
            } catch (Exception ignored) {
                socket.abort();
            }
        } catch (CompletionException ex) {
            String msg = "[PiShock] Connectivity check failed: websocket broker unavailable.";
            reportIssueToChat("check-websocket-unavailable", msg);
            return msg;
        }

        return "[PiShock] Connectivity OK: API + websocket broker reachable.";
    }

    private static HttpResponse<String> sendJsonRequest(String path, String method, String jsonBody, String apiKey)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(REST_API_BASE + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("X-PiShock-Api-Key", apiKey);

        if (jsonBody != null) {
            builder.header("Content-Type", "application/json");
        }

        switch (method) {
            case "GET" -> builder.GET();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            case "DELETE" -> builder.DELETE();
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

    private static Boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static OperationLimits enforceOperationLimits(int requestedIntensity, int requestedDurationMs, boolean respectConfiguredLimits) {
        int maxIntensity = HARD_MAX_INTENSITY;
        int maxDuration = HARD_MAX_DURATION_MS;

        if (respectConfiguredLimits) {
            maxIntensity = clamp(PiShockConfig.PISHOCK_INTENSITY.get(), HARD_MIN_INTENSITY, HARD_MAX_INTENSITY);
            maxDuration = clamp(PiShockConfig.PISHOCK_DURATION.get(), HARD_MIN_DURATION_MS, HARD_MAX_DURATION_MS);
        }

        int safeIntensity = clamp(requestedIntensity, HARD_MIN_INTENSITY, maxIntensity);
        int safeDurationMs = clamp(requestedDurationMs, HARD_MIN_DURATION_MS, maxDuration);
        return new OperationLimits(safeIntensity, safeDurationMs);
    }

    private static boolean isDispatchGapTooSmall() {
        long now = System.currentTimeMillis();
        synchronized (ShockHandler.class) {
            if (now - lastDispatchAtMs < FAILSAFE_MIN_DISPATCH_GAP_MS) {
                return true;
            }
            lastDispatchAtMs = now;
            return false;
        }
    }

    private static String toWsMode(PiShock.PiShockMode mode) {
        return switch (mode) {
            case Shock -> "s";
            case Vibrate -> "v";
            case Beep -> "b";
        };
    }

    private static void reportIssueToChat(String key, String message) {
        long now = System.currentTimeMillis();
        boolean shouldSend;
        synchronized (ShockHandler.class) {
            shouldSend = !key.equals(lastIssueChatKey) || (now - lastIssueChatAtMs) >= ISSUE_CHAT_COOLDOWN_MS;
            if (shouldSend) {
                lastIssueChatKey = key;
                lastIssueChatAtMs = now;
            }
        }

        if (shouldSend) {
            Utils.sendToMinecraftChat(message);
        }
    }

    private static void reportSuccessToChat(String message) {
        long now = System.currentTimeMillis();
        if (now - lastSuccessChatAtMs < SUCCESS_CHAT_COOLDOWN_MS) {
            return;
        }
        lastSuccessChatAtMs = now;
        Utils.sendToMinecraftChat(message);
    }

    private static void handleBrokerMessage(String rawMessage) {
        JsonObject payload;
        try {
            JsonElement parsed = GSON.fromJson(rawMessage, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                return;
            }
            payload = parsed.getAsJsonObject();
        } catch (Exception ignored) {
            return;
        }

        Boolean isError = getBoolean(payload, "IsError");
        if (isError == null) {
            return;
        }

        String brokerMessage = getString(payload, "Message");
        if (Boolean.TRUE.equals(isError)) {
            String errorCode = getString(payload, "ErrorCode");
            String summary = (errorCode == null || errorCode.isBlank()) ? "BROKER_ERROR" : errorCode;
            String details = (brokerMessage == null || brokerMessage.isBlank()) ? "WebSocket broker returned an error." : brokerMessage;
            reportIssueToChat("broker-" + summary, "[PiShock] Broker error (" + summary + "): " + details);
            return;
        }

        if (!PiShockConfig.PISHOCK_SHOW_SUCCESS_CONFIRMATION.get() || isDebugActive()) {
            return;
        }

        if (brokerMessage != null && brokerMessage.toLowerCase().contains("publish successful")) {
            String originalCommand = getString(payload, "OriginalCommand");
            AckDetails ackDetails = extractAckDetails(originalCommand);
            if (ackDetails != null) {
                reportSuccessToChat("[PiShock] Shocked " + ackDetails.intensity + "% (" + formatDurationSeconds(ackDetails.durationMs) + "s)");
            } else {
                reportSuccessToChat("[PiShock] Shock command acknowledged.");
            }
        }
    }

    private static String formatDurationSeconds(int durationMs) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0, durationMs) / 1000.0);
    }

    private static AckDetails extractAckDetails(String originalCommand) {
        if (originalCommand == null || originalCommand.isBlank()) {
            return null;
        }

        try {
            JsonElement parsed = GSON.fromJson(originalCommand, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                return null;
            }

            JsonObject commandObj = parsed.getAsJsonObject();
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
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void debug(String message) {
        if (!isDebugActive()) {
            return;
        }
        String formatted = "[PiShock Debug] " + message;
        Utils.unilog(formatted);
        Utils.sendToMinecraftChat(formatted);
    }

    private static boolean isDebugActive() {
        return debugEnabled || PiShockConfig.PISHOCK_DEBUG.get();
    }

    private record DeviceRouting(int hubId, int shockerId) {
    }

    private record DiscoveryResult(int userId, int hubId, int shockerId) {
    }

    private record DiscoveryCache(String username, String apiKey, int userId, int hubId, int shockerId) {
        private boolean matches(String candidateUsername, String candidateApiKey) {
            return username.equals(candidateUsername) && apiKey.equals(candidateApiKey);
        }
    }

    private record OperationLimits(int intensity, int durationMs) {
    }

    private record AckDetails(int intensity, int durationMs) {
    }

    @SuppressWarnings("unused")
    private static class PublishCommand {
        private final String operation = "PUBLISH";
        private final List<PublishMessage> publishCommands;

        private PublishCommand(List<PublishMessage> publishCommands) {
            this.publishCommands = publishCommands;
        }
    }

    @SuppressWarnings("unused")
    private static class PublishMessage {
        private final String target;
        private final ShockerCommand body;

        private PublishMessage(String target, ShockerCommand body) {
            this.target = target;
            this.body = body;
        }
    }

    @SuppressWarnings("unused")
    private static class ShockerCommand {
        @SerializedName("id")
        private final int id;

        @SerializedName("m")
        private final String op;

        @SerializedName("i")
        private final int intensity;

        @SerializedName("d")
        private final int duration;

        @SerializedName("r")
        private final boolean replace;

        @SerializedName("l")
        private final LogMetadata logMetadata;

        private ShockerCommand(int id, String op, int intensity, int duration, boolean replace, LogMetadata logMetadata) {
            this.id = id;
            this.op = op;
            this.intensity = intensity;
            this.duration = duration;
            this.replace = replace;
            this.logMetadata = logMetadata;
        }
    }

    @SuppressWarnings("unused")
    private static class LogMetadata {
        @SerializedName("u")
        private final int userId;

        @SerializedName("ty")
        private final AccessType accessType;

        @SerializedName("w")
        private final boolean warning;

        @SerializedName("h")
        private final boolean continuous;

        @SerializedName("o")
        private final String logIdentifier;

        private LogMetadata(int userId, AccessType accessType, boolean warning, boolean continuous, String logIdentifier) {
            this.userId = userId;
            this.accessType = accessType;
            this.warning = warning;
            this.continuous = continuous;
            this.logIdentifier = logIdentifier;
        }
    }

    private enum AccessType {
        @SerializedName("api")
        API
    }
}
