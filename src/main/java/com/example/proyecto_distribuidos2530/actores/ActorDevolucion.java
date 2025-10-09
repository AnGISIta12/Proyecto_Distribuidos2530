package com.example.proyecto_distribuidos2530.actores;
//Actor 1 que gestiona las devoluciones de los libros
import org.zeromq.ZMQ; // Importamos la libreria de ZeroMQ que toca usar para el proyecto
public class ActorDevolucion {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("devolucion".getBytes());

        System.out.println("ActorDevolucion escuchando...");

        while (!Thread.currentThread().isInterrupted()) {
            String topic = subscriber.recvStr();
            String msg = subscriber.recvStr();
            System.out.println("ActorDevolucion → " + msg);
        }

        subscriber.close();
        context.close();
    }
}
