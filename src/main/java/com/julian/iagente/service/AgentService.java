package com.julian.iagente.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.julian.iagente.entity.ChatMessage;
import com.julian.iagente.entity.UserMemory;
import com.julian.iagente.repository.ChatMessageRepository;

@Service
public class AgentService {

    private final ChatClient chatClient;
    private final ChatMessageRepository chatRepo;
    private final UserMemoryService userMemoryService;

    public AgentService(ChatClient chatClient,
                        ChatMessageRepository chatRepo,
                        UserMemoryService userMemoryService) {
        this.chatClient = chatClient;
        this.chatRepo = chatRepo;
        this.userMemoryService = userMemoryService;
    }

    public String chat(String userId, String message) {

        // 1. Guardar mensaje usuario
        save(userId, "user", message);

        // 2. Extraer memoria automáticamente con IA
        userMemoryService.extractAndSave(userId, message);

        // 3. Recuperar memoria estructurada
        List<UserMemory> memories =
                userMemoryService.getMemory(userId);

        // 4. Recuperar historial reciente
        List<ChatMessage> history =
                chatRepo.findTop10ByUserIdOrderByCreatedAtAsc(userId);

        // 5. Construir contexto
        StringBuilder context = new StringBuilder();

        context.append("MEMORIA DEL USUARIO:\n");

        for (UserMemory memory : memories) {

            context.append("- ")
                   .append(memory.getMemoryKey())
                   .append(": ")
                   .append(memory.getMemoryValue())
                   .append("\n");
        }

        context.append("\nCONVERSACION RECIENTE:\n");

        for (ChatMessage msg : history) {

            context.append(msg.getRole())
                   .append(": ")
                   .append(msg.getContent())
                   .append("\n");
        }

        // 6. Generar respuesta
        String response = chatClient.prompt()
                .system("""
                        Eres un asistente personal con memoria.

                        Utiliza la memoria del usuario para recordar
                        preferencias, gustos, ubicaciones, trabajo,
                        familia y cualquier dato relevante.

                        Si la memoria contiene la respuesta,
                        úsala antes que la conversación reciente.
                        
                        Responde de forma concisa y siempre en Español
                        """)
                .user(context.toString())
                .call()
                .content();

        // 7. Guardar respuesta
        save(userId, "assistant", response);

        return response;
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