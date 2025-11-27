package com.example.proyecto_distribuidos2530.solicitante;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Proceso Solicitante - Genera peticiones de los usuarios
 *
 * Lee peticiones desde archivo y las envía al Gestor de Carga
 * Recopila métricas de rendimiento para análisis
 */
public class ProcesoSolicitante {
    private static final int TIMEOUT = 5000; // 5 segundos

    private String idProceso;
    private String sede;
    private String ipGestorCarga ;
    private List<Long> tiemposRespuesta = new ArrayList<>();
    private int solicitudesProcesadas = 0;
    private int solicitudesFallidas = 0;

    // Métricas por tipo de operación
    private Map<String, List<Long>> tiemposPorOperacion = new HashMap<>();

    public ProcesoSolicitante(String idProceso, String sede, String ipGestorCarga) {
        this.idProceso = idProceso;
        this.sede = sede;
        this.ipGestorCarga = ipGestorCarga;

        tiemposPorOperacion.put("PRESTAMO", new ArrayList<>());
        tiemposPorOperacion.put("DEVOLUCION", new ArrayList<>());
        tiemposPorOperacion.put("RENOVACION", new ArrayList<>());
    }

    //Procesa archivo de peticiones

    public void procesarArchivo(String rutaArchivo, long duracionMs) throws Exception {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket requester = context.createSocket(ZMQ.REQ);
            requester.connect("tcp://" + ipGestorCarga + ":5555");
            requester.setReceiveTimeOut(TIMEOUT);

            System.out.println("==============================================");
            System.out.println("Proceso Solicitante " + idProceso);
            System.out.println("Sede: " + sede);
            System.out.println("Conectado a GC: " + ipGestorCarga + ":5555");
            System.out.println("==============================================\n");

            List<String> peticiones = cargarPeticiones(rutaArchivo);
            System.out.println("Peticiones cargadas: " + peticiones.size() + "\n");

            long tiempoInicio = System.currentTimeMillis();
            long tiempoLimite = tiempoInicio + duracionMs;
            int indice = 0;

            while (System.currentTimeMillis() < tiempoLimite) {
                // Obtener siguiente petición (circular)
                String peticion = peticiones.get(indice % peticiones.size());
                indice++;

                enviarPeticion(requester, peticion);

                // Pequeña pausa entre peticiones
                Thread.sleep(100);
            }

            System.out.println("\n==============================================");
            System.out.println("RESUMEN PS " + idProceso);
            generarReporte();
            System.out.println("==============================================\n");

            requester.close();
        }
    }

    //Procesa archivo completo (sin límite de tiempo)

    public void procesarArchivoCompleto(String rutaArchivo) throws Exception {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket requester = context.createSocket(ZMQ.REQ);
            requester.connect("tcp://" + ipGestorCarga + ":5555");
            requester.setReceiveTimeOut(TIMEOUT);

            System.out.println("==============================================");
            System.out.println("Proceso Solicitante " + idProceso);
            System.out.println("Sede: " + sede);
            System.out.println("Conectado a GC: " + ipGestorCarga + ":5555");
            System.out.println("==============================================\n");

            List<String> peticiones = cargarPeticiones(rutaArchivo);
            System.out.println("Procesando " + peticiones.size() + " peticiones...\n");

            for (String peticion : peticiones) {
                enviarPeticion(requester, peticion);
                Thread.sleep(200); // Pausa entre peticiones
            }

            System.out.println("\n==============================================");
            System.out.println("RESUMEN PS " + idProceso);
            generarReporte();
            System.out.println("==============================================\n");

            requester.close();
        }
    }

    //Envía una petición al GC y mide el tiempo de respuesta

    private void enviarPeticion(ZMQ.Socket socket, String peticion) {
        long inicio = System.currentTimeMillis();

        try {
            System.out.println("[" + getTimestamp() + "] PS " + idProceso + " envía: " + peticion);

            socket.send(peticion);
            String respuesta = socket.recvStr();

            long tiempoRespuesta = System.currentTimeMillis() - inicio;

            if (respuesta != null) {
                System.out.println("  → Respuesta (" + tiempoRespuesta + "ms): " + respuesta);

                tiemposRespuesta.add(tiempoRespuesta);
                solicitudesProcesadas++;

                // Registrar por tipo de operación
                String tipoOperacion = peticion.split("\\|")[0];
                if (tipoOperacion.equals("PRESTAMO")) {
                    tiemposPorOperacion.get("PRESTAMO").add(tiempoRespuesta);
                } else if (tipoOperacion.equals("DEVOLUCION")) {
                    tiemposPorOperacion.get("DEVOLUCION").add(tiempoRespuesta);
                } else if (tipoOperacion.equals("RENOVACION")) {
                    tiemposPorOperacion.get("RENOVACION").add(tiempoRespuesta);
                }
            } else {
                System.err.println("  → ERROR: Timeout esperando respuesta");
                solicitudesFallidas++;
            }

        } catch (Exception e) {
            System.err.println("  → ERROR: " + e.getMessage());
            solicitudesFallidas++;
        }
    }

    //Carga peticiones desde archivo

    private List<String> cargarPeticiones(String rutaArchivo) throws IOException {
        Path path = Paths.get(rutaArchivo);
        if (!Files.exists(path)) {
            // Si no existe, crear archivo de ejemplo
            generarArchivoPeticionesEjemplo(rutaArchivo);
        }

        return Files.readAllLines(path);
    }

    // Genera archivo de peticiones de ejemplo

    private void generarArchivoPeticionesEjemplo(String rutaArchivo) throws IOException {
        List<String> peticiones = new ArrayList<>();
        Random random = new Random();

        String[] operaciones = {"PRESTAMO", "DEVOLUCION", "RENOVACION"};

        for (int i = 1; i <= 30; i++) {
            String operacion = operaciones[random.nextInt(operaciones.length)];
            String libro = "LIB" + (random.nextInt(1000) + 1);
            String usuario = "U" + (100 + i);

            peticiones.add(operacion + "|" + libro + "|" + usuario);
        }

        Files.write(Paths.get(rutaArchivo), peticiones);
        System.out.println("Archivo de peticiones generado: " + rutaArchivo);
    }


     //Genera reporte de métricas

    private void generarReporte() {
        System.out.println("Solicitudes procesadas: " + solicitudesProcesadas);
        System.out.println("Solicitudes fallidas: " + solicitudesFallidas);

        if (!tiemposRespuesta.isEmpty()) {
            double promedio = calcularPromedio(tiemposRespuesta);
            double desviacion = calcularDesviacionEstandar(tiemposRespuesta);
            long minimo = Collections.min(tiemposRespuesta);
            long maximo = Collections.max(tiemposRespuesta);

            System.out.println("\nTiempos de respuesta (TODAS las operaciones):");
            System.out.println("  Promedio: " + String.format("%.2f", promedio) + " ms");
            System.out.println("  Desv. estándar: " + String.format("%.2f", desviacion) + " ms");
            System.out.println("  Mínimo: " + minimo + " ms");
            System.out.println("  Máximo: " + maximo + " ms");

            // Reporte por tipo de operación
            System.out.println("\nDesglose por operación:");
            for (Map.Entry<String, List<Long>> entry : tiemposPorOperacion.entrySet()) {
                String operacion = entry.getKey();
                List<Long> tiempos = entry.getValue();

                if (!tiempos.isEmpty()) {
                    double prom = calcularPromedio(tiempos);
                    double desv = calcularDesviacionEstandar(tiempos);
                    System.out.println("  " + operacion + ":");
                    System.out.println("    Cantidad: " + tiempos.size());
                    System.out.println("    Promedio: " + String.format("%.2f", prom) + " ms");
                    System.out.println("    Desv. est.: " + String.format("%.2f", desv) + " ms");
                }
            }
        }

        // Guardar métricas en archivo CSV
        guardarMetricas();
    }

    //Guarda métricas en archivo CSV

    private void guardarMetricas() {
        try {
            String nombreArchivo = "metricas_" + idProceso + "_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                    ".csv";

            try (PrintWriter writer = new PrintWriter(new FileWriter(nombreArchivo))) {
                writer.println("Proceso,Sede,Solicitudes_Procesadas,Solicitudes_Fallidas,Tiempo_Promedio_ms,Desv_Estandar_ms");

                double promedio = tiemposRespuesta.isEmpty() ? 0 : calcularPromedio(tiemposRespuesta);
                double desviacion = tiemposRespuesta.isEmpty() ? 0 : calcularDesviacionEstandar(tiemposRespuesta);

                writer.println(idProceso + "," + sede + "," + solicitudesProcesadas + "," +
                        solicitudesFallidas + "," + String.format("%.2f", promedio) + "," +
                        String.format("%.2f", desviacion));

                // Detalle de cada solicitud
                writer.println("\nDetalle_Solicitudes");
                writer.println("Indice,Tiempo_Respuesta_ms");
                for (int i = 0; i < tiemposRespuesta.size(); i++) {
                    writer.println((i + 1) + "," + tiemposRespuesta.get(i));
                }
            }

            System.out.println("\nMétricas guardadas en: " + nombreArchivo);

        } catch (IOException e) {
            System.err.println("Error guardando métricas: " + e.getMessage());
        }
    }

    private double calcularPromedio(List<Long> valores) {
        return valores.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double calcularDesviacionEstandar(List<Long> valores) {
        double promedio = calcularPromedio(valores);
        double varianza = valores.stream()
                .mapToDouble(v -> Math.pow(v - promedio, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(varianza);
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Uso: java ProcesoSolicitante <id> <sede> <ip_gc> [archivo] [duracion_ms]");
            System.out.println("Ejemplo: java ProcesoSolicitante PS1 SEDE1 localhost peticiones.txt 120000");
            return;
        }

        String idProceso = args[0];
        String sede = args[1];
        String ipGC = args[2];
        String archivo = (args.length >= 4) ? args[3] : "peticiones_" + idProceso + ".txt";

        ProcesoSolicitante ps = new ProcesoSolicitante(idProceso, sede, ipGC);

        if (args.length >= 5) {
            // Modo con duración limitada (para experimentos)
            long duracion = Long.parseLong(args[4]);
            ps.procesarArchivo(archivo, duracion);
        } else {
            // Modo archivo completo
            ps.procesarArchivoCompleto(archivo);
        }
    }
}