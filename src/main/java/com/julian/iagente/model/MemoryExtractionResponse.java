package com.julian.iagente.model;

import java.util.Map;

public record MemoryExtractionResponse(
        Map<String, String> memories) {
}
