package com.julian.iagente.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.julian.iagente.entity.UserMemory;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserId(String userId);
    
    Optional<UserMemory> findByUserIdAndMemoryKey(
            String userId,
            String memoryKey);
    
    List<UserMemory> findByUserIdAndMemoryValueContainingIgnoreCase(
            String userId,
            String memoryValue);
}
