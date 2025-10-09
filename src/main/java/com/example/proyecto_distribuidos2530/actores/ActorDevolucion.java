package com.example.proyecto_distribuidos2530.actores;

import org.zeromq.ZMQ;

public class ActorDevolucion {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("devolucion".getBytes());

        System.out.println("ActorDevolucion escuchando...");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                String topic = subscriber.recvStr();
                String msg = subscriber.recvStr();

                if (msg == null) {
                    System.err.println("ActorDevolucion → mensaje vacío, ignorando...");
                    continue;
                }

                System.out.println("ActorDevolucion procesó: " + msg);

            } catch (Exception e) {
                System.err.println("Error en ActorDevolucion: " + e.getMessage());
            }
        }

        subscriber.close();
        context.close();
    }
}
