package com.flowpilot.agent.config;

import com.flowpilot.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/model-settings")
public class ModelSettingsController {
  private final ModelSettingsService service;

  public ModelSettingsController(ModelSettingsService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<?> overview() {
    return ApiResponse.ok(service.overview());
  }

  @PutMapping
  public ApiResponse<?> settings(@Valid @RequestBody ModelSettingsService.Settings r) {
    service.save(r);
    return ApiResponse.ok(null);
  }

  @PostMapping("/providers")
  public ApiResponse<?> provider(@Valid @RequestBody ModelSettingsService.Provider r) {
    return ApiResponse.ok(service.provider(null, r));
  }

  @PutMapping("/providers/{id}")
  public ApiResponse<?> providerEdit(
      @PathVariable String id, @Valid @RequestBody ModelSettingsService.Provider r) {
    return ApiResponse.ok(service.provider(id, r));
  }

  @PostMapping("/models")
  public ApiResponse<?> model(@Valid @RequestBody ModelSettingsService.Model r) {
    return ApiResponse.ok(service.model(null, r));
  }

  @PutMapping("/models/{id}")
  public ApiResponse<?> modelEdit(
      @PathVariable String id, @Valid @RequestBody ModelSettingsService.Model r) {
    return ApiResponse.ok(service.model(id, r));
  }

  @PostMapping("/routes")
  public ApiResponse<?> route(@Valid @RequestBody ModelSettingsService.Route r) {
    return ApiResponse.ok(service.route(null, r));
  }

  @PutMapping("/routes/{id}")
  public ApiResponse<?> routeEdit(
      @PathVariable String id, @Valid @RequestBody ModelSettingsService.Route r) {
    return ApiResponse.ok(service.route(id, r));
  }

  @GetMapping("/preview")
  public ApiResponse<?> preview(@RequestParam String routeKey) {
    return ApiResponse.ok(service.preview(routeKey));
  }

  public record Reconcile(boolean release, String reason) {}

  @PostMapping("/ledger/{id}/reconcile")
  public ApiResponse<?> reconcile(@PathVariable String id, @RequestBody Reconcile r) {
    service.reconcile(id, r.release(), r.reason());
    return ApiResponse.ok(null);
  }
}
