package com.accuenergy.octopus.control.interfaces;

import com.accuenergy.octopus.control.application.IoTPluginCatalogService;
import com.accuenergy.octopus.iot.spi.MessageKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only plugin discovery used by onboarding UIs and integration tooling. */
@RestController
@RequestMapping("/api/v1/iot-plugins")
@PreAuthorize("hasAuthority('PERM_iot:plugin:read') or hasAuthority('PERM_device:configure')"
        + " or hasAuthority('PERM_platform:all') or hasAuthority('SCOPE_iot:plugin:read')")
public final class IoTPluginCatalogController {
    private final IoTPluginCatalogService catalog;

    public IoTPluginCatalogController(IoTPluginCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<IoTPluginCatalogService.PluginEntry> list() {
        return catalog.catalog();
    }

    @PostMapping("/preflight")
    public IoTPluginCatalogService.PreflightResult preflight(@Valid @RequestBody PreflightRequest request) {
        return catalog.preflight(request.pluginId(), request.codecId(), request.requiredKinds());
    }

    public record PreflightRequest(@NotBlank String pluginId, @NotBlank String codecId,
            @NotEmpty Set<MessageKind> requiredKinds) { }
}
