package com.rutaexpress.demo;

import com.rutaexpress.auth.AuthDtos.CreateUserRequest;
import com.rutaexpress.auth.Role;
import com.rutaexpress.auth.UserService;
import com.rutaexpress.catalog.CatalogDtos.CreateDriverRequest;
import com.rutaexpress.catalog.CatalogDtos.DepotRequest;
import com.rutaexpress.catalog.CatalogDtos.DepotResponse;
import com.rutaexpress.catalog.CatalogDtos.VehicleRequest;
import com.rutaexpress.catalog.CatalogDtos.VehicleResponse;
import com.rutaexpress.catalog.CatalogDtos.ZoneRequest;
import com.rutaexpress.catalog.CatalogDtos.ZoneResponse;
import com.rutaexpress.catalog.CatalogService;
import com.rutaexpress.parcel.ParcelDtos.AddressDto;
import com.rutaexpress.parcel.ParcelDtos.CreateParcelRequest;
import com.rutaexpress.parcel.ParcelDtos.RecipientDto;
import com.rutaexpress.parcel.ParcelService;
import com.rutaexpress.shared.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Datos de demostración: un día de reparto en Valencia (almacén, 2 zonas, 2 furgonetas, 2 repartidores y
 * ~30 paquetes con coordenadas reales aproximadas). Solo se cargan si la base de datos está vacía.
 * Las credenciales son de prueba y solo para entornos locales (ver README).
 */
@Component
@ConditionalOnProperty(name = "rutaexpress.demo.enabled", havingValue = "true")
class DemoDataLoader {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final UserService users;
    private final CatalogService catalog;
    private final ParcelService parcels;
    private final String password;

    DemoDataLoader(UserService users, CatalogService catalog, ParcelService parcels,
                   @Value("${rutaexpress.demo.password:RutaExpress-demo-2026}") String password) {
        this.users = users;
        this.catalog = catalog;
        this.parcels = parcels;
        this.password = password;
    }

    private record DemoParcel(String name, String phone, String street, String number, String cp, double lat,
                              double lon, String notes, LocalTime from, LocalTime to, double kg) {
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void load() {
        if (users.exists("admin@rutaexpress.local")) {
            return;
        }
        log.info("Cargando datos de demostración (Valencia)");
        users.create(new CreateUserRequest("admin@rutaexpress.local", password, Role.ADMIN));
        users.create(new CreateUserRequest("trafico@rutaexpress.local", password, Role.DISPATCHER));

        DepotResponse depot = catalog.createDepot(new DepotRequest("Almacén Valencia Norte",
                "Avenida de Burjassot 220, 46025 Valencia", new GeoPoint(39.4930, -0.3950)));

        ZoneResponse centro = catalog.createZone(new ZoneRequest("Valencia Centro", depot.id(), List.of(
                new GeoPoint(39.4820, -0.3920), new GeoPoint(39.4820, -0.3610),
                new GeoPoint(39.4550, -0.3610), new GeoPoint(39.4550, -0.3920)), "#1976d2"));
        ZoneResponse este = catalog.createZone(new ZoneRequest("Valencia Este", depot.id(), List.of(
                new GeoPoint(39.4860, -0.3610), new GeoPoint(39.4860, -0.3220),
                new GeoPoint(39.4520, -0.3220), new GeoPoint(39.4520, -0.3610)), "#e65100"));

        VehicleResponse van1 = catalog.createVehicle(new VehicleRequest("1234LMN", new BigDecimal("1200"),
                new BigDecimal("10.5"), 150, depot.id(), true));
        VehicleResponse van2 = catalog.createVehicle(new VehicleRequest("5678PQR", new BigDecimal("900"),
                new BigDecimal("8.0"), 120, depot.id(), true));

        catalog.createDriver(new CreateDriverRequest("Lucía Martín", "+34 600 111 222", "B-12345678", depot.id(),
                centro.id(), van1.id(), "lucia@rutaexpress.local", password));
        catalog.createDriver(new CreateDriverRequest("Javier Ruiz", "+34 600 333 444", "B-87654321", depot.id(),
                este.id(), van2.id(), "javier@rutaexpress.local", password));

        List<DemoParcel> demo = List.of(
                // Zona centro
                new DemoParcel("María García", "+34 611 000 001", "Plaza del Ayuntamiento", "1", "46002", 39.4699, -0.3763, "Dejar en conserjería", null, null, 1.2),
                new DemoParcel("José López", "+34 611 000 002", "Calle de Colón", "20", "46004", 39.4706, -0.3720, null, LocalTime.of(10, 0), LocalTime.of(12, 0), 3.5),
                new DemoParcel("Ana Martínez", "+34 611 000 003", "Calle de la Paz", "15", "46003", 39.4740, -0.3722, "2º piso, sin ascensor", null, null, 0.8),
                new DemoParcel("Pedro Sánchez", "+34 611 000 004", "Plaza del Mercado", "6", "46001", 39.4736, -0.3790, null, null, null, 5.0),
                new DemoParcel("Lucía Fernández", "+34 611 000 005", "Plaza de la Virgen", "3", "46001", 39.4761, -0.3754, null, null, null, 2.1),
                new DemoParcel("Carlos Gómez", "+34 611 000 006", "Calle de Caballeros", "26", "46001", 39.4766, -0.3790, "Llamar antes", null, null, 1.0),
                new DemoParcel("Elena Díaz", "+34 611 000 007", "Gran Vía del Marqués del Turia", "49", "46005", 39.4672, -0.3680, null, LocalTime.of(9, 0), LocalTime.of(11, 0), 4.2),
                new DemoParcel("Miguel Ruiz", "+34 611 000 008", "Calle de Ruzafa", "30", "46004", 39.4665, -0.3735, null, null, null, 0.5),
                new DemoParcel("Laura Torres", "+34 611 000 009", "Calle de Sueca", "45", "46006", 39.4610, -0.3740, null, null, null, 2.7),
                new DemoParcel("Javier Navarro", "+34 611 000 010", "Calle de Cuba", "12", "46006", 39.4605, -0.3720, null, null, null, 1.9),
                new DemoParcel("Sara Romero", "+34 611 000 011", "Calle de Císcar", "8", "46005", 39.4655, -0.3660, null, null, null, 0.7),
                new DemoParcel("David Alonso", "+34 611 000 012", "Plaza de Cánovas del Castillo", "4", "46005", 39.4664, -0.3640, "Tienda: entregar en mostrador", LocalTime.of(10, 0), LocalTime.of(14, 0), 8.0),
                new DemoParcel("Marta Gil", "+34 611 000 013", "Calle de Jorge Juan", "11", "46004", 39.4693, -0.3698, null, null, null, 1.1),
                new DemoParcel("Pablo Serrano", "+34 611 000 014", "Calle de Quart", "60", "46001", 39.4760, -0.3850, null, null, null, 2.4),
                new DemoParcel("Nuria Molina", "+34 611 000 015", "Calle de Guillem de Castro", "100", "46003", 39.4720, -0.3840, null, null, null, 3.0),
                new DemoParcel("Raúl Ortega", "+34 611 000 016", "Calle de Xàtiva", "14", "46002", 39.4670, -0.3770, null, null, null, 0.9),
                // Dos paquetes al mismo portal: una sola parada
                new DemoParcel("María García", "+34 611 000 001", "Plaza del Ayuntamiento", "1", "46002", 39.4699, -0.3763, "Segundo bulto", null, null, 2.0),
                // Zona este
                new DemoParcel("Cristina Vidal", "+34 622 000 001", "Avenida de Blasco Ibáñez", "50", "46010", 39.4760, -0.3480, null, null, null, 1.5),
                new DemoParcel("Alberto Castro", "+34 622 000 002", "Calle del Doctor Manuel Candela", "20", "46021", 39.4720, -0.3500, null, null, null, 2.2),
                new DemoParcel("Patricia Rubio", "+34 622 000 003", "Avenida del Puerto", "180", "46023", 39.4630, -0.3450, null, LocalTime.of(12, 0), LocalTime.of(14, 0), 6.5),
                new DemoParcel("Fernando Marín", "+34 622 000 004", "Calle de Eugenia Viñes", "150", "46011", 39.4700, -0.3270, "Casa con jardín, timbre a la izquierda", null, null, 1.3),
                new DemoParcel("Isabel Herrero", "+34 622 000 005", "Calle de la Reina", "70", "46011", 39.4680, -0.3300, null, null, null, 0.6),
                new DemoParcel("Sergio Pascual", "+34 622 000 006", "Avenida de Aragón", "30", "46021", 39.4720, -0.3600, null, null, null, 3.3),
                new DemoParcel("Rosa Lorenzo", "+34 622 000 007", "Calle de la Serrería", "40", "46022", 39.4660, -0.3400, null, null, null, 2.8),
                new DemoParcel("Andrés Iglesias", "+34 622 000 008", "Calle de Ibiza", "10", "46022", 39.4625, -0.3550, null, null, null, 1.7),
                new DemoParcel("Teresa Medina", "+34 622 000 009", "Avenida de Baleares", "25", "46023", 39.4580, -0.3500, null, null, null, 4.4),
                new DemoParcel("Jorge Cano", "+34 622 000 010", "Calle del Pintor Maella", "15", "46023", 39.4580, -0.3530, null, null, null, 0.4),
                new DemoParcel("Silvia Prieto", "+34 622 000 011", "Avenida del Primado Reig", "100", "46020", 39.4800, -0.3570, null, null, null, 2.6),
                new DemoParcel("Óscar Méndez", "+34 622 000 012", "Calle del Músico Ginés", "5", "46022", 39.4700, -0.3440, null, LocalTime.of(16, 0), LocalTime.of(19, 0), 1.8));

        for (DemoParcel p : demo) {
            parcels.create(new CreateParcelRequest(null,
                    new RecipientDto(p.name(), p.phone(), null),
                    new AddressDto(p.street(), p.number(), null, p.cp(), "Valencia", "España",
                            new GeoPoint(p.lat(), p.lon())),
                    BigDecimal.valueOf(p.kg()), 30, 20, 15, p.from(), p.to(), p.notes(), depot.id(), null));
        }
        log.info("Datos de demostración cargados: {} paquetes. Usuarios: admin@, trafico@, lucia@, javier@rutaexpress.local",
                demo.size());
    }
}
