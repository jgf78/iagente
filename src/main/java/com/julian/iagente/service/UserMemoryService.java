package com.julian.iagente.service;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.julian.iagente.entity.UserMemory;
import com.julian.iagente.repository.UserMemoryRepository;

@Service
public class UserMemoryService {

    private final ChatClient chatClient;
    private final UserMemoryRepository repo;

    public UserMemoryService(ChatClient chatClient, UserMemoryRepository repo) {
        this.chatClient = chatClient;
        this.repo = repo;
    }

    public void extractAndSave(String userId, String message) {

        String response = chatClient.prompt().system("""
                    Extrae hechos importantes del usuario.
                    Devuelve SOLO JSON en este formato:

                    {
                      "key": "value"
                    }

                    Si no hay nada importante, devuelve {}
                """).user(message).call().content();

        if (response == null || response.contains("{}")) {
            return;
        }

        try {
            String cleaned = response.replace("{", "").replace("}", "").replace("\"", "");

            String[] parts = cleaned.split(":");

            if (parts.length == 2) {
                save(userId, parts[0].trim(), parts[1].trim());
            }

        } catch (Exception e) {
            // fallback silencioso
        }
    }

    public void save(String userId, String key, String value) {

        Optional<UserMemory> existing = repo.findByUserIdAndMemoryKey(userId, key);

        if (existing.isPresent()) {

            UserMemory memory = existing.get();

            memory.setMemoryValue(value);

            repo.save(memory);

        } else {

            UserMemory memory = UserMemory.builder().userId(userId).memoryKey(key).memoryValue(value).build();

            repo.save(memory);
        }
    }

    public List<UserMemory> getMemory(String userId) {
        return repo.findByUserId(userId);
    }
}