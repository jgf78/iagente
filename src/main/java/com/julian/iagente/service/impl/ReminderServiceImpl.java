package com.julian.iagente.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.julian.iagente.entity.Reminder;
import com.julian.iagente.repository.ReminderRepository;
import com.julian.iagente.service.ReminderService;

@Service
public class ReminderServiceImpl implements ReminderService {

    private final ReminderRepository reminderRepository;

    public ReminderServiceImpl(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @Override
    public Reminder save(String userId,
                         String title,
                         LocalDateTime reminderDate,
                         LocalDateTime endReminderDate,
                         String recurrence) {

        Reminder reminder = Reminder.builder()
                .userId(userId)
                .title(title)
                .reminderDate(reminderDate)
                .endReminderDate(endReminderDate)
                .sent(false)
                .recurrence(recurrence == null ? "NONE" : recurrence)
                .build();

        return reminderRepository.save(reminder);
    }

    @Override
    public List<Reminder> getPendingReminders() {
        return reminderRepository
                .findBySentFalseAndReminderDateLessThanEqual(LocalDateTime.now());
    }

    @Override
    public void processRecurrence(Reminder reminder) {

        if ("NONE".equals(reminder.getRecurrence())) {

            reminder.setSent(true);
            reminderRepository.save(reminder);
            return;
        }

        LocalDateTime nextDate = calculateNextDate(reminder);

        if (reminder.getEndReminderDate() != null
                && nextDate.isAfter(reminder.getEndReminderDate())) {

            reminder.setSent(true);

        } else {

            reminder.setReminderDate(nextDate);
        }

        reminderRepository.save(reminder);
    }
    
    private LocalDateTime calculateNextDate(Reminder reminder) {

        return switch (reminder.getRecurrence()) {

            case "DAILY" ->
                    reminder.getReminderDate().plusDays(1);

            case "WEEKLY" ->
                    reminder.getReminderDate().plusWeeks(1);

            case "MONTHLY" ->
                    reminder.getReminderDate().plusMonths(1);

            case "YEARLY" ->
                    reminder.getReminderDate().plusYears(1);

            default ->
                    reminder.getReminderDate();
        };
    }

}
