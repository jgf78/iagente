package com.julian.iagente.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julian.iagente.entity.ChatMessage;
import com.julian.iagente.entity.UserMemory;
import com.julian.iagente.model.ContextPayload;
import com.julian.iagente.model.RouteDecision;
import com.julian.iagente.model.WebResult;
import com.julian.iagente.repository.ChatMessageRepository;

@Service
public class AgentService {

    private static final Logger log =
            LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final ChatMessageRepository chatRepo;
    private final UserMemoryService userMemoryService;
    private final QueryRouterService queryRouterService;
    private final WebSearchService webSearchService;
    private final ObjectMapper objectMapper;

    public AgentService(ChatClient chatClient,
                        ChatMessageRepository chatRepo,
                        UserMemoryService userMemoryService,
                        QueryRouterService queryRouterService,
                        WebSearchService webSearchService,
                        ObjectMapper objectMapper) {

        this.chatClient = chatClient;
        this.chatRepo = chatRepo;
        this.userMemoryService = userMemoryService;
        this.queryRouterService = queryRouterService;
        this.webSearchService = webSearchService;
        this.objectMapper = objectMapper;
    }

    public String chat(String userId, String message) {

        log.info("==================================================");
        log.info("NUEVA PETICION");
        log.info("USER: {}", userId);
        log.info("MESSAGE: {}", message);
        log.info("==================================================");

        save(userId, "user", message);

        userMemoryService.extractAndSave(userId, message);

        List<UserMemory> memories =
                userMemoryService.getMemory(userId);

        List<ChatMessage> history =
                chatRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId);

        RouteDecision decision =
                queryRouterService.decide(message);

        log.info("ROUTER DECISION -> {}", decision);

        // =====================================
        // MEMORY
        // =====================================

        List<String> memoryList = new ArrayList<>();

        if (decision.useMemory()) {

            memoryList = memories.stream()
                    .map(m -> m.getMemoryKey() + ": " + m.getMemoryValue())
                    .toList();
        }

        log.info("MEMORY FOUND -> {}", memoryList);

        // =====================================
        // WEB (HARDENED)
        // =====================================

        List<String> webList = new ArrayList<>();

        if (decision.useWeb()) {

            String webQuery = decision.webQuery();

            if (!isValidWebQuery(webQuery, message)) {

                log.warn("WEB QUERY INVALID -> DISABLING WEB");
                webList.add("WEB_SKIPPED_INVALID_QUERY");

            } else {

                try {

                    log.info("WEB SEARCH -> {}", webQuery);

                    List<WebResult> results =
                            webSearchService.search(webQuery);

                    if (results == null || results.isEmpty()) {

                        webList.add("WEB_EMPTY");
                        log.warn("WEB RESULT EMPTY");

                    } else {

                        results.stream()
                                .limit(5)
                                .forEach(r -> {

                                    String item =
                                            """
                                            TITLE: %s
                                            URL: %s
                                            CONTENT: %s
                                            """
                                                    .formatted(
                                                            r.title(),
                                                            r.url(),
                                                            r.snippet());

                                    webList.add(item);
                                });

                        log.info("WEB RESULTS -> {}", webList.size());
                    }

                } catch (Exception e) {

                    log.error("WEB ERROR", e);
                    webList.add("WEB_ERROR");
                }
            }
        }

        // =====================================
        // HISTORY
        // =====================================

        List<String> historyList =
                history.stream()
                        .filter(m -> "user".equals(m.getRole()))
                        .map(ChatMessage::getContent)
                        .toList();

        log.info("HISTORY -> {}", historyList);

        // =====================================
        // CONTEXT JSON
        // =====================================

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
                            .writeValueAsString(payload);

        } catch (Exception e) {

            log.error("ERROR BUILDING CONTEXT", e);

            context = """
                    {
                      "error":"context_build_failed"
                    }
                    """;
        }

        log.info("FINAL CONTEXT SENT TO LLM:");
        log.info("\n{}", context);

        // =====================================
        // DATE CONTEXT (NEW - FIXED)
        // =====================================

        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String year = String.valueOf(LocalDate.now().getYear());

        // =====================================
        // LLM
        // =====================================

        String response = chatClient.prompt()
                .system("""
Eres un asistente estricto basado en CONTEXTO.

FECHA SISTEMA:
- Fecha completa: %s
- Año actual: %s

REGLA CRÍTICA:
- Para preguntas sobre fecha, día o año, usa SIEMPRE esta información.
- Está PROHIBIDO decir que no sabes la fecha.

El contexto contiene:

MEMORY → datos personales del usuario
WEB → información externa
HISTORY → conversación previa

REGLAS CRÍTICAS:

1. MEMORY solo si es explícito.
2. WEB solo si contiene evidencia fiable.
3. Si WEB contiene WEB_EMPTY o WEB_ERROR → ignóralo completamente.
4. NUNCA inventes datos personales.
5. NUNCA inventes eventos, deportes o noticias.
6. Si no hay evidencia suficiente:
   responde EXACTAMENTE:
   "No dispongo de información suficiente para responder con certeza."

7. HISTORY solo ayuda a interpretar la pregunta.
8. Está PROHIBIDO mezclar MEMORY y WEB como fuente única.

Responde SIEMPRE en Español.
""".formatted(today, year))
                .user("""
Pregunta actual:
%s

Contexto:
%s
""".formatted(message, context))
                .call()
                .content();

        log.info("LLM RESPONSE -> {}", response);

        save(userId, "assistant", response);

        return response;
    }

    // =====================================
    // WEB VALIDATION
    // =====================================

    private boolean isValidWebQuery(String webQuery, String message) {

        if (webQuery == null || webQuery.isBlank()) return false;

        if (webQuery.length() < 6) return false;

        String q = webQuery.toLowerCase();

        if (q.contains("mi ") || q.contains("me ") || q.contains("recuerdas")) {
            return false;
        }

        if (q.equals(message.toLowerCase())) {
            return true;
        }

        if (q.split(" ").length < 3) {
            return false;
        }

        return true;
    }

    private void save(String userId,
                      String role,
                      String content) {

        ChatMessage msg = new ChatMessage();

        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);

        chatRepo.save(msg);
    }
}