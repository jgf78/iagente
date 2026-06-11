package com.julian.iagente.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reminder")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "reminder_date", nullable = false)
    private LocalDateTime reminderDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endReminderDate;

    @Builder.Default
    @Column(name = "sent", nullable = false)
    private boolean sent = false;

    @Builder.Default
    @Column(name = "recurrence", nullable = false, length = 20)
    private String recurrence = "NONE";
}