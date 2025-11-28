package com.example.proyecto_distribuidos2530.almacenamiento;

import com.example.proyecto_distribuidos2530.basedatos.Database;
import org.zeromq.ZMQ;

public class GestorAlmcto {
    private Database bdPrimaria;
    private Database bdSecundaria;
    private String sede;
    private boolean esPrimario;
    private static final int MAX_RETRY_REPLICA = 3;
    private static final int RETRY_DELAY_MS = 1000;

    public GestorAlmcto(String sede, boolean esPrimario) {
        this.sede = sede;
        this.esPrimario = esPrimario;
        String pathPrimaria = "bd_primaria_" + sede + ".dat";
        String pathSecundaria = "bd_secundaria_" + sede + ".dat";

        this.bdPrimaria = new Database(sede, pathPrimaria);
        this.bdSecundaria = new Database(sede, pathSecundaria);
    }

    public synchronized boolean prestarLibro(String codigo) {
        boolean exito = bdPrimaria.prestarLibro(codigo);
        if (exito) {
            // Actualizar réplica secundaria con retry
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

    /**
     * Sincroniza operación con réplica secundaria con reintentos
     */
    private void sincronizarReplica(Runnable operacion, String descripcion) {
        new Thread(() -> {
            boolean exitoso = false;
            for (int intento = 1; intento <= MAX_RETRY_REPLICA && !exitoso; intento++) {
                try {
                    operacion.run();
                    exitoso = true;
                    if (intento > 1) {
                        System.out.println("[RÉPLICA] " + descripcion + 
                            " sincronizada en intento " + intento);
                    }
                } catch (Exception e) {
                    System.err.println("[RÉPLICA] Error en " + descripcion + 
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
                System.err.println("[RÉPLICA] FALLO CRÍTICO: No se pudo sincronizar " + 
                    descripcion + " después de " + MAX_RETRY_REPLICA + " intentos");
            }
        }).start();
    }

    public int getCopiasDisponibles(String codigo) {
        return bdPrimaria.getCopiasDisponibles(codigo);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java GestorAlmcto <sede> <es_primario>");
            return;
        }

        String sede = args[0];
        boolean esPrimario = Boolean.parseBoolean(args[1]);
        GestorAlmcto gestor = new GestorAlmcto(sede, esPrimario);

        // Servicio ZeroMQ para recibir operaciones de los Actores
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket receiver = context.socket(ZMQ.REP);
        receiver.bind("tcp://*:5558"); // Puerto para operaciones BD

        // Health check endpoint
        ZMQ.Socket healthCheck = context.socket(ZMQ.REP);
        healthCheck.bind("tcp://*:5559"); // Puerto health check
        
        System.out.println("==============================================");
        System.out.println("GestorAlmcto " + sede + " listo (" +
                (esPrimario ? "PRIMARIO" : "SECUNDARIO") + ")");
        System.out.println("Puerto operaciones: 5558");
        System.out.println("Puerto health check: 5559");
        System.out.println("==============================================\n");

        // Thread para health check
        Thread healthCheckThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String ping = healthCheck.recvStr(ZMQ.DONTWAIT);
                    if (ping != null && ping.equals("PING")) {
                        healthCheck.send("PONG|" + sede + "|" + 
                            (esPrimario ? "PRIMARIO" : "SECUNDARIO"));
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    // Ignorar errores de health check
                }
            }
        });
        healthCheckThread.setDaemon(true);
        healthCheckThread.start();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                String request = receiver.recvStr();
                if (request == null) {
                    continue;
                }
                
                String[] partes = request.split("\\|");
                String operacion = partes[0];
                String respuesta = "";

                switch (operacion.toUpperCase()) {
                    case "PRESTAMO":
                        boolean exito = gestor.prestarLibro(partes[1]);
                        respuesta = exito ? "PRESTAMO_OK" : "PRESTAMO_NOK";
                        break;

                    case "DEVOLUCION":
                        gestor.devolverLibro(partes[1]);
                        respuesta = "DEVOLUCION_OK";
                        break;

                    case "RENOVACION":
                        gestor.renovarLibro(partes[1]);
                        respuesta = "RENOVACION_OK";
                        break;

                    case "CONSULTA":
                        int disponibles = gestor.getCopiasDisponibles(partes[1]);
                        respuesta = "DISPONIBLES_" + disponibles;
                        break;
                        
                    default:
                        respuesta = "ERROR|Operación desconocida: " + operacion;
                        System.err.println("Operación desconocida: " + operacion);
                }

                receiver.send(respuesta);
                
            } catch (Exception e) {
                System.err.println("[ERROR] Procesando petición: " + e.getMessage());
                try {
                    receiver.send("ERROR|" + e.getMessage());
                } catch (Exception sendError) {
                    System.err.println("[ERROR] No se pudo enviar respuesta de error");
                }
            }
        }

        receiver.close();
        healthCheck.close();
        context.close();
    }
}