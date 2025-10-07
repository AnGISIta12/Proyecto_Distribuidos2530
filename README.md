comandos para correr las terminales

* terimanl 1: mvn exec:java -Dexec.mainClass="com.example.proyecto_distribuidos2530.actores.ActorDevolucion"


* terminal 2: mvn exec:java -Dexec.mainClass="com.example.proyecto_distribuidos2530.actores.ActorRenovacion"


* terminal 3: mvn exec:java -Dexec.mainClass="com.example.proyecto_distribuidos2530.carga.GestorCarga"


* terminal 4: mvn exec:java -Dexec.mainClass="com.example.proyecto_distribuidos2530.solicitante.ProcesoSolicitante" -Dexec.args="src/main/resources/peticiones.txt"
