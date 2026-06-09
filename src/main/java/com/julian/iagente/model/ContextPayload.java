package com.julian.iagente.model;

import java.util.List;

public record ContextPayload(
        List<String> memory,
        List<String> web,
        List<String> history
) {
    public ContextPayload {
        memory = (memory != null) ? List.copyOf(memory) : List.of();
        web = (web != null) ? List.copyOf(web) : List.of();
        history = (history != null) ? List.copyOf(history) : List.of();
    }
}