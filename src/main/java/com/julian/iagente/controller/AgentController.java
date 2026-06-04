package com.julian.iagente.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.julian.iagente.model.ChatRequest;
import com.julian.iagente.service.AgentService;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return agentService.chat(request.userId(), request.message());
    }
}
