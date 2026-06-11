package com.julian.iagente.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.julian.iagente.entity.Reminder;
import com.julian.iagente.service.ReminderService;
import com.julian.iagente.service.notification.NotificationService;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminderService;
    private final NotificationService notificationService;

    public ReminderScheduler(ReminderService reminderService, NotificationService notificationService) {
        this.reminderService = reminderService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 60000) // cada 1 minuto
    public void processReminders() {

        log.info("REMINDER SCHEDULER -> checking pending reminders");

        List<Reminder> reminders = reminderService.getPendingReminders();

        if (reminders.isEmpty()) {
            return;
        }

        for (Reminder reminder : reminders) {

            try {
                log.info("REMINDER TRIGGERED -> userId={}, title={}",
                        reminder.getUserId(),
                        reminder.getTitle());

                notificationService.sendReminderNotification(reminder);
                
                reminderService.processRecurrence(reminder);

            } catch (Exception e) {
                log.error("ERROR PROCESSING REMINDER -> id={}", reminder.getId(), e);
            }
        }
    }
}