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
import com.julian.iagente.model.AgentPersona;
import com.julian.iagente.model.ContextPayload;
import com.julian.iagente.model.RouteDecision;
import com.julian.iagente.model.WebResult;
import com.julian.iagente.repository.ChatMessageRepository;
import com.julian.iagente.service.tool.CalendarService;
import com.julian.iagente.service.tool.WeatherService;

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
    private final AgentPersonaService personaService;

    private final WeatherService weatherService;
    private final CalendarService calendarService;

    public AgentService(ChatClient chatClient,
                        ChatMessageRepository chatRepo,
                        UserMemoryService userMemoryService,
                        QueryRouterService queryRouterService,
                        WebSearchService webSearchService,
                        ObjectMapper objectMapper,
                        AgentPersonaService personaService,
                        WeatherService weatherService,
                        CalendarService calendarService) {

        this.chatClient = chatClient;
        this.chatRepo = chatRepo;
        this.userMemoryService = userMemoryService;
        this.queryRouterService = queryRouterService;
        this.webSearchService = webSearchService;
        this.objectMapper = objectMapper;
        this.personaService = personaService;
        this.weatherService = weatherService;
        this.calendarService = calendarService;
    }

    public String chat(String userId, String message) {

        log.info("==================================================");
        log.info("NUEVA PETICION");
        log.info("USER: {}", userId);
        log.info("MESSAGE: {}", message);
        log.info("==================================================");

        save(userId, "user", message);

        userMemoryService.extractAndSave(userId, message);

        List<UserMemory> memories = userMemoryService.getMemory(userId);

        List<ChatMessage> history =
                chatRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId);

        RouteDecision decision =
                queryRouterService.decide(message);

        log.info("ROUTER DECISION -> {}", decision);

        AgentPersona persona = personaService.getPersona(userId);

        String personalityBlock = """
        AGENT_PERSONA:

        nickname: %s
        tone: %s
        style: %s
        verbosity: %s
        language: %s
        """.formatted(
                persona.nickname(),
                persona.tone(),
                persona.style(),
                persona.verbosity(),
                persona.language()
        );

        List<String> memoryList = new ArrayList<>();

        if (decision.useMemory()) {
            memoryList = memories.stream()
                    .map(m -> m.getMemoryKey() + ": " + m.getMemoryValue())
                    .toList();
        }

        log.info("MEMORY FOUND -> {}", memoryList);

        List<String> webList = new ArrayList<>();

        if (decision.useWeb()) {

            try {

                String query = decision.webQuery();

                if (query == null || query.isBlank()) {
                    query = message;

                    log.warn("WEB QUERY VACIA -> fallback al mensaje original");
                }

                log.info("WEB SEARCH -> {}", query);

                List<WebResult> results =
                        webSearchService.search(query);

                results.stream()
                        .limit(5)
                        .forEach(r -> webList.add("""
                                TITLE: %s
                                URL: %s
                                CONTENT: %s
                                """.formatted(
                                        r.title(),
                                        r.url(),
                                        r.snippet()
                                )));

            } catch (Exception e) {

                log.error("WEB ERROR", e);

                webList.add("WEB_ERROR");
            }
        }

        // ==========================
        // TOOL EXECUTION
        // ==========================

        List<String> toolList = new ArrayList<>();

        String tool = decision.tool();

        if ("WEATHER".equals(tool)) {

            String city = clean(decision.toolInput());

            log.info("WEATHER TOOL -> {}", city);

            if (!city.isBlank()) {
                String weather = weatherService.getWeather(city);

                toolList.add("""
                        DATA: %s
                        """.formatted(weather));
            }
        }

        if ("CALENDAR".equals(tool)) {

            String rawDate = clean(decision.toolInput());

            String date = normalizeDate(rawDate, message);

            log.info("CALENDAR TOOL -> raw: {}, normalized: {}", rawDate, date);

            String calendar = calendarService.getAgenda(date);

            toolList.add("""
                    DATA: %s
                    """.formatted(calendar));
        }

        // ==========================
        // TOOL BYPASS
        // ==========================

        if (!toolList.isEmpty()) {

            log.info("TOOL RESPONSE MODE (bypass LLM)");

            String toolResponse = toolList.get(0);

            save(userId, "assistant", toolResponse);

            return toolResponse;
        }

        List<String> historyList =
                history.stream()
                        .filter(m -> "user".equals(m.getRole()))
                        .map(ChatMessage::getContent)
                        .toList();

        log.info("HISTORY -> {}", historyList);

        String context;

        try {
            ContextPayload payload =
                    new ContextPayload(memoryList, webList, historyList);

            context =
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(payload);

        } catch (Exception e) {
            log.error("ERROR BUILDING CONTEXT", e);
            context = "{ \"error\":\"context_build_failed\" }";
        }

        log.info("FINAL CONTEXT SENT TO LLM:");
        log.info("\n{}", context);

        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String year = String.valueOf(LocalDate.now().getYear());

        String response = chatClient.prompt()
                .system("""
%s

=====================================

Eres un asistente estricto basado en CONTEXTO.

FECHA SISTEMA:
- Fecha completa: %s
- Año actual: %s

REGLAS:
- TOOL > ALL
- No inventes datos
- Responde siempre en español
""".formatted(personalityBlock, today, year))
                .user("""
Pregunta:
%s

Contexto:
%s

TOOLS:
%s
""".formatted(message, context, toolList))
                .call()
                .content();

        log.info("LLM RESPONSE -> {}", response);

        save(userId, "assistant", response);

        return response;
    }

    private String normalizeDate(String input, String originalMessage) {

        if (input == null || input.isBlank()) {
            return LocalDate.now().toString();
        }

        LocalDate today = LocalDate.now();
        String normalizedInput = input.trim().toLowerCase();
        String message = originalMessage == null ? "" : originalMessage.toLowerCase();

        // =====================================
        // RELATIVOS
        // =====================================
        if (normalizedInput.contains("hoy")) return today.toString();
        if (normalizedInput.contains("mañana")) return today.plusDays(1).toString();
        if (normalizedInput.contains("ayer")) return today.minusDays(1).toString();

        // =====================================
        // PARSEO FECHAS (ISO O ES)
        // =====================================
        LocalDate parsed = parseDate(normalizedInput);

        if (parsed == null) {
            return today.toString();
        }

        // =====================================
        // AJUSTE AÑO
        // =====================================
        if (message.contains("este año")) {
            parsed = parsed.withYear(today.getYear());
        } else if (message.contains("año pasado")) {
            parsed = parsed.withYear(today.getYear() - 1);
        } else if (message.contains("próximo año") || message.contains("proximo año")) {
            parsed = parsed.withYear(today.getYear() + 1);
        }

        // =====================================
        // AJUSTE MES
        // =====================================
        if (message.contains("este mes")) {
            parsed = parsed.withMonth(today.getMonthValue()).withYear(today.getYear());
        } else if (message.contains("mes pasado")) {
            LocalDate t = today.minusMonths(1);
            parsed = parsed.withMonth(t.getMonthValue()).withYear(t.getYear());
        } else if (message.contains("próximo mes") || message.contains("proximo mes")) {
            LocalDate t = today.plusMonths(1);
            parsed = parsed.withMonth(t.getMonthValue()).withYear(t.getYear());
        }

        return parsed.toString();
    }

    private LocalDate parseDate(String input) {

        try {
            if (input.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(input);
            }

            if (input.matches("\\d{2}/\\d{2}/\\d{4}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(input, formatter);
            }

        } catch (Exception e) {
            return null;
        }

        return null;
    }
    
    private String clean(String input) {
        if (input == null) return "";
        return input.trim();
    }

    private void save(String userId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        chatRepo.save(msg);
    }
}