package com.example.proyecto_distribuidos2530.actores;
//Actor 3 que gestiona las renovaciones de los libros
import org.zeromq.ZMQ; // Importamos la libreria de ZeroMQ que toca usar para el proyecto
public class ActorRenovacion {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("renovacion".getBytes());

        System.out.println("ActorRenovacion escuchando...");

        while (!Thread.currentThread().isInterrupted()) {
            String topic = subscriber.recvStr();
            String msg = subscriber.recvStr();
            System.out.println("ActorRenovacion → " + msg);
        }

        subscriber.close();
        context.close();
    }
}
