package ch.zhaw.prometheus.model.hand;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;

/** The opt-in per-hand wire contract. Legacy RPS payloads are deliberately separate. */
public record HandSignObservation(String observationId, String source, Instant observed, List<Hand> hands) {
    public static final Set<String> SIGNS = Set.of("fist", "peace", "open_palm", "thumbs_up", "thumbs_down", "index_up");
    public record Hand(String id, String handedness, String sign, Double confidence) {}
    public HandSignObservation { hands = List.copyOf(hands); }

    public static HandSignObservation parse(String payload) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > 16384) throw new IllegalArgumentException("invalid_contract");
        try {
            var data = JsonParser.parseString(payload).getAsJsonObject();
            exact(data, "schemaVersion", "observationId", "source", "ts", "frame", "hands");
            integer(data.get("schemaVersion"), 1, 1);
            String id = identifier(data.get("observationId")), source = identifier(data.get("source"));
            String timestamp = text(data.get("ts"));
            if (timestamp.length() > 40) throw new IllegalArgumentException();
            Instant observed = Instant.parse(timestamp);
            var frame = data.getAsJsonObject("frame"); exact(frame, "id", "revision", "space", "mirrored");
            identifier(frame.get("id")); integer(frame.get("revision"), 1, Integer.MAX_VALUE);
            if (!"camera_image".equals(text(frame.get("space"))) || !frame.get("mirrored").isJsonPrimitive()
                    || !frame.get("mirrored").getAsJsonPrimitive().isBoolean() || frame.get("mirrored").getAsBoolean()) throw new IllegalArgumentException();
            var raw = data.getAsJsonArray("hands");
            if (raw.size() > 2) throw new IllegalArgumentException();
            var hands = new ArrayList<Hand>(); var ids = new HashSet<String>();
            for (var item : raw) {
                var hand = item.getAsJsonObject(); exact(hand, "id", "handedness", "sign", "confidence");
                String handId = identifier(hand.get("id")), side = text(hand.get("handedness"));
                if (!ids.add(handId) || !Set.of("left", "right", "unknown").contains(side)) throw new IllegalArgumentException();
                String sign = hand.get("sign").isJsonNull() ? null : text(hand.get("sign"));
                if (sign != null && !SIGNS.contains(sign)) throw new IllegalArgumentException("unsupported_capability");
                Double confidence = hand.get("confidence").isJsonNull() ? null : number(hand.get("confidence"));
                hands.add(new Hand(handId, side, sign, confidence));
            }
            return new HandSignObservation(id, source, observed, hands);
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("invalid_contract"); }
    }
    public String identity() { return source + "/" + observationId; }
    public static String text(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    public static String identifier(JsonElement value) {
        String text = text(value);
        if (!text.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,79}")) throw new IllegalArgumentException();
        return text;
    }
    public static void exact(JsonObject object, String... fields) {
        if (!object.keySet().equals(Set.of(fields))) throw new IllegalArgumentException();
    }
    public static double number(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < 0 || number > 1) throw new IllegalArgumentException();
        return number;
    }
    private static void integer(JsonElement value, int min, int max) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        int number = value.getAsBigDecimal().intValueExact();
        if (number < min || number > max) throw new IllegalArgumentException();
    }
}
