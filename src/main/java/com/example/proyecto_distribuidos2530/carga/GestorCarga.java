package com.example.proyecto_distribuidos2530.carga;

import org.zeromq.ZMQ;

public class GestorCarga {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);

        // Socket para recibir solicitudes de los PS
        ZMQ.Socket receiver = context.socket(ZMQ.REP);
        receiver.bind("tcp://*:5555");

        // Socket para publicar mensajes a los actores
        ZMQ.Socket publisher = context.socket(ZMQ.PUB);
        publisher.bind("tcp://*:5556");

        System.out.println("Gestor de Carga listo...");

        while (!Thread.currentThread().isInterrupted()) {
            // Recibir solicitud desde un Proceso Solicitante
            String request = receiver.recvStr();
            System.out.println("GC recibió: " + request);

            try {
                // Separar la solicitud por el caracter '|'
                String[] partes = request.split("\\|");
                String operacion = partes[0];
                String libro = partes[1];
                String usuario = partes[2];

                switch (operacion.toUpperCase()) {
                    case "DEVOLUCION":
                        publisher.sendMore("devolucion");
                        publisher.send("Libro " + libro + " devuelto por " + usuario);
                        receiver.send("Devolución procesada correctamente para " + libro);
                        break;

                    case "RENOVACION":
                        publisher.sendMore("renovacion");
                        publisher.send("Libro " + libro + " renovado por " + usuario);
                        receiver.send("Renovación procesada correctamente para " + libro);
                        break;

                    case "PRESTAMO":
                        // Por ahora solo se simula la respuesta
                        receiver.send("Préstamo recibido (no implementado aún): " + libro);
                        break;

                    default:
                        receiver.send("Operación no reconocida: " + operacion);
                }

            } catch (Exception e) {
                receiver.send("Error al procesar la solicitud: " + e.getMessage());
                System.err.println("Error al procesar: " + request);
            }
        }

        receiver.close();
        publisher.close();
        context.close();
    }
}
