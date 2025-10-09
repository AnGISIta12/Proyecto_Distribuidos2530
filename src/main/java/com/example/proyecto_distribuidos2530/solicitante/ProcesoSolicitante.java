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

        String archivo = args[0];
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket requester = context.socket(ZMQ.REQ);
        requester.setReceiveTimeOut(2000); // timeout de 2s
        requester.connect("tcp://localhost:5555"); // puerto del GC

        List<String> lineas;
        try {
            lineas = Files.readAllLines(Paths.get(archivo));
        } catch (Exception e) {
            System.err.println("Error al leer archivo de peticiones: " + e.getMessage());
            return;
        }

        for (String linea : lineas) {
            try {
                requester.send(linea);
                String respuesta = requester.recvStr();

                if (respuesta == null) {
                    System.err.println("Timeout esperando respuesta del GC → reintentando...");
                    requester.send(linea);
                    respuesta = requester.recvStr();
                }

                System.out.println("PS recibió: " + respuesta);
                Thread.sleep(800);

            } catch (Exception e) {
                System.err.println("Error enviando petición: " + e.getMessage());
            }
        }

        requester.close();
        context.close();
    }
}
