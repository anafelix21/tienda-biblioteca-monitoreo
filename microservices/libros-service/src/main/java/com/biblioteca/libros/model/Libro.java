package com.biblioteca.libros.model;

public record Libro(String id, String titulo, String autor,
                    String categoria, int anio, int copiasDisponibles) {

    public Libro conCopiasDisponibles(int nuevasCopias) {
        return new Libro(id, titulo, autor, categoria, anio, nuevasCopias);
    }
}
