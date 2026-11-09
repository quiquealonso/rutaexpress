package com.rutaexpress.mcp;

import com.rutaexpress.shared.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite de peticiones por usuario para el MCP y el asistente (ventana fija de un minuto en Redis).
 * Si Redis no está disponible se usa un contador en memoria: el límite se degrada, no se desactiva.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final int perMinute;
    private final boolean useRedis;
    private final Map<String, AtomicInteger> local = new ConcurrentHashMap<>();
    private volatile long localWindow;

    RateLimitFilter(StringRedisTemplate redis,
                    @Value("${rutaexpress.rate-limit.ai-per-minute:30}") int perMinute,
                    @Value("${spring.cache.type:simple}") String cacheType) {
        this.redis = redis;
        this.perMinute = perMinute;
        this.useRedis = "redis".equalsIgnoreCase(cacheType);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // El canal SSE del MCP es una conexión larga: solo se limitan los mensajes
        return !(path.startsWith("/mcp/message") || path.startsWith("/api/assistant"))
                || path.equals("/api/assistant/status");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var user = CurrentUser.find();
        if (user.isPresent()) {
            long window = Instant.now().getEpochSecond() / 60;
            String key = "ratelimit:ai:" + user.get().userId() + ":" + window;
            if (count(key, window) > perMinute) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                        {"type":"https://rutaexpress.app/problems/rate-limited","title":"Too Many Requests","status":429,\
                        "detail":"Demasiadas peticiones al asistente. Espera un minuto."}""");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private long count(String key, long window) {
        if (useRedis) {
            try {
                Long value = redis.opsForValue().increment(key);
                if (value != null && value == 1) {
                    redis.expire(key, Duration.ofSeconds(90));
                }
                return value == null ? 0 : value;
            } catch (RuntimeException e) {
                log.debug("Redis no disponible para el límite de peticiones: {}", e.getMessage());
            }
        }
        if (window != localWindow) {
            local.clear();
            localWindow = window;
        }
        return local.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }
}
