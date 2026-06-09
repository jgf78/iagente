package com.julian.iagente.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TavilyRequest(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("query") String query,
        @JsonProperty("search_depth") String searchDepth,
        @JsonProperty("max_results") Integer maxResults
) {}