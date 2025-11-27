package com.example.proyecto_distribuidos2530.basedatos;

import com.example.proyecto_distribuidos2530.modelo.Libro;

import java.util.*;
import java.io.*;

// Clase que simula una base de datos de libros para una sede específica
public class Database {
    private Map<String, Libro> libros;
    private String sede;
    private String filePath;

    public Database(String sede, String filePath) {
        this.sede = sede;
        this.filePath = filePath;
        this.libros = new HashMap<>();
        cargarDatosIniciales();
    }

    private void cargarDatosIniciales() {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                cargarDesdeArchivo();
            } else {
                inicializarDatosEjemplo();
                guardarEnArchivo();
            }
        } catch (Exception e) {
            System.err.println("Error cargando BD: " + e.getMessage());
            inicializarDatosEjemplo();
        }
    }

    private void inicializarDatosEjemplo() {
        // 1000 libros, 200 prestados inicialmente
        for (int i = 1; i <= 1000; i++) {
            String codigo = "LIB" + i;
            int disponibles = (i <= 200) ? 0 : (i <= 500) ? 1 : 2;
            libros.put(codigo, new Libro(codigo, "Libro " + i, disponibles));
        }
        System.out.println("BD inicializada con " + libros.size() + " libros");
    }

    public synchronized boolean prestarLibro(String codigo) {
        Libro libro = libros.get(codigo);
        if (libro != null && libro.getCopiasDisponibles() > 0) {
            libro.setCopiasDisponibles(libro.getCopiasDisponibles() - 1);
            guardarEnArchivo();
            return true;
        }
        return false;
    }

    public synchronized void devolverLibro(String codigo) {
        Libro libro = libros.get(codigo);
        if (libro != null) {
            libro.setCopiasDisponibles(libro.getCopiasDisponibles() + 1);  // CORREGIDO
            guardarEnArchivo();
        }
    }
    public synchronized void renovarLibro(String codigo) {
        // Para renovación, básicamente es mantener el estado actual
        System.out.println("Libro " + codigo + " renovado en sede " + sede);
        guardarEnArchivo();
    }

    public synchronized int getCopiasDisponibles(String codigo) {
        Libro libro = libros.get(codigo);
        return (libro != null) ? libro.getCopiasDisponibles() : -1;  // CORREGIDO
    }

    private void cargarDesdeArchivo() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            libros = (Map<String, Libro>) ois.readObject();
            System.out.println("BD cargada desde archivo: " + filePath);
        } catch (Exception e) {
            System.err.println("Error cargando BD desde archivo: " + e.getMessage());
        }
    }

    private void guardarEnArchivo() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(libros);
        } catch (Exception e) {
            System.err.println("Error guardando BD: " + e.getMessage());
        }
    }
}