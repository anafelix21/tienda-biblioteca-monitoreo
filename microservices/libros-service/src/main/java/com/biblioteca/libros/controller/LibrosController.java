package com.biblioteca.libros.controller;

import com.biblioteca.libros.model.Libro;
import com.biblioteca.libros.service.LibrosService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/libros")
public class LibrosController {

    private final LibrosService service;

    public LibrosController(LibrosService service) {
        this.service = service;
    }

    // 1. Listar libros (opcionalmente filtrado por categoria)
    @GetMapping
    public List<Libro> listar(@RequestParam(required = false) String categoria) {
        return service.listar(categoria);
    }

    // 2. Obtener un libro por ID
    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable String id) {
        return service.obtener(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Libro no encontrado: " + id)));
    }

    // 3. Libros sin copias disponibles
    @GetMapping("/sin-copias")
    public List<Libro> sinCopias() {
        return service.sinCopias();
    }

    // 4. Crear o registrar un libro
    @PostMapping
    public ResponseEntity<Libro> crear(@RequestBody Libro libro) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(libro));
    }

    // 5. Prestar una copia de un libro (descuenta 1 copia: 200, 404 o 409)
    @PostMapping("/{id}/prestar")
    public ResponseEntity<?> prestar(@PathVariable String id) {
        LibrosService.OperacionLibro op = service.prestar(id);
        return switch (op.resultado()) {
            case OK -> ResponseEntity.ok(op.libro());
            case NO_ENCONTRADO -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Libro no encontrado: " + id));
            case SIN_COPIAS -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Sin copias disponibles", "copiasDisponibles", 0, "libroId", id));
        };
    }

    // 6. Devolver una copia de un libro (suma 1 copia: 200 o 404)
    @PostMapping("/{id}/devolver")
    public ResponseEntity<?> devolver(@PathVariable String id) {
        LibrosService.OperacionLibro op = service.devolver(id);
        return switch (op.resultado()) {
            case OK -> ResponseEntity.ok(op.libro());
            case NO_ENCONTRADO -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Libro no encontrado: " + id));
            case SIN_COPIAS -> ResponseEntity.badRequest().build();
        };
    }
}
