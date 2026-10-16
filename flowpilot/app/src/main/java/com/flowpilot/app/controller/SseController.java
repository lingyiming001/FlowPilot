package com.flowpilot.app.controller;

import com.flowpilot.workflow.service.WorkflowApplicationService;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {
  private final WorkflowApplicationService workflows;
  private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2);

  public SseController(WorkflowApplicationService workflows) {
    this.workflows = workflows;
  }

  @GetMapping(value = "/api/workflows/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @PathVariable String id,
      @RequestHeader(value = "Last-Event-ID", required = false) String last) {
    var original = SecurityContextHolder.getContext();
    var initial = workflows.events(id);
    Set<String> seen = ConcurrentHashMap.newKeySet();
    if (last != null) {
      boolean found = false;
      for (var e : initial) {
        seen.add(e.get("id").toString());
        if (e.get("id").equals(last)) {
          found = true;
          break;
        }
      }
      if (!found) seen.clear();
    }
    var emitter = new SseEmitter(25000L);
    var future =
        timer.scheduleWithFixedDelay(
            () -> {
              try {
                SecurityContextHolder.setContext(original);
                for (var event : workflows.events(id)) {
                  String eventId = event.get("id").toString();
                  if (seen.add(eventId))
                    emitter.send(SseEmitter.event().id(eventId).name("transition").data(event));
                }
                emitter.send(SseEmitter.event().comment("heartbeat"));
              } catch (Exception e) {
                emitter.completeWithError(e);
              } finally {
                SecurityContextHolder.clearContext();
              }
            },
            0,
            2,
            TimeUnit.SECONDS);
    emitter.onCompletion(() -> future.cancel(false));
    emitter.onTimeout(
        () -> {
          future.cancel(false);
          emitter.complete();
        });
    emitter.onError(e -> future.cancel(false));
    return emitter;
  }

  @PreDestroy
  public void close() {
    timer.shutdownNow();
  }
}
