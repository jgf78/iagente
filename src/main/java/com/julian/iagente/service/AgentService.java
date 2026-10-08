package com.julian.iagente.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julian.iagente.entity.ChatMessage;
import com.julian.iagente.model.ContextPayload;
import com.julian.iagente.model.RouteDecision;
import com.julian.iagente.model.UserMemoryDTO;
import com.julian.iagente.model.WebResult;
import com.julian.iagente.repository.ChatMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private static final String USER = "USER";
    private static final String ASSISTANT = "ASSISTANT";

    private final ChatClient chatClient;

    private final QueryRouterService queryRouterService;

    private final UserMemoryService userMemoryService;

    private final WebSearchService webSearchService;

    private final ChatMessageRepository chatRepo;

    private final ObjectMapper objectMapper;


    // ============================================================
    // CHAT
    // ============================================================

    public String chat(
            String userId,
            String message) {

        log.info("==================================================");
        log.info("AGENT REQUEST");
        log.info("USER ID -> {}", userId);
        log.info("MESSAGE -> {}", message);
        log.info("==================================================");


        // --------------------------------------------------------
        // Guard
        // --------------------------------------------------------

        if (message == null || message.isBlank()) {
            return "No puedo procesar un mensaje vacío.";
        }


        // --------------------------------------------------------
        // Save user message
        // --------------------------------------------------------

        save(
                userId,
                USER,
                message);


        // --------------------------------------------------------
        // Math bypass
        // --------------------------------------------------------

        if (isMathExpression(message)) {

            log.info("MATH QUERY DETECTED");

            String result =
                    calculateMath(message);

            save(
                    userId,
                    ASSISTANT,
                    result);

            return result;
        }


        // --------------------------------------------------------
        // Memory extraction
        // --------------------------------------------------------

        try {

            userMemoryService.extractAndSave(
                    userId,
                    message);

        } catch (Exception e) {

            log.warn(
                    "MEMORY EXTRACTION ERROR",
                    e);
        }


        // --------------------------------------------------------
        // Small talk
        // --------------------------------------------------------

        if (isSmallTalk(message)) {

            log.info("SMALL TALK DETECTED");

            String response =
                    callLLM(
                            message,
                            "",
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of());

            save(
                    userId,
                    ASSISTANT,
                    response);

            return response;
        }


        // --------------------------------------------------------
        // Router
        // --------------------------------------------------------

        RouteDecision decision =
                queryRouterService.decide(message);

        log.info(
                "ROUTER DECISION -> {}",
                decision);


        // --------------------------------------------------------
        // Personality
        // --------------------------------------------------------

        String personalityBlock = "";

        if (decision.useLlm()) {

            try {

                personalityBlock =
                        userMemoryService
                                .buildPersonalityPrompt(userId);

            } catch (Exception e) {

                log.warn(
                        "PERSONALITY ERROR",
                        e);
            }
        }


        // --------------------------------------------------------
        // Memory
        // --------------------------------------------------------

        List<String> memoryList =
                new ArrayList<>();

        if (decision.useMemory()) {
            try {
                Optional<UserMemoryDTO> memory =
                        userMemoryService.findBestMatch(
                                userId,
                                message);

                log.info("MEMORY FOUND -> {}", memory);

                memory.ifPresent(m -> {
                    log.info("MEMORY KEY -> {}", m.memoryKey());
                    log.info("MEMORY VALUE -> {}", m.memoryValue());

                    memoryList.add(m.memoryValue());
                });

            } catch (Exception e) {
                log.warn(
                        "MEMORY SEARCH ERROR",
                        e);
            }
        }


        // --------------------------------------------------------
        // Web
        // --------------------------------------------------------

        List<String> webList =
                new ArrayList<>();

        boolean isPersonalQuestion =
                isAPersonalQuestion(message);

        websearch(
                message,
                decision,
                webList,
                isPersonalQuestion);


        // --------------------------------------------------------
        // Tools
        // --------------------------------------------------------

        List<String> toolList =
                new ArrayList<>();

        executeTools(
                userId,
                message,
                decision,
                toolList);


        // --------------------------------------------------------
        // Tool bypass
        // --------------------------------------------------------

        if (!toolList.isEmpty()) {

            String toolResponse =
                    toolList.get(0);

            save(
                    userId,
                    ASSISTANT,
                    toolResponse);

            return toolResponse;
        }


        // --------------------------------------------------------
        // History
        // --------------------------------------------------------

        List<String> historyList =
                new ArrayList<>();

        if (decision.useLlm()) {

            try {

                List<ChatMessage> history =
                        chatRepo.findTop10ByUserIdOrderByCreatedAtDesc(
                                userId);

                historyList =
                        history.stream()
                                .filter(m ->
                                        USER.equals(
                                                m.getRole()))
                                .map(ChatMessage::getContent)
                                .toList();

            } catch (Exception e) {

                log.warn(
                        "HISTORY ERROR",
                        e);
            }
        }


        // --------------------------------------------------------
        // Context
        //
        // Lo mantenemos para logging/debug.
        // --------------------------------------------------------

        String context =
                buildContext(
                        memoryList,
                        webList,
                        historyList);


        // --------------------------------------------------------
        // Personal question without data
        // --------------------------------------------------------

        if (isPersonalQuestion
                && memoryList.isEmpty()) {

            String response =
                    "No lo sé";

            save(
                    userId,
                    ASSISTANT,
                    response);

            return response;
        }


        // --------------------------------------------------------
        // Memory only
        // --------------------------------------------------------

        if (decision.useMemory()
                && !decision.useLlm()) {

            if (!memoryList.isEmpty()) {

                String response =
                        memoryList.get(0);

                save(
                        userId,
                        ASSISTANT,
                        response);

                return response;
            }

            String response =
                    "No lo sé";

            save(
                    userId,
                    ASSISTANT,
                    response);

            return response;
        }


        // --------------------------------------------------------
        // LLM
        // --------------------------------------------------------

        if (decision.useLlm()) {

            String response =
                    callLLM(
                            message,
                            personalityBlock,
                            toolList,
                            memoryList,
                            webList,
                            historyList);

            save(
                    userId,
                    ASSISTANT,
                    response);

            return response;
        }


        // --------------------------------------------------------
        // Fallback
        // --------------------------------------------------------

        String response =
                "No lo sé";

        save(
                userId,
                ASSISTANT,
                response);

        return response;
    }


    // ============================================================
    // WEB SEARCH
    // ============================================================

    private void websearch(
            String message,
            RouteDecision decision,
            List<String> webList,
            boolean isPersonalQuestion) {

        if (decision.useWeb()
                && !isPersonalQuestion) {

            try {

                String query =
                        decision.webQuery();

                if (query == null
                        || query.isBlank()) {

                    query = message;

                    log.warn(
                            "WEB QUERY VACIA -> fallback al mensaje original");
                }

                log.info(
                        "WEB SEARCH -> {}",
                        query);

                List<WebResult> results =
                        webSearchService.search(query);

                log.info(
                        "WEB RESULTS COUNT -> {}",
                        results.size());

                log.info(
                        "WEB RESULTS -> {}",
                        results);

                results.stream()
                        .limit(5)
                        .forEach(r ->
                                webList.add("""
                                TITLE: %s
                                URL: %s
                                CONTENT: %s
                                """.formatted(
                                        r.title(),
                                        r.url(),
                                        r.snippet()
                                )));

            } catch (Exception e) {

                log.error(
                        "WEB ERROR",
                        e);

                webList.add(
                        "WEB_ERROR");
            }

        } else if (decision.useWeb()
                && isPersonalQuestion) {

            log.warn(
                    "WEB BLOQUEADA -> pregunta personal detectada");
        }
    }


    // ============================================================
    // BUILD CONTEXT
    // ============================================================

    private String buildContext(
            List<String> memoryList,
            List<String> webList,
            List<String> historyList) {

        String context;

        try {

            ContextPayload payload =
                    new ContextPayload(
                            memoryList,
                            webList,
                            historyList);

            context =
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(
                                    payload);

        } catch (Exception e) {

            log.error(
                    "ERROR BUILDING CONTEXT",
                    e);

            context =
                    "{ \"error\":\"context_build_failed\" }";
        }

        log.info(
                "FINAL CONTEXT SENT TO LLM:");

        log.info(
                "\n{}",
                context);

        return context;
    }


    // ============================================================
    // LLM
    // ============================================================

    private String callLLM(
            String message,
            String personalityBlock,
            List<String> toolList,
            List<String> memoryList,
            List<String> webList,
            List<String> historyList) {

        log.info(
                "LLM MESSAGE -> {}",
                message);

        log.info(
                "LLM WEB RESULTS -> {}",
                webList);

        log.info(
                "LLM MEMORY -> {}",
                memoryList);

        log.info(
                "LLM HISTORY -> {}",
                historyList);

        log.info(
                "LLM TOOLS -> {}",
                toolList);


        String today =
                LocalDate.now()
                        .format(
                                DateTimeFormatter.ofPattern(
                                        "dd/MM/yyyy"));

        String year =
                String.valueOf(
                        LocalDate.now().getYear());


        String webContext =
                formatWebResults(
                        webList);

        String memoryContext =
                formatList(
                        memoryList);

        String historyContext =
                formatList(
                        historyList);

        String toolContext =
                formatList(
                        toolList);


        String response =
                chatClient.prompt()

                        // =================================================
                        // SYSTEM
                        // =================================================

                        .system("""
                                %s

                                =====================================

                                Eres un asistente estricto basado en CONTEXTO.

                                FECHA SISTEMA:

                                - Fecha actual: %s
                                - Año actual: %s
                                - Uso horario: Madrid/Europa

                                =====================================

                                REGLAS GENERALES:

                                - Responde siempre en español.
                                - Responde directamente a la pregunta actual.
                                - NO INVENTES DATOS BAJO NINGÚN CONCEPTO.
                                - Si no existe información suficiente para responder,
                                  responde exactamente: "No lo sé".

                                =====================================

                                PRIORIDAD DE INFORMACIÓN:

                                TOOL > WEB > MEMORY > CONOCIMIENTO INTERNO

                                =====================================

                                RESULTADOS WEB:

                                Los RESULTADOS WEB son información REAL obtenida
                                mediante una búsqueda web realizada para la pregunta
                                actual del usuario.

                                IMPORTANTE:

                                - Los RESULTADOS WEB NO son el historial.
                                - Los RESULTADOS WEB NO son memoria antigua.
                                - Los RESULTADOS WEB contienen información obtenida
                                  específicamente para esta consulta.
                                - Si los RESULTADOS WEB contienen información
                                  relevante para responder a la pregunta,
                                  DEBES utilizarla.
                                - Si existe información relevante en WEB,
                                  debes darle prioridad frente a tu conocimiento
                                  interno.
                                - NO digas que no tienes acceso a información actual
                                  si los RESULTADOS WEB contienen información
                                  relevante.
                                - Extrae directamente de WEB las fechas, horas,
                                  nombres, resultados, precios y demás datos
                                  necesarios para responder.
                                - No inventes datos que no aparezcan en WEB.

                                =====================================

                                MEMORY:

                                MEMORY contiene información personal previamente
                                almacenada sobre el usuario.

                                Utiliza MEMORY solamente cuando sea relevante
                                para la pregunta actual.

                                =====================================

                                HISTORIAL:

                                HISTORY contiene mensajes anteriores de la
                                conversación.

                                HISTORY NO es historial de búsquedas web.

                                Utiliza HISTORY únicamente para comprender el
                                contexto de la conversación.

                                =====================================

                                TOOLS:

                                Si una TOOL proporciona información, esa
                                información tiene prioridad sobre cualquier
                                otra fuente.

                                =====================================

                                FORMATO DE FECHAS:

                                Cuando tengas que mostrar fechas:

                                dd/MM/yyyy HH:mm

                                """.formatted(
                                personalityBlock,
                                today,
                                year))

                        // =================================================
                        // USER
                        // =================================================

                        .user("""
                                PREGUNTA ACTUAL DEL USUARIO:

                                %s

                                =====================================

                                RESULTADOS WEB:

                                %s

                                =====================================

                                MEMORIA DEL USUARIO:

                                %s

                                =====================================

                                HISTORIAL DE CONVERSACIÓN:

                                %s

                                =====================================

                                TOOLS:

                                %s

                                =====================================

                                INSTRUCCIÓN FINAL:

                                Responde directamente a la PREGUNTA ACTUAL.

                                Si los RESULTADOS WEB contienen información
                                relevante para responderla, utiliza esos datos
                                directamente.

                                No respondas diciendo que no tienes acceso a
                                información actual si los RESULTADOS WEB contienen
                                la información necesaria.

                                Si los RESULTADOS WEB no contienen información
                                suficiente, no inventes la respuesta.

                                """.formatted(
                                message,
                                webContext,
                                memoryContext,
                                historyContext,
                                toolContext))

                        .call()
                        .content();


        log.info(
                "LLM RESPONSE -> {}",
                response);

        return response;
    }


    // ============================================================
    // FORMAT WEB
    // ============================================================

    private String formatWebResults(
            List<String> webList) {

        if (webList == null
                || webList.isEmpty()) {

            return "No hay resultados web.";
        }

        StringBuilder sb =
                new StringBuilder();

        for (int i = 0;
             i < webList.size();
             i++) {

            sb.append(
                    "RESULTADO WEB ")
                    .append(i + 1)
                    .append("\n");

            sb.append(
                    webList.get(i));

            sb.append(
                    "\n\n");
        }

        return sb.toString();
    }


    // ============================================================
    // FORMAT LIST
    // ============================================================

    private String formatList(
            List<String> list) {

        if (list == null
                || list.isEmpty()) {

            return "Ninguno.";
        }

        return String.join(
                "\n",
                list);
    }


    // ============================================================
    // PERSONAL QUESTION
    // ============================================================

    private boolean isAPersonalQuestion(
            String message) {

        String m =
                normalizeMessage(message);

        return m.contains("mi ")
                || m.contains("me llamo")
                || m.contains("como me llamo")
                || m.contains("quien soy")
                || m.contains("que sabes de mi")
                || m.contains("pareja")
                || m.contains("hijo");
    }


    // ============================================================
    // NORMALIZE
    // ============================================================

    private String normalizeMessage(
            String message) {

        if (message == null) {
            return "";
        }

        return Normalizer
                .normalize(
                        message,
                        Normalizer.Form.NFD)
                .replaceAll(
                        "\\p{M}",
                        "")
                .toLowerCase()
                .replaceAll(
                        "[^a-z0-9\\s]",
                        "")
                .replaceAll(
                        "\\s+",
                        " ")
                .trim();
    }


    // ============================================================
    // SMALL TALK
    // ============================================================

    private boolean isSmallTalk(
            String message) {

        String m =
                normalizeMessage(message);

        return m.equals("hola")
                || m.equals("buenas")
                || m.equals("buenos dias")
                || m.equals("buenas tardes")
                || m.equals("buenas noches")
                || m.equals("gracias")
                || m.equals("muchas gracias")
                || m.equals("adios")
                || m.equals("hasta luego");
    }


    // ============================================================
    // MATH
    // ============================================================

    private boolean isMathExpression(
            String message) {

        if (message == null
                || message.isBlank()) {

            return false;
        }

        return message.matches(
                "[0-9+\\-*/().\\s]+");
    }


    private String calculateMath(
            String message) {

        try {

            // Mantengo aquí tu implementación actual
            // de cálculo matemático.

            return "No puedo calcular esa expresión.";

        } catch (Exception e) {

            log.warn(
                    "MATH ERROR",
                    e);

            return "No puedo calcular esa expresión.";
        }
    }


    // ============================================================
    // TOOLS
    // ============================================================

    private void executeTools(
            String userId,
            String message,
            RouteDecision decision,
            List<String> toolList) {

        /*
         * Mantén aquí EXACTAMENTE tu implementación actual
         * de executeTools().
         *
         * No modificamos esta parte en esta prueba.
         */
    }


    // ============================================================
    // SAVE
    // ============================================================

    private void save(
            String userId,
            String role,
            String content) {

        ChatMessage chatMessage =
                new ChatMessage();

        chatMessage.setUserId(
                userId);

        chatMessage.setRole(
                role);

        chatMessage.setContent(
                content);

        chatRepo.save(
                chatMessage);
    }
}