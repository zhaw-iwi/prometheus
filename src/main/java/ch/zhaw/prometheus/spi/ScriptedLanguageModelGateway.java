package ch.zhaw.prometheus.spi;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.spi.script.InteractionScript;
import ch.zhaw.prometheus.spi.script.InteractionScript.GatewayCall;
import ch.zhaw.prometheus.spi.script.InteractionScriptLoader;

@Component
@ConditionalOnProperty(name = "prometheus.gateway.mode", havingValue = "scripted")
public class ScriptedLanguageModelGateway implements LanguageModelGateway {
    private final InteractionScript script;
    private final AtomicInteger cursor;

    public ScriptedLanguageModelGateway(
            @Value("${prometheus.gateway.script:classpath:scripts/multimodal-replay-script.json}") String scriptLocation) {
        this.script = InteractionScriptLoader.load(scriptLocation);
        this.cursor = new AtomicInteger(0);
    }

    @Override
    public String complete(List<PromptMessage> messages) {
        return requireStringValue(nextCall("complete"));
    }

    @Override
    public boolean decide(List<PromptMessage> messages) {
        JsonElement value = requireValue(nextCall("decide"));
        if (!value.isJsonPrimitive()) {
            throw new IllegalStateException("scripted decide response must be a JSON primitive boolean");
        }
        return value.getAsBoolean();
    }

    @Override
    public JsonElement extract(List<PromptMessage> messages) {
        return requireValue(nextCall("extract"));
    }

    @Override
    public JsonElement summarise(List<PromptMessage> messages) {
        return requireValue(nextCall("summarise"));
    }

    @Override
    public String summariseOffline(List<PromptMessage> messages) {
        return requireStringValue(nextCall("summariseOffline"));
    }

    private GatewayCall nextCall(String method) {
        int index = this.cursor.getAndIncrement();
        List<GatewayCall> calls = this.script.getGatewayCalls();
        if (index >= calls.size()) {
            throw new IllegalStateException("scripted gateway exhausted at call " + index + " while expecting " + method);
        }
        GatewayCall current = calls.get(index);
        if (current == null || current.getMethod() == null || !method.equals(current.getMethod())) {
            String encountered = current == null ? "null" : String.valueOf(current.getMethod());
            throw new IllegalStateException(
                    "scripted gateway call mismatch at index " + index + ": expected " + method + " but found "
                            + encountered);
        }
        return current;
    }

    private static JsonElement requireValue(GatewayCall call) {
        if (call == null || call.getValue() == null || call.getValue().isJsonNull()) {
            throw new IllegalStateException("scripted gateway call value is null");
        }
        return call.getValue();
    }

    private static String requireStringValue(GatewayCall call) {
        JsonElement value = requireValue(call);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalStateException("scripted gateway expected a JSON string response");
        }
        return primitive.getAsString();
    }
}
