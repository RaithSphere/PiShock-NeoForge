package net.raith.pishock.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.raith.pishock.PiShockConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class PiShockConfigScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Screen parent;

    private EditBox usernameField;
    private EditBox apiKeyField;
    private EditBox userIdField;
    private EditBox hubIdField;
    private EditBox shockerIdField;
    private EditBox logIdentifierField;
    private EditBox durationField;
    private EditBox intensityField;
    private CycleButton<Boolean> triggerOnDeathButton;

    private Button fetchIdsButton;
    private Component statusMessage = new TextComponent("");

    public PiShockConfigScreen(Screen parent) {
        super(new TextComponent("PiShock Setup"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = 260;
        int left = centerX - (fieldWidth / 2);
        int y = 36;

        this.usernameField = new EditBox(this.font, left, y, fieldWidth, 20, new TextComponent("Username"));
        this.usernameField.setValue(PiShockConfig.PISHOCK_USERNAME.get());
        this.addRenderableWidget(this.usernameField);

        y += 24;
        this.apiKeyField = new EditBox(this.font, left, y, fieldWidth, 20, new TextComponent("API Key"));
        this.apiKeyField.setValue(PiShockConfig.PISHOCK_APIKEY.get());
        this.addRenderableWidget(this.apiKeyField);

        y += 24;
        this.userIdField = new EditBox(this.font, left, y, fieldWidth, 20, new TextComponent("User ID"));
        this.userIdField.setValue(Integer.toString(PiShockConfig.PISHOCK_USER_ID.get()));
        this.addRenderableWidget(this.userIdField);

        y += 24;
        this.hubIdField = new EditBox(this.font, left, y, fieldWidth, 20, new TextComponent("Hub ID"));
        this.hubIdField.setValue(Integer.toString(PiShockConfig.PISHOCK_HUB_ID.get()));
        this.addRenderableWidget(this.hubIdField);

        y += 24;
        this.shockerIdField = new EditBox(this.font, left, y, fieldWidth, 20, new TextComponent("Shocker ID"));
        this.shockerIdField.setValue(PiShockConfig.PISHOCK_SHOCKER_ID.get());
        this.addRenderableWidget(this.shockerIdField);

        y += 24;
        this.logIdentifierField = new EditBox(this.font, left, y, fieldWidth, 20, new TextComponent("Log Identifier"));
        this.logIdentifierField.setValue(PiShockConfig.PISHOCK_LOG_IDENTIFIER.get());
        this.addRenderableWidget(this.logIdentifierField);

        y += 24;
        this.durationField = new EditBox(this.font, left, y, 126, 20, new TextComponent("Duration (ms)"));
        this.durationField.setValue(Integer.toString(PiShockConfig.PISHOCK_DURATION.get()));
        this.addRenderableWidget(this.durationField);

        this.intensityField = new EditBox(this.font, left + 134, y, 126, 20, new TextComponent("Max Intensity"));
        this.intensityField.setValue(Integer.toString(PiShockConfig.PISHOCK_INTENSITY.get()));
        this.addRenderableWidget(this.intensityField);

        y += 28;
        this.triggerOnDeathButton = CycleButton.onOffBuilder(PiShockConfig.PISHOCK_TRIGGER_ON_DEATH.get())
                .create(left, y, fieldWidth, 20, new TextComponent("Trigger On Death"));
        this.addRenderableWidget(this.triggerOnDeathButton);

        y += 28;
        this.fetchIdsButton = new Button(left, y, 126, 20, new TextComponent("Fetch IDs"), btn -> fetchIds());
        this.addRenderableWidget(this.fetchIdsButton);

        this.addRenderableWidget(new Button(left + 134, y, 126, 20, new TextComponent("Advanced Config"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(PiShockClothConfigScreen.create(this));
                    }
                }));

        y += 28;
        this.addRenderableWidget(new Button(left, y, 126, 20, new TextComponent("Save"), btn -> saveAndClose()));

        this.addRenderableWidget(new Button(left + 134, y, 126, 20, new TextComponent("Cancel"), btn -> onClose()));
    }

    private void fetchIds() {
        String username = this.usernameField.getValue().trim();
        String apiKey = this.apiKeyField.getValue().trim();

        if (username.isEmpty() || apiKey.isEmpty()) {
            this.statusMessage = new TextComponent("Enter Username and API Key first.");
            return;
        }

        this.fetchIdsButton.active = false;
        this.statusMessage = new TextComponent("Fetching IDs from PiShock API...");

        CompletableFuture.runAsync(() -> {
            try {
                Integer userId = fetchUserId(apiKey);
                DevicePair routing = fetchDeviceRouting(apiKey);

                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        if (userId != null) {
                            this.userIdField.setValue(Integer.toString(userId));
                        }
                        if (routing != null) {
                            this.hubIdField.setValue(Integer.toString(routing.hubId));
                            this.shockerIdField.setValue(Integer.toString(routing.shockerId));
                        }

                        if (userId != null && routing != null) {
                            this.statusMessage = new TextComponent("Fetched UserId/HubId/ShockerId successfully.");
                        } else if (userId != null) {
                            this.statusMessage = new TextComponent("Fetched UserId, but no valid shocker routing found.");
                        } else {
                            this.statusMessage = new TextComponent("Could not fetch IDs. Check API key/account access.");
                        }
                        this.fetchIdsButton.active = true;
                    });
                }
            } catch (Exception ex) {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        this.statusMessage = new TextComponent("Fetch failed: " + ex.getMessage());
                        this.fetchIdsButton.active = true;
                    });
                }
            }
        });
    }

    private Integer fetchUserId(String apiKey) throws Exception {
        HttpResponse<String> response = sendJsonRequest("/Account", apiKey);
        if (response.statusCode() != 200) {
            return null;
        }

        JsonElement parsed = GSON.fromJson(response.body(), JsonElement.class);
        if (parsed == null || !parsed.isJsonObject()) {
            return null;
        }

        return getInt(parsed.getAsJsonObject(), "UserId");
    }

    private DevicePair fetchDeviceRouting(String apiKey) throws Exception {
        HttpResponse<String> response = sendJsonRequest("/Shockers", apiKey);
        if (response.statusCode() != 200) {
            return null;
        }

        JsonElement parsed = GSON.fromJson(response.body(), JsonElement.class);
        if (parsed == null || !parsed.isJsonArray()) {
            return null;
        }

        JsonArray shockers = parsed.getAsJsonArray();
        for (JsonElement element : shockers) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            Integer hubId = getInt(candidate, "HubId");
            Integer shockerId = getInt(candidate, "ShockerId");
            if (hubId != null && shockerId != null) {
                return new DevicePair(hubId, shockerId);
            }
        }

        return null;
    }

    private HttpResponse<String> sendJsonRequest(String path, String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.pishock.com" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("X-PiShock-Api-Key", apiKey)
                .GET()
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void saveAndClose() {
        PiShockConfig.PISHOCK_USERNAME.set(this.usernameField.getValue().trim());
        PiShockConfig.PISHOCK_APIKEY.set(this.apiKeyField.getValue().trim());
        PiShockConfig.PISHOCK_SHOCKER_ID.set(this.shockerIdField.getValue().trim());
        PiShockConfig.PISHOCK_LOG_IDENTIFIER.set(this.logIdentifierField.getValue().trim());

        PiShockConfig.PISHOCK_USER_ID.set(parseIntOrDefault(this.userIdField.getValue(), -1));
        PiShockConfig.PISHOCK_HUB_ID.set(parseIntOrDefault(this.hubIdField.getValue(), -1));
        PiShockConfig.PISHOCK_DURATION.set(clamp(parseIntOrDefault(this.durationField.getValue(), 1000), 100, 15000));
        PiShockConfig.PISHOCK_INTENSITY.set(clamp(parseIntOrDefault(this.intensityField.getValue(), 80), 1, 100));
        PiShockConfig.PISHOCK_TRIGGER_ON_DEATH.set(this.triggerOnDeathButton.getValue());

        PiShockConfig.save();
        onClose();
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

    private int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int left = centerX - 130;

        drawCenteredString(poseStack, this.font, this.title, centerX, 12, 0xFFFFFF);

        this.font.draw(poseStack, "Username", left, 28, 0xA0A0A0);
        this.font.draw(poseStack, "API Key", left, 52, 0xA0A0A0);
        this.font.draw(poseStack, "User ID", left, 76, 0xA0A0A0);
        this.font.draw(poseStack, "Hub ID", left, 100, 0xA0A0A0);
        this.font.draw(poseStack, "Shocker ID", left, 124, 0xA0A0A0);
        this.font.draw(poseStack, "Log Identifier", left, 148, 0xA0A0A0);

        if (!this.statusMessage.getString().isEmpty()) {
            drawCenteredString(poseStack, this.font, this.statusMessage, centerX, this.height - 24, 0xFFD37F);
        }
    }

    private record DevicePair(int hubId, int shockerId) {
    }
}
