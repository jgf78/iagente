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

        RouteDecision decision = chatClient.prompt().system("""
                                        Eres un clasificador de consultas.

                                        Debes decidir si la consulta requiere:

                                        - MEMORY (datos personales del usuario)
                                        - WEB (información externa o actual)
                                        - LLM (conocimiento general)
                                        - TOOL (servicios especializados)

                                        TOOLS DISPONIBLES:

                                        WEATHER
                                        - tiempo
                                        - clima
                                        - temperatura
                                        - lluvia
                                        - previsión meteorológica

                                        CALENDAR
                                        - agenda
                                        - calendario
                                        - eventos
                                        - reuniones

                                        =========================
                                        REGLAS CRÍTICAS
                                        =========================

                                        1. MEMORY:
                                        - datos personales del usuario

                                        2. WEATHER:
                                        tool="WEATHER"
                                        toolInput = SOLO ciudad o localización limpia

                                        Ejemplos:
                                        - "qué tiempo hace en Madrid"
                                          → Madrid

                                        - "lloverá en Alcorcón"
                                          → Alcorcón

                                        3. CALENDAR:
                                        tool="CALENDAR"
                                        toolInput = fecha en formato YYYY-MM-DD

                                        Reglas:
                                        - "hoy" → fecha actual
                                        - "mañana" → fecha actual + 1 día
                                        - "ayer" → fecha actual - 1 día

                                        4. WEB:

                                        Usar WEB para cualquier información externa que pueda cambiar con el tiempo
                                        o que requiera datos actuales, verificados o recientes.
                        
                                        Incluye:
                        
                                        - noticias de cualquier temática
                                        - política y gobiernos
                                        - economía y mercados financieros
                                        - noticias deportivas
                                        - resultados de partidos
                                        - calendarios deportivos
                                        - famosos y actualidad del corazón
                                        - tecnología y lanzamientos
                                        - eventos públicos
                                        - conciertos, festivales y ferias
                                        - horarios y fechas oficiales
                                        - precios, cotizaciones y estadísticas actuales
                                        - elecciones y resultados electorales
                                        - información sobre empresas, organizaciones o personas públicas
                                        - cualquier consulta que contenga palabras como:
                                          hoy, ayer, mañana, actual, actualmente,
                                          última hora, últimas noticias, reciente,
                                          este año, próximo, próximo partido,
                                          clasificación, ranking, resultado
                        
                                        Ejemplos:
                        
                                        "cuando juega España en el mundial 2026"
                                        → WEB
                        
                                        "resultado del Madrid ayer"
                                        → WEB
                        
                                        "quién ganó la Champions"
                                        → WEB
                        
                                        "últimas noticias sobre OpenAI"
                                        → WEB
                        
                                        "quién es el presidente actual de Francia"
                                        → WEB
                        
                                        "precio actual del Bitcoin"
                                        → WEB
                        
                                        "qué está pasando en Ucrania"
                                        → WEB
                        
                                        "elecciones en Estados Unidos"
                                        → WEB
                        
                                        Respuesta:
                        
                                        {
                                          "useMemory": false,
                                          "useWeb": true,
                                          "useLlm": false,
                                          "webQuery": "<consulta original>",
                                          "tool": "NONE",
                                          "toolInput": ""
                                        }

                                        5. LLM:
                                        conocimiento general

                                        6. REGLA ABSOLUTA:
                                        - toolInput NO debe contener años inventados
                                        - toolInput debe ser EXACTAMENTE lo que dice el usuario
                                        - NO transformes fechas
                                        - NO conviertas a YYYY-MM-DD

                                        =========================
                                        REGLAS DE SALIDA
                                        =========================

                                        - DEVUELVE SOLO UN OBJETO JSON
                                        - PROHIBIDO texto adicional
                                        - PROHIBIDO múltiples JSON
                                        - PROHIBIDO explicaciones

                                        FORMATO:

                                        {
                                          "useMemory": false,
                                          "useWeb": false,
                                          "useLlm": false,
                                          "webQuery": "",
                                          "tool": "NONE",
                                          "toolInput": ""
                                        }
                                        
                                        PRIORIDAD:

                                        TOOL > WEB > MEMORY > LLM
                                        
                                        Si existe duda entre WEB y LLM,
                                        elegir siempre WEB.
                                        """).user(message).call().entity(RouteDecision.class);

        return decision;
    }
}