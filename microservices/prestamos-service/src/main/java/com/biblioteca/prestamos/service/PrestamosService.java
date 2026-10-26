package com.biblioteca.prestamos.service;

import com.biblioteca.prestamos.model.Libro;
import com.biblioteca.prestamos.model.Prestamo;
import com.biblioteca.prestamos.model.PrestamoRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PrestamosService {

    private static final Logger log = LoggerFactory.getLogger(PrestamosService.class);

    public enum EstadoPrestamo { CONFIRMADO, SIN_COPIAS, LIBRO_NO_EXISTE, LIBROS_NO_DISPONIBLE, INVALIDO }
    public record Resultado(EstadoPrestamo estado, Prestamo prestamo) {}
    private record RespuestaLibros(int status, Libro libro) {}

    private final WebClient libros;
    private final long timeoutMs;
    private final MeterRegistry registry;
    private final List<Prestamo> prestamos = new CopyOnWriteArrayList<>();

    // Metricas Micrometer
    private final Counter prestamosConfirmados;
    private final Counter librosErrores;
    private final Timer llamadaLibros;

    public PrestamosService(WebClient librosWebClient,
                            @Value("${libros.timeout-ms}") long timeoutMs,
                            MeterRegistry registry) {
        this.libros = librosWebClient;
        this.timeoutMs = timeoutMs;
        this.registry = registry;

        prestamosConfirmados = Counter.builder("prestamos.confirmados")
                .description("Total de prestamos confirmados exitosamente")
                .register(registry);
        librosErrores = Counter.builder("prestamos.libros.errores")
                .description("Total de fallos o timeouts en llamadas a libros-service")
                .register(registry);
        llamadaLibros = Timer.builder("prestamos.libros.llamada.duracion")
                .description("Duracion de las llamadas HTTP hacia libros-service")
                .publishPercentileHistogram()
                .register(registry);

        Gauge.builder("prestamos.activos", prestamos,
                        l -> l.stream().filter(p -> "ACTIVO".equalsIgnoreCase(p.estado())).count())
                .description("Cantidad de prestamos actualmente activos")
                .register(registry);
        Gauge.builder("prestamos.libros.timeout.ms", this, s -> s.timeoutMs)
                .description("Timeout configurado hacia libros-service (ms)")
                .register(registry);

        log.info("prestamos-service iniciado. Timeout hacia libros = {} ms", timeoutMs);
    }

    public Resultado crearPrestamo(PrestamoRequest req) {
        if (req == null || req.socio() == null || req.socio().isBlank()
                || req.libroId() == null || req.libroId().isBlank()) {
            return rechazo("invalido", EstadoPrestamo.INVALIDO);
        }

        RespuestaLibros r = llamarPrestarLibro(req.libroId());

        if (r == null) {
            return rechazo("libros_no_disponible", EstadoPrestamo.LIBROS_NO_DISPONIBLE);
        }
        if (r.status() == 404) {
            return rechazo("libro_no_existe", EstadoPrestamo.LIBRO_NO_EXISTE);
        }
        if (r.status() == 409) {
            return rechazo("sin_copias", EstadoPrestamo.SIN_COPIAS);
        }
        if (r.libro() == null) {
            return rechazo("libros_no_disponible", EstadoPrestamo.LIBROS_NO_DISPONIBLE);
        }

        Libro libro = r.libro();
        LocalDate hoy = LocalDate.now();
        LocalDate vencimiento = hoy.plusDays(14);

        Prestamo p = new Prestamo(
                "PREST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                req.socio(),
                libro.id(),
                libro.titulo(),
                hoy.toString(),
                vencimiento.toString(),
                "ACTIVO"
        );

        prestamos.add(p);
        prestamosConfirmados.increment();
        return new Resultado(EstadoPrestamo.CONFIRMADO, p);
    }

    private RespuestaLibros llamarPrestarLibro(String libroId) {
        return llamadaLibros.record(() -> {
            try {
                return libros.post()
                        .uri("/libros/{id}/prestar", libroId)
                        .exchangeToMono(resp -> {
                            if (resp.statusCode().is2xxSuccessful()) {
                                return resp.bodyToMono(Libro.class)
                                        .map(l -> new RespuestaLibros(200, l));
                            }
                            return resp.releaseBody()
                                    .thenReturn(new RespuestaLibros(resp.statusCode().value(), null));
                        })
                        .timeout(Duration.ofMillis(timeoutMs))
                        .block();
            } catch (Exception e) {
                librosErrores.increment();
                log.error("Fallo al consultar libros-service (timeout={} ms, libroId={}): {}",
                        timeoutMs, libroId, e.toString());
                return null;
            }
        });
    }

    private Resultado rechazo(String motivo, EstadoPrestamo estado) {
        registry.counter("prestamos.rechazados", "motivo", motivo).increment();
        return new Resultado(estado, null);
    }

    public List<Prestamo> listar() {
        return List.copyOf(prestamos);
    }

    public Prestamo obtener(String id) {
        return prestamos.stream()
                .filter(p -> p.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public Map<String, Object> resumen() {
        long activos = prestamos.stream().filter(p -> "ACTIVO".equalsIgnoreCase(p.estado())).count();
        long devueltos = prestamos.stream().filter(p -> "DEVUELTO".equalsIgnoreCase(p.estado())).count();
        return Map.of(
                "totalPrestamos", prestamos.size(),
                "prestamosActivos", activos,
                "prestamosDevueltos", devueltos
        );
    }

    public synchronized Prestamo devolver(String id) {
        Prestamo p = obtener(id);
        if (p == null) {
            return null;
        }
        if ("DEVUELTO".equalsIgnoreCase(p.estado())) {
            return p;
        }

        // Llamar a libros-service para sumar la copia
        try {
            libros.post()
                    .uri("/libros/{id}/devolver", p.libroId())
                    .retrieve()
                    .bodyToMono(Libro.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
        } catch (Exception e) {
            librosErrores.increment();
            log.error("Error al devolver libro en libros-service para prestamo {}: {}", id, e.toString());
        }

        Prestamo actualizado = p.conEstado("DEVUELTO");
        int index = prestamos.indexOf(p);
        if (index >= 0) {
            prestamos.set(index, actualizado);
        }
        return actualizado;
    }

    public List<Libro> catalogoDisponible() {
        try {
            return libros.get()
                    .uri("/libros")
                    .retrieve()
                    .bodyToFlux(Libro.class)
                    .collectList()
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
        } catch (Exception e) {
            librosErrores.increment();
            log.error("Fallo al listar catalogo desde prestamos: {}", e.toString());
            return null;
        }
    }
}
