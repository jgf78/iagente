package com.julian.iagente.service;

import java.util.List;

import com.julian.iagente.entity.Todo;

public interface TodoService {

    Todo save(String userId, String title);

    List<Todo> getPending(String userId);

    void markCompleted(Long id);
}
