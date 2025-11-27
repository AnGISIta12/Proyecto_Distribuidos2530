package com.example.proyecto_distribuidos2530.almacenamiento;

import com.example.proyecto_distribuidos2530.basedatos.Database;
import org.zeromq.ZMQ;

public class GestorAlmcto {
    private Database bdPrimaria;
    private Database bdSecundaria;
    private String sede;
    private boolean esPrimario;

    public GestorAlmcto(String sede, boolean esPrimario) {
        this.sede = sede;
        this.esPrimario = esPrimario;
        String pathPrimaria = "bd_primaria_" + sede + ".dat";
        String pathSecundaria = "bd_secundaria_" + sede + ".dat";

        this.bdPrimaria = new Database(sede, pathPrimaria);
        this.bdSecundaria = new Database(sede, pathSecundaria);
    }

    public synchronized boolean prestarLibro(String codigo) {
        boolean exito = bdPrimaria.prestarLibro(codigo);
        if (exito) {
            // Actualizar réplica secundaria de forma asíncrona
            new Thread(() -> bdSecundaria.prestarLibro(codigo)).start();
        }
        return exito;
    }

    public synchronized void devolverLibro(String codigo) {
        bdPrimaria.devolverLibro(codigo);
        new Thread(() -> bdSecundaria.devolverLibro(codigo)).start();
    }

    public synchronized void renovarLibro(String codigo) {
        bdPrimaria.renovarLibro(codigo);
        new Thread(() -> bdSecundaria.renovarLibro(codigo)).start();
    }

    public int getCopiasDisponibles(String codigo) {
        return bdPrimaria.getCopiasDisponibles(codigo);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java GestorAlmcto <sede> <es_primario>");
            return;
        }

        String sede = args[0];
        boolean esPrimario = Boolean.parseBoolean(args[1]);
        GestorAlmcto gestor = new GestorAlmcto(sede, esPrimario);

        // Servicio ZeroMQ para recibir operaciones de los Actores
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket receiver = context.socket(ZMQ.REP);
        receiver.bind("tcp://*:5558"); // Puerto para operaciones BD

        System.out.println("GestorAlmcto " + sede + " listo (" +
                (esPrimario ? "PRIMARIO" : "SECUNDARIO") + ")");

        while (!Thread.currentThread().isInterrupted()) {
            String request = receiver.recvStr();
            String[] partes = request.split("\\|");
            String operacion = partes[0];
            String respuesta = "";

            switch (operacion.toUpperCase()) {
                case "PRESTAMO":
                    boolean exito = gestor.prestarLibro(partes[1]);
                    respuesta = exito ? "PRESTAMO_OK" : "PRESTAMO_NOK";
                    break;

                case "DEVOLUCION":
                    gestor.devolverLibro(partes[1]);
                    respuesta = "DEVOLUCION_OK";
                    break;

                case "RENOVACION":
                    gestor.renovarLibro(partes[1]);
                    respuesta = "RENOVACION_OK";
                    break;

                case "CONSULTA":
                    int disponibles = gestor.getCopiasDisponibles(partes[1]);
                    respuesta = "DISPONIBLES_" + disponibles;
                    break;
            }

            receiver.send(respuesta);
        }

        receiver.close();
        context.close();
    }
}