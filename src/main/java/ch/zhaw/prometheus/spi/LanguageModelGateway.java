package ch.zhaw.prometheus.spi;

import java.util.List;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.policy.PromptMessage;

public interface LanguageModelGateway {
    String REMINDER_DECISION = "Remember to reply with either true or false only so that it can be parsed with the Java programming language. Your answer needs to work with Boolean.parseBoolean() method, which only accepts English true or false.";
    String REMINDER_EXTRACTION = """
            Return valid JSON data that can be parsed with the GSON library for Java.
            If the value extracted is of type string, ensure it is enclosed in double quotes.
            If your response is a JSON object, ensure it starts and ends with curly brackets.
            If your response is a JSON list, ensure it starts and ends with square brackets.
            Return only the raw JSON text without any markdown formatting (do not include triple backticks), explanations, or additional text.
            """;
    String REMINDER_SUMMARISATION = "Remember to reply with the summary in JSON format only so that it can be parsed with a Java program using the GSON library.";

    String complete(List<PromptMessage> messages);

    boolean decide(List<PromptMessage> messages);

    JsonElement extract(List<PromptMessage> messages);

    JsonElement summarise(List<PromptMessage> messages);

    String summariseOffline(List<PromptMessage> messages);
}

