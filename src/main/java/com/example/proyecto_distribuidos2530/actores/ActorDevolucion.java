package com.example.proyecto_distribuidos2530.actores;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
//Actor de Devolución - Procesa devoluciones de forma asíncrona, Patrón: Subscriber (Pub/Sub)

public class ActorDevolucion {
    private static final String PUERTO_SUB = "5556";
    private static final String PUERTO_BD = "5558";
    private static final String PUERTO_HEALTH = "5559";
    private static final String TOPICO = "devolucion";
    private static final int MAX_RETRY = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_FALLOS = 2;
    
    // Configuración SEDE2 (respaldo)
    private static final int SEDE2_PUERTO_BD = 6558;
    private static final int SEDE2_PUERTO_HEALTH = 6559;

    private String sede;
    private String ipGestorCarga;
    private String ipGestorAlmacenamiento;
    private int operacionesProcesadas = 0;
    private int fallosConsecutivos = 0;

    public ActorDevolucion(String sede, String ipGestorCarga, String ipGestorAlmacenamiento) {
        this.sede = sede;
        this.ipGestorCarga = ipGestorCarga;
        this.ipGestorAlmacenamiento = ipGestorAlmacenamiento;
    }

    /**
     * Envía petición con retry y timeout
     */
    private String enviarConRetry(ZMQ.Socket socket, String peticion, String operacion) {
        for (int intento = 1; intento <= MAX_RETRY; intento++) {
            try {
                socket.send(peticion);
                socket.setReceiveTimeOut(TIMEOUT_MS);
                String respuesta = socket.recvStr();
                
                if (respuesta != null) {
                    if (intento > 1) {
                        System.out.println("  [✓] " + operacion + " exitoso en intento " + intento);
                    }
                    return respuesta;
                } else {
                    System.err.println("  [!] Timeout en " + operacion + " (intento " + 
                        intento + "/" + MAX_RETRY + ")");
                }
            } catch (Exception e) {
                System.err.println("  [X] Error en " + operacion + " (intento " + 
                    intento + "/" + MAX_RETRY + "): " + e.getMessage());
            }
            
            if (intento < MAX_RETRY) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        System.err.println("  [X] FALLO: " + operacion + " falló después de " + 
            MAX_RETRY + " intentos");
        return null;
    }
    
    /**
     * Verifica salud del GA
     */
    private boolean verificarSaludGA(ZMQ.Socket healthSocket) {
        try {
            healthSocket.send("PING");
            healthSocket.setReceiveTimeOut(2000);
            String respuesta = healthSocket.recvStr();
            return respuesta != null && respuesta.startsWith("PONG");
        } catch (Exception e) {
            return false;
        }
    }

    public void iniciar() {
        ZContext context = new ZContext();
        boolean usandoSede2 = false;
        String sedeActual = "SEDE1";
        
        try {
            // Socket SUB para recibir publicaciones del GC
            ZMQ.Socket subscriber = context.createSocket(ZMQ.SUB);
            subscriber.connect("tcp://" + ipGestorCarga + ":" + PUERTO_SUB);
            subscriber.subscribe(TOPICO.getBytes());

            // Socket REQ para actualizar BD (inicialmente SEDE1)
            ZMQ.Socket bdRequester = context.createSocket(ZMQ.REQ);
            bdRequester.connect("tcp://" + ipGestorAlmacenamiento + ":" + PUERTO_BD);
            bdRequester.setReceiveTimeOut(TIMEOUT_MS);
            
            // Socket REQ para health check
            ZMQ.Socket healthChecker = context.createSocket(ZMQ.REQ);
            healthChecker.connect("tcp://" + ipGestorAlmacenamiento + ":" + PUERTO_HEALTH);

            System.out.println("==============================================");
            System.out.println("ActorDevolucion SEDE " + sede + " iniciado");
            System.out.println("Suscrito al tópico: " + TOPICO);
            System.out.println("SEDE PRIMARIA: " + ipGestorAlmacenamiento + ":" + PUERTO_BD);
            System.out.println("SEDE RESPALDO: localhost:" + SEDE2_PUERTO_BD);
            System.out.println("==============================================\n");

            while (!Thread.currentThread().isInterrupted()) {
                // FAILOVER: Si hay muchos fallos, cambiar a SEDE2
                if (fallosConsecutivos >= MAX_FALLOS) {
                    System.out.println("\n[FAILOVER-DEV] Detectados " + fallosConsecutivos + " fallos en " + sedeActual);
                    
                    if (!usandoSede2) {
                        System.out.println("[FAILOVER-DEV] Cambiando a SEDE2...");
                        bdRequester.close();
                        healthChecker.close();
                        
                        bdRequester = context.createSocket(ZMQ.REQ);
                        bdRequester.connect("tcp://localhost:" + SEDE2_PUERTO_BD);
                        bdRequester.setReceiveTimeOut(TIMEOUT_MS);
                        healthChecker = context.createSocket(ZMQ.REQ);
                        healthChecker.connect("tcp://localhost:" + SEDE2_PUERTO_HEALTH);
                        
                        if (verificarSaludGA(healthChecker)) {
                            System.out.println("[FAILOVER-DEV] ✓ Conectado a SEDE2");
                            usandoSede2 = true;
                            sedeActual = "SEDE2";
                            fallosConsecutivos = 0;
                        } else {
                            System.err.println("[FAILOVER-DEV] X SEDE2 no disponible");
                            Thread.sleep(5000);
                            continue;
                        }
                    } else {
                        System.out.println("[FAILOVER-DEV] Intentando volver a SEDE1...");
                        ZMQ.Socket testHealth = context.createSocket(ZMQ.REQ);
                        testHealth.connect("tcp://" + ipGestorAlmacenamiento + ":" + PUERTO_HEALTH);
                        
                        if (verificarSaludGA(testHealth)) {
                            System.out.println("[FAILOVER-DEV] ✓ SEDE1 recuperada");
                            bdRequester.close();
                            healthChecker.close();
                            
                            bdRequester = context.createSocket(ZMQ.REQ);
                            bdRequester.connect("tcp://" + ipGestorAlmacenamiento + ":" + PUERTO_BD);
                            bdRequester.setReceiveTimeOut(TIMEOUT_MS);
                            healthChecker = testHealth;
                            
                            usandoSede2 = false;
                            sedeActual = "SEDE1";
                            fallosConsecutivos = 0;
                        } else {
                            testHealth.close();
                            Thread.sleep(5000);
                            continue;
                        }
                    }
                }
                
                procesarDevolucion(subscriber, bdRequester);
            }

        } catch (Exception e) {
            System.err.println("Error en ActorDevolucion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void procesarDevolucion(ZMQ.Socket subscriber, ZMQ.Socket bdRequester) {
        try {
            // Recibir tópico
            String topic = subscriber.recvStr();

            // Recibir mensaje
            String mensaje = subscriber.recvStr();

            System.out.println("\n[" + getTimestamp() + "] ActorDevolucion SEDE " + sede +
                    " recibió del tópico '" + topic + "':");
            System.out.println("  Mensaje: " + mensaje);

            // Parsear mensaje: libro|usuario|timestamp
            String[] partes = mensaje.split("\\|");
            if (partes.length >= 2) {
                String codigoLibro = partes[0];
                String usuario = partes[1];

                // Actualizar BD con retry
                System.out.println("  → Procesando devolución de " + codigoLibro +
                        " por usuario " + usuario);

                String respuesta = enviarConRetry(bdRequester, 
                    "DEVOLUCION|" + codigoLibro, 
                    "Devolución de " + codigoLibro);

                if (respuesta != null && respuesta.startsWith("DEVOLUCION_OK")) {
                    operacionesProcesadas++;
                    fallosConsecutivos = 0; // Reset en éxito
                    System.out.println("  → ✓ Devolución registrada exitosamente");
                    System.out.println("  → BD respondió: " + respuesta);
                    System.out.println("  → Total procesadas: " + operacionesProcesadas);
                } else if (respuesta != null) {
                    fallosConsecutivos++;
                    System.err.println("  → ✗ Error registrando devolución: " + respuesta);
                } else {
                    fallosConsecutivos++;
                    System.err.println("  → ✗ Error de comunicación con GA (" + 
                        fallosConsecutivos + "/" + MAX_FALLOS + " fallos)");
                }
            } else {
                System.err.println("  → ERROR: Formato de mensaje incorrecto");
            }

        } catch (Exception e) {
            System.err.println("  → ERROR procesando devolución: " + e.getMessage());
        }
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java ActorDevolucion <sede> <ip_gc> <ip_ga>");
            System.out.println("Ejemplo: java ActorDevolucion SEDE1 localhost localhost");
            return;
        }

        String sede = args[0];
        String ipGC = args[1];
        String ipGA = args[2];

        ActorDevolucion actor = new ActorDevolucion(sede, ipGC, ipGA);
        actor.iniciar();
    }
}