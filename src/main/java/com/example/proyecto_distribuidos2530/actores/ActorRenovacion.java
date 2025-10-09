package com.example.proyecto_distribuidos2530.actores;

import org.zeromq.ZMQ;

public class ActorRenovacion {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("renovacion".getBytes());

        System.out.println("ActorRenovacion escuchando...");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                String topic = subscriber.recvStr();
                String msg = subscriber.recvStr();

                if (msg == null) {
                    System.err.println("ActorRenovacion → mensaje vacío, ignorando...");
                    continue;
                }

                System.out.println("ActorRenovacion procesó: " + msg);

            } catch (Exception e) {
                System.err.println("Error en ActorRenovacion: " + e.getMessage());
            }
        }

        subscriber.close();
        context.close();
    }
}
