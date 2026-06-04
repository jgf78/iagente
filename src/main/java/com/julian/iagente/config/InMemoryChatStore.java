package com.julian.iagente.config;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class InMemoryChatStore {

    private final Map<String, List<Message>> store = new HashMap<>();

    public List<Message> getHistory(String userId) {
        return store.getOrDefault(userId, new ArrayList<>());
    }

    public void addMessage(String userId, Message message) {
        store.computeIfAbsent(userId, k -> new ArrayList<>()).add(message);
    }

    public record Message(String role, String content) {}
}