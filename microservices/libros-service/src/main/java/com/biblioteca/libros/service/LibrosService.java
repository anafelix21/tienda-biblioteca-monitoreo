package com.biblioteca.libros.service;

import com.biblioteca.libros.model.Libro;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LibrosService {

    private static final Logger log = LoggerFactory.getLogger(LibrosService.class);

    public enum ResultadoPrestamo { OK, NO_ENCONTRADO, SIN_COPIAS }
    public record OperacionLibro(ResultadoPrestamo resultado, Libro libro) {}

    private final Map<String, Libro> libros = new ConcurrentHashMap<>();
    private final long delayMs;

    // Metricas Micrometer
    private final Counter consultas;
    private final Counter prestamosExitosos;
    private final Counter prestamosRechazados;
    private final Timer duracionConsulta;

    public LibrosService(MeterRegistry registry,
                         @Value("${libros.delay-ms:0}") long delayMs) {
        this.delayMs = delayMs;

        consultas = Counter.builder("libros.consultas")
                .description("Total de consultas al catalogo de libros")
                .register(registry);
        prestamosExitosos = Counter.builder("libros.prestamos.exitosos")
                .description("Total de prestamos concedidos exitosamente")
                .register(registry);
        prestamosRechazados = Counter.builder("libros.prestamos.rechazados")
                .description("Total de prestamos rechazados por falta de copias disponibles")
                .register(registry);
        duracionConsulta = Timer.builder("libros.consulta.duracion")
                .description("Duracion de las consultas y operaciones en libros-service")
                .publishPercentileHistogram()
                .register(registry);

        Gauge.builder("libros.titulos", libros, Map::size)
                .description("Cantidad total de titulos registrados en el catalogo")
                .register(registry);
        Gauge.builder("libros.copias.disponibles", libros,
                        m -> m.values().stream().mapToInt(Libro::copiasDisponibles).sum())
                .description("Total de copias disponibles para prestamo")
                .register(registry);
        Gauge.builder("libros.delay.artificial.ms", this, s -> s.delayMs)
                .description("Delay artificial configurado en libros-service (ms)")
                .register(registry);

        cargarDatosIniciales();
        log.info("libros-service iniciado. Delay artificial = {} ms", delayMs);
    }

    private void cargarDatosIniciales() {
        guardar(new Libro("L001", "Cien anos de soledad", "Gabriel Garcia Marquez", "NOVELA", 1967, 10));
        guardar(new Libro("L002", "Don Quijote de la Mancha", "Miguel de Cervantes", "CLASICO", 1605, 8));
        guardar(new Libro("L003", "Calculo de una variable", "James Stewart", "CIENCIAS", 2015, 3));
        guardar(new Libro("L004", "Clean Code", "Robert C. Martin", "INGENIERIA", 2008, 6));
        guardar(new Libro("L005", "Fisica Universitaria", "Sears y Zemansky", "CIENCIAS", 2018, 5));
        guardar(new Libro("L006", "Breve historia del tiempo", "Stephen Hawking", "DIVULGACION", 1988, 4));
    }

    private void guardar(Libro l) {
        libros.put(l.id(), l);
    }

    private void simularDelay() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public List<Libro> listar(String categoria) {
        return duracionConsulta.record(() -> {
            simularDelay();
            consultas.increment();
            return libros.values().stream()
                    .filter(l -> categoria == null || l.categoria().equalsIgnoreCase(categoria))
                    .toList();
        });
    }

    public Optional<Libro> obtener(String id) {
        return duracionConsulta.record(() -> {
            simularDelay();
            consultas.increment();
            return Optional.ofNullable(libros.get(id));
        });
    }

    public List<Libro> sinCopias() {
        return duracionConsulta.record(() -> {
            simularDelay();
            consultas.increment();
            return libros.values().stream()
                    .filter(l -> l.copiasDisponibles() <= 0)
                    .toList();
        });
    }

    public Libro crear(Libro nuevo) {
        String id = (nuevo.id() == null || nuevo.id().isBlank())
                ? "L" + UUID.randomUUID().toString().substring(0, 5).toUpperCase()
                : nuevo.id();
        Libro l = new Libro(id, nuevo.titulo(), nuevo.autor(), nuevo.categoria(),
                nuevo.anio(), nuevo.copiasDisponibles());
        guardar(l);
        return l;
    }

    public synchronized OperacionLibro prestar(String id) {
        return duracionConsulta.record(() -> {
            simularDelay();
            Libro l = libros.get(id);
            if (l == null) {
                return new OperacionLibro(ResultadoPrestamo.NO_ENCONTRADO, null);
            }
            if (l.copiasDisponibles() <= 0) {
                prestamosRechazados.increment();
                return new OperacionLibro(ResultadoPrestamo.SIN_COPIAS, l);
            }
            Libro actualizado = l.conCopiasDisponibles(l.copiasDisponibles() - 1);
            guardar(actualizado);
            prestamosExitosos.increment();
            return new OperacionLibro(ResultadoPrestamo.OK, actualizado);
        });
    }

    public synchronized OperacionLibro devolver(String id) {
        return duracionConsulta.record(() -> {
            simularDelay();
            Libro l = libros.get(id);
            if (l == null) {
                return new OperacionLibro(ResultadoPrestamo.NO_ENCONTRADO, null);
            }
            Libro actualizado = l.conCopiasDisponibles(l.copiasDisponibles() + 1);
            guardar(actualizado);
            return new OperacionLibro(ResultadoPrestamo.OK, actualizado);
        });
    }
}
