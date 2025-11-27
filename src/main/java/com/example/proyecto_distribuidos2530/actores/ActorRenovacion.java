package com.example.proyecto_distribuidos2530.actores;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActorRenovacion {
    private static final String PUERTO_SUB = "5556";
    private static final String PUERTO_BD = "5558";
    private static final String TOPICO = "renovacion";

    private String sede;
    private String ipGestorCarga;
    private String ipGestorAlmacenamiento;
    private int operacionesProcesadas = 0;

    public ActorRenovacion(String sede, String ipGestorCarga, String ipGestorAlmacenamiento) {
        this.sede = sede;
        this.ipGestorCarga = ipGestorCarga;
        this.ipGestorAlmacenamiento = ipGestorAlmacenamiento;
    }

    public void iniciar() {
        try (ZContext context = new ZContext()) {
            // Socket SUB para recibir publicaciones del GC
            ZMQ.Socket subscriber = context.createSocket(ZMQ.SUB);
            subscriber.connect("tcp://" + ipGestorCarga + ":" + PUERTO_SUB);
            subscriber.subscribe(TOPICO.getBytes());

            // Socket REQ para actualizar BD
            ZMQ.Socket bdRequester = context.createSocket(ZMQ.REQ);
            bdRequester.connect("tcp://" + ipGestorAlmacenamiento + ":" + PUERTO_BD);
            bdRequester.setReceiveTimeOut(5000);

            System.out.println("==============================================");
            System.out.println("ActorRenovacion SEDE " + sede + " iniciado");
            System.out.println("Suscrito al tópico: " + TOPICO);
            System.out.println("GC: " + ipGestorCarga + ":" + PUERTO_SUB);
            System.out.println("GA: " + ipGestorAlmacenamiento + ":" + PUERTO_BD);
            System.out.println("==============================================\n");

            while (!Thread.currentThread().isInterrupted()) {
                procesarRenovacion(subscriber, bdRequester);
            }

        } catch (Exception e) {
            System.err.println("Error en ActorRenovacion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void procesarRenovacion(ZMQ.Socket subscriber, ZMQ.Socket bdRequester) {
        try {
            // Recibir tópico
            String topic = subscriber.recvStr();

            // Recibir mensaje
            String mensaje = subscriber.recvStr();

            System.out.println("\n[" + getTimestamp() + "] ActorRenovacion SEDE " + sede +
                    " recibió del tópico '" + topic + "':");
            System.out.println("  Mensaje: " + mensaje);

            // Parsear mensaje: libro|usuario|fechaActual|nuevaFecha
            String[] partes = mensaje.split("\\|");
            if (partes.length >= 4) {
                String codigoLibro = partes[0];
                String usuario = partes[1];
                String fechaActual = partes[2];
                String nuevaFecha = partes[3];

                // Actualizar BD
                System.out.println("  → Procesando renovación de " + codigoLibro +
                        " por usuario " + usuario);
                System.out.println("  → Nueva fecha de entrega: " + nuevaFecha);

                bdRequester.send("RENOVACION|" + codigoLibro);
                String respuesta = bdRequester.recvStr();

                if (respuesta != null && respuesta.startsWith("RENOVACION_OK")) {
                    operacionesProcesadas++;
                    System.out.println("  → ✓ Renovación registrada exitosamente");
                    System.out.println("  → BD respondió: " + respuesta);
                    System.out.println("  → Total procesadas: " + operacionesProcesadas);
                } else {
                    System.err.println("  → ✗ Error registrando renovación: " + respuesta);
                }
            } else {
                System.err.println("  → ERROR: Formato de mensaje incorrecto");
            }

        } catch (Exception e) {
            System.err.println("  → ERROR procesando renovación: " + e.getMessage());
        }
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java ActorRenovacion <sede> <ip_gc> <ip_ga>");
            System.out.println("Ejemplo: java ActorRenovacion SEDE1 localhost localhost");
            return;
        }

        String sede = args[0];
        String ipGC = args[1];
        String ipGA = args[2];

        ActorRenovacion actor = new ActorRenovacion(sede, ipGC, ipGA);
        actor.iniciar();
    }
}