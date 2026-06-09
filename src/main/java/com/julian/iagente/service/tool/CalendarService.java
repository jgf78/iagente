package com.julian.iagente.service.tool;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CalendarService {

    private final RestClient restClient =
            RestClient.create();

    public String getAgenda(String date) {

        return restClient.get()
                .uri(
                        "http://jgf78japan.duckdns.org:8083/calendar/day/message?date={date}",
                        date)
                .retrieve()
                .body(String.class);
    }
}
