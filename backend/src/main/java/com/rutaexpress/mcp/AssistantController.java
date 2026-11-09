package com.rutaexpress.mcp;

import com.rutaexpress.shared.events.EventSource;
import com.rutaexpress.shared.security.AuthenticatedUser;
import com.rutaexpress.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Chat del asistente de la PWA. El modelo de lenguaje (Claude, vía Spring AI) tiene disponibles las mismas
 * herramientas que el servidor MCP. La identidad del repartidor viaja en el ToolContext, nunca en el prompt.
 */
@Tag(name = "Asistente")
@RestController
@RequestMapping("/api/assistant")
@PreAuthorize("hasRole('DRIVER')")
class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);
    private static final int MAX_HISTORY = 12;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final DriverTools tools;
    private final boolean enabled;
    private volatile ChatClient client;

    AssistantController(ObjectProvider<ChatClient.Builder> chatClientBuilder, DriverTools tools,
                        @Value("${rutaexpress.assistant.enabled:false}") boolean enabled) {
        this.chatClientBuilder = chatClientBuilder;
        this.tools = tools;
        this.enabled = enabled;
    }

    record ChatTurn(@Pattern(regexp = "user|assistant") String role, @NotBlank @Size(max = 2000) String content) {
    }

    record ChatRequest(@NotBlank @Size(max = 1000) String message, @Size(max = 40) List<@Valid ChatTurn> history) {
    }

    record ChatResponse(String answer) {
    }

    record Status(boolean enabled, boolean stationary) {
    }

    /** La app solo abre el chat a pantalla completa con el vehículo parado (velocidad GPS casi cero). */
    @GetMapping("/status")
    Status status() {
        return new Status(enabled, tools.driverIsStationary());
    }

    @PostMapping
    ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String answer = prompt(request).call().content();
        return new ChatResponse(answer);
    }

    /** Misma conversación pero con la respuesta en streaming (Server-Sent Events). */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request) {
        return prompt(request).stream().content()
                .map(chunk -> ServerSentEvent.builder(chunk).event("token").build())
                .concatWith(Flux.just(ServerSentEvent.builder("").event("done").build()))
                .onErrorResume(e -> {
                    log.warn("Error en el streaming del asistente: {}", e.getMessage());
                    return Flux.just(ServerSentEvent.builder("No he podido responder ahora mismo.").event("error").build());
                });
    }

    private ChatClient.ChatClientRequestSpec prompt(ChatRequest request) {
        if (!enabled) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente no está configurado (falta ANTHROPIC_API_KEY)");
        }
        AuthenticatedUser user = CurrentUser.get();
        Map<String, Object> toolContext = ToolIdentity.context(user, EventSource.ASSISTANT);
        return client().prompt()
                .system(McpServerConfig.DRIVER_MODE_PROMPT)
                .messages(history(request.history()))
                .user(request.message())
                .tools(tools)
                .toolContext(toolContext);
    }

    private ChatClient client() {
        if (client == null) {
            ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
            if (builder == null) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "No hay ningún modelo de lenguaje configurado");
            }
            client = builder.build();
        }
        return client;
    }

    private static List<Message> history(List<ChatTurn> turns) {
        if (turns == null) {
            return List.of();
        }
        return turns.stream().skip(Math.max(0, turns.size() - MAX_HISTORY))
                .map(t -> "assistant".equals(t.role()) ? (Message) new AssistantMessage(t.content())
                        : new UserMessage(t.content()))
                .toList();
    }
}
