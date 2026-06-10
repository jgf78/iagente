package com.julian.iagente.service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private final Map<String, String> childIdentityCache = new HashMap<>();

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

    private static final List<String> ALLOWED_ATTRIBUTES = List.of("nombre", "fecha_nacimiento", "edad", "ciudad",
            "gustos", "pareja", "trabajo");

    private static final List<String> ALLOWED_SUBJECTS = List.of("self", "pareja", "hijo");

    public void extractAndSave(String userId, String message) {

        String response = chatClient.prompt().system("""
                Eres un extractor de memoria estructurada EXTREMADAMENTE RIGUROSO.

                Devuelve SOLO JSON válido.

                FORMATO OBLIGATORIO:
                Debes devolver SIEMPRE un array plano de objetos JSON.

                [
                  {
                    "subject": "self | pareja | hijo",
                    "attribute": "nombre | fecha_nacimiento | edad | ciudad | gustos | pareja | trabajo",
                    "value": "texto exacto del mensaje"
                  }
                ]

                ========================
                REGLAS CRÍTICAS
                ========================

                1. SUBJECTS PERMITIDOS:
                - self
                - pareja
                - hijo

                2. ATRIBUTOS PERMITIDOS (OBLIGATORIO ELEGIR SOLO UNO):
                - nombre
                - fecha_nacimiento
                - edad
                - ciudad
                - gustos
                - pareja
                - trabajo

                🚨 REGLA MÁS IMPORTANTE:
                - "attribute" DEBE SER UN SOLO VALOR
                - PROHIBIDO usar separadores como: "|", ",", "o"
                - PROHIBIDO listar opciones
                - PROHIBIDO múltiples atributos en un solo campo

                ❌ INCORRECTO:
                "nombre | fecha_nacimiento | edad"

                ✅ CORRECTO:
                "fecha_nacimiento"

                3. VALUE:
                - copiar EXACTAMENTE del mensaje
                - NUNCA resumir
                - NUNCA interpretar
                - NUNCA corregir fechas

                4. HIJOS:
                - "hijo" es genérico
                - SOLO crear identidad cuando exista nombre explícito
                - si aparece nombre + dato → usar subject "hijo"

                5. SELF:
                - SOLO usar si el sujeto es el usuario claramente

                6. PROHIBIDO:
                - arrays dentro de arrays
                - objetos vacíos
                - inferir relaciones
                - mezclar atributos
                - generar texto explicativo

                7. SI NO HAY MEMORIA → []

                8. OUTPUT:
                - SOLO JSON
                - SIN markdown
                - SIN texto adicional

                9. 🚨 VALIDACIÓN FINAL OBLIGATORIA:
                Antes de responder verifica:

                - attribute SOLO puede ser UNA palabra
                - NO puede contener espacios
                - NO puede contener "|"
                - NO puede contener múltiples valores
                
                10. SI EL MENSAJE ES UNA PREGUNTA:

                DEVUELVE []
                
                Ejemplos:
                
                Usuario:
                ¿Cómo se llaman mis hijos?
                
                Respuesta:
                []
                
                Usuario:
                ¿Cuándo nació Pablo?
                
                Respuesta:
                []
                
                Usuario:
                ¿Qué edad tiene mi hijo?
                
                Respuesta:
                []
                
                NUNCA inventes datos para completar la respuesta.
                """).user(message).call().content();

        log.info("MEMORY EXTRACTOR RESPONSE -> {}", response);

        try {

            if (response == null || response.isBlank() || response.contains("[,") || response.equals("[]")) {
                return;
            }

            List<MemoryItem> memories = objectMapper.readValue(response, new TypeReference<List<MemoryItem>>() {
            });

            for (MemoryItem m : memories) {

                if (m == null)
                    continue;

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

                // ==========================
                // HARD SAFETY CHECKS
                // ==========================
                if (isBlank(subject) || isBlank(attribute) || isBlank(value)) {
                    log.warn("REJECTED EMPTY MEMORY -> subject={}, attribute={}, value={}", subject, attribute, value);
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

                String normalizedSubject = resolveChildSubject(userId, m, message);

                save(userId, normalizedSubject, attribute, value);
            }

        } catch (Exception e) {

            log.error("ERROR PARSING MEMORY");
            log.error("RAW RESPONSE -> {}", response, e);
        }
    }

    private String resolveChildSubject(String userId, MemoryItem m, String originalMessage) {

        if (!"hijo".equals(m.subject)) {
            return m.subject;
        }

        if ("nombre".equals(m.attribute)) {
            String name = extractChildName(m.value);
            if (name != null) {
                String normalized = normalize(name);
                childIdentityCache.put(userId, normalized);
                return "hijo:" + normalized;
            }
        }

        // Buscar en cache primero
        String cached = childIdentityCache.get(userId);
        if (cached != null) {
            return "hijo:" + cached;
        }

        // 🔑 CLAVE: inferir desde el mensaje completo, no desde m.value
        String inferred = extractChildName(originalMessage);
        if (inferred != null) {
            String normalized = normalize(inferred);
            childIdentityCache.put(userId, normalized);
            return "hijo:" + normalized;
        }

        return "hijo:unknown";
    }

    private String extractChildName(String value) {

        if (value == null)
            return null;

        String[] tokens = value.split(" ");

        if (tokens.length > 0) {
            String first = tokens[0].trim();

            if (Character.isUpperCase(first.charAt(0))) {
                return first;
            }
        }

        return null;
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

            UserMemory memory = UserMemory.builder().userId(userId).memoryKey(key).memoryValue(value).build();

            repo.save(memory);
        });
    }

    public List<UserMemory> getMemory(String userId) {
        return repo.findByUserId(userId);
    }

    private boolean isRedundant(String userId, String key, String newValue) {
        return repo.findByUserIdAndMemoryKey(userId, key)
                .map(existing -> existing.getMemoryValue().equalsIgnoreCase(newValue)).orElse(false);
    }

    private String normalizeSubject(String subject) {

        if (subject == null)
            return null;

        String s = normalize(subject);

        if (s.contains("yo") || s.contains("me") || s.contains("mi") || s.contains("tengo") || s.contains("self")
                || s.contains("usuario")) {
            return "self";
        }

        if (s.contains("mujer") || s.contains("esposa") || s.contains("pareja") || s.contains("novia")) {
            return "pareja";
        }

        if (s.contains("hijo") || s.contains("hija") || s.contains("niño") || s.contains("niña")) {
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

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isInvalidValue(String value) {

        if (value == null)
            return true;

        if (value.matches(".*\\d{4}-\\d{4}.*"))
            return true;

        return false;
    }
}