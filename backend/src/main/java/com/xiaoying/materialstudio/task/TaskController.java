package com.xiaoying.materialstudio.task;

import com.xiaoying.materialstudio.identity.SessionService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @RequestBody CreateTaskRequest request) {
        TaskResponse task = service.create(token, request);
        return ResponseEntity.accepted().header(HttpHeaders.LOCATION, "/api/tasks/" + task.id())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(task);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> get(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @PathVariable String id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(service.get(token, id));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> history(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(service.history(token));
    }
}
