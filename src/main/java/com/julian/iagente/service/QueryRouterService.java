package com.julian.iagente.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julian.iagente.model.RouteDecision;

@Service
public class QueryRouterService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public QueryRouterService(ChatClient chatClient,
                              ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public RouteDecision decide(String message) {

        String response = chatClient.prompt()
                .system("""
                        Eres un clasificador de consultas.

                        Debes decidir si la pregunta necesita:

                        - memoria del usuario (datos personales)
                        - búsqueda web (hechos externos actuales)
                        - conocimiento general del modelo

                        REGLAS CRÍTICAS:

                        1. Si la pregunta contiene "mi", "me", "yo", "tengo", "preferido", "favorito"
                           y está relacionada con el usuario → MEMORY = true, WEB = false

                        2. Si es una pregunta de actualidad externa (noticias, resultados, política, deportes recientes)
                           → WEB = true

                        3. Si es conocimiento general → LLM = true

                        4. NUNCA uses web para información personal del usuario

                        Devuelve SOLO JSON válido:

                        {
                          "useMemory": boolean,
                          "useWeb": boolean,
                          "useLlm": boolean,
                          "webQuery": "string"
                        }
                        """)
                .user(message)
                .call()
                .content();

        try {
            return objectMapper.readValue(response, RouteDecision.class);
        } catch (Exception e) {
            return new RouteDecision(true, false, true, "");
        }
    }
}