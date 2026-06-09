package com.julian.iagente.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.julian.iagente.model.TavilyRequest;
import com.julian.iagente.model.TavilyResponse;
import com.julian.iagente.model.WebResult;

@Service
public class TavilyWebSearchService implements WebSearchService {

    private final RestClient restClient;

    @Value("${TAVILY_API_KEY}")
    private String apiKey;

    public TavilyWebSearchService(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public List<WebResult> search(String query) {

        TavilyRequest request = new TavilyRequest(
                apiKey,
                query,
                "basic",
                5
        );

        TavilyResponse response = restClient.post()
                .uri("https://api.tavily.com/search")
                .body(request) 
                .retrieve()
                .body(TavilyResponse.class);

        if (response == null) {
            return List.of();
        }

        return response.toWebResults();
    }
}