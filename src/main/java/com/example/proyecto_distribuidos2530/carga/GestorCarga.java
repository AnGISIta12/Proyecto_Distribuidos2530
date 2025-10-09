package com.example.proyecto_distribuidos2530.carga;

import org.zeromq.ZMQ;

public class GestorCarga {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket receiver = context.socket(ZMQ.REP);
        receiver.bind("tcp://*:5555");

        ZMQ.Socket publisher = context.socket(ZMQ.PUB);
        publisher.bind("tcp://*:5556");

        System.out.println("Gestor de Carga listo...");

        while (!Thread.currentThread().isInterrupted()) {
            String request = receiver.recvStr();
            if (request == null) continue;

            System.out.println("GC recibió: " + request);

            try {
                String[] partes = request.split("\\|");
                if (partes.length < 3) {
                    receiver.send("Error: formato inválido en la solicitud");
                    continue;
                }

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
                        // Aquí se delega al ActorPrestamo
                        receiver.send("Solicitud de préstamo enviada para " + libro);
                        break;

                    default:
                        receiver.send("Operación no reconocida: " + operacion);
                }

            } catch (Exception e) {
                receiver.send("Error procesando solicitud: " + e.getMessage());
            }
        }

        receiver.close();
        publisher.close();
        context.close();
    }
}
