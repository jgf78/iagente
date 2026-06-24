package com.julian.iagente.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.julian.iagente.model.RouteDecision;
import com.julian.iagente.util.ToolUtils;

@Service
public class QueryRouterService {

    private static final String NONE = "NONE";

    private static final Logger log = LoggerFactory.getLogger(QueryRouterService.class);

    private final ChatClient chatClient;

    public QueryRouterService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public RouteDecision decide(String message) {

        log.info("ROUTER INPUT -> {}", message);

        String msg = message.toLowerCase();

        // =========================
        // 1. MEMORY FAST ROUTE (PRIORIDAD REAL)
        // =========================
        if (isPersonalQuery(msg)) {

            RouteDecision decision = new RouteDecision(
                    true,
                    false,
                    false,
                    "",
                    NONE,
                    ""
            );

            log.info("ROUTER MEMORY FAST DECISION -> {}", decision);
            return decision;
        }

        // =========================
        // 2. WEATHER TOOL
        // =========================
        if (msg.contains("tiempo")
                || msg.contains("clima")
                || msg.contains("llover")
                || msg.contains("temperatura")) {

            RouteDecision decision = new RouteDecision(
                    false,
                    false,
                    false,
                    "",
                    ToolUtils.TOOL_WEATHER,
                    message
            );

            log.info("ROUTER WEATHER DECISION -> {}", decision);
            return decision;
        }

        // =========================
        // 3. CALENDAR TOOL
        // =========================
        if (msg.contains("agenda")
                || msg.contains("calendario")
                || msg.contains("evento")
                || msg.contains("reunión")
                || msg.contains("reunion")) {

            RouteDecision decision = new RouteDecision(
                    false,
                    false,
                    false,
                    "",
                    ToolUtils.TOOL_CALENDAR,
                    message
            );

            log.info("ROUTER CALENDAR DECISION -> {}", decision);
            return decision;
        }
        
        // =========================
        // 4. REMINDER TOOL
        // =========================
        if (msg.contains("recordatorio")
                || msg.contains("avisame")
                || msg.contains("recuerdame")
                || msg.contains("aviso")) {

            RouteDecision decision = new RouteDecision(
                    false,
                    false,
                    false,
                    "",
                    ToolUtils.TOOL_REMINDER,
                    message
            );

            log.info("ROUTER REMINDER DECISION -> {}", decision);
            return decision;
        }
        
        // =========================
        // 5. TODO TOOL
        // =========================
        if (msg.contains("apunta")
                || msg.contains("lista")) {

            RouteDecision decision = new RouteDecision(
                    false,
                    false,
                    false,
                    "",
                    ToolUtils.TOOL_TODO,
                    message
            );

            log.info("ROUTER TODO DECISION -> {}", decision);
            return decision;
        }
        
        // =========================
        // 6. TODO_LIST TOOL
        // =========================
        
        if (msg.contains("que tareas tengo ")
                || msg.contains("tareas pendientes")
                || msg.contains("mis tareas")
                || msg.contains("listado de tareas")) {

            RouteDecision decision = new RouteDecision(
                    false,
                    false,
                    false,
                    "",
                    ToolUtils.TOOL_TODO_LIST,
                    message
            );

            log.info("ROUTER TODO DECISION -> {}", decision);
            return decision;
        }
        
        // =========================
        // 7. TODO_LIST TOOL
        // =========================
        
        if (msg.contains("marca la tarea ")
                || msg.contains("comprar")
                || msg.contains("completada la tarea")) {

            RouteDecision decision = new RouteDecision(
                    false,
                    false,
                    false,
                    "",
                    ToolUtils.TOOL_TODO_COMPLETE,
                    message
            );

            log.info("ROUTER TODO_COMPLETE DECISION -> {}", decision);
            return decision;
        }
        
        // =========================
        // 8. TIME
        // =========================
        
        if (msg.contains("que hora es")) {

            RouteDecision decision = new RouteDecision(
                    false,
                    false,
                    false,
                    "",
                    ToolUtils.TOOL_TIME,
                    message
            );

            log.info("ROUTER TIME DECISION -> {}", decision);
            return decision;
        }

        // =========================
        // 9. LLM ROUTER
        // =========================
        RouteDecision decision = chatClient.prompt()
                .system("""
                        Eres un clasificador de consultas.

                        Debes decidir entre:

                        - MEMORY: datos personales del usuario
                        - WEB: información externa o actual
                        - LLM: conocimiento general
                        - TOOL: servicios externos

                        =========================
                        REGLAS IMPORTANTES
                        =========================

                        MEMORY:
                        - preguntas sobre usuario, familia, datos personales
                        - NO uses WEB si hay datos personales en contexto

                        WEATHER TOOL:
                        tool = WEATHER
                        toolInput = solo ciudad

                        CALENDAR TOOL:
                        tool = CALENDAR
                        toolInput = fecha (YYYY-MM-DD)
                        
                        REMINDER TOOL:
                        tool = REMINDER
                        toolInput = fecha (YYYY-MM-DD HH:mm)
                        
                        TODO TOOL:
                        tool = TODO
                        toolInput = mensaje
                        
                        TODO_LIST TOOL:
                        tool = TODO_LIST
                        toolInput = ""
                        
                        TODO_COMPLETE TOOL:
                        tool = TODO_COMPLETE
                        toolInput = mensaje
                        
                        TIME TOOL:
                        tool = TIME
                        toolInput = ""

                        WEB:
                        SOLO para información externa o actual:
                        - noticias
                        - deportes
                        - política
                        - resultados deportivos
                        - resultados elecciones
                        - noticias de corazon
                        - noticias financieras
                        - programacion television
                        - noticias videojuegos
                        - noticias musicales
                        - precios actuales
                        - eventos recientes
                        - que dia es hoy, que dia de la semana es hoy

                        Si hay duda → WEB

                        =========================
                        OUTPUT OBLIGATORIO
                        =========================

                        Devuelve SOLO JSON:

                        {
                          "useMemory": false,
                          "useWeb": false,
                          "useLlm": false,
                          "webQuery": "",
                          "tool": "NONE",
                          "toolInput": ""
                        }

                        PROHIBIDO:
                        - texto adicional
                        - múltiples JSON
                        """)
                .user(message)
                .call()
                .entity(RouteDecision.class);

        // fallback webQuery vacío
        if (decision != null && decision.useWeb()
                && (decision.webQuery() == null || decision.webQuery().isBlank())) {

            log.warn("WEB QUERY NULL -> fallback message");

            decision = new RouteDecision(
                    decision.useMemory(),
                    true,
                    decision.useLlm(),
                    message,
                    decision.tool(),
                    decision.toolInput()
            );
        }

        log.info("ROUTER FINAL DECISION -> {}", decision);

        return decision;
    }

    // =========================
    // MEMORY DETECTOR 
    // =========================
    private boolean isPersonalQuery(String msg) {

        return msg.contains("me llamo")
                || msg.contains("mi hijo")
                || msg.contains("mis hijos")
                || msg.contains("mi mujer")
                || msg.contains("mi pareja")
                || msg.contains("tengo")
                || msg.contains("vivo en")
                || msg.contains("cuantos años")
                || msg.contains("qué edad")
                || msg.contains("cuando naci")
                || msg.contains("como se llama mi");
    }
}