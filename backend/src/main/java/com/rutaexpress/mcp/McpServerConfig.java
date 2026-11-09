package com.rutaexpress.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

/**
 * Servidor MCP dentro del propio backend (starter de Spring AI, transporte HTTP/SSE en /mcp).
 * Expone las herramientas del conductor, recursos de solo lectura y el prompt "modo conductor".
 */
@Configuration
class McpServerConfig {

    static final String DRIVER_MODE_PROMPT = """
            Eres el asistente de reparto de RutaExpress y hablas con un repartidor que está conduciendo o a pie
            de calle. Reglas:
            - Responde en castellano, en una o dos frases cortas, aptas para leer en voz alta.
            - Nada de listas largas, tablas ni markdown. Números redondeados ("unos 300 metros", "las 11:40").
            - Usa siempre las herramientas para consultar o cambiar el reparto; nunca inventes paradas, direcciones
              ni horas.
            - Antes de marcar una entrega como hecha o fallida, asegúrate de qué parada es; si hay duda, pregunta.
            - Si el repartidor menciona un accidente o una avería, registra la incidencia y recuérdale que pare el
              vehículo en un lugar seguro.
            - Por seguridad vial, no propongas leer pantallas mientras conduce.""";

    @Bean
    ToolCallbackProvider driverToolCallbacks(DriverTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    List<SyncResourceSpecification> driverResources(DriverTools tools, ObjectMapper mapper) {
        var current = new McpSchema.Resource("route://current", "Ruta actual",
                "Resumen de la ruta en curso del repartidor autenticado", "application/json", null);
        var stopTemplate = new McpSchema.Resource("stop://next", "Siguiente parada",
                "Detalle de la siguiente parada pendiente (para otra, usa stop://{id})", "application/json", null);
        return List.of(
                new SyncResourceSpecification(current, (exchange, request) -> ToolIdentity.run(null, () ->
                        new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(request.uri(),
                                "application/json", tools.currentRouteJsonSummary()))))),
                new SyncResourceSpecification(stopTemplate, (exchange, request) -> ToolIdentity.run(null, () -> {
                    String uri = request.uri();
                    Object view = uri.equals("stop://next") ? tools.getNextStop(null)
                            : tools.stopView(UUID.fromString(uri.substring("stop://".length())));
                    return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(uri,
                            "application/json", toJson(mapper, view))));
                })));
    }

    @Bean
    List<SyncPromptSpecification> driverPrompts() {
        var prompt = new McpSchema.Prompt("modo_conductor",
                "Instrucciones para responder al repartidor con frases cortas aptas para voz", List.of());
        return List.of(new SyncPromptSpecification(prompt, (exchange, request) ->
                new McpSchema.GetPromptResult("Modo conductor", List.of(
                        new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(DRIVER_MODE_PROMPT))))));
    }

    private static String toJson(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
