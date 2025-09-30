package com.example.proyecto_distribuidos2530.actores;

public class Libro {
    private String code;
    private String title;
    private int copiesAvailable;


    public Libro(String code, String title, int copiesAvailable) {
        this.code = code;
        this.title = title;
        this.copiesAvailable = copiesAvailable;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getCopiesAvailable() { return copiesAvailable; }
    public void setCopiesAvailable(int v) { this.copiesAvailable = v; }
}
