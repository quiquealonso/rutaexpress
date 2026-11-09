package com.rutaexpress.routing;

import com.rutaexpress.routing.RoutingDtos.ComparisonResponse;
import com.rutaexpress.routing.RoutingDtos.RouteDetail;
import com.rutaexpress.routing.RoutingDtos.RouteListItem;
import com.rutaexpress.routing.RoutingDtos.RouteSummary;
import com.rutaexpress.routing.RoutingDtos.RouteVersionResponse;
import com.rutaexpress.routing.RoutingDtos.StopDetail;
import com.rutaexpress.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Consultas de rutas. Las acciones de ejecución (iniciar, recalcular, entregar...) están en delivery. */
@Tag(name = "Rutas")
@RestController
@RequestMapping("/api/routes")
class RouteController {

    private final RouteService routes;

    RouteController(RouteService routes) {
        this.routes = routes;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    List<RouteListItem> today() {
        return routes.listToday();
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('DRIVER')")
    ResponseEntity<RouteDetail> current() {
        return ResponseEntity.of(routes.currentForDriver(CurrentUser.driverId()));
    }

    /** Ruta con paradas ordenadas, geometría y ETA. */
    @GetMapping("/{id}")
    RouteDetail get(@PathVariable UUID id) {
        return routes.get(id);
    }

    @GetMapping("/{id}/next-stop")
    ResponseEntity<StopDetail> nextStop(@PathVariable UUID id) {
        return ResponseEntity.of(routes.nextStop(id));
    }

    @GetMapping("/{id}/summary")
    RouteSummary summary(@PathVariable UUID id) {
        return routes.summary(id);
    }

    /** Versiones de la ruta: planificado frente a real, recálculo a recálculo. */
    @GetMapping("/{id}/versions")
    List<RouteVersionResponse> versions(@PathVariable UUID id) {
        return routes.versions(id);
    }

    /** Ejecuta todos los algoritmos sobre las paradas abiertas y compara con el orden de escaneo. */
    @GetMapping("/{id}/compare")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    ComparisonResponse compare(@PathVariable UUID id) {
        return routes.compare(id);
    }
}
