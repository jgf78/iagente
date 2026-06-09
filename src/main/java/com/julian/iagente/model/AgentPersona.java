package com.julian.iagente.model;

import java.util.List;

public record AgentPersona(
        String nickname,
        String tone,
        String style,
        String verbosity,
        String language,
        List<String> forbiddenBehaviours
) {

    public AgentPersona {

        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname no puede ser vacío");
        }

        if (forbiddenBehaviours == null) {
            forbiddenBehaviours = List.of();
        }
    }
}
