package net.raith.pishock.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.StringListListEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.raith.pishock.PiShock;
import net.raith.pishock.PiShockConfig;
import net.raith.pishock.Utils;
import net.raith.pishock.network.ShockHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

public final class PiShockClothConfigScreen {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<Screen, FetchContext> FETCH_CONTEXTS = new WeakHashMap<>();
    private static final Map<Screen, ActionButtons> ACTION_BUTTONS = new WeakHashMap<>();
    private static volatile List<DevicePair> CACHED_DEVICE_ROUTES = List.of();

    private PiShockClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return create(parent, false);
    }

    public static Screen create(Screen parent, boolean openApiFirst) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("PiShock Setup"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory behavior;
        ConfigCategory api;
        if (openApiFirst) {
            api = builder.getOrCreateCategory(Component.literal("API"));
            behavior = builder.getOrCreateCategory(Component.literal("Behavior"));
        } else {
            behavior = builder.getOrCreateCategory(Component.literal("Behavior"));
            api = builder.getOrCreateCategory(Component.literal("API"));
        }
        api.addEntry(entryBuilder.startTextDescription(Component.literal(
                        "Click Fetch IDs to populate User ID, Hub ID, and Shocker ID from your PiShock account."))
                .build());

        StringListEntry usernameEntry = entryBuilder.startStrField(Component.literal("Username"), PiShockConfig.PISHOCK_USERNAME.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_USERNAME::set)
                .build();
        api.addEntry(usernameEntry);

        StringListEntry apiKeyEntry = entryBuilder.startStrField(Component.literal("API Key"), PiShockConfig.PISHOCK_APIKEY.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_APIKEY::set)
                .build();
        api.addEntry(apiKeyEntry);

        StringListEntry userIdEntry = entryBuilder.startStrField(Component.literal("User ID (-1 = Auto)"), Integer.toString(PiShockConfig.PISHOCK_USER_ID.get()))
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_USER_ID.set(parseIntOrDefault(value, -1)))
                .build();
        api.addEntry(userIdEntry);

        StringListEntry hubIdEntry = entryBuilder.startStrField(Component.literal("Hub ID (-1 = Auto)"), Integer.toString(PiShockConfig.PISHOCK_HUB_ID.get()))
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_HUB_ID.set(parseIntOrDefault(value, -1)))
                .build();
        api.addEntry(hubIdEntry);

        StringListEntry shockerIdEntry = entryBuilder.startStrField(Component.literal("Shocker ID (blank = Auto)"), PiShockConfig.PISHOCK_SHOCKER_ID.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_SHOCKER_ID::set)
                .build();
        api.addEntry(shockerIdEntry);

        StringListListEntry hubShockerListEntry = entryBuilder
                .startStrList(Component.literal("Hub -> Shockers"), toHubShockerDisplayList(CACHED_DEVICE_ROUTES))
                .setSaveConsumer(value -> {
                    // Display list only; selection is done through Hub ID + Shocker ID fields.
                })
                .setExpanded(true)
                .build();
        api.addEntry(hubShockerListEntry);

        api.addEntry(entryBuilder.startStrField(Component.literal("Log Identifier"), PiShockConfig.PISHOCK_LOG_IDENTIFIER.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_LOG_IDENTIFIER::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enabled"), PiShockConfig.PISHOCK_ENABLED.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_ENABLED::set)
                .build());
        behavior.addEntry(entryBuilder.startEnumSelector(Component.literal("Mode"), PiShock.PiShockMode.class, PiShockConfig.PISHOCK_MODE.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_MODE::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(Component.literal("Trigger On Death"), PiShockConfig.PISHOCK_TRIGGER_ON_DEATH.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_TRIGGER_ON_DEATH::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Success Confirmation"), PiShockConfig.PISHOCK_SHOW_SUCCESS_CONFIRMATION.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_SHOW_SUCCESS_CONFIRMATION::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(Component.literal("Debug Logging"), PiShockConfig.PISHOCK_DEBUG.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_DEBUG::set)
                .build());
        behavior.addEntry(entryBuilder.startIntField(Component.literal("Duration (ms)"), PiShockConfig.PISHOCK_DURATION.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_DURATION.set(clamp(value, 100, 15000)))
                .build());
        behavior.addEntry(entryBuilder.startIntField(Component.literal("Max Intensity"), PiShockConfig.PISHOCK_INTENSITY.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_INTENSITY.set(clamp(value, 1, 100)))
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(Component.literal("Combine Rapid Damage"), PiShockConfig.PISHOCK_COMBINE_DAMAGE_EVENTS.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_COMBINE_DAMAGE_EVENTS::set)
                .build());
        behavior.addEntry(entryBuilder.startIntField(Component.literal("Combine Window (ms)"), PiShockConfig.PISHOCK_COMBINE_WINDOW_MS.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_COMBINE_WINDOW_MS.set(clamp(value, 0, 5000)))
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(Component.literal("Queue Shocks"), PiShockConfig.PISHOCK_QUEUE_ENABLED.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_QUEUE_ENABLED::set)
                .build());
        behavior.addEntry(entryBuilder.startIntField(Component.literal("Queue Max Size"), PiShockConfig.PISHOCK_QUEUE_MAX_SIZE.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_QUEUE_MAX_SIZE.set(clamp(value, 1, 512)))
                .build());

        builder.setAfterInitConsumer(screen -> {
            synchronized (FETCH_CONTEXTS) {
                FETCH_CONTEXTS.put(screen, new FetchContext(parent, usernameEntry, apiKeyEntry, userIdEntry, hubIdEntry, shockerIdEntry));
            }
        });

        builder.setSavingRunnable(PiShockConfig::save);
        return builder.build();
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        FetchContext context;
        synchronized (FETCH_CONTEXTS) {
            context = FETCH_CONTEXTS.get(event.getScreen());
        }
        if (context == null) {
            return;
        }

        int buttonHeight = 18;
        int fetchWidth = 108;
        int smallWidth = 52;
        int gap = 4;
        int x = event.getScreen().width - Math.max(fetchWidth, (smallWidth * 2) + gap) - 8;
        int y = 8;

        Button fetchButton = Button.builder(Component.literal("Fetch IDs"), button -> fetchIds(button, context))
                .bounds(x, y, fetchWidth, buttonHeight)
                .build();
        event.addListener(fetchButton);

        Button checkButton = Button.builder(Component.literal("Check"), PiShockClothConfigScreen::runConnectivityCheck)
                .bounds(x, y + buttonHeight + 4, smallWidth, buttonHeight)
                .build();
        event.addListener(checkButton);

        Button testVibrationButton = Button.builder(Component.literal("Test"), PiShockClothConfigScreen::runVibrationTest)
                .bounds(x + smallWidth + gap, y + buttonHeight + 4, smallWidth, buttonHeight)
                .build();
        event.addListener(testVibrationButton);

        synchronized (ACTION_BUTTONS) {
            ACTION_BUTTONS.put(event.getScreen(), new ActionButtons(fetchButton, checkButton, testVibrationButton));
        }
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        ActionButtons buttons;
        synchronized (ACTION_BUTTONS) {
            buttons = ACTION_BUTTONS.get(event.getScreen());
        }
        if (buttons == null) {
            return;
        }

        boolean show = isApiTabSelected(event.getScreen());
        buttons.fetchButton.visible = show;
        buttons.checkButton.visible = show;
        buttons.testButton.visible = show;
    }

    private static void fetchIds(Button button, FetchContext context) {
        String username = context.usernameEntry.getValue().trim();
        String apiKey = context.apiKeyEntry.getValue().trim();
        logFetch("Fetch IDs pressed. usernameSet=" + !username.isEmpty() + ", apiKeySet=" + !apiKey.isEmpty());

        if (username.isEmpty() || apiKey.isEmpty()) {
            button.setMessage(Component.literal("Need creds"));
            return;
        }

        button.active = false;
        button.setMessage(Component.literal("Fetching..."));

        CompletableFuture.supplyAsync(() -> lookupIds(username, apiKey))
                .whenComplete((result, throwable) -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.execute(() -> {
                        button.active = true;

                        if (throwable != null || result == null) {
                            if (throwable != null) {
                                logFetch("Fetch IDs failed with exception: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                            } else {
                                logFetch("Fetch IDs completed but returned no result.");
                            }
                            button.setMessage(Component.literal("Fetch failed"));
                            return;
                        }

                        context.usernameEntry.setValue(username);
                        context.apiKeyEntry.setValue(apiKey);
                        context.userIdEntry.setValue(Integer.toString(result.userId));
                        PiShockConfig.PISHOCK_USERNAME.set(username);
                        PiShockConfig.PISHOCK_APIKEY.set(apiKey);
                        PiShockConfig.PISHOCK_USER_ID.set(result.userId);

                        CACHED_DEVICE_ROUTES = List.copyOf(result.devices);
                        if (!result.devices.isEmpty()) {
                            DevicePair preferred = choosePreferredRoute(result.devices, context.hubIdEntry.getValue(), context.shockerIdEntry.getValue());
                            context.hubIdEntry.setValue(Integer.toString(preferred.hubId));
                            context.shockerIdEntry.setValue(Integer.toString(preferred.shockerId));
                            PiShockConfig.PISHOCK_HUB_ID.set(preferred.hubId);
                            PiShockConfig.PISHOCK_SHOCKER_ID.set(Integer.toString(preferred.shockerId));
                            logFetch("Fetch IDs success. userId=" + result.userId + ", routes=" + result.devices.size()
                                    + ", selected=Hub " + preferred.hubId + " / Shocker " + preferred.shockerId);
                        } else {
                            logFetch("Fetch IDs resolved userId=" + result.userId + " but no shocker routes were returned.");
                        }

                        PiShockConfig.save();
                        button.setMessage(Component.literal("Fetched"));
                        minecraft.setScreen(create(context.parentScreen, true));
                    });
                });
    }

    private static LookupResult lookupIds(String username, String apiKey) {
        try {
            Integer userId = fetchUserId(username, apiKey);
            List<DevicePair> devices = fetchDeviceRoutes(userId, apiKey);
            if (userId == null) {
                return null;
            }
            return new LookupResult(userId, devices);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void runConnectivityCheck(Button button) {
        button.active = false;
        button.setMessage(Component.literal("..."));
        ShockHandler.checkConnectivityAsync().whenComplete((message, throwable) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                button.active = true;
                if (throwable != null) {
                    button.setMessage(Component.literal("Fail"));
                    return;
                }

                boolean ok = message != null && message.contains("Connectivity OK");
                button.setMessage(Component.literal(ok ? "OK" : "Issue"));
            });
        });
    }

    private static void runVibrationTest(Button button) {
        button.active = false;
        button.setMessage(Component.literal("..."));
        ShockHandler.sendVibrationTestAsync().whenComplete((message, throwable) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                button.active = true;
                if (throwable != null) {
                    button.setMessage(Component.literal("Fail"));
                    return;
                }

                boolean ok = message != null && message.contains("sent");
                button.setMessage(Component.literal(ok ? "Sent" : "Fail"));
            });
        });
    }

    private static Integer fetchUserId(String username, String apiKey) throws Exception {
        String path = "https://auth.pishock.com/Auth/GetUserIfAPIKeyValid?apikey="
                + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                + "&username="
                + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> response = sendAbsoluteGet(path);
        logFetch("GetUserIfAPIKeyValid status=" + response.statusCode() + ", body=" + trimForLog(response.body()));
        if (response.statusCode() != 200) {
            return null;
        }

        JsonElement parsed = GSON.fromJson(response.body(), JsonElement.class);
        if (parsed == null || !parsed.isJsonObject()) {
            return null;
        }

        JsonObject object = parsed.getAsJsonObject();
        Integer userId = firstPresentInt(object, "UserID", "UserId", "Id", "ID");
        if (userId == null) {
            logFetch("GetUserIfAPIKeyValid did not contain a recognizable user id.");
        }
        return userId;
    }

    private static List<DevicePair> fetchDeviceRoutes(Integer userId, String apiKey) throws Exception {
        if (userId == null || userId < 0) {
            logFetch("GetUserDevices skipped because no resolved user id is available yet.");
            return List.of();
        }

        String path = "https://ps.pishock.com/PiShock/GetUserDevices?UserId="
                + userId
                + "&Token="
                + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                + "&api=true";
        HttpResponse<String> response = sendAbsoluteGet(path);
        logFetch("GetUserDevices status=" + response.statusCode() + ", body=" + trimForLog(response.body()));
        if (response.statusCode() != 200) {
            return List.of();
        }

        JsonElement parsed = GSON.fromJson(response.body(), JsonElement.class);
        if (parsed == null || !parsed.isJsonArray()) {
            return List.of();
        }

        List<DevicePair> routes = new ArrayList<>();
        JsonArray shockers = parsed.getAsJsonArray();
        logFetch("GetUserDevices entries=" + shockers.size());
        for (JsonElement element : shockers) {
            if (!element.isJsonObject()) {
                logFetch("Skipping non-object device entry: " + element);
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            addRoutesFromCandidate(candidate, routes);
        }

        logFetch("Parsed routes count=" + routes.size());

        return routes;
    }

    private static void addRoutesFromCandidate(JsonObject candidate, List<DevicePair> routes) {
        logFetch("Parsing candidate=" + trimForLog(candidate.toString()));
        Integer hubId = firstPresentInt(candidate, "HubId", "ClientId", "ClientID", "clientId");

        // Flat shapes: { HubId, ShockerId } or { ClientId, Id }
        Integer flatShockerId = firstPresentInt(candidate, "ShockerId", "Id", "ID", "shockerId");
        if (hubId != null && flatShockerId != null) {
            addRoute(routes, hubId, flatShockerId);
        }

        // Nested shapes: { HubId/ClientId, Shockers: [...] }
        JsonElement nestedShockers = candidate.has("Shockers") ? candidate.get("Shockers") : candidate.get("shockers");
        if (hubId != null && nestedShockers != null && nestedShockers.isJsonArray()) {
            for (JsonElement nestedElement : nestedShockers.getAsJsonArray()) {
                if (!nestedElement.isJsonObject()) {
                    logFetch("Skipping non-object nested shocker entry: " + nestedElement);
                    continue;
                }
                JsonObject nested = nestedElement.getAsJsonObject();
                Integer nestedShockerId = firstPresentInt(nested, "ShockerId", "Id", "ID", "shockerId");
                if (nestedShockerId != null) {
                    addRoute(routes, hubId, nestedShockerId);
                }
            }
        }

        // Optional deeper nesting: { Hubs: [{ HubId/ClientId, Shockers:[...] }] }
        JsonElement hubs = candidate.get("Hubs");
        if (hubs != null && hubs.isJsonArray()) {
            for (JsonElement hubElement : hubs.getAsJsonArray()) {
                if (!hubElement.isJsonObject()) {
                    logFetch("Skipping non-object hub entry: " + hubElement);
                    continue;
                }
                JsonObject hubObj = hubElement.getAsJsonObject();
                Integer nestedHubId = firstPresentInt(hubObj, "HubId", "ClientId", "ClientID", "clientId");
                if (nestedHubId == null) {
                    logFetch("Skipping hub with no recognized hub id: " + trimForLog(hubObj.toString()));
                    continue;
                }
                JsonElement shockerArray = hubObj.has("Shockers") ? hubObj.get("Shockers") : hubObj.get("shockers");
                if (shockerArray == null || !shockerArray.isJsonArray()) {
                    logFetch("Hub has no Shockers array: hubId=" + nestedHubId);
                    continue;
                }
                for (JsonElement shockerElement : shockerArray.getAsJsonArray()) {
                    if (!shockerElement.isJsonObject()) {
                        logFetch("Skipping non-object hub/shocker entry: " + shockerElement);
                        continue;
                    }
                    Integer nestedShockerId = firstPresentInt(shockerElement.getAsJsonObject(), "ShockerId", "Id", "ID", "shockerId");
                    if (nestedShockerId != null) {
                        addRoute(routes, nestedHubId, nestedShockerId);
                    }
                }
            }
        }
    }

    private static void addRoute(List<DevicePair> routes, int hubId, int shockerId) {
        DevicePair route = new DevicePair(hubId, shockerId);
        if (!routes.contains(route)) {
            routes.add(route);
            logFetch("Added route Hub " + hubId + " / Shocker " + shockerId);
        } else {
            logFetch("Duplicate route ignored Hub " + hubId + " / Shocker " + shockerId);
        }
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

    private static HttpResponse<String> sendGet(String path, String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.pishock.com" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("X-PiShock-Api-Key", apiKey)
                .GET()
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendAbsoluteGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
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

    private static int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static DevicePair choosePreferredRoute(List<DevicePair> routes, String currentHubText, String currentShockerText) {
        int currentHub = parseIntOrDefault(currentHubText, -1);
        int currentShocker = parseIntOrDefault(currentShockerText, -1);
        for (DevicePair route : routes) {
            if (route.hubId == currentHub && route.shockerId == currentShocker) {
                return route;
            }
        }
        return routes.get(0);
    }

    private static List<String> toHubShockerDisplayList(List<DevicePair> routes) {
        LinkedHashMap<Integer, List<Integer>> grouped = new LinkedHashMap<>();
        for (DevicePair route : routes) {
            grouped.computeIfAbsent(route.hubId, ignored -> new ArrayList<>());
            List<Integer> shockers = grouped.get(route.hubId);
            if (!shockers.contains(route.shockerId)) {
                shockers.add(route.shockerId);
            }
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : grouped.entrySet()) {
            String shockers = entry.getValue().stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
            lines.add("Hub " + entry.getKey() + " -> [" + shockers + "]");
        }
        return lines;
    }

    private static void logFetch(String message) {
        if (!PiShockConfig.PISHOCK_DEBUG.get()) {
            return;
        }
        Utils.unilog("[PiShock FetchIDs] " + message);
    }

    private static String trimForLog(String value) {
        if (value == null) {
            return "null";
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        int max = 1200;
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, max) + "...(truncated)";
    }

    private static boolean isApiTabSelected(Screen screen) {
        if (screen instanceof me.shedaniel.clothconfig2.gui.ClothConfigScreen clothScreen) {
            Component selected = clothScreen.getSelectedCategory();
            return selected != null && "API".equalsIgnoreCase(selected.getString());
        }
        return true;
    }

    private record DevicePair(int hubId, int shockerId) {
    }

    private record LookupResult(int userId, List<DevicePair> devices) {
    }

    private record FetchContext(
            Screen parentScreen,
            StringListEntry usernameEntry,
            StringListEntry apiKeyEntry,
            StringListEntry userIdEntry,
            StringListEntry hubIdEntry,
            StringListEntry shockerIdEntry
    ) {
    }

    private record ActionButtons(Button fetchButton, Button checkButton, Button testButton) {
    }
}
