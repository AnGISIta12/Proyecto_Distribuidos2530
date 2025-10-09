# 📚 Sistema Distribuido de Préstamo de Libros — Primera Entrega

---

## 🚀 Descripción General

El sistema implementa una versión distribuida del **préstamo de libros en la Biblioteca de la Universidad Ada Lovelace**.  
Cada operación (devolución, renovación o préstamo) se maneja por procesos independientes que se comunican mediante **ZeroMQ** bajo los patrones de **Request/Reply** y **Publish/Subscribe**.

### 🧩 Procesos Principales
| Proceso | Descripción |
|----------|--------------|
| **Proceso Solicitante (PS)** | Lee peticiones desde un archivo (`peticiones.txt`) y las envía al Gestor de Carga. |
| **Gestor de Carga (GC)** | Recibe solicitudes de los PS, responde a cada una y publica mensajes a los actores. |
| **Actores** | Escuchan los tópicos correspondientes (`devolucion`, `renovacion`) y procesan las operaciones. |
| **Libro (modelo)** | Representa los libros en la biblioteca (ya implementado por el grupo). |

---

## ⚙️ Tecnologías utilizadas

- **Java 17+**
- **ZeroMQ (librería jeromq)**
- **Maven** como herramienta de construcción
- **Arquitectura distribuida** con procesos independientes

---


