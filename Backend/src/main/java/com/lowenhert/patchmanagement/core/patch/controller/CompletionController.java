package com.lowenhert.patchmanagement.core.patch.controller;

import com.lowenhert.patchmanagement.core.patch.service.CompletionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/completion")
public class CompletionController {

    @Autowired
    private CompletionService completionService;

    public static class QuestionRequest {
        private String question;
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> getCompletion(@RequestBody QuestionRequest request) {
        String answer = completionService.fetchCompletion(request.getQuestion());
        Map<String, String> response = new HashMap<>();
        response.put("answer", answer);
        return ResponseEntity.ok(response);
    }
}
