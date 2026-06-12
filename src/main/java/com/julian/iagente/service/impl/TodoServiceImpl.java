package com.julian.iagente.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.julian.iagente.entity.Todo;
import com.julian.iagente.repository.TodoRepository;
import com.julian.iagente.service.TodoService;

@Service
public class TodoServiceImpl implements TodoService {

    private final TodoRepository repo;

    public TodoServiceImpl(TodoRepository repo) {
        this.repo = repo;
    }

    @Override
    public Todo save(String userId, String title) {

        Todo todo = Todo.builder()
                .userId(userId)
                .title(title)
                .completed(false)
                .createdAt(LocalDateTime.now())
                .build();

        return repo.save(todo);
    }

    @Override
    public List<Todo> getPending(String userId) {
        return repo.findByUserIdAndCompletedFalse(userId);
    }

    @Override
    public void markCompleted(Long id) {

        repo.findById(id).ifPresent(todo -> {
            todo.setCompleted(true);
            todo.setCompletedAt(LocalDateTime.now());
            repo.save(todo);
        });
    }
}
