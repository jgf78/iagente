package com.julian.iagente.service.notification.impl;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.julian.iagente.entity.Reminder;
import com.julian.iagente.service.notification.NotificationService;



@Service
public class NotificationServiceImpl implements NotificationService {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String URL =
            "http://jgf78.duckdns.org:8083/api/messages/send";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public void sendReminderNotification(Reminder reminder) {

        String startDate = reminder.getReminderDate() != null
                ? reminder.getReminderDate().format(FORMATTER)
                : "";

        String endDate = reminder.getEndReminderDate() != null
                ? reminder.getEndReminderDate().format(FORMATTER)
                : "";
        
        String message =
                "🔥 RECORDATORIO IAGENTE\n\n"
              + "📌 " + reminder.getTitle() + "\n"
              + "⏰ " + startDate
              + (!endDate.isBlank() ? " → " + endDate : "") + "\n"
              + "🔁 " + translateRecurrence(reminder.getRecurrence()) + "\n\n"
              + "🤖 Estoy aquí para ayudarte a no olvidarlo.";

        Map<String, Object> body = Map.of(
                "message", message,
                "destination", "telegram",
                "destinationTelegram", "bot"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(URL, request, String.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String translateRecurrence(String recurrence) {

        if (recurrence == null) return "";
        
        if("DAILY".equals(recurrence)) {
            return "diario";
        }else if("WEEKLY".equals(recurrence)) {
            return "semanal";
        }else if("MONTHLY".equals(recurrence)) {
            return "mensual";
        }else if("YEARLY".equals(recurrence)) {
            return "anual";
        }else return "";

    }

}