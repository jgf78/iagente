package com.julian.iagente.service.tool;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WeatherService {

    private final RestClient restClient =
            RestClient.create();

    @Cacheable(
            value = "weather",
            key = "T(java.lang.String).valueOf(#city).toLowerCase().trim()"
        )
    public String getWeather(String city) {

        return restClient.get()
                .uri("http://jgf78.duckdns.org:8083/api/weather?city={city}",
                        city)
                .retrieve()
                .body(String.class);
    }
}