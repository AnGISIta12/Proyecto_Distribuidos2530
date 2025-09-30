package com.example.proyecto_distribuidos2530.actores;

public class Libro {
    private String codigo;
    private String titulo;
    private int copiasDisponibles;


    public Libro(String code, String title, int copiesAvailable) {
        this.codigo = code;
        this.titulo = title;
        this.copiasDisponibles = copiesAvailable;
    }

    public String getCode() { return codigo; }
    public String getTitle() { return titulo; }
    public int getCopiesAvailable() { return copiasDisponibles; }
    public void setCopiesAvailable(int v) { this.copiasDisponibles = v; }
}
