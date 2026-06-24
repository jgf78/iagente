package com.julian.iagente.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julian.iagente.entity.ChatMessage;
import com.julian.iagente.entity.Todo;
import com.julian.iagente.model.AgentPersona;
import com.julian.iagente.model.ContextPayload;
import com.julian.iagente.model.ReminderItem;
import com.julian.iagente.model.RouteDecision;
import com.julian.iagente.model.TodoItem;
import com.julian.iagente.model.UserMemoryDTO;
import com.julian.iagente.model.WebResult;
import com.julian.iagente.repository.ChatMessageRepository;
import com.julian.iagente.service.tool.CalendarService;
import com.julian.iagente.service.tool.WeatherService;
import com.julian.iagente.util.ToolUtils;

@Service
public class AgentService {

    private static final String ASSISTANT = "assistant";

    private static final String USER = "user";

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
    private final ReminderService reminderService;
    private final TodoService todoService;

    public AgentService(ChatClient chatClient,
                        ChatMessageRepository chatRepo,
                        UserMemoryService userMemoryService,
                        QueryRouterService queryRouterService,
                        WebSearchService webSearchService,
                        ObjectMapper objectMapper,
                        AgentPersonaService personaService,
                        WeatherService weatherService,
                        CalendarService calendarService,
                        ReminderService reminderService,
                        TodoService todoService) {

        this.chatClient = chatClient;
        this.chatRepo = chatRepo;
        this.userMemoryService = userMemoryService;
        this.queryRouterService = queryRouterService;
        this.webSearchService = webSearchService;
        this.objectMapper = objectMapper;
        this.personaService = personaService;
        this.weatherService = weatherService;
        this.calendarService = calendarService;
        this.reminderService = reminderService;
        this.todoService = todoService;
    }

    public String chat(String userId, String message) {

        log.info("==================================================");
        log.info("NUEVA PETICION");
        log.info("USER: {}", userId);
        log.info("MESSAGE: {}", message);
        log.info("==================================================");

        save(userId, USER, message);

        boolean memoryResult = userMemoryService.extractAndSave(userId, message);
        
        if (memoryResult) {

            String response =
                    "Perfecto, lo guardaré en mi memoria.";

            log.info("MEMORY CONFIRMATION -> {}", response);

            save(userId, ASSISTANT, response);

            return response;
        }

        // ==========================
        // GREETINGS
        // ==========================
        if (isSmallTalk(message)) {
            log.info("SMALL TALK DETECTED -> bypass router");

            return chatClient.prompt()
                    .system("Eres un asistente amable. Responde saludo breve.")
                    .user(message)
                    .call()
                    .content();
        }
        
        // ==========================
        // ROUTE DECISION
        // ==========================
        RouteDecision decision =
                queryRouterService.decide(message);

        log.info("ROUTER DECISION -> {}", decision);

        // ==========================
        // PERSONALITY BOT
        // ==========================
        String personalityBlock = "";

        if (decision.useLlm()) {
            personalityBlock =
                    getPersonalityBotByUserId(userId);
        }

        // ==========================
        // MEMORY
        // ==========================
        List<String> memoryList = List.of();

        if (decision.useMemory()) {

            List<UserMemoryDTO> memories =
                    userMemoryService.getMemory(userId);

            memoryList = memories.stream()
                    .map(m -> toNaturalMemory(
                            m.memoryKey(),
                            m.memoryValue()))
                    .toList();

            log.info("MEMORY FOUND -> {}", memoryList);
        }
        
        // ==========================
        // WEB 
        // ==========================
        List<String> webList = new ArrayList<>();

        boolean isPersonalQuestion = isAPersonalQuestion(message);

        websearch(message, decision, webList, isPersonalQuestion);

        // ==========================
        // TOOL EXECUTION
        // ==========================
        List<String> toolList = new ArrayList<>();

        toolWeather(decision, toolList);

        toolCalendar(message, decision, toolList);
        
        toolReminder(message, decision, toolList, userId);
        
        toolTodo(message, decision, toolList, userId);
        
        toolTodoList(decision, toolList, userId);
        
        toolTodoComplete(message, decision, toolList, userId);
        
        toolTime(decision, toolList);

        // ==========================
        // TOOL BYPASS
        // ==========================
        if (!toolList.isEmpty()) {

            log.info("TOOL RESPONSE MODE (bypass LLM)");

            String toolResponse = toolList.get(0);

            save(userId, ASSISTANT, toolResponse);

            return toolResponse;
        }

        // ==========================
        // HISTORY
        // ==========================
        List<String> historyList = new ArrayList<>();
        
        if (decision.useLlm()) {

            List<ChatMessage> history =
                    chatRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId);
            
            historyList =
                    history.stream()
                            .filter(m -> USER.equals(m.getRole()))
                            .map(ChatMessage::getContent)
                            .toList();

            log.info("HISTORY -> {}", historyList);

        }
        
        // ==========================
        // SAFE CONTEXT BUILD
        // ==========================
        String context = "";

        if (decision.useLlm()) {
            context = buildContext(
                    memoryList,
                    webList,
                    historyList);
        }

        // ==========================
        // SAFETY FALLBACK 
        // ==========================
        boolean hasNoMemory = memoryList.isEmpty();
        boolean hasNoWeb = webList.isEmpty();
        boolean isPersonalNoData = isPersonalQuestion && hasNoMemory && hasNoWeb;

        if (isPersonalNoData) {

            String safeResponse =
                    "No tengo información suficiente para responder a eso.";

            log.info("SAFE FALLBACK -> no memory / no web for personal question");

            save(userId, ASSISTANT, safeResponse);
            return safeResponse;
        }

        // ==========================
        // LLM CALL
        // ==========================
        String response = "";
        
        if (decision.useMemory() && !decision.useLlm()) {

            Optional<UserMemoryDTO> result =
                    userMemoryService.findBestMatch(
                            userId,
                            message);

            return result
                    .map(UserMemoryDTO::memoryValue)
                    .orElse("No lo sé");
        }else response = callLLM(message, personalityBlock, toolList, context);
        
        save(userId, ASSISTANT, response);

        return response;
    }

    private String callLLM(String message, String personalityBlock, List<String> toolList, String context) {
        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String year = String.valueOf(LocalDate.now().getYear());

        String response = chatClient.prompt()
                .system("""
                %s
                
                =====================================
                
                Eres un asistente estricto basado en CONTEXTO.
                
                Formato de salida de las fechas dd/MM/yyyy hh:mm
                
                FECHA SISTEMA:
                - Fecha actual: %s
                - Año actual: %s
                - Uso horario Madrid/Europa
                
                REGLAS:
                - TOOL > ALL
                - NO INVENTES DATOS BAJO NINGÚN CONCEPTO
                - SI NO HAY INFORMACIÓN → RESPONDE: "No lo sé"
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
        return response;
    }

    private String buildContext(List<String> memoryList, List<String> webList, List<String> historyList) {
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
        return context;
    }

    private void toolCalendar(String message, RouteDecision decision, List<String> toolList) {
        if (ToolUtils.TOOL_CALENDAR.equals(decision.tool())) {

            String rawDate = clean(decision.toolInput());

            String date = normalizeDate(rawDate, message);

            log.info("CALENDAR TOOL -> raw: {}, normalized: {}", rawDate, date);

            String calendar = calendarService.getAgenda(date);

            toolList.add("DATA: %s".formatted(calendar));
        }
    }

    private void toolWeather(RouteDecision decision, List<String> toolList) {
        if (ToolUtils.TOOL_WEATHER.equals(decision.tool())) {

            String city = extractCity(decision.toolInput());

            log.info("WEATHER TOOL -> {}", city);

            if (!city.isBlank()) {
                String weather = weatherService.getWeather(city);

                toolList.add("DATA: %s".formatted(weather));
            }
        }
    }
    
    private void toolReminder(String message,
            RouteDecision decision,
            List<String> toolList,
            String userId) {

        if (ToolUtils.TOOL_REMINDER.equals(decision.tool())) {
        
            log.info("REMINDER TOOL INPUT -> {}", decision.toolInput());
            
            String today = LocalDate.now().toString();
            String year = String.valueOf(LocalDate.now().getYear());
            
            try {
            
            String response = chatClient.prompt()
                    .system("""
                            Eres un extractor de recordatorios.

                            FECHA ACTUAL DEL SISTEMA:
                            - Hoy: %s
                            - Año actual: %s

                            REGLA CRÍTICA:

                            "recurrence" DEBE SER NONE por defecto.
                            
                            SOLO puedes devolver:
                            - DAILY
                            - WEEKLY
                            - MONTHLY
                            - YEARLY
                            
                            si aparecen expresiones explícitas en el mensaje.
                            
                            Ejemplos:
                            
                            "cada día" -> DAILY
                            "todos los días" -> DAILY
                            
                            "cada semana" -> WEEKLY
                            "todas las semanas" -> WEEKLY
                            
                            "cada mes" -> MONTHLY
                            "todos los meses" -> MONTHLY
                            
                            "cada año" -> YEARLY
                            "todos los años" -> YEARLY
                            
                            Si NO aparece ninguna expresión de repetición:
                            recurrence = NONE
                            
                            Está PROHIBIDO inferir repeticiones.
                            Está PROHIBIDO asumir DAILY.

                            Devuelve SOLO JSON válido.

                            FORMATO:
                            {
                              "title": "texto del evento",
                              "dateTime": "yyyy-MM-dd HH:mm",
                              "endDateTime": "yyyy-MM-dd HH:mm",
                              "recurrence": "DAILY | WEEKLY | MONTHLY | YEARLY | NONE"
                            }

                            """.formatted(today, year))
                  .user("""
                          Mensaje:
                          %s
                          """.formatted(message))
                  .call()
                  .content();
            
            log.info("REMINDER EXTRACTOR RESPONSE -> {}", response);
            
            ObjectMapper mapper = new ObjectMapper();
            
            ReminderItem item = mapper.readValue(response, ReminderItem.class);
            
            DateTimeFormatter formatter =
                  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            
            LocalDateTime dateTime =
                  LocalDateTime.parse(item.dateTime(), formatter);
            
            LocalDateTime endDateTime = null;
            
            if (item.endDateTime() != null && !item.endDateTime().isBlank()) {
                endDateTime = LocalDateTime.parse(item.endDateTime(), formatter);
            }
            
            String recurrence =
                  item.recurrence() != null ? item.recurrence() : "NONE";
            
            reminderService.save(
                  userId,
                  item.title(),
                  dateTime,
                  endDateTime,
                  recurrence
            );
            
            log.info("REMINDER SAVED -> title={}, dateTime={}, recurrence={}",
                  item.title(), dateTime, recurrence);
            
            toolList.add("RECORDATORIO CREADO: " + item.title() + " el " + dateTime);
            
            } catch (Exception e) {
                log.error("ERROR PARSING REMINDER", e);
            }
        }
    }
    
    private void toolTodoList(
            RouteDecision decision,
            List<String> toolList,
            String userId) {

        if (ToolUtils.TOOL_TODO_LIST.equals(decision.tool())) {
        
            log.info("TODO LIST TOOL");
            
            List<Todo> todos = todoService.getPending(userId);
            
            if (todos.isEmpty()) {
                toolList.add("No tienes tareas pendientes.");
                return;
            }
            
            StringBuilder sb = new StringBuilder("Tareas pendientes:\n");
            
            for (int i = 0; i < todos.size(); i++) {
                sb.append(i + 1)
                .append(". ")
                .append(todos.get(i).getTitle())
                .append("\n");
            }
            
            toolList.add(sb.toString());
        }
}
    
    private void toolTodo(String message,
            RouteDecision decision,
            List<String> toolList,
            String userId) {

        if (ToolUtils.TOOL_TODO.equals(decision.tool())) {
        
            log.info("TODO TOOL INPUT -> {}", decision.toolInput());
            
            try {
            
             String response = chatClient.prompt()
                     .system("""
                             Eres un extractor de tareas TODO.
            
                             Devuelve SOLO JSON válido.
            
                             FORMATO:
                             {
                               "title": "texto de la tarea"
                             }
            
                             REGLAS:
                             - No inventes datos
                             - No añadas fechas
                             - No interpretes
                             - Solo el título
                             """)
                     .user(message)
                     .call()
                     .content();
            
             log.info("TODO EXTRACTOR RESPONSE -> {}", response);
            
             ObjectMapper mapper = new ObjectMapper();
            
             TodoItem item = mapper.readValue(response, TodoItem.class);
            
             todoService.save(userId, item.title());
            
             toolList.add("TODO CREADO: " + item.title());
            
            } catch (Exception e) {
             log.error("ERROR PARSING TODO", e);
            }
        }
}
    
    private void toolTodoComplete(String message,
            RouteDecision decision,
            List<String> toolList,
            String userId) {

    if (ToolUtils.TOOL_TODO_COMPLETE.equals(decision.tool())) {
    
        String task = normalizeTodoText(decision.toolInput());
        
        log.info("TODO COMPLETE TOOL -> {}", task);
        
        List<Todo> todos = todoService.getPending(userId);
        
        Optional<Todo> match = todos.stream()
                .filter(t -> normalizeTodoText(t.getTitle())
                        .equalsIgnoreCase(task))
                .findFirst();
        
        if (match.isPresent()) {
        
            todoService.markCompleted(match.get().getId());
            
            toolList.add("Tarea completada: " + task);
            
         } else {
            toolList.add("No he encontrado esa tarea.");
         }
    }
}
    
    private void toolTime(
            RouteDecision decision,
            List<String> toolList) {

        if (ToolUtils.TOOL_TIME.equals(decision.tool())) {

            String time =
                    ZonedDateTime.now(
                        ZoneId.of("Europe/Madrid"))
                    .format(
                        DateTimeFormatter.ofPattern(
                            "HH:mm"));

            toolList.add(
                "Son las " + time);
        }
    }    
    
    private void websearch(String message, RouteDecision decision, List<String> webList, boolean isPersonalQuestion) {
        if (decision.useWeb() && !isPersonalQuestion) {

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
        } else if (decision.useWeb() && isPersonalQuestion) {
            log.warn("WEB BLOQUEADA -> pregunta personal detectada");
        }
    }

    private boolean isAPersonalQuestion(String message) {
        boolean isPersonalQuestion =
                message.toLowerCase().contains("mi ") ||
                message.toLowerCase().contains("mujer") ||
                message.toLowerCase().contains("pareja") ||
                message.toLowerCase().contains("hijo");
        return isPersonalQuestion;
    }

    private String getPersonalityBotByUserId(String userId) {
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
        return personalityBlock;
    }

    private String normalizeDate(String input, String originalMessage) {

        if (input == null || input.isBlank()) {
            return LocalDate.now().toString();
        }

        LocalDate today = LocalDate.now();
        String normalizedInput = input.trim().toLowerCase();

        if (normalizedInput.contains("hoy")) return today.toString();
        if (normalizedInput.contains("mañana")) return today.plusDays(1).toString();
        if (normalizedInput.contains("ayer")) return today.minusDays(1).toString();

        LocalDate parsed = parseDate(normalizedInput);

        if (parsed == null) return today.toString();

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
    
    private String toNaturalMemory(String key, String value) {

        if (key == null || value == null) return "";

        if (key.contains("pareja:fecha_nacimiento")) {
            return "La pareja del usuario nació el " + value;
        }

        if (key.contains("pareja:nombre")) {
            return "La pareja del usuario se llama " + value;
        }

        if (key.contains("self:nombre")) {
            return "El nombre del usuario es " + value;
        }

        if (key.contains("self:fecha_nacimiento")) {
            return "El usuario nació el " + value;
        }

        if (key.contains("hijo") && key.contains("nombre")) {
            return "El hijo del usuario se llama " + value;
        }

        if (key.contains("hijo") && key.contains("fecha_nacimiento")) {
            return "El hijo del usuario nació el " + value;
        }

        return key + ": " + value;
    }
    
    private boolean isSmallTalk(String message) {

        String m = message.toLowerCase();

        return m.contains("hola")
            || m.contains("qué tal")
            || m.contains("como estás")
            || m.contains("buenos días")
            || m.contains("buenos noches")
            || m.contains("buenas tardes");
    }
    
    private String extractCity(String text) {
        
        if (text == null) {
            return "";
        }

        String lower = text.toLowerCase();

        String[] patterns = {
                "tiempo en ",
                "tiempo de ",
                "hace en ",
                "clima en ",
                "clima de ",
                "temperatura en ",
                "temperatura de ",
                "en "
        };

        for (String pattern : patterns) {

            int index = lower.lastIndexOf(pattern);

            if (index >= 0) {
                return text.substring(index + pattern.length()).trim();
            }
        }

        return text.trim();
    }
    
    private String normalizeTodoText(String text) {

        return text.toLowerCase()
                .replace("marca", "")
                .replace("como completada", "")
                .replace("completada", "")
                .replace("completa", "")
                .replace("he terminado", "")
                .replace("ya hice", "")
                .trim();
    }
}