package ch.zhaw.prometheus.spi;

import java.util.List;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.policy.PromptMessage;

public class NoOpLanguageModelGateway implements LanguageModelGateway {
    @Override
    public String complete(List<PromptMessage> messages) {
        throw new UnsupportedOperationException("language model gateway is not configured");
    }

    @Override
    public boolean decide(List<PromptMessage> messages) {
        throw new UnsupportedOperationException("language model gateway is not configured");
    }

    @Override
    public JsonElement extract(List<PromptMessage> messages) {
        throw new UnsupportedOperationException("language model gateway is not configured");
    }

    @Override
    public JsonElement summarise(List<PromptMessage> messages) {
        throw new UnsupportedOperationException("language model gateway is not configured");
    }

    @Override
    public String summariseOffline(List<PromptMessage> messages) {
        throw new UnsupportedOperationException("language model gateway is not configured");
    }
}
