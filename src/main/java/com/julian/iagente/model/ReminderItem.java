package com.julian.iagente.model;

public record ReminderItem(
        String title,
        String dateTime,
        String endDateTime,
        String recurrence
) {
    public ReminderItem {
        if (recurrence == null || recurrence.isBlank()) {
            recurrence = "NONE";
        }
    }
}