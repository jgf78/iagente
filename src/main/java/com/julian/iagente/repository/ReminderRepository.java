package com.julian.iagente.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.julian.iagente.entity.Reminder;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByUserId(String userId);

    List<Reminder> findBySentFalseAndReminderDateLessThanEqual(LocalDateTime now);
}