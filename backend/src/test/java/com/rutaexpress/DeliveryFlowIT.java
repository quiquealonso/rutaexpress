package com.rutaexpress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo contra PostgreSQL + PostGIS reales (Testcontainers): login → abrir carga → escanear →
 * cerrar → la ruta se optimiza en segundo plano → siguiente parada → entregar. Necesita Docker.
 * OSRM no está levantado: se usa la estimación de respaldo, que es justo lo que se quiere probar aquí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "rutaexpress.demo.enabled=true",
        "rutaexpress.geo.osrm-url=http://localhost:1",
        "rutaexpress.geo.timeout=1s",
        "rutaexpress.routing.algorithm=TWO_OPT",
        "management.health.redis.enabled=false"
})
class DeliveryFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"RutaExpress-demo-2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    @Test
    void scanOptimizeAndDeliver() throws Exception {
        String driver = login("lucia@rutaexpress.local");

        JsonNode load = json.readTree(mvc.perform(post("/api/loads").header("Authorization", driver))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String loadId = load.get("id").asText();
        int expected = load.get("expectedCount").asInt();
        assertThat(expected).as("paquetes de la zona centro").isGreaterThan(10);

        // Escanear todos los paquetes que faltan (con un reenvío para comprobar la idempotencia)
        for (JsonNode missing : load.get("missing")) {
            String scan = "{\"code\":\"" + missing.get("trackingCode").asText() + "\",\"clientScanId\":\""
                    + UUID.randomUUID() + "\"}";
            mvc.perform(post("/api/loads/" + loadId + "/scans").header("Authorization", driver)
                    .contentType(MediaType.APPLICATION_JSON).content(scan)).andExpect(status().isOk());
            mvc.perform(post("/api/loads/" + loadId + "/scans").header("Authorization", driver)
                    .contentType(MediaType.APPLICATION_JSON).content(scan)).andExpect(status().isOk());
        }
        JsonNode summary = json.readTree(mvc.perform(get("/api/loads/" + loadId).header("Authorization", driver))
                .andReturn().getResponse().getContentAsString());
        assertThat(summary.get("scannedCount").asInt()).isEqualTo(expected);
        assertThat(summary.get("duplicateCount").asInt()).isZero();

        mvc.perform(post("/api/loads/" + loadId + "/close").header("Authorization", driver))
                .andExpect(status().isAccepted());

        // La optimización es asíncrona
        await().atMost(Duration.ofSeconds(30)).until(() ->
                mvc.perform(get("/api/routes/current").header("Authorization", driver))
                        .andReturn().getResponse().getStatus() == 200);
        JsonNode route = json.readTree(mvc.perform(get("/api/routes/current").header("Authorization", driver))
                .andReturn().getResponse().getContentAsString());
        String routeId = route.get("id").asText();
        assertThat(route.get("stops").size()).as("dos paquetes al mismo portal = una parada").isLessThan(expected);

        mvc.perform(post("/api/routes/" + routeId + "/start").header("Authorization", driver)).andExpect(status().isOk());
        JsonNode next = json.readTree(mvc.perform(get("/api/routes/" + routeId + "/next-stop").header("Authorization", driver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mvc.perform(multipart("/api/stops/" + next.get("id").asText() + "/deliver")
                        .param("proofType", "CODE").param("receiverName", "Portero")
                        .header("Authorization", driver))
                .andExpect(status().isOk());

        JsonNode after = json.readTree(mvc.perform(get("/api/routes/" + routeId + "/summary").header("Authorization", driver))
                .andReturn().getResponse().getContentAsString());
        assertThat(after.get("delivered").asInt()).isEqualTo(1);

        // Otro repartidor no puede ver esta ruta
        String other = login("javier@rutaexpress.local");
        mvc.perform(get("/api/routes/" + routeId).header("Authorization", other)).andExpect(status().isForbidden());
    }
}
