package ch.zhaw.prometheus.model;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

final class TestPolicyRuntime {
    private TestPolicyRuntime() {
    }

    static PolicyRuntime runtime() {
        return new PolicyRuntime(new PromptMessageAssembler(), new LanguageModelGateway() {
            @Override
            public String complete(List<PromptMessage> messages) {
                return "";
            }

            @Override
            public boolean decide(List<PromptMessage> messages) {
                return false;
            }

            @Override
            public JsonElement extract(List<PromptMessage> messages) {
                return JsonNull.INSTANCE;
            }

            @Override
            public JsonElement summarise(List<PromptMessage> messages) {
                return JsonNull.INSTANCE;
            }

            @Override
            public String summariseOffline(List<PromptMessage> messages) {
                return "";
            }
        });
    }
}
