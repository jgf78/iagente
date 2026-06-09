package com.julian.iagente.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julian.iagente.entity.UserMemory;
import com.julian.iagente.repository.UserMemoryRepository;

@Service
public class UserMemoryService {

    private static final Logger log = LoggerFactory.getLogger(UserMemoryService.class);

    private final ChatClient chatClient;
    private final UserMemoryRepository repo;
    private final ObjectMapper objectMapper;

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
            "nombre",
            "fecha_nacimiento",
            "edad",
            "ciudad",
            "gustos",
            "pareja",
            "trabajo"
    );

    private static final List<String> ALLOWED_SUBJECTS = List.of(
            "self",
            "pareja",
            "hijo"
    );

    public void extractAndSave(String userId, String message) {

        String response = chatClient.prompt().system("""
                Eres un sistema de extracción de memoria ESTRICTO.

                Devuelve SOLO JSON válido.

                FORMATO:

                [
                  {
                    "subject": "self | pareja | hijo",
                    "attribute": "nombre normalizado",
                    "value": "valor exacto del texto"
                  }
                ]

                REGLAS CRÍTICAS:

                1. SUBJECTS PERMITIDOS:
                   - self
                   - pareja
                   - hijo

                2. "hijo" se usa para cualquier hijo del usuario.

                3. SI HAY VARIOS HIJOS:
                   - usa el nombre en VALUE
                   - subject SIEMPRE = "hijo"

                4. PROHIBIDO:
                   - nombres propios en subject
                   - inventar subjects nuevos

                5. attributes permitidos:
                   - nombre
                   - fecha_nacimiento
                   - edad
                   - ciudad
                   - gustos
                   - pareja
                   - trabajo

                6. No infieras datos.

                7. Si no hay memoria → []

                8. OUTPUT SOLO JSON.
                """)
                .user(message)
                .call()
                .content();

        log.info("MEMORY EXTRACTOR RESPONSE -> {}", response);

        try {

            List<MemoryItem> memories = objectMapper.readValue(
                    response,
                    new TypeReference<List<MemoryItem>>() {}
            );

            for (MemoryItem m : memories) {

                if (m == null) continue;

                String subject = normalizeSubject(m.subject);
                String attribute = normalizeAttribute(m.attribute);
                String value = m.value == null ? null : m.value.trim();

                if (isBlank(subject) || isBlank(attribute) || isBlank(value)) {
                    continue;
                }

                if (!ALLOWED_SUBJECTS.contains(subject)) {
                    log.warn("REJECTED SUBJECT -> {}", subject);
                    continue;
                }

                if (!ALLOWED_ATTRIBUTES.contains(attribute)) {
                    log.warn("REJECTED ATTRIBUTE -> {}", attribute);
                    continue;
                }

                if (isInvalidValue(value)) {
                    log.warn("REJECTED VALUE -> {}", value);
                    continue;
                }

                String normalizedSubject = expandChildSubject(subject, value);

                save(userId, normalizedSubject, attribute, value);
            }

        } catch (Exception e) {

            log.error("ERROR PARSING MEMORY");
            log.error("RAW RESPONSE -> {}", response, e);
        }
    }

    /**
     * 🔥 CLAVE: evita colisión entre hijos
     * ejemplo:
     * hijo + Pablo -> hijo:pablo
     */
    private String expandChildSubject(String subject, String value) {

        if (!"hijo".equals(subject)) {
            return subject;
        }

        String normalizedName = normalize(value);

        return "hijo:" + normalizedName;
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

    public List<UserMemory> getMemory(String userId) {
        return repo.findByUserId(userId);
    }

    private boolean isRedundant(String userId, String key, String newValue) {

        return repo.findByUserIdAndMemoryKey(userId, key)
                .map(existing -> existing.getMemoryValue().equalsIgnoreCase(newValue))
                .orElse(false);
    }

    private String normalizeSubject(String subject) {

        if (subject == null) return null;

        String s = normalize(subject);

        if (s.contains("yo") ||
            s.contains("me") ||
            s.contains("mi") ||
            s.contains("tengo") ||
            s.contains("self") ||
            s.contains("usuario")) {
            return "self";
        }

        if (s.contains("mujer") ||
            s.contains("esposa") ||
            s.contains("pareja") ||
            s.contains("novia")) {
            return "pareja";
        }

        if (s.contains("hijo") ||
            s.contains("hija") ||
            s.contains("niño") ||
            s.contains("niña")) {
            return "hijo";
        }

        return s;
    }

    private String normalizeAttribute(String attribute) {

        String a = normalize(attribute);

        return switch (a) {

            case "nacimiento" -> "fecha_nacimiento";
            case "fecha_de_nacimiento" -> "fecha_nacimiento";

            case "lugar_de_residencia",
                 "residencia",
                 "vive_en" -> "ciudad";

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

        if (value.matches(".*\\d{4}-\\d{4}.*")) {
            return true;
        }

        return false;
    }
}