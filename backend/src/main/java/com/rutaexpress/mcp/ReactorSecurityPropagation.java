package com.rutaexpress.mcp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * El SDK MCP ejecuta las herramientas síncronas en Schedulers.boundedElastic(), así que el SecurityContext
 * (ThreadLocal) de la petición HTTP se perdería. Este hook envuelve cada tarea programada para que el hilo
 * de Reactor vea el mismo contexto que el hilo que la programó (el de la petición POST /mcp/message).
 */
@Component
class ReactorSecurityPropagation {

    private static final String HOOK = "rutaexpress-security-context";

    @PostConstruct
    void install() {
        Schedulers.onScheduleHook(HOOK, DelegatingSecurityContextRunnable::new);
    }

    @PreDestroy
    void uninstall() {
        Schedulers.resetOnScheduleHook(HOOK);
    }
}
