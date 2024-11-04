package com.example.personalstatement.service;

import com.theokanning.openai.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.personalstatement.service.AssistantApi.*;
import com.example.personalstatement.service.Data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AssistantAPIHandler {
    private final AssistantApi APIClient;
    private final Data.Assistant assistant;

    public AssistantAPIHandler(@Value("${spring.ai.openai.api-key}") String auth,
                         @Value("${spring.ai.openai.assistant-api-key}") String apiKey) {
        if (auth == null || auth.isEmpty()) {
            throw new IllegalArgumentException("OpenAI API key must be set.");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Assistant API key must be set.");
        }
        this.APIClient = new AssistantApi(auth);
        this.assistant = this.APIClient.retrieveAssistant(apiKey);
    }

    private DataList<Message> RunAndGetResponse(Data.Thread thread, String messages) {
        this.APIClient.createMessage( new MessageRequest(
                        Role.user, messages),
                thread.id()
        );
        Data.Run run = this.APIClient.createRun(
                thread.id(),
                new RunRequest(this.assistant.id())
        );
        while (this.APIClient.retrieveRun(thread.id(), run.id()).status() != Run.Status.completed) {
            try {
                java.lang.Thread.sleep(500);
            } catch (InterruptedException e) {}
        }
        return this.APIClient.listMessages(new ListRequest(), thread.id());
    }

    private String getAssistantResponse(DataList<Message> messages) {
        // Filter out the assistant messages only.
        List<Message> assistantMessages = messages.data().stream()
                .filter(msg -> msg.role() == Role.assistant).toList();

        List<String> resault = new ArrayList<>();

        for (Message message  : assistantMessages) {
            for (Content content : message.content()) {
                if (content.type() == Content.Type.text) {
                    resault.add(content.text().value());
                }
            }
        }

        return String.join("\n ", resault);
    }

    public String SendMassageAndGetResponse(String message) {
        Data.Thread thread = this.APIClient.createThread(new Data.ThreadRequest());

        DataList<Message> messages = RunAndGetResponse(thread,message);
        return getAssistantResponse(messages);
    }
}
