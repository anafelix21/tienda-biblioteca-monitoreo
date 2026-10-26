package com.biblioteca.prestamos.controller;

import com.biblioteca.prestamos.model.Libro;
import com.biblioteca.prestamos.model.Prestamo;
import com.biblioteca.prestamos.model.PrestamoRequest;
import com.biblioteca.prestamos.service.PrestamosService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PrestamosController {

    private final PrestamosService service;

    public PrestamosController(PrestamosService service) {
        this.service = service;
    }

    // 1. Registrar un prestamo (valida y descuenta copia en libros-service)
    @PostMapping("/prestamos")
    public ResponseEntity<?> crear(@RequestBody PrestamoRequest req) {
        PrestamosService.Resultado r = service.crearPrestamo(req);
        return switch (r.estado()) {
            case CONFIRMADO -> ResponseEntity.status(HttpStatus.CREATED).body(r.prestamo());
            case INVALIDO -> ResponseEntity.badRequest()
                    .body(Map.of("error", "Solicitud invalida: socio y libroId son obligatorios"));
            case LIBRO_NO_EXISTE -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "El libro no existe en el catalogo"));
            case SIN_COPIAS -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No hay copias disponibles para prestamo"));
            case LIBROS_NO_DISPONIBLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El servicio de libros no responde. Intenta nuevamente en unos instantes"));
        };
    }

    // 2. Listar todos los prestamos
    @GetMapping("/prestamos")
    public List<Prestamo> listar() {
        return service.listar();
    }

    // 3. Consultar prestamo por ID
    @GetMapping("/prestamos/{id}")
    public ResponseEntity<?> obtener(@PathVariable String id) {
        Prestamo p = service.obtener(id);
        return p == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Prestamo no encontrado: " + id))
                : ResponseEntity.ok(p);
    }

    // 4. Resumen de prestamos
    @GetMapping("/prestamos/resumen")
    public Map<String, Object> resumen() {
        return service.resumen();
    }

    // 5. Devolver prestamo
    @PostMapping("/prestamos/{id}/devolver")
    public ResponseEntity<?> devolver(@PathVariable String id) {
        Prestamo p = service.devolver(id);
        return p == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Prestamo no encontrado: " + id))
                : ResponseEntity.ok(p);
    }

    // 6. Consultar catalogo de libros a traves de prestamos
    @GetMapping("/biblioteca/libros")
    public ResponseEntity<?> catalogo() {
        List<Libro> libros = service.catalogoDisponible();
        return libros == null
                ? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "El servicio de libros no responde"))
                : ResponseEntity.ok(libros);
    }
}
