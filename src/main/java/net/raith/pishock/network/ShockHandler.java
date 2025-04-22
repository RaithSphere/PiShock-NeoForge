package net.raith.pishock.network;
import net.raith.pishock.Utils;

import com.mojang.authlib.minecraft.client.ObjectMapper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.raith.pishock.PiShock;
import net.raith.pishock.PiShockConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private static final String ENDPOINT = "https://do.pishock.com/api/apioperate";

    // Shock Post to Server
    public static void shock(float damage)
    {
        Utils.unilog("Damage:" +damage);
        Component message = Component.translatable("Starting Shock"); // Customize your message

        PiShock.PiShockMode currentMode = PiShockConfig.PISHOCK_MODE.get();
        int modeId = currentMode.getValue();

        Utils.sendToMinecraftChat("[PiShock] Shocking at " + damage); // Customize your message

        try {
            HashMap<String, Object> args = new HashMap<>();
            args.put("Username", PiShockConfig.PISHOCK_USERNAME.get());
            args.put("Code", PiShockConfig.PISHOCK_CODE.get());
            args.put("ApiKey", PiShockConfig.PISHOCK_APIKEY.get());
            args.put("Op", modeId);
            args.put("Name", "MineCraft");
            args.put("Duration", PiShockConfig.PISHOCK_DURATION.get());
            args.put("Intensity", Math.round(damage));
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

            Utils.unilog("Sent HTTP request (POST) with arguments: " + args);
            Utils.unilog("Request anwsered with response code: " + response.getStatusLine().getStatusCode());

            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
