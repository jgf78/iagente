package com.julian.iagente.service;



import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julian.iagente.entity.UserMemory;
import com.julian.iagente.model.UserMemoryDTO;
import com.julian.iagente.repository.UserMemoryRepository;

@Service
public class UserMemoryService {

    private static final Logger log = LoggerFactory.getLogger(UserMemoryService.class);

    private final ChatClient chatClient;
    private final UserMemoryRepository repo;
    private final ObjectMapper objectMapper;

    private final Map<String, String> childIdentityCache = new ConcurrentHashMap<>();

    public UserMemoryService(ChatClient chatClient, UserMemoryRepository repo, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    public static class MemoryItem {
        public String subject;
        public String attribute;
        public String value;
    }

    private static final List<String> ALLOWED_ATTRIBUTES = List.of(
            "nombre", "fecha_nacimiento", "edad", "ciudad", "gustos", "pareja", "trabajo"
    );

    private static final List<String> ALLOWED_SUBJECTS = List.of(
            "self", "pareja", "hijo"
    );

    public void extractAndSave(String userId, String message) {

        String response = chatClient.prompt()
                .system("""
                        Eres un extractor de memoria estructurada.

                        REGLAS OBLIGATORIAS:
                        - Devuelve ÚNICAMENTE JSON válido
                        - NO expliques nada
                        - NO texto adicional
                        - NO markdown
                        - NO frases tipo "claro", "aquí tienes"
                        - SIEMPRE devuelve una lista JSON

                        FORMATO OBLIGATORIO:
                        [
                          {
                            "subject": "self|pareja|hijo",
                            "attribute": "nombre|edad|ciudad|gustos|fecha_nacimiento|trabajo",
                            "value": "string"
                          }
                        ]

                        Si no hay memoria útil devuelve: []
                        """)
                .user(message)
                .call()
                .content();

        log.info("MEMORY EXTRACTOR RESPONSE -> {}", response);

        try {

            if (response == null || response.isBlank()) {
                return;
            }

            String json = extractJsonArray(response);

            if (json == null) {
                log.warn("MEMORY SKIPPED -> response is not valid JSON array");
                return;
            }

            List<MemoryItem> memories = objectMapper.readValue(
                    json,
                    new TypeReference<List<MemoryItem>>() {}
            );

            for (MemoryItem m : memories) {

                if (m == null) continue;

                String subject = normalizeSubject(m.subject);
                String attribute = normalizeAttribute(m.attribute);

                if (!ALLOWED_ATTRIBUTES.contains(attribute)) {
                    log.warn("REJECTED ATTRIBUTE -> {}", attribute);
                    continue;
                }

                if (attribute.contains("|")) {
                    log.warn("REJECTED MULTI ATTRIBUTE -> {}", attribute);
                    continue;
                }

                String value = m.value == null ? null : m.value.trim();

                if (isBlank(subject) || isBlank(attribute) || isBlank(value)) {
                    log.warn("REJECTED EMPTY MEMORY -> subject={}, attribute={}, value={}",
                            subject, attribute, value);
                    continue;
                }

                if (!ALLOWED_SUBJECTS.contains(subject)) {
                    log.warn("REJECTED SUBJECT -> {}", subject);
                    continue;
                }

                if (isInvalidValue(value)) {
                    log.warn("REJECTED VALUE -> {}", value);
                    continue;
                }

                String normalizedSubject = resolveChildSubject(userId, m, message);

                save(userId, normalizedSubject, attribute, value);
            }

        } catch (Exception e) {
            log.error("ERROR PARSING MEMORY");
            log.error("RAW RESPONSE -> {}", response, e);
        }
    }

    private String extractJsonArray(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start == -1 || end == -1 || end <= start) {
            return null;
        }

        return response.substring(start, end + 1);
    }

    public void save(String userId, String subject, String attribute, String value) {

        String key = "persona:" + subject + ":" + attribute;

        if (isRedundant(userId, key, value)) {
            log.info("MEMORY REDUNDANT -> {}={}", key, value);
            return;
        }

        repo.findByUserIdAndMemoryKey(userId, key).ifPresentOrElse(existing -> {

            log.info("MEMORY UPDATE -> {} : {} -> {}", key, existing.getMemoryValue(), value);

            existing.setMemoryValue(value);
            repo.save(existing);

        }, () -> {

            log.info("MEMORY INSERT -> {} = {}", key, value);

            UserMemory memory = UserMemory.builder()
                    .userId(userId)
                    .memoryKey(key)
                    .memoryValue(value)
                    .build();

            repo.save(memory);
        });
    }

    public List<UserMemoryDTO> getMemory(String userId) {
        return repo.findByUserId(userId)
                .stream()
                .map(m -> new UserMemoryDTO(m.getMemoryKey(), m.getMemoryValue()))
                .toList();
    }

    private boolean isRedundant(String userId, String key, String newValue) {
        return repo.findByUserIdAndMemoryKey(userId, key)
                .map(existing -> existing.getMemoryValue().equalsIgnoreCase(newValue))
                .orElse(false);
    }

    private String resolveChildSubject(String userId, MemoryItem m, String originalMessage) {
        if (!"hijo".equals(m.subject)) return m.subject;

        if ("nombre".equals(m.attribute)) {
            String name = extractChildName(m.value);
            if (name != null) {
                String normalized = normalize(name);
                childIdentityCache.put(userId, normalized);
                return "hijo:" + normalized;
            }
        }

        String cached = childIdentityCache.get(userId);
        if (cached != null) return "hijo:" + cached;

        String inferred = extractChildName(originalMessage);
        if (inferred != null) {
            String normalized = normalize(inferred);
            childIdentityCache.put(userId, normalized);
            return "hijo:" + normalized;
        }

        return "hijo:unknown";
    }

    private String extractChildName(String value) {
        if (value == null) return null;

        String[] tokens = value.split(" ");
        if (tokens.length > 0) {
            String first = tokens[0].trim();
            if (Character.isUpperCase(first.charAt(0))) return first;
        }
        return null;
    }

    private String normalizeSubject(String subject) {
        if (subject == null) return null;

        String s = normalize(subject);

        if (s.contains("yo") || s.contains("me") || s.contains("mi")
                || s.contains("tengo") || s.contains("self")
                || s.contains("usuario")) {
            return "self";
        }

        if (s.contains("mujer") || s.contains("esposa")
                || s.contains("pareja") || s.contains("novia")) {
            return "pareja";
        }

        if (s.contains("hijo") || s.contains("hija")
                || s.contains("niño") || s.contains("niña")) {
            return "hijo";
        }

        return s;
    }

    private String normalizeAttribute(String attribute) {
        String a = normalize(attribute);

        return switch (a) {
            case "nacimiento" -> "fecha_nacimiento";
            case "fecha_de_nacimiento" -> "fecha_nacimiento";
            case "lugar_de_residencia", "residencia", "vive_en" -> "ciudad";
            default -> a;
        };
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isInvalidValue(String value) {
        if (value == null) return true;
        if (value.matches(".*\\d{4}-\\d{4}.*")) return true;
        return false;
    }
}