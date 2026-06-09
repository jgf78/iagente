package com.julian.iagente.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.julian.iagente.model.AgentPersona;

@Service
public class AgentPersonaService {

    public AgentPersona getPersona(String userId) {
        return buildDefaultPersona();
    }

    private AgentPersona buildDefaultPersona() {

        return new AgentPersona(
                "FryBot",                       // nickname
                "cercano_profesional",          // tone
                "claro_directo_tecnico_medio",  // style
                "media",                        // verbosity
                "es",                           // language
                List.of(
                        "inventar identidad del usuario",
                        "afirmar información sin contexto",
                        "mezclar memoria y web como fuente única"
                )
        );
    }
}