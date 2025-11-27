package com.example.proyecto_distribuidos2530.modelo;

public class Libro {
    private String codigo;
    private String titulo;
    private int copiasDisponibles;

    public Libro(String codigo, String titulo, int copiasDisponibles) {
        this.codigo = codigo;
        this.titulo = titulo;
        this.copiasDisponibles = copiasDisponibles;
    }

    public String getCodigo() { return codigo; }
    public String getTitulo() { return titulo; }
    public int getCopiasDisponibles() { return copiasDisponibles; }
    public void setCopiasDisponibles(int copiasDisponibles) {
        this.copiasDisponibles = copiasDisponibles;
    }
}