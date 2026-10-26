package com.biblioteca.prestamos.model;

public record Libro(String id, String titulo, String autor,
                    String categoria, int anio, int copiasDisponibles) {
}
