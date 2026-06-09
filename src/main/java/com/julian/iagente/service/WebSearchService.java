package com.julian.iagente.service;

import java.util.List;

import com.julian.iagente.model.WebResult;

public interface WebSearchService {
    List<WebResult> search(String query);
}