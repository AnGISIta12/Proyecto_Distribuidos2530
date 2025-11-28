package com.example.proyecto_distribuidos2530.actores;

import org.zeromq.ZMQ;

public class ActorPrestamo {
    private static final int MAX_RETRY = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_FALLOS = 2; // Reducido para pruebas
    
    // Configuración SEDE1 (primaria)
    private static final String SEDE1_BD = "tcp://localhost:5558";
    private static final String SEDE1_HEALTH = "tcp://localhost:5559";
    
    // Configuración SEDE2 (respaldo)
    private static final String SEDE2_BD = "tcp://localhost:6558";
    private static final String SEDE2_HEALTH = "tcp://localhost:6559";
    /**
     * Envía petición con retry y timeout
     */
    private static String enviarConRetry(ZMQ.Socket socket, String peticion, String operacion) {
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
    private static boolean verificarSaludGA(ZMQ.Socket healthSocket) {
        try {
            healthSocket.send("PING");
            healthSocket.setReceiveTimeOut(2000);
            String respuesta = healthSocket.recvStr();
            return respuesta != null && respuesta.startsWith("PONG");
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);
        int fallosConsecutivos = 0;
        boolean usandoSede2 = false;
        String sedeActual = "SEDE1";

        // Socket PULL para recibir tareas del GC
        ZMQ.Socket puller = context.socket(ZMQ.PULL);
        puller.connect("tcp://localhost:5557");

        // Sockets REQ para BD (inicialmente SEDE1)
        ZMQ.Socket bdRequester = context.socket(ZMQ.REQ);
        bdRequester.connect(SEDE1_BD);
        
        // Socket REQ para health check
        ZMQ.Socket healthChecker = context.socket(ZMQ.REQ);
        healthChecker.connect(SEDE1_HEALTH);

        System.out.println("==============================================");
        System.out.println("ActorPrestamo iniciado");
        System.out.println("Escuchando tareas en puerto 5557");
        System.out.println("SEDE PRIMARIA: " + SEDE1_BD);
        System.out.println("SEDE RESPALDO: " + SEDE2_BD);
        System.out.println("==============================================\n");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // FAILOVER: Si hay muchos fallos, cambiar a SEDE2
                if (fallosConsecutivos >= MAX_FALLOS) {
                    System.out.println("\n[FAILOVER] Detectados " + fallosConsecutivos + " fallos en " + sedeActual);
                    
                    if (!usandoSede2) {
                        // Intentar failover a SEDE2
                        System.out.println("[FAILOVER] Intentando cambiar a SEDE2...");
                        
                        // Cerrar conexiones actuales
                        bdRequester.close();
                        healthChecker.close();
                        
                        // Crear nuevas conexiones a SEDE2
                        bdRequester = context.socket(ZMQ.REQ);
                        bdRequester.connect(SEDE2_BD);
                        healthChecker = context.socket(ZMQ.REQ);
                        healthChecker.connect(SEDE2_HEALTH);
                        
                        if (verificarSaludGA(healthChecker)) {
                            System.out.println("[FAILOVER] ✓ Conectado exitosamente a SEDE2");
                            System.out.println("[FAILOVER] Operaciones continúan transparentemente...");
                            usandoSede2 = true;
                            sedeActual = "SEDE2";
                            fallosConsecutivos = 0;
                        } else {
                            System.err.println("[FAILOVER] X SEDE2 tampoco disponible");
                            Thread.sleep(5000);
                            continue;
                        }
                    } else {
                        // Ya estamos en SEDE2, intentar volver a SEDE1
                        System.out.println("[FAILOVER] Intentando reconectar a SEDE1...");
                        
                        ZMQ.Socket testHealth = context.socket(ZMQ.REQ);
                        testHealth.connect(SEDE1_HEALTH);
                        
                        if (verificarSaludGA(testHealth)) {
                            System.out.println("[FAILOVER] ✓ SEDE1 recuperada, reconectando...");
                            bdRequester.close();
                            healthChecker.close();
                            
                            bdRequester = context.socket(ZMQ.REQ);
                            bdRequester.connect(SEDE1_BD);
                            healthChecker = testHealth;
                            
                            usandoSede2 = false;
                            sedeActual = "SEDE1";
                            fallosConsecutivos = 0;
                        } else {
                            testHealth.close();
                            System.err.println("[FAILOVER] SEDE1 aún no disponible, continuando en SEDE2");
                            Thread.sleep(5000);
                            continue;
                        }
                    }
                }
                
                String msg = puller.recvStr();
                if (msg == null) continue;
                
                System.out.println("\n[TAREA] ActorPrestamo recibió: " + msg);

                String[] partesMsg = msg.split("\\|");
                if (partesMsg.length >= 2) {
                    String codigoLibro = partesMsg[0];
                    String usuario = partesMsg[1];

                    // Consultar disponibilidad con retry
                    String respuestaBD = enviarConRetry(bdRequester, 
                        "CONSULTA|" + codigoLibro, "Consulta de " + codigoLibro);
                    
                    if (respuestaBD == null) {
                        fallosConsecutivos++;
                        System.err.println("[ERROR] No se pudo consultar disponibilidad (" + 
                            fallosConsecutivos + "/" + MAX_FALLOS + " fallos)");
                        continue;
                    }
                    
                    System.out.println("  [BD] " + respuestaBD);

                    if (respuestaBD.startsWith("DISPONIBLES_")) {
                        int disponibles = Integer.parseInt(respuestaBD.split("_")[1]);
                        
                        if (disponibles > 0) {
                            // Realizar préstamo con retry
                            String resultado = enviarConRetry(bdRequester, 
                                "PRESTAMO|" + codigoLibro, "Préstamo de " + codigoLibro);
                            
                            if (resultado != null) {
                                System.out.println("  [✓] Préstamo exitoso para usuario " + usuario + ": " + resultado);
                                fallosConsecutivos = 0; // Reset en éxito
                            } else {
                                fallosConsecutivos++;
                            }
                        } else {
                            System.out.println("  [!] Préstamo denegado - No hay copias disponibles de " + codigoLibro);
                        }
                    }
                } else {
                    System.err.println("  [ERROR] Formato de mensaje incorrecto (esperado: libro|usuario|timestamp): " + msg);
                }
                
            } catch (Exception e) {
                System.err.println("[ERROR] Excepción procesando tarea: " + e.getMessage());
                fallosConsecutivos++;
            }
        }

        puller.close();
        bdRequester.close();
        healthChecker.close();
        context.close();
    }
}