# 📚 Sistema Distribuido de Préstamo de Libros - Segunda Entrega

## 🎯 Biblioteca Distribuida

---

## 📋 Descripción General

Sistema distribuido para gestión de préstamos de libros implementado en **Java** con **ZeroMQ**, operando en dos sedes con tolerancia a fallas y réplicas de base de datos.

### ✨ Características Principales

- ✅ **Dos sedes operativas** (SEDE1 y SEDE2)
- ✅ **Tolerancia a fallas** del Gestor de Almacenamiento y BD
- ✅ **Réplicas primaria y secundaria** con sincronización asíncrona
- ✅ **Patrones de comunicación**:
  - Pub/Sub para devoluciones y renovaciones (asíncrono)
  - Push/Pull para préstamos (síncrono)
  - Request/Reply entre componentes

---

## 🏗️ Arquitectura del Sistema

### Componentes por Sede

```
SEDE 1 (Máquina 1)                    SEDE 2 (Máquina 2)
├── Gestor de Carga                   ├── Gestor de Carga
├── Gestor de Almacenamiento (P)      ├── Gestor de Almacenamiento (S)
├── BD Primaria                       ├── BD Primaria
├── BD Secundaria                     ├── BD Secundaria
├── Actor Préstamo                    ├── Actor Préstamo
├── Actor Devolución                  ├── Actor Devolución
└── Actor Renovación                  └── Actor Renovación

CLIENTES (Máquina 3)
└── Procesos Solicitantes (PS1, PS2, PS3, ...)
```

### Puertos Utilizados

| Componente | Puerto | Protocolo | Descripción |
|------------|--------|-----------|-------------|
| Gestor Carga → PS | 5555 | REP | Recibe solicitudes |
| Gestor Carga → Actores | 5556 | PUB | Publica devoluciones/renovaciones |
| Gestor Carga → Actor Préstamo | 5557 | PUSH | Envía tareas de préstamo |
| Gestor Almacenamiento | 5558 | REP | Operaciones de BD |
| Health Check | 5559 | REP | Verificación de estado |
| Replicación | 5560 | PUB | Sincronización de réplicas |

---

## 🚀 Instalación y Configuración

### Requisitos

- **Java 21** (JDK 21+)
- **Maven 3.8+**
- **ZeroMQ** (jeromq 0.5.3)
- **3 computadores** en red o 3 máquinas virtuales

### Compilación

```bash
# Clonar repositorio
git clone <url-repositorio>
cd proyecto_distribuidos2530

# Compilar proyecto
mvn clean package

# Verificar compilación
ls target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar
```

### Configuración de Red

Antes de ejecutar, actualizar las IPs en los scripts:

```bash
# En ejecutar_clientes.sh
IP_SEDE1="192.168.1.100"  # IP de Máquina 1
IP_SEDE2="192.168.1.101"  # IP de Máquina 2
```

---

## 🎮 Ejecución del Sistema

### Opción 1: Ejecución Automática 

#### Máquina 1 (SEDE 1):
```bash
chmod +x ejecutar_sede1.sh
./ejecutar_sede1.sh
```

#### Máquina 2 (SEDE 2):
```bash
chmod +x ejecutar_sede2.sh
./ejecutar_sede2.sh
```

#### Máquina 3 (Clientes):
```bash
chmod +x ejecutar_clientes.sh
./ejecutar_clientes.sh
```

### Opción 2: Ejecución Manual

#### Paso 1: Iniciar Gestores de Almacenamiento

**Máquina 1 (SEDE1 - Primario):**
```bash
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.almacenamiento.GestorAlmcto \
  SEDE1 true SEDE2 192.168.1.101
```

**Máquina 2 (SEDE2 - Secundario):**
```bash
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.almacenamiento.GestorAlmcto \
  SEDE2 false SEDE1 192.168.1.100
```

#### Paso 2: Iniciar Gestores de Carga

**Máquina 1:**
```bash
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.carga.GestorCarga SEDE1
```

**Máquina 2:**
```bash
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.carga.GestorCarga SEDE2
```

#### Paso 3: Iniciar Actores

**En ambas máquinas:**
```bash
# Actor Préstamo
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.actores.ActorPrestamo \
  SEDE1 localhost 192.168.1.101

# Actor Devolución
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.actores.ActorDevolucion

# Actor Renovación
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.actores.ActorRenovacion
```

#### Paso 4: Iniciar Procesos Solicitantes

**Máquina 3:**
```bash
# PS para SEDE1
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.solicitante.ProcesoSolicitante \
  PS1 SEDE1 192.168.1.100 peticiones_ps1.txt

# PS para SEDE2
java -cp target/proyecto_distribuidos2530-1.0-SNAPSHOT.jar \
  com.example.proyecto_distribuidos2530.solicitante.ProcesoSolicitante \
  PS2 SEDE2 192.168.1.101 peticiones_ps2.txt
```

---

## 📁 Estructura del Proyecto

```
proyecto_distribuidos2530/
├── src/main/java/com/example/proyecto_distribuidos2530/
│   ├── actores/
│   │   ├── ActorDevolucion.java
│   │   ├── ActorPrestamo.java
│   │   └── ActorRenovacion.java
│   ├── almacenamiento/
│   │   └── GestorAlmcto.java
│   ├── basedatos/
│   │   └── Database.java
│   ├── carga/
│   │   └── GestorCarga.java
│   ├── modelo/
│   │   └── Libro.java
│   └── solicitante/
│       └── ProcesoSolicitante.java
├── src/main/resources/
│   ├── peticiones.txt
│   └── libros.csv
├── pom.xml
└── README.md
```

---

## 🔧 Formato de Peticiones

Archivo `peticiones.txt` (formato):
```
PRESTAMO|LIB123|U100
DEVOLUCION|LIB456|U101
RENOVACION|LIB789|U102
```

Campos:
- **OPERACION**: PRESTAMO, DEVOLUCION, RENOVACION
- **CODIGO_LIBRO**: Identificador del libro (ej: LIB1, LIB2, ...)
- **USUARIO**: ID del usuario (ej: U100, U101, ...)

---

## 📈 Interpretación de Resultados

### Métricas Clave

1. **Tiempo de respuesta**: Menor es mejor
2. **Desviación estándar**: Menor indica más consistencia
3. **Solicitudes procesadas**: Mayor es mejor
4. **Tasa de éxito**: Debe ser cercana al 100%

### Comparación de Diseños

El informe debe comparar:

- **Opción A**: Gestores seriales vs multihilos
- **Opción B**: Comunicaciones asíncronas vs síncronas

**Preguntas a responder:**
- ¿Cuál diseño es más escalable?
- ¿Cómo afecta la carga al tiempo de respuesta?
- ¿Hay cuellos de botella identificables?

---

## 🐛 Solución de Problemas

### Error: "Connection refused"
- Verificar que todos los servicios estén iniciados
- Verificar IPs y puertos en la configuración
- Revisar firewall/antivirus

### Error: "Address already in use"
- Puerto ocupado por otra aplicación
- Detener procesos anteriores: `./detener.sh`

### Bases de datos corruptas
- Limpiar y reiniciar: `./limpiar_bd.sh`
- Reiniciar todos los componentes

### Logs útiles
```bash
# Ver procesos activos
ps aux | grep java

# Ver puertos en uso
netstat -tulpn | grep 555

# Verificar conectividad
telnet <ip_sede> 5555
```

---

## 📝 Entregables

### Segunda Entrega incluye:

1. **Código fuente** (archivo .zip)
2. **README** con instrucciones de ejecución
3. **Video** (máx 10 minutos) mostrando:
   - Distribución de componentes
   - Librerías y patrones usados
   - Tratamiento de fallas
   - Generación de carga
4. **Informe** (máx 5 páginas) con:
   - Especificaciones HW/SW
   - Resultados de experimentos
   - Tablas y gráficos
   - Análisis de resultados

---

## 👥 Equipo de Desarrollo

- **Integrante 1**: [Nombre]
- **Integrante 2**: [Nombre]
- **Integrante 3**: [Nombre]

---

## 📚 Referencias

- [ZeroMQ Guide](https://zguide.zeromq.org/)
- [Documentación del proyecto](Biblioteca2025-30.pdf)
- [Maven Central - JeroMQ](https://mvnrepository.com/artifact/org.zeromq/jeromq)

---

## 📄 Licencia

Proyecto académico - Pontificia Universidad Javeriana - 2025-30
