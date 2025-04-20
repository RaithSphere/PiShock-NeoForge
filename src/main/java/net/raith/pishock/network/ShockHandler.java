package net.raith.pishock.network;

import com.mojang.authlib.minecraft.client.ObjectMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.raith.pishock.PiShock;
import net.raith.pishock.PiShockConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.minecraft.client.ObjectMapper;
import net.minecraft.client.Minecraft;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;

public class ShockHandler {
    private static final String ENDPOINT = "https://do.pishock.com/Api/apioperate";

    public static float last = 0;
    private static Date millis = new Date();

    // Shock Post to Server
    public static void shock(float damage, float now, float max, int isAlive, Player player)
    {

        PiShock.LOGGER.info("Damage:" +damage+ " Now:" +now+ " Max:" +max+ " IsAlive: " +isAlive);
        Component message = Component.translatable("Starting Shock"); // Customize your message


        if(!isCooldownOk()) {// If we are in cooldown do NOT SHOCK THE PLAYER
            message = Component.translatable("[PiShock] Cooldown in progress"); // Customize your message
            player.sendSystemMessage(message);
            return;
        }
        if(Minecraft.getInstance().player.isDeadOrDying() && !PiShockConfig.PISHOCK_TRIGGER.get()) {
            return;
        }

        millis = new Date();
        millis.setSeconds(millis.getSeconds() + PiShockConfig.PISHOCK_COOLDOWN.get());

        int mode = PiShockConfig.PISHOCK_MODE.get();
        double m0 = (damage/max) * 100f;
        double m1 = ((max - now) / max) * 100f;

        double intensity = mode == 0 ? m0 : m1;

        if (isAlive == 0)
            intensity = PiShockConfig.PISHOCK_INTENSITY.get();

        message = Component.translatable("[PiShock] Shocking at " + intensity); // Customize your message
        player.sendSystemMessage(message);

        try {
            HashMap<String, Object> args = new HashMap<>();
            args.put("Username", PiShockConfig.PISHOCK_USERNAME.get());
            args.put("Code", PiShockConfig.PISHOCK_CODE.get());
            args.put("ApiKey", PiShockConfig.PISHOCK_APIKEY.get());
            args.put("Op", 0);
            args.put("Name", "MineCraft");
            args.put("Duration", 1);
            args.put("Intensity", Math.round(intensity));
            args.put("Scale", true);

            Gson g = new GsonBuilder().setPrettyPrinting().create();
            String json = new ObjectMapper(g).writeValueAsString(args);
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            int length = out.length;

            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost request = new HttpPost(ENDPOINT);
            request.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-type", "application/json");
            CloseableHttpResponse response = client.execute(request);

            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            PiShock.LOGGER.info("Sent HTTP request (POST) with arguments: " + args);
            PiShock.LOGGER.info("Request anwsered with response code: " + response.getStatusLine().getStatusCode());

            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private static boolean isCooldownOk()
    {
        return new Date().after(millis);
    }
}
