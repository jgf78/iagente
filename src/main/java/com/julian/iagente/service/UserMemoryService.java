package com.julian.iagente.service;



import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, String> childIdentityCache = new ConcurrentHashMap<>();

    public UserMemoryService(ChatClient chatClient, UserMemoryRepository repo, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.userMemoryRepository = repo;
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

    public boolean extractAndSave(String userId, String message) {

        boolean saved = false;

        String response = chatClient.prompt()
                .system("""
                        Eres un extractor de memoria estructurada para un asistente personal.

                        Tu única función es detectar INFORMACIÓN NUEVA que el usuario está proporcionando y convertirla en memoria.

                        IMPORTANTE:
                        NO respondas al usuario.
                        NO intentes resolver preguntas.
                        NO busques información existente.
                        NO completes datos que no aparecen explícitamente.

                        --------------------------------------------------
                        REGLA PRINCIPAL
                        --------------------------------------------------

                        Solo debes extraer memoria cuando el usuario está AFIRMANDO un dato nuevo.

                        Una pregunta o consulta NUNCA debe generar memoria.

                        --------------------------------------------------
                        PATRONES DE INFORMACIÓN NUEVA (MUY IMPORTANTE)
                        --------------------------------------------------

                        Si el usuario usa estas expresiones, SIEMPRE debes extraer memoria:

                        SELF (usuario):
                        - "mi nombre es ..."
                        - "me llamo ..."
                        - "soy ..."
                        - "vivo en ..."
                        - "trabajo como ..."
                        - "tengo ... años"

                        PAREJA:
                        - "mi pareja se llama ..."
                        - "mi pareja nació ..."
                        - "mi mujer se llama ..."
                        - "mi novia se llama ..."
                        - "mi esposa se llama ..."

                        HIJOS:
                        - "mi hijo se llama ..."
                        - "mi hija se llama ..."
                        - "mi hijo nació ..."
                        - "mi hija nació ..."

                        --------------------------------------------------
                        EJEMPLOS VÁLIDOS
                        --------------------------------------------------

                        Usuario:
                        "Mi pareja se llama Mercedes"

                        Respuesta:
                        [
                          {
                            "subject": "pareja",
                            "attribute": "nombre",
                            "value": "Mercedes"
                          }
                        ]

                        Usuario:
                        "Mi pareja nació el 20 de mayo de 1980"

                        Respuesta:
                        [
                          {
                            "subject": "pareja",
                            "attribute": "fecha_nacimiento",
                            "value": "20 de mayo de 1980"
                          }
                        ]

                        Usuario:
                        "mi nombre es Julián Gómez Fernández"

                        Respuesta:
                        [
                          {
                            "subject": "self",
                            "attribute": "nombre",
                            "value": "Julián Gómez Fernández"
                          }
                        ]

                        Usuario:
                        "vivo en Madrid"

                        Respuesta:
                        [
                          {
                            "subject": "self",
                            "attribute": "ciudad",
                            "value": "Madrid"
                          }
                        ]

                        Usuario:
                        "trabajo como ingeniero de software"

                        Respuesta:
                        [
                          {
                            "subject": "self",
                            "attribute": "trabajo",
                            "value": "ingeniero de software"
                          }
                        ]

                        --------------------------------------------------
                        NO EXTRAER EN ESTOS CASOS
                        --------------------------------------------------

                        Si el usuario pregunta, solicita, consulta o intenta recuperar información existente devuelve siempre [].

                        Ejemplos:

                        Usuario:
                        "cuando nació mi pareja?"

                        Respuesta:
                        []

                        Usuario:
                        "como se llama mi pareja?"

                        Respuesta:
                        []

                        Usuario:
                        "qué edad tengo?"

                        Respuesta:
                        []

                        Usuario:
                        "recuérdame mis datos"

                        Respuesta:
                        []

                        --------------------------------------------------
                        REGLAS OBLIGATORIAS DE EXTRACCIÓN
                        --------------------------------------------------

                        - Extrae únicamente datos presentes literalmente en el mensaje.
                        - Nunca inventes valores.
                        - Nunca uses valores genéricos.
                        - Nunca uses "string".
                        - Nunca uses ejemplos del formato como valores reales.
                        - Si falta el valor del dato devuelve [].
                        - Una pregunta nunca genera memoria.
                        - Si el mensaje contiene interrogación o es una pregunta, devuelve [].

                        --------------------------------------------------
                        FORMATO DE RESPUESTA
                        --------------------------------------------------

                        Devuelve ÚNICAMENTE JSON válido.

                        Siempre devuelve una lista JSON.

                        Formato:

                        [
                          {
                            "subject": "self|pareja|hijo",
                            "attribute": "nombre|edad|ciudad|gustos|fecha_nacimiento|trabajo",
                            "value": "valor real extraído del mensaje"
                          }
                        ]

                        Si no existe información nueva devuelve exactamente:

                        []

                        """)
                .user(message)
                .call()
                .content();


        log.info("MEMORY EXTRACTOR RESPONSE -> {}", response);


        try {

            if (response == null || response.isBlank()) {
                return false;
            }


            String json = extractJsonArray(response);

            if (json == null) {
                log.warn("MEMORY SKIPPED -> response is not valid JSON array");
                return false;
            }


            List<MemoryItem> memories = objectMapper.readValue(
                    json,
                    new TypeReference<List<MemoryItem>>() {}
            );


            for (MemoryItem m : memories) {

                if (m == null) {
                    continue;
                }


                String subject = normalizeSubject(m.subject);

                String attribute =
                        normalizeAttribute(m.attribute);


                if (!ALLOWED_ATTRIBUTES.contains(attribute)) {
                    log.warn("REJECTED ATTRIBUTE -> {}", attribute);
                    continue;
                }


                if (attribute.contains("|")) {
                    log.warn("REJECTED MULTI ATTRIBUTE -> {}", attribute);
                    continue;
                }


                String value =
                        m.value == null ? null : m.value.trim();


                if (isBlank(subject)
                        || isBlank(attribute)
                        || isBlank(value)) {

                    log.warn(
                        "REJECTED EMPTY MEMORY -> subject={}, attribute={}, value={}",
                        subject,
                        attribute,
                        value);

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


                String normalizedSubject =
                        resolveChildSubject(
                                userId,
                                m,
                                message);


                boolean memorySaved =
                        save(
                            userId,
                            normalizedSubject,
                            attribute,
                            value);


                saved = saved || memorySaved;
            }


        } catch (Exception e) {

            log.error("ERROR PARSING MEMORY");
            log.error("RAW RESPONSE -> {}", response, e);

            return false;
        }


        return saved;
    }

    private String extractJsonArray(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start == -1 || end == -1 || end <= start) {
            return null;
        }

        return response.substring(start, end + 1);
    }

    public boolean save(String userId, String subject, String attribute, String value) {

        String key = "persona:" + subject + ":" + attribute;

        if (isRedundant(userId, key, value)) {
            log.info("MEMORY REDUNDANT -> {}={}", key, value);
            return false;
        }

        return userMemoryRepository.findByUserIdAndMemoryKey(userId, key)
                .map(existing -> {

                    log.info("MEMORY UPDATE -> {} : {} -> {}",
                            key,
                            existing.getMemoryValue(),
                            value);

                    existing.setMemoryValue(value);
                    userMemoryRepository.save(existing);

                    return true;

                })
                .orElseGet(() -> {

                    log.info("MEMORY INSERT -> {} = {}", key, value);

                    UserMemory memory = UserMemory.builder()
                            .userId(userId)
                            .memoryKey(key)
                            .memoryValue(value)
                            .build();

                    userMemoryRepository.save(memory);

                    return true;
                });
    }

    public List<UserMemoryDTO> getMemory(String userId) {
        return userMemoryRepository.findByUserId(userId)
                .stream()
                .map(m -> new UserMemoryDTO(m.getMemoryKey(), m.getMemoryValue()))
                .toList();
    }

    private boolean isRedundant(String userId, String key, String newValue) {
        return userMemoryRepository.findByUserIdAndMemoryKey(userId, key)
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

    public Optional<UserMemoryDTO> findBestMatch(String userId, String message) {

        if (userId == null || message == null || message.isBlank()) {
            return Optional.empty();
        }

        List<UserMemory> memories = userMemoryRepository.findByUserId(userId);

        if (memories.isEmpty()) {
            return Optional.empty();
        }

        UserMemory bestMatch = null;
        int bestScore = 0;


        for (UserMemory memory : memories) {

            int score = calculateMemoryScore(message, memory);


            if (score > bestScore) {
                bestScore = score;
                bestMatch = memory;
            }
        }


        if (bestMatch == null || bestScore < 2) {
            return Optional.empty();
        }


        return Optional.of(
                new UserMemoryDTO(
                        bestMatch.getMemoryKey(),
                        bestMatch.getMemoryValue()
                )
        );
    }
    
    private int calculateMemoryScore(String message, UserMemory memory) {

        String text = message.toLowerCase();
        String key = memory.getMemoryKey().toLowerCase();

        int score = 0;


        /*
         * 1. Analizar partes de la key
         */
        String[] keyParts = key.split(":");


        for (String part : keyParts) {

            if (part.equals("persona")) {
                continue;
            }

            if (text.contains(part)) {

                switch (part) {

                    case "fecha_nacimiento":
                        score += 10;
                        break;

                    case "nombre":
                        score += 8;
                        break;

                    case "edad":
                        score += 7;
                        break;

                    case "gustos":
                        score += 5;
                        break;

                    case "trabajo":
                        score += 5;
                        break;

                    default:
                        score += 2;
                }
            }
        }


        /*
         * 2. Detección de atributo por lenguaje natural
         */

        if (key.contains("fecha_nacimiento")) {

            if (text.contains("fecha")
                    || text.contains("nació")
                    || text.contains("nacio")
                    || text.contains("nacimiento")
                    || text.contains("cumpleaños")
                    || text.contains("cumple")) {

                score += 15;
            }
        }


        if (key.contains("gustos")) {

            if (text.contains("gusto")
                    || text.contains("gustos")
                    || text.contains("gusta")
                    || text.contains("aficiones")
                    || text.contains("hobby")
                    || text.contains("hobbie")) {

                score += 10;
            }
        }


        /*
         * 3. Detección del sujeto
         */

        boolean self = 
                text.contains("yo")
                || text.contains("mi ")
                || text.contains("mis")
                || text.contains("tengo");


        if (self && key.contains("persona:self")) {
            score += 20;
        }


        boolean pareja =
                text.contains("pareja")
                || text.contains("mujer")
                || text.contains("novia")
                || text.contains("esposa");


        if (pareja && key.contains("persona:pareja")) {
            score += 20;
        }


        boolean hijo =
                text.contains("hijo")
                || text.contains("hija");


        if (hijo && key.contains("persona:hijo")) {
            score += 20;
        }


        /*
         * 4. Penalizaciones para evitar falsos positivos
         */

        // Si pregunta por uno mismo no queremos pareja/hijos
        if (self && !pareja && !hijo) {

            if (key.contains("persona:pareja")
                    || key.contains("persona:hijo")) {
                score -= 10;
            }
        }


        // Si pregunta por pareja no queremos sus hijos
        if (pareja && !hijo) {

            if (key.contains("persona:hijo")) {
                score -= 15;
            }
        }


        return score;
    }
}