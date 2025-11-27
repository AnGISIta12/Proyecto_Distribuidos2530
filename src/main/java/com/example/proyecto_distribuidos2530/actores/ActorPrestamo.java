package com.example.proyecto_distribuidos2530.actores;

import org.zeromq.ZMQ;

public class ActorPrestamo {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);

        // Socket PULL para recibir tareas del GC
        ZMQ.Socket puller = context.socket(ZMQ.PULL);
        puller.connect("tcp://localhost:5557");

        // Socket REQ para consultar BD
        ZMQ.Socket bdRequester = context.socket(ZMQ.REQ);
        bdRequester.connect("tcp://localhost:5558");

        System.out.println("ActorPrestamo escuchando tareas...");

        while (!Thread.currentThread().isInterrupted()) {
            String msg = puller.recvStr();
            System.out.println("ActorPrestamo recibió: " + msg);

            String[] partesMsg = msg.split(" ");
            if (partesMsg.length >= 2) {
                String codigoLibro = partesMsg[1]; // "B0001"

                // Consultar BD
                bdRequester.send("CONSULTA|" + codigoLibro);
                String respuestaBD = bdRequester.recvStr();
                System.out.println("BD respondió: " + respuestaBD);

                if (respuestaBD.startsWith("DISPONIBLES_")) {
                    int disponibles = Integer.parseInt(respuestaBD.split("_")[1]);
                    if (disponibles > 0) {
                        // Realizar préstamo
                        bdRequester.send("PRESTAMO|" + codigoLibro);
                        String resultado = bdRequester.recvStr();
                        System.out.println("Préstamo resultado: " + resultado);
                    } else {
                        System.out.println("Préstamo denegado - No hay copias disponibles de " + codigoLibro);
                    }
                }
            }
        }

        puller.close();
        bdRequester.close();
        context.close();
    }
}