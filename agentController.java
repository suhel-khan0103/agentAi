package com.suhel.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final List<Map<String, Object>> notes = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> reminders = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> contacts = new CopyOnWriteArrayList<>();

    @Value("${agent.ai.api-key:}") private String apiKey;
    @Value("${agent.ai.base-url:https://api.openai.com/v1}") private String baseUrl;
    @Value("${agent.ai.model:gpt-4o-mini}") private String model;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "app", "Suhel AI Agent", "time", Instant.now().toString(),
                "aiConfigured", !apiKey.isBlank());
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("message", "").trim();
        if (question.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Please enter a message."));
        if (apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "AI API key set nahi hai. README.md mein setup steps follow karo. Notes, reminders, contacts aur WhatsApp link features phir bhi use ho sakte hain."
            ));
        }
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are Suhel's helpful personal study and productivity assistant. Reply clearly in Hindi/Hinglish when appropriate. Never claim to have sent a message, made a call, or changed a phone setting unless a real integration confirms it. Ask for confirmation before consequential actions."),
                            Map.of("role", "user", "content", question)
                    ),
                    "temperature", 0.5
            );
            Map result = RestClient.create(baseUrl)
                    .post().uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(Map.class);
            List choices = (List) result.get("choices");
            Map first = (Map) choices.get(0);
            Map message = (Map) first.get("message");
            return ResponseEntity.ok(Map.of("reply", String.valueOf(message.get("content"))));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "AI provider request failed. API key/model/network check karo."));
        }
    }

    @GetMapping("/notes")
    public List<Map<String, Object>> getNotes() { return notes; }

    @PostMapping("/notes")
    public Map<String, Object> addNote(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "").trim();
        if (text.isBlank()) throw new IllegalArgumentException("Note cannot be empty.");
        Map<String, Object> note = Map.of("id", UUID.randomUUID().toString(), "text", text, "createdAt", Instant.now().toString());
        notes.add(0, note);
        return note;
    }

    @DeleteMapping("/notes/{id}")
    public Map<String, Object> deleteNote(@PathVariable String id) {
        boolean removed = notes.removeIf(n -> id.equals(n.get("id")));
        return Map.of("deleted", removed);
    }

    @GetMapping("/reminders")
    public List<Map<String, Object>> getReminders() { return reminders; }

    @PostMapping("/reminders")
    public Map<String, Object> addReminder(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        String at = body.getOrDefault("at", "").trim();
        if (title.isBlank() || at.isBlank()) throw new IllegalArgumentException("Reminder title and date/time required.");
        Map<String, Object> item = Map.of("id", UUID.randomUUID().toString(), "title", title, "at", at, "createdAt", Instant.now().toString());
        reminders.add(0, item);
        return item;
    }

    @DeleteMapping("/reminders/{id}")
    public Map<String, Object> deleteReminder(@PathVariable String id) {
        boolean removed = reminders.removeIf(r -> id.equals(r.get("id")));
        return Map.of("deleted", removed);
    }

    @GetMapping("/contacts")
    public List<Map<String, Object>> getContacts() { return contacts; }

    @PostMapping("/contacts")
    public Map<String, Object> addContact(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        String phone = body.getOrDefault("phone", "").replaceAll("[^0-9+]", "");
        if (name.isBlank() || phone.length() < 7) throw new IllegalArgumentException("Valid name and phone number required.");
        Map<String, Object> contact = Map.of("id", UUID.randomUUID().toString(), "name", name, "phone", phone);
        contacts.add(0, contact);
        return contact;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
