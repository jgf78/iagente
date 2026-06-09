package com.julian.iagente.model;

public record RouteDecision(
        boolean useMemory,
        boolean useWeb,
        boolean useLlm,
        String webQuery
) {}