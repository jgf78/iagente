package com.julian.iagente.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julian.iagente.entity.UserMemory;
import com.julian.iagente.repository.UserMemoryRepository;

@Service
public class UserMemoryService {

    private final ChatClient chatClient;
    private final UserMemoryRepository repo;
    private final ObjectMapper objectMapper;

    public UserMemoryService(ChatClient chatClient,
                             UserMemoryRepository repo,
                             ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    public void extractAndSave(String userId, String message) {

        String response = chatClient.prompt()
                .system("""
                    Extrae únicamente hechos permanentes o relevantes del usuario.

                    Devuelve EXCLUSIVAMENTE JSON válido.

                    Ejemplos:

                    {"color_favorito":"verde"}

                    {"ciudad":"Madrid","lenguaje":"Java"}

                    Si no hay información relevante devuelve:

                    {}
                    """)
                .user(message)
                .call()
                .content();

        try {

            Map<String, String> memories =
                    objectMapper.readValue(
                            response,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});

            memories.forEach((key, value) -> {

                if (key != null &&
                    value != null &&
                    !key.isBlank() &&
                    !value.isBlank()) {

                    save(userId, key, value);
                }
            });

        } catch (Exception e) {

            System.err.println("Error parseando memoria:");
            System.err.println(response);
        }
    }

    public void save(String userId,
                     String key,
                     String value) {

        if (isRedundant(userId, key, value)) {
            return; 
        }
        
        repo.findByUserIdAndMemoryKey(userId, key)
                .ifPresentOrElse(existing -> {

                    existing.setMemoryValue(value);

                    repo.save(existing);

                }, () -> {

                    UserMemory memory = UserMemory.builder()
                            .userId(userId)
                            .memoryKey(key)
                            .memoryValue(value)
                            .build();

                    repo.save(memory);
                });
    }

    public List<UserMemory> getMemory(String userId) {
        return repo.findByUserId(userId);
    }
    
    private boolean isRedundant(String userId, String key, String newValue) {

        return repo.findByUserIdAndMemoryKey(userId, key)
                .map(existing -> existing.getMemoryValue()
                        .equalsIgnoreCase(newValue))
                .orElse(false);
    }
}
