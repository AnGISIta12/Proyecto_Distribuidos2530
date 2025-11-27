package com.example.proyecto_distribuidos2530.actores;
//Actor 2 que gestiona los prestamos de los libros
import org.zeromq.ZMQ; // Importamos la libreria de ZeroMQ que toca usar para el proyecto
public class ActorPrestamo {
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket puller = context.socket(ZMQ.PULL);
        puller.connect("tcp://localhost:5557");

        System.out.println("ActorPrestamo escuchando tareas...");

        while (!Thread.currentThread().isInterrupted()) {
            String msg = puller.recvStr();
            System.out.println("ActorPrestamo → " + msg);
        }

        puller.close();
        context.close();
    }
}