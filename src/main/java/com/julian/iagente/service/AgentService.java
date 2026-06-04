package com.julian.iagente.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.julian.iagente.config.InMemoryChatStore;

@Service
public class AgentService {

    private final ChatClient chatClient;
    private final InMemoryChatStore memory;

    public AgentService(ChatClient chatClient, InMemoryChatStore memory) {
        this.chatClient = chatClient;
        this.memory = memory;
    }

    public String chat(String userId, String message) {

        // 1. guardar mensaje usuario
        memory.addMessage(userId, new InMemoryChatStore.Message("user", message));

        // 2. construir historial
        List<InMemoryChatStore.Message> history = memory.getHistory(userId);

        StringBuilder context = new StringBuilder();

        for (var m : history) {
            context.append(m.role()).append(": ").append(m.content()).append("\n");
        }

        // 3. llamar modelo
        String response = chatClient.prompt()
                .system("Eres un asistente con memoria de conversación que sabe de todo.")
                .user(context + "\nuser: " + message)
                .call()
                .content();

        // 4. guardar respuesta
        memory.addMessage(userId, new InMemoryChatStore.Message("assistant", response));

        return response;
    }
}