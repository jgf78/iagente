package com.julian.iagente.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.julian.iagente.model.RouteDecision;

@Service
public class QueryRouterService {

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
                    "NONE",
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
                    "WEATHER",
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
                    "CALENDAR",
                    message
            );

            log.info("ROUTER CALENDAR DECISION -> {}", decision);
            return decision;
        }

        // =========================
        // 4. LLM ROUTER
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

                        WEB:
                        SOLO para información externa o actual:
                        - noticias
                        - deportes
                        - política
                        - resultados
                        - precios actuales
                        - eventos recientes

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