package com.julian.iagente.service;

import java.time.LocalDateTime;
import java.util.List;

import com.julian.iagente.entity.Reminder;

public interface ReminderService {

    List<Reminder> getUserReminders(String userId);

    List<Reminder> getPendingReminders();

    void markAsSent(Long reminderId);

    Reminder save(String userId, String title, LocalDateTime reminderDate, LocalDateTime endReminderDate,
            String recurrence);
}
