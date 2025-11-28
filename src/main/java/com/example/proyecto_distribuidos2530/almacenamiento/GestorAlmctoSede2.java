package com.example.proyecto_distribuidos2530.almacenamiento;

import com.example.proyecto_distribuidos2530.basedatos.Database;
import org.zeromq.ZMQ;

/**
 * GestorAlmacenamiento para SEDE2 (respaldo)
 * Usa puertos 6558, 6559, 6560 para simulación local
 */
public class GestorAlmctoSede2 {
    private Database bdPrimaria;
    private Database bdSecundaria;
    private String sede;
    private static final int MAX_RETRY_REPLICA = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    // Puertos diferentes para SEDE2
    private static final int PUERTO_BD = 6558;
    private static final int PUERTO_HEALTH = 6559;

    public GestorAlmctoSede2(String sede) {
        this.sede = sede;
        String pathPrimaria = "bd_primaria_" + sede + ".dat";
        String pathSecundaria = "bd_secundaria_" + sede + ".dat";

        this.bdPrimaria = new Database(sede, pathPrimaria);
        this.bdSecundaria = new Database(sede, pathSecundaria);
    }

    public synchronized boolean prestarLibro(String codigo) {
        boolean exito = bdPrimaria.prestarLibro(codigo);
        if (exito) {
            sincronizarReplica(() -> bdSecundaria.prestarLibro(codigo), 
                "Préstamo de " + codigo);
        }
        return exito;
    }

    public synchronized void devolverLibro(String codigo) {
        bdPrimaria.devolverLibro(codigo);
        sincronizarReplica(() -> bdSecundaria.devolverLibro(codigo), 
            "Devolución de " + codigo);
    }

    public synchronized void renovarLibro(String codigo) {
        bdPrimaria.renovarLibro(codigo);
        sincronizarReplica(() -> bdSecundaria.renovarLibro(codigo), 
            "Renovación de " + codigo);
    }

    private void sincronizarReplica(Runnable operacion, String descripcion) {
        new Thread(() -> {
            boolean exitoso = false;
            for (int intento = 1; intento <= MAX_RETRY_REPLICA && !exitoso; intento++) {
                try {
                    operacion.run();
                    exitoso = true;
                    if (intento > 1) {
                        System.out.println("[RÉPLICA-SEDE2] " + descripcion + 
                            " sincronizada en intento " + intento);
                    }
                } catch (Exception e) {
                    System.err.println("[RÉPLICA-SEDE2] Error en " + descripcion + 
                        " (intento " + intento + "/" + MAX_RETRY_REPLICA + "): " + 
                        e.getMessage());
                    if (intento < MAX_RETRY_REPLICA) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            if (!exitoso) {
                System.err.println("[RÉPLICA-SEDE2] FALLO: No se pudo sincronizar " + 
                    descripcion);
            }
        }).start();
    }

    public int getCopiasDisponibles(String codigo) {
        return bdPrimaria.getCopiasDisponibles(codigo);
    }

    public static void main(String[] args) {
        String sede = args.length > 0 ? args[0] : "SEDE2";
        GestorAlmctoSede2 gestor = new GestorAlmctoSede2(sede);

        ZMQ.Context context = ZMQ.context(1);
        
        // Socket para operaciones BD en puerto 6558
        ZMQ.Socket receiver = context.socket(ZMQ.REP);
        receiver.bind("tcp://*:" + PUERTO_BD);

        // Health check en puerto 6559
        ZMQ.Socket healthCheck = context.socket(ZMQ.REP);
        healthCheck.bind("tcp://*:" + PUERTO_HEALTH);

        // Thread para health check
        Thread healthThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String ping = healthCheck.recvStr();
                    if ("PING".equals(ping)) {
                        healthCheck.send("PONG|" + sede + "|respaldo");
                    }
                } catch (Exception e) {
                    System.err.println("[HEALTH-SEDE2] Error: " + e.getMessage());
                }
            }
        });
        healthThread.setDaemon(true);
        healthThread.start();

        System.out.println("==============================================");
        System.out.println("GestorAlmcto " + sede + " listo (RESPALDO)");
        System.out.println("Puerto operaciones: " + PUERTO_BD);
        System.out.println("Puerto health check: " + PUERTO_HEALTH);
        System.out.println("==============================================");
        System.out.println("[SEDE2] Esperando conexiones de failover...");
        System.out.println("[SEDE2] Listo para procesar operaciones\n");

        // Loop principal para operaciones BD
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String peticion = receiver.recvStr();
                if (peticion == null) continue;

                System.out.println("[SEDE2] <- Petición recibida: " + peticion);

                String[] partes = peticion.split("\\|");
                if (partes.length < 2) {
                    receiver.send("ERROR|Formato incorrecto");
                    System.err.println("[SEDE2] ✗ Formato incorrecto: " + peticion);
                    continue;
                }

                String operacion = partes[0];
                String codigoLibro = partes[1];
                String respuesta = "";

                switch (operacion) {
                    case "CONSULTA":
                        int disponibles = gestor.getCopiasDisponibles(codigoLibro);
                        respuesta = "DISPONIBLES_" + disponibles;
                        System.out.println("[SEDE2] Consulta: " + codigoLibro + " -> " + disponibles + " copias disponibles");
                        break;

                    case "PRESTAMO":
                        boolean exito = gestor.prestarLibro(codigoLibro);
                        respuesta = exito ? "PRESTAMO_OK|" + codigoLibro : "PRESTAMO_FAIL|Sin copias";
                        if (exito) {
                            System.out.println("[SEDE2] ✓ Préstamo exitoso: " + codigoLibro);
                        } else {
                            System.out.println("[SEDE2] ✗ Préstamo fallido: " + codigoLibro + " (sin copias)");
                        }
                        break;

                    case "DEVOLUCION":
                        gestor.devolverLibro(codigoLibro);
                        respuesta = "DEVOLUCION_OK";
                        System.out.println("[SEDE2] ✓ Devolución exitosa: " + codigoLibro);
                        break;

                    case "RENOVACION":
                        gestor.renovarLibro(codigoLibro);
                        respuesta = "RENOVACION_OK";
                        System.out.println("[SEDE2] ✓ Renovación exitosa: " + codigoLibro);
                        break;

                    default:
                        respuesta = "ERROR|Operación desconocida: " + operacion;
                        System.err.println("[SEDE2] ✗ Operación desconocida: " + operacion);
                }

                System.out.println("[SEDE2] -> Respuesta enviada: " + respuesta + "\n");
                receiver.send(respuesta);

            } catch (Exception e) {
                System.err.println("[ERROR-SEDE2] Procesando petición: " + e.getMessage());
                e.printStackTrace();
                try {
                    receiver.send("ERROR|" + e.getMessage());
                } catch (Exception ex) {
                    System.err.println("[ERROR-SEDE2] No se pudo enviar respuesta de error");
                }
            }
        }

        receiver.close();
        healthCheck.close();
        context.close();
    }
}
