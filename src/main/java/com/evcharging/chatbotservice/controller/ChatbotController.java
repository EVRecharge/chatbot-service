package com.evcharging.chatbotservice.controller;

import com.evcharging.chatbotservice.dto.SendMessageRequest;
import com.evcharging.chatbotservice.service.ChatbotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    @Autowired
    private ChatbotService chatbotService;

    @GetMapping("/history")
    public ResponseEntity<?> history() {
        String entityType = currentEntityType();
        Long entityId = currentEntityId();
        if (entityType == null || entityId == null) {
            return forbidden();
        }
        return ResponseEntity.ok(chatbotService.getHistory(entityType, entityId));
    }

    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        String entityType = currentEntityType();
        Long entityId = currentEntityId();
        if (entityType == null || entityId == null) {
            return forbidden();
        }
        return ResponseEntity.ok(chatbotService.sendMessage(entityType, entityId, request.getMessage()));
    }

    @DeleteMapping("/history")
    public ResponseEntity<?> clearHistory() {
        String entityType = currentEntityType();
        Long entityId = currentEntityId();
        if (entityType == null || entityId == null) {
            return forbidden();
        }
        chatbotService.clearHistory(entityType, entityId);
        return ResponseEntity.ok(Map.of("message", "Conversation cleared"));
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only drivers and providers can use the chat assistant"));
    }

    private Long currentEntityId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long ? (Long) auth.getPrincipal() : null;
    }

    private String currentEntityType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(Object::toString)
                .filter(a -> a.equals("ROLE_USER") || a.equals("ROLE_PROVIDER"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }
}
