@echo off
REM Script de prueba local para Windows - Sistema de Préstamo de Libros
REM Ejecuta todos los componentes en localhost

setlocal enabledelayedexpansion

set JAR=target\proyecto_distribuidos2530-1.0-SNAPSHOT-jar-with-dependencies.jar

echo ==================================================
echo   PRUEBA LOCAL - Sistema Distribuido
echo   Ejecutando en localhost (Windows)
echo ==================================================
echo.

REM Verificar JAR
if not exist "%JAR%" (
    echo ERROR: JAR no encontrado
    echo Ejecuta: mvn clean package
    pause
    exit /b 1
)

REM Limpiar procesos anteriores
echo Limpiando procesos anteriores...
taskkill /F /FI "WINDOWTITLE eq GestorAlmacenamiento*" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq GestorCarga*" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq ActorDevolucion*" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq ActorRenovacion*" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq ActorPrestamo*" >nul 2>&1
timeout /t 2 >nul

REM Crear directorio para logs
if not exist logs mkdir logs
del /Q logs\*.log >nul 2>&1

echo Iniciando componentes...
echo.

REM 0. SEDE2 Respaldo (para pruebas de failover)
echo [0/7] Iniciando SEDE2 (Respaldo)...
start "SEDE2-Respaldo" cmd /k "java -cp %JAR% com.example.proyecto_distribuidos2530.almacenamiento.GestorAlmctoSede2 > logs\sede2.log 2>&1"
timeout /t 2 >nul
echo    OK - Ventana abierta (mantener ejecutando para failover)
echo.

REM 1. Gestor de Almacenamiento
echo [1/7] Iniciando GestorAlmacenamiento (SEDE1)...
start "GestorAlmacenamiento" cmd /k "java -cp %JAR% com.example.proyecto_distribuidos2530.almacenamiento.GestorAlmcto SEDE1 true > logs\almacenamiento.log 2>&1"
timeout /t 3 >nul
echo    OK - Ventana abierta

REM 2. Gestor de Carga
echo [2/7] Iniciando GestorCarga...
start "GestorCarga" cmd /k "java -cp %JAR% com.example.proyecto_distribuidos2530.carga.GestorCarga SEDE1 > logs\carga.log 2>&1"
timeout /t 3 >nul
echo    OK - Ventana abierta

REM 3. Actor Devolución
echo [3/7] Iniciando ActorDevolucion...
start "ActorDevolucion" cmd /k "java -cp %JAR% com.example.proyecto_distribuidos2530.actores.ActorDevolucion SEDE1 localhost localhost > logs\devolucion.log 2>&1"
timeout /t 1 >nul
echo    OK - Ventana abierta

REM 4. Actor Renovación
echo [4/7] Iniciando ActorRenovacion...
start "ActorRenovacion" cmd /k "java -cp %JAR% com.example.proyecto_distribuidos2530.actores.ActorRenovacion SEDE1 localhost localhost > logs\renovacion.log 2>&1"
timeout /t 1 >nul
echo    OK - Ventana abierta

REM 5. Actor Préstamo
echo [5/7] Iniciando ActorPrestamo...
start "ActorPrestamo" cmd /k "java -cp %JAR% com.example.proyecto_distribuidos2530.actores.ActorPrestamo SEDE1 localhost localhost > logs\prestamo.log 2>&1"
timeout /t 2 >nul
echo    OK - Ventana abierta

echo.
echo Esperando estabilizacion (3 segundos)...
timeout /t 3 >nul

REM 6. Proceso Solicitante
echo [6/7] Iniciando ProcesoSolicitante...
echo.
echo ==================================================
echo   ENVIANDO PETICIONES (100 peticiones)
echo ==================================================
echo.

start "ProcesoSolicitante" cmd /k "java -cp %JAR% com.example.proyecto_distribuidos2530.solicitante.ProcesoSolicitante PS1 SEDE1 localhost src\main\resources\peticiones.txt"

echo.
echo ==================================================
echo   COMPONENTES INICIADOS
echo ==================================================
echo.
echo Se han abierto 7 ventanas de terminal:
echo   0. SEDE2-Respaldo (puerto 6558/6559)
echo   1. GestorAlmacenamiento SEDE1 (puerto 5558/5559)
echo   2. GestorCarga
echo   3. ActorDevolucion
echo   4. ActorRenovacion
echo   5. ActorPrestamo
echo   6. ProcesoSolicitante
echo.
echo IMPORTANTE:
echo - Observa cada ventana para ver los logs en tiempo real
echo - El ProcesoSolicitante mostrara el resumen al finalizar
echo - Para detener: cierra cada ventana o ejecuta detener_procesos.bat
echo.
echo PRUEBA DE FAILOVER:
echo - Espera unos segundos a que se procesen 10-20 peticiones
echo - Cierra la ventana 1 (GestorAlmacenamiento SEDE1)
echo - Observa los mensajes [FAILOVER-*] en ventanas 3, 4 y 5
echo - El sistema cambiara automaticamente a SEDE2
echo - Las peticiones restantes se procesaran via SEDE2
echo.
echo Logs guardados en: logs\
echo.

pause