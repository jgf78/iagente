package com.julian.iagente.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TavilyResponse {

    @JsonProperty("results")
    private List<ResultItem> results = new ArrayList<>();

    public List<ResultItem> getResults() {
        return results;
    }

    public void setResults(List<ResultItem> results) {
        this.results = results;
    }

    public List<WebResult> toWebResults() {

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .map(r -> new WebResult(
                        safe(r.getTitle()),
                        safe(r.getUrl()),
                        safe(r.getContent())
                ))
                .toList();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}