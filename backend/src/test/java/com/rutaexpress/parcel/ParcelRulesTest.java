package com.rutaexpress.parcel;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelRulesTest {

    @Test
    void statusTransitions() {
        assertThat(ParcelStatus.REGISTERED.canTransitionTo(ParcelStatus.LOADED)).isTrue();
        assertThat(ParcelStatus.LOADED.canTransitionTo(ParcelStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(ParcelStatus.OUT_FOR_DELIVERY.canTransitionTo(ParcelStatus.DELIVERED)).isTrue();
        assertThat(ParcelStatus.FAILED_ATTEMPT.canTransitionTo(ParcelStatus.REGISTERED)).isTrue();
        assertThat(ParcelStatus.DELIVERED.canTransitionTo(ParcelStatus.FAILED_ATTEMPT)).isFalse();
        assertThat(ParcelStatus.REGISTERED.canTransitionTo(ParcelStatus.DELIVERED)).isFalse();
        assertThat(ParcelStatus.DELIVERED.isFinal()).isTrue();
    }

    @Test
    void csvWithSemicolonsAndQuotes() throws Exception {
        String csv = """
                nombre;telefono;calle;numero;cp;ciudad;peso;notas;franja_inicio;franja_fin
                María García;600111222;Calle de Colón;20;46004;Valencia;1,5;"Portal 2; timbre \"\"B\"\"";9:00;11:00
                ;600;Calle X;1;46001;Valencia;1;;;
                """;
        var rows = CsvParcelParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), UUID.randomUUID());
        assertThat(rows).hasSize(2);
        var first = rows.getFirst().request();
        assertThat(first.recipient().name()).isEqualTo("María García");
        assertThat(first.weightKg()).isEqualByComparingTo("1.5");
        assertThat(first.notes()).isEqualTo("Portal 2; timbre \"B\"");
        assertThat(first.windowStart()).hasToString("09:00");
        assertThat(rows.get(1).error()).contains("nombre");
    }

    @Test
    void csvSplitHandlesCommas() {
        assertThat(CsvParcelParser.split("a,\"b,c\",d", ',')).containsExactly("a", "b,c", "d");
    }
}
