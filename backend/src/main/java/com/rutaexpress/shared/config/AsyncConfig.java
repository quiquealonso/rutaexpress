package com.rutaexpress.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

import java.util.concurrent.Executor;

/**
 * Pool propio para tareas pesadas (optimizar, geocodificar en lote), separado del de peticiones HTTP.
 * El decorador propaga el contexto de seguridad para que la auditoría sepa quién lanzó la tarea.
 */
@Configuration
public class AsyncConfig {

    public static final String OPTIMIZATION_EXECUTOR = "optimizationExecutor";

    @Bean(name = {OPTIMIZATION_EXECUTOR, "applicationTaskExecutor", "taskExecutor"})
    public Executor optimizationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("optim-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setTaskDecorator(securityContextDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    private static TaskDecorator securityContextDecorator() {
        return DelegatingSecurityContextRunnable::new;
    }
}
