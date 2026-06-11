package com.julian.iagente.service.notification;

import com.julian.iagente.entity.Reminder;

public interface NotificationService {

    void sendReminderNotification(Reminder reminder);

}
