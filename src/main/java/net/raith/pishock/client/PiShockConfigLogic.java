package net.raith.pishock.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PiShockConfigLogic {
    private PiShockConfigLogic() {
    }

    static String resolveShockerIdValue(String initialValue, String apiValue, String serialValue) {
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

    static String trimConfigString(String value) {
        return value == null ? "" : value.trim();
    }

    static List<String> toHubShockerDisplayList(List<DevicePair> routes) {
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

    record DevicePair(int hubId, int shockerId) {
    }
}
