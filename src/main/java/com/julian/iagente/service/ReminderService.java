package com.julian.iagente.service;

import java.time.LocalDateTime;
import java.util.List;

import com.julian.iagente.entity.Reminder;

public interface ReminderService {

    List<Reminder> getPendingReminders();

    Reminder save(String userId, String title, LocalDateTime reminderDate, LocalDateTime endReminderDate,
            String recurrence);

    void processRecurrence(Reminder reminder);
}
