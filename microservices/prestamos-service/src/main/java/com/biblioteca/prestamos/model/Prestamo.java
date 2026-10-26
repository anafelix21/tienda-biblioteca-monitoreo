package com.biblioteca.prestamos.model;

public record Prestamo(String id, String socio, String libroId, String titulo,
                       String fechaPrestamo, String fechaVencimiento, String estado) {

    public Prestamo conEstado(String nuevoEstado) {
        return new Prestamo(id, socio, libroId, titulo, fechaPrestamo, fechaVencimiento, nuevoEstado);
    }
}
