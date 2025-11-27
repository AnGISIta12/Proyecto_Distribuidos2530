package com.example.proyecto_distribuidos2530.solicitante;

import org.zeromq.ZMQ;
import java.nio.file.*;
import java.util.List;

public class ProcesoSolicitante {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java ProcesoSolicitante <archivo_peticiones>");
            return;
        }

        String archivo = "peticiones.txt";
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket requester = context.socket(ZMQ.REQ);
        requester.connect("tcp://localhost:5555"); // puerto del GC

        List<String> lineas = Files.readAllLines(Paths.get(archivo));
        for (String linea : lineas) {
            requester.send(linea);
            String respuesta = requester.recvStr();
            System.out.println("PS recibió: " + respuesta);
            Thread.sleep(800);
        }

        requester.close();
        context.close();
    }
}
