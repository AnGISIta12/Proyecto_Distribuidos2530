package com.example.proyecto_distribuidos2530.carga;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gestor de Carga - Recibe peticiones de los Procesos Solicitantes
 * y las distribuye a los Actores correspondientes.
 *
 * Patrón usado:
 * - REP para comunicación síncrona con PS
 * - PUB para operaciones asíncronas (devolución, renovación)
 * - PUSH para operaciones síncronas (préstamo)
 */
public class GestorCarga {
    private static final String PUERTO_PS = "5555";        // Recibe de PS
    private static final String PUERTO_PUB = "5556";       // Pub/Sub para actores
    private static final String PUERTO_PRESTAMO = "5557";  // Push para préstamos

    private String sede;

    public GestorCarga(String sede) {
        this.sede = sede;
    }

    public void iniciar() {
        try (ZContext context = new ZContext()) {
            // Socket REP para recibir solicitudes de los PS
            ZMQ.Socket receiver = context.createSocket(ZMQ.REP);
            receiver.bind("tcp://*:" + PUERTO_PS);

            // Socket PUB para publicar mensajes a actores (devolución y renovación)
            ZMQ.Socket publisher = context.createSocket(ZMQ.PUB);
            publisher.bind("tcp://*:" + PUERTO_PUB);

            // Socket PUSH para enviar tareas de préstamo
            ZMQ.Socket pusher = context.createSocket(ZMQ.PUSH);
            pusher.bind("tcp://*:" + PUERTO_PRESTAMO);

            System.out.println("==============================================");
            System.out.println("Gestor de Carga - SEDE " + sede + " iniciado");
            System.out.println("Puerto PS: " + PUERTO_PS);
            System.out.println("Puerto PUB: " + PUERTO_PUB);
            System.out.println("Puerto PUSH: " + PUERTO_PRESTAMO);
            System.out.println("==============================================\n");

            // Dar tiempo a que los suscriptores se conecten
            Thread.sleep(500);

            while (!Thread.currentThread().isInterrupted()) {
                procesarSolicitud(receiver, publisher, pusher);
            }
        } catch (Exception e) {
            System.err.println("Error en GestorCarga: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void procesarSolicitud(ZMQ.Socket receiver, ZMQ.Socket publisher, ZMQ.Socket pusher) {
        String request = receiver.recvStr();
        long timestampInicio = System.currentTimeMillis();

        System.out.println("\n[" + getTimestamp() + "] GC SEDE " + sede + " recibió: " + request);

        try {
            String[] partes = request.split("\\|");
            if (partes.length < 3) {
                receiver.send("ERROR|Formato incorrecto. Use: OPERACION|LIBRO|USUARIO");
                return;
            }

            String operacion = partes[0].toUpperCase();
            String libro = partes[1];
            String usuario = partes[2];

            switch (operacion) {
                case "DEVOLUCION":
                    procesarDevolucion(receiver, publisher, libro, usuario);
                    break;

                case "RENOVACION":
                    procesarRenovacion(receiver, publisher, libro, usuario);
                    break;

                case "PRESTAMO":
                    procesarPrestamo(receiver, pusher, libro, usuario, timestampInicio);
                    break;

                default:
                    receiver.send("ERROR|Operación no reconocida: " + operacion);
            }

        } catch (Exception e) {
            String errorMsg = "ERROR|Error al procesar solicitud: " + e.getMessage();
            receiver.send(errorMsg);
            System.err.println(errorMsg);
        }
    }

    //Procesa devolución de forma asíncrona

    private void procesarDevolucion(ZMQ.Socket receiver, ZMQ.Socket publisher,
                                    String libro, String usuario) {
        // Respuesta inmediata al PS
        String respuesta = "OK|Devolución aceptada para " + libro;
        receiver.send(respuesta);
        System.out.println("  → Respuesta enviada al PS: " + respuesta);

        // Publicar al tópico de devolución (asíncrono)
        String mensaje = libro + "|" + usuario + "|" + getTimestamp();
        publisher.sendMore("devolucion");
        publisher.send(mensaje);
        System.out.println("  → Publicado en tópico 'devolucion': " + mensaje);
    }


     //procesa renovación de forma asíncrona

    private void procesarRenovacion(ZMQ.Socket receiver, ZMQ.Socket publisher,
                                    String libro, String usuario) {
        // Calcular nueva fecha de entrega (1 semana después)
        LocalDateTime fechaActual = LocalDateTime.now();
        LocalDateTime nuevaFecha = fechaActual.plusWeeks(1);
        String fechaFormateada = nuevaFecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // Respuesta inmediata al PS
        String respuesta = "OK|Renovación aceptada para " + libro + ". Nueva fecha: " + fechaFormateada;
        receiver.send(respuesta);
        System.out.println("  → Respuesta enviada al PS: " + respuesta);

        // Publicar al tópico de renovación (asíncrono)
        String mensaje = libro + "|" + usuario + "|" + fechaActual + "|" + nuevaFecha;
        publisher.sendMore("renovacion");
        publisher.send(mensaje);
        System.out.println("  → Publicado en tópico 'renovacion': " + mensaje);
    }

    //Procesa préstamo de forma síncrona mediante PUSH-PULL

    private void procesarPrestamo(ZMQ.Socket receiver, ZMQ.Socket pusher,
                                  String libro, String usuario, long timestampInicio) {
        // Enviar tarea al ActorPrestamo
        String mensaje = libro + "|" + usuario + "|" + timestampInicio;
        pusher.send(mensaje);
        System.out.println("  → Tarea enviada a ActorPrestamo: " + mensaje);

        // Para préstamos síncronos, el ActorPrestamo debe responder
        // En esta implementación simplificada, respondemos inmediatamente
        // En producción, deberías esperar la respuesta del Actor
        String respuesta = "OK|Solicitud de préstamo procesada para " + libro;
        receiver.send(respuesta);

        long tiempoRespuesta = System.currentTimeMillis() - timestampInicio;
        System.out.println("  → Tiempo de respuesta: " + tiempoRespuesta + "ms");
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java GestorCarga <sede>");
            System.out.println("Ejemplo: java GestorCarga SEDE1");
            return;
        }

        String sede = args[0];
        GestorCarga gestor = new GestorCarga(sede);
        gestor.iniciar();
    }
}