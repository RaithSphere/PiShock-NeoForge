package net.raith.pishock.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListListEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import me.shedaniel.clothconfig2.gui.entries.TextListEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.event.ScreenEvent;
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
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PiShockClothConfigScreen {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<Screen, FetchContext> FETCH_CONTEXTS = new WeakHashMap<>();
    private static volatile List<DevicePair> CACHED_DEVICE_ROUTES = List.of();
    private static volatile SerialDisplay SERIAL_DISPLAY = SerialDisplay.empty();

    private PiShockClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return create(parent, false, false);
    }

    public static Screen create(Screen parent, boolean openApiFirst) {
        return create(parent, openApiFirst, false);
    }

    private static Screen create(Screen parent, boolean openApiFirst, boolean openSerialFirst) {
        String initialShockerId = PiShockConfig.PISHOCK_SHOCKER_ID.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(new TextComponent("PiShock Setup"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory behavior;
        ConfigCategory api;
        ConfigCategory serial;
        if (openApiFirst) {
            behavior = builder.getOrCreateCategory(new TextComponent("Behavior"));
            api = builder.getOrCreateCategory(new TextComponent("API"));
            serial = builder.getOrCreateCategory(new TextComponent("Serial"));
        } else {
            behavior = builder.getOrCreateCategory(new TextComponent("Behavior"));
            api = builder.getOrCreateCategory(new TextComponent("API"));
            serial = builder.getOrCreateCategory(new TextComponent("Serial"));
        }
        api.addEntry(entryBuilder.startTextDescription(new TextComponent(
                        "Click Fetch IDs to populate User ID, Hub ID, and Shocker ID from your PiShock account."))
                .build());

        StringListEntry usernameEntry = entryBuilder.startStrField(new TextComponent("Username"), PiShockConfig.PISHOCK_USERNAME.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_USERNAME::set)
                .build();
        api.addEntry(usernameEntry);

        StringListEntry apiKeyEntry = entryBuilder.startStrField(new TextComponent("API Key"), PiShockConfig.PISHOCK_APIKEY.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_APIKEY::set)
                .build();
        api.addEntry(apiKeyEntry);

        StringListEntry userIdEntry = entryBuilder.startStrField(new TextComponent("User ID (-1 = Auto)"), Integer.toString(PiShockConfig.PISHOCK_USER_ID.get()))
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_USER_ID.set(parseIntOrDefault(value, -1)))
                .build();
        api.addEntry(userIdEntry);

        StringListEntry hubIdEntry = entryBuilder.startStrField(new TextComponent("Hub ID (-1 = Auto)"), Integer.toString(PiShockConfig.PISHOCK_HUB_ID.get()))
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_HUB_ID.set(parseIntOrDefault(value, -1)))
                .build();
        api.addEntry(hubIdEntry);

        StringListEntry shockerIdEntry = entryBuilder.startStrField(new TextComponent("Shocker ID (blank = Auto)"), initialShockerId)
                .setSaveConsumer(PiShockConfig.PISHOCK_SHOCKER_ID::set)
                .build();
        api.addEntry(shockerIdEntry);

        StringListListEntry hubShockerListEntry = entryBuilder
                .startStrList(new TextComponent("Hub -> Shockers"), toHubShockerDisplayList(CACHED_DEVICE_ROUTES))
                .setSaveConsumer(value -> {
                    // Display list only; selection is done through Hub ID + Shocker ID fields.
                })
                .setExpanded(true)
                .build();
        api.addEntry(hubShockerListEntry);

        api.addEntry(entryBuilder.startStrField(new TextComponent("Log Identifier"), PiShockConfig.PISHOCK_LOG_IDENTIFIER.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_LOG_IDENTIFIER::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(new TextComponent("Enabled"), PiShockConfig.PISHOCK_ENABLED.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_ENABLED::set)
                .build());
        behavior.addEntry(entryBuilder.startEnumSelector(new TextComponent("Mode"), PiShock.PiShockMode.class, PiShockConfig.PISHOCK_MODE.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_MODE::set)
                .build());
        behavior.addEntry(entryBuilder.startEnumSelector(new TextComponent("Transport"), PiShock.PiShockTransport.class, PiShockConfig.PISHOCK_TRANSPORT.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_TRANSPORT::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(new TextComponent("Trigger On Death"), PiShockConfig.PISHOCK_TRIGGER_ON_DEATH.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_TRIGGER_ON_DEATH::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(new TextComponent("Show Success Confirmation"), PiShockConfig.PISHOCK_SHOW_SUCCESS_CONFIRMATION.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_SHOW_SUCCESS_CONFIRMATION::set)
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(new TextComponent("Debug Logging"), PiShockConfig.PISHOCK_DEBUG.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_DEBUG::set)
                .build());
        behavior.addEntry(entryBuilder.startIntField(new TextComponent("Duration (ms)"), PiShockConfig.PISHOCK_DURATION.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_DURATION.set(clamp(value, 100, 15000)))
                .build());
        behavior.addEntry(entryBuilder.startIntField(new TextComponent("Max Intensity"), PiShockConfig.PISHOCK_INTENSITY.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_INTENSITY.set(clamp(value, 1, 100)))
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(new TextComponent("Combine Rapid Damage"), PiShockConfig.PISHOCK_COMBINE_DAMAGE_EVENTS.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_COMBINE_DAMAGE_EVENTS::set)
                .build());
        behavior.addEntry(entryBuilder.startIntField(new TextComponent("Combine Window (ms)"), PiShockConfig.PISHOCK_COMBINE_WINDOW_MS.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_COMBINE_WINDOW_MS.set(clamp(value, 0, 5000)))
                .build());
        behavior.addEntry(entryBuilder.startBooleanToggle(new TextComponent("Queue Shocks"), PiShockConfig.PISHOCK_QUEUE_ENABLED.get())
                .setSaveConsumer(PiShockConfig.PISHOCK_QUEUE_ENABLED::set)
                .build());
        behavior.addEntry(entryBuilder.startIntField(new TextComponent("Queue Max Size"), PiShockConfig.PISHOCK_QUEUE_MAX_SIZE.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_QUEUE_MAX_SIZE.set(clamp(value, 1, 512)))
                .build());

        serial.addEntry(entryBuilder.startTextDescription(new TranslatableComponent("pishock.configuration.serial.description"))
                .build());
        String[] serialPortOptions = serialPortOptions();
        SelectionListEntry<String> serialPortEntry = entryBuilder.startSelector(new TranslatableComponent("pishock.configuration.serial.port"), serialPortOptions, selectedSerialPort(serialPortOptions))
                .setNameProvider(PiShockClothConfigScreen::serialPortLabel)
                .setSaveConsumer(PiShockClothConfigScreen::setSerialPort)
                .build();
        IntegerListEntry serialBaudEntry = entryBuilder.startIntField(new TranslatableComponent("pishock.configuration.serial.baud"), PiShockConfig.PISHOCK_SERIAL_BAUD.get())
                .setSaveConsumer(value -> PiShockConfig.PISHOCK_SERIAL_BAUD.set(clamp(value, 1200, 921600)))
                .build();
        StringListEntry serialShockerIdEntry = entryBuilder.startStrField(new TranslatableComponent("pishock.configuration.serial.shocker_id"), initialShockerId)
                .setSaveConsumer(PiShockConfig.PISHOCK_SHOCKER_ID::set)
                .build();
        SerialContext serialContext = new SerialContext(parent, usernameEntry, apiKeyEntry, userIdEntry, hubIdEntry, shockerIdEntry, serialShockerIdEntry, serialPortEntry, serialBaudEntry);
        serial.addEntry(new SerialControlsEntry(
                queryButton -> runSerialConnect(queryButton, serialContext),
                saveButton -> saveSerialConfig(saveButton, serialContext)
        ));
        serial.addEntry(new RightAlignedInfoEntry("pishock.configuration.serial.type", () -> SERIAL_DISPLAY.type()));
        serial.addEntry(new RightAlignedInfoEntry("pishock.configuration.serial.client_id", () -> SERIAL_DISPLAY.clientId()));
        serial.addEntry(new RightAlignedInfoEntry("pishock.configuration.serial.firmware", () -> SERIAL_DISPLAY.firmware()));
        serial.addEntry(new RightAlignedInfoEntry("pishock.configuration.serial.shockers", () -> SERIAL_DISPLAY.shockers()));
        serial.addEntry(serialShockerIdEntry);
        serial.addEntry(serialPortEntry);
        serial.addEntry(serialBaudEntry);

        builder.setAfterInitConsumer(screen -> {
            synchronized (FETCH_CONTEXTS) {
                FETCH_CONTEXTS.put(screen, new FetchContext(parent, usernameEntry, apiKeyEntry, userIdEntry, hubIdEntry, shockerIdEntry));
            }
        });

        builder.setSavingRunnable(() -> {
            PiShockConfig.PISHOCK_SHOCKER_ID.set(resolveShockerIdValue(
                    initialShockerId,
                    shockerIdEntry.getValue(),
                    serialShockerIdEntry.getValue()
            ));
            PiShockConfig.save();
        });
        return builder.build();
    }

    private static String resolveShockerIdValue(String initialValue, String apiValue, String serialValue) {
        String initial = trimConfigString(initialValue);
        String api = trimConfigString(apiValue);
        String serial = trimConfigString(serialValue);
        boolean apiChanged = !api.equals(initial);
        boolean serialChanged = !serial.equals(initial);

        if (apiChanged && !serialChanged) {
            return api;
        }
        if (serialChanged) {
            return serial;
        }
        return api;
    }

    public static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
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

        Button fetchButton = new Button(x, y, fetchWidth, buttonHeight, new TextComponent("Fetch IDs"), button -> fetchIds(button, context));
        event.addListener(fetchButton);

        Button checkButton = new Button(x, y + buttonHeight + 4, smallWidth, buttonHeight, new TextComponent("Check"), PiShockClothConfigScreen::runConnectivityCheck);
        event.addListener(checkButton);

        Button testVibrationButton = new Button(x + smallWidth + gap, y + buttonHeight + 4, smallWidth, buttonHeight, new TextComponent("Test"), PiShockClothConfigScreen::runVibrationTest);
        event.addListener(testVibrationButton);
    }

    public static void onScreenRender(ScreenEvent.DrawScreenEvent.Post event) {
    }

    private static void fetchIds(Button button, FetchContext context) {
        String username = context.usernameEntry.getValue().trim();
        String apiKey = context.apiKeyEntry.getValue().trim();
        logFetch("Fetch IDs pressed. usernameSet=" + !username.isEmpty() + ", apiKeySet=" + !apiKey.isEmpty());

        if (username.isEmpty() || apiKey.isEmpty()) {
            button.setMessage(new TextComponent("Need creds"));
            return;
        }

        button.active = false;
        button.setMessage(new TextComponent("Fetching..."));

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
                            button.setMessage(new TextComponent("Fetch failed"));
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
                        button.setMessage(new TextComponent("Fetched"));
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
        button.setMessage(new TextComponent("..."));
        ShockHandler.checkConnectivityAsync().whenComplete((message, throwable) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                button.active = true;
                if (throwable != null) {
                    button.setMessage(new TextComponent("Fail"));
                    return;
                }

                boolean ok = message != null && (message.contains("Connectivity OK") || message.contains("Serial check OK"));
                button.setMessage(new TextComponent(ok ? "OK" : "Issue"));
            });
        });
    }

    private static void runVibrationTest(Button button) {
        button.active = false;
        button.setMessage(new TextComponent("..."));
        ShockHandler.sendVibrationTestAsync().whenComplete((message, throwable) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                button.active = true;
                if (throwable != null) {
                    button.setMessage(new TextComponent("Fail"));
                    return;
                }

                boolean ok = message != null && message.contains("sent");
                button.setMessage(new TextComponent(ok ? "Sent" : "Fail"));
            });
        });
    }

    private static void runSerialConnect(Button button, SerialContext context) {
        applySerialConfig(context);
        button.active = false;
        button.setMessage(new TextComponent("..."));
        ShockHandler.fetchSerialInfoAsync().whenComplete((result, throwable) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                button.active = true;
                if (throwable != null || result == null || !result.success()) {
                    button.setMessage(new TextComponent("Fail"));
                    Utils.sendToMinecraftChat("[PiShock] Serial connect failed.");
                    return;
                }

                if (result.clientId() != null) {
                    context.hubIdEntry.setValue(Integer.toString(result.clientId()));
                    PiShockConfig.PISHOCK_HUB_ID.set(result.clientId());
                }
                if (result.shockerId() != null) {
                    context.shockerIdEntry.setValue(Integer.toString(result.shockerId()));
                    context.serialShockerIdEntry.setValue(Integer.toString(result.shockerId()));
                    PiShockConfig.PISHOCK_SHOCKER_ID.set(Integer.toString(result.shockerId()));
                }
                PiShockConfig.save();
                SERIAL_DISPLAY = SerialDisplay.from(result);

                button.setMessage(new TextComponent("Found"));
                Utils.sendToMinecraftChat(result.message());
            });
        });
    }

    private static void saveSerialConfig(Button button, SerialContext context) {
        applySerialConfig(context);
        button.setMessage(new TextComponent("Saved"));
        Utils.sendToMinecraftChat("[PiShock] Serial settings saved.");
    }

    private static void setSerialPort(String value) {
        PiShockConfig.PISHOCK_SERIAL_PORT.set(value == null ? "" : value);
    }

    private static void applySerialConfig(SerialContext context) {
        PiShockConfig.PISHOCK_SERIAL_PORT.set(context.serialPortEntry.getValue() == null ? "" : context.serialPortEntry.getValue());
        PiShockConfig.PISHOCK_SERIAL_BAUD.set(clamp(context.serialBaudEntry.getValue(), 1200, 921600));
        saveOpenConfigScreen();
        PiShockConfig.PISHOCK_SERIAL_PORT.set(context.serialPortEntry.getValue() == null ? "" : context.serialPortEntry.getValue());
        PiShockConfig.PISHOCK_SERIAL_BAUD.set(clamp(context.serialBaudEntry.getValue(), 1200, 921600));
        PiShockConfig.save();
    }

    private static void saveOpenConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof me.shedaniel.clothconfig2.gui.ClothConfigScreen clothScreen) {
            clothScreen.save();
            return;
        }
        PiShockConfig.save();
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

    private static String trimConfigString(String value) {
        return value == null ? "" : value.trim();
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

    private static String[] serialPortOptions() {
        List<String> ports = new ArrayList<>(ShockHandler.listSerialPortOptions());
        String configuredPort = PiShockConfig.PISHOCK_SERIAL_PORT.get();
        if (configuredPort != null && !configuredPort.isBlank() && !ports.contains(configuredPort)) {
            ports.add(configuredPort);
        }
        return ports.toArray(String[]::new);
    }

    private static String selectedSerialPort(String[] options) {
        String configuredPort = PiShockConfig.PISHOCK_SERIAL_PORT.get();
        if (configuredPort == null || configuredPort.isBlank()) {
            return "";
        }
        for (String option : options) {
            if (configuredPort.equals(option)) {
                return option;
            }
        }
        return "";
    }

    private static Component serialPortLabel(String port) {
        return port == null || port.isBlank()
                ? new TranslatableComponent("pishock.configuration.serial.auto_detect")
                : new TextComponent(port);
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

    private record DevicePair(int hubId, int shockerId) {
    }

    private record LookupResult(int userId, List<DevicePair> devices) {
    }

    private record SerialDisplay(String type, String clientId, String firmware, String shockers) {
        private static SerialDisplay empty() {
            return new SerialDisplay("", "", "", "");
        }

        private static SerialDisplay from(ShockHandler.SerialInfoResult result) {
            return new SerialDisplay(
                    formatType(result.type()),
                    result.clientId() == null ? "" : Integer.toString(result.clientId()),
                    formatFirmware(result.firmwareVersion()),
                    result.shockers() == null ? "" : result.shockers()
            );
        }

        private static String formatType(Integer type) {
            if (type == null) {
                return "";
            }
            return switch (type) {
                case 3 -> "Next (3)";
                case 4 -> "Lite (4)";
                default -> Integer.toString(type);
            };
        }

        private static String formatFirmware(String firmwareVersion) {
            if (firmwareVersion == null || firmwareVersion.isBlank()) {
                return "";
            }
            String[] parts = firmwareVersion.split("\\.");
            if (parts.length < 3) {
                return firmwareVersion;
            }
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
    }

    private static final class RightAlignedInfoEntry extends TextListEntry {
        private static final int TEXT_PADDING = 6;
        private final Component label;
        private final Supplier<String> valueSupplier;

        private RightAlignedInfoEntry(String translationKey, Supplier<String> valueSupplier) {
            super(new TranslatableComponent(translationKey), TextComponent.EMPTY);
            this.label = new TranslatableComponent(translationKey);
            this.valueSupplier = valueSupplier;
        }

        @Override
        public void render(PoseStack poseStack, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            var font = Minecraft.getInstance().font;
            int textY = y + (entryHeight - font.lineHeight) / 2;
            font.draw(poseStack, label, x + TEXT_PADDING, textY, getPreferredTextColor());
            String rawValue = valueSupplier.get();
            Component value = new TextComponent(rawValue == null || rawValue.isBlank() ? "-" : rawValue);
            int valueWidth = font.width(value);
            font.draw(poseStack, value, x + entryWidth - valueWidth - TEXT_PADDING, textY, getPreferredTextColor());
        }
    }

    private static final class SerialControlsEntry extends TextListEntry {
        private static final int TEXT_PADDING = 6;
        private static final int BUTTON_WIDTH = 72;
        private static final int BUTTON_HEIGHT = 20;
        private static final int BUTTON_GAP = 4;
        private static final int ENTRY_HEIGHT = 28;
        private final Component label = new TranslatableComponent("pishock.configuration.serial.connection");
        private final Button queryButton;
        private final Button saveButton;

        private SerialControlsEntry(Consumer<Button> onQuery, Consumer<Button> onSave) {
            super(new TranslatableComponent("pishock.configuration.serial.connection"), TextComponent.EMPTY);
            this.queryButton = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("pishock.configuration.serial.query"), onQuery::accept);
            this.saveButton = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("pishock.configuration.serial.save"), onSave::accept);
        }

        @Override
        public void render(PoseStack poseStack, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            var font = Minecraft.getInstance().font;
            int textY = y + (entryHeight - font.lineHeight) / 2;
            font.draw(poseStack, label, x + TEXT_PADDING, textY, getPreferredTextColor());
            int buttonsWidth = (BUTTON_WIDTH * 2) + BUTTON_GAP;
            int buttonY = y + (entryHeight - BUTTON_HEIGHT) / 2;
            queryButton.x = x + entryWidth - buttonsWidth - TEXT_PADDING;
            queryButton.y = buttonY;
            saveButton.x = x + entryWidth - BUTTON_WIDTH - TEXT_PADDING;
            saveButton.y = buttonY;
            queryButton.render(poseStack, mouseX, mouseY, delta);
            saveButton.render(poseStack, mouseX, mouseY, delta);
        }

        @Override
        public int getItemHeight() {
            return ENTRY_HEIGHT;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(queryButton, saveButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(queryButton, saveButton);
        }
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

    private record SerialContext(
            Screen parentScreen,
            StringListEntry usernameEntry,
            StringListEntry apiKeyEntry,
            StringListEntry userIdEntry,
            StringListEntry hubIdEntry,
            StringListEntry shockerIdEntry,
            StringListEntry serialShockerIdEntry,
            SelectionListEntry<String> serialPortEntry,
            IntegerListEntry serialBaudEntry
    ) {
    }

}
