# Notas: arranque con la caché AOT de Java 25

El plan (§10) pide generar la caché AOT de Java 25 en la imagen y medir el
arranque antes y después. Medido el 2026-10-29.

## Qué es

Al arrancar, la JVM lee, verifica y enlaza miles de clases (Spring, Tomcat,
Jackson…), y empieza a perfilar los métodos desde cero. Con la caché AOT
(JEP 483, 514 y 515) ese trabajo se hace una vez, en un **arranque de
entrenamiento** al construir la imagen, y se guarda en `app.aot`; los
arranques siguientes lo leen de ahí.

En el `Dockerfile`:

- La etapa de compilación **desempaqueta el jar** (`-Djarmode=tools …
  extract`) en `app.jar` + `lib/`: la caché necesita jars normales en el
  *classpath*, no los anidados de un jar de Spring Boot.
- La etapa final hace el entrenamiento: `java -XX:AOTCacheOutput=app.aot
  -Dspring.context.exit=onRefresh -jar app.jar`. Spring crea el contexto y
  sale. Sin base de datos: Flyway y los *workers* de análisis se apagan, y el
  *pool* de conexiones solo conecta al primer uso.
- El arranque normal añade `-XX:AOTCache=app.aot`.

La caché vale solo para la misma JVM y los mismos jars; por eso se genera en
la propia imagen. Si no sirve, la JVM arranca igual, sin ella. La CI
comprueba que sí sirve: con `-XX:AOTMode=on` la JVM se niega a arrancar si no
puede usarla (probado con un *classpath* distinto: `Unable to use AOT
cache`).

## Cómo se midió

- La aplicación completa con `docker compose`, PostgreSQL incluido.
  Portátil i7-1165G7, Docker Desktop (WSL 2).
- Cada medida: `docker compose stop app`, `start app` y esperar a que
  `/actuator/health/readiness` responda. Se anota la línea de Spring
  (`Started KopiApplication in … seconds`) y el tiempo hasta *ready* visto
  desde fuera.
- Cinco arranques sin límite de CPU, y tres con `cpus: 0.5`, la «máquina
  modesta» de `presupuesto-analisis.md`.

## Resultados

Sin límite de CPU:

- **Spring arrancado:** 4,8–5,8 s antes, **2,5–3,2 s** después.
- ***Ready* desde fuera:** 6,8–8,3 s antes, **4,2–5,1 s** después.

Con media CPU:

- **Spring arrancado:** 18,0–18,6 s antes, **7,8–8,9 s** después.
- ***Ready* desde fuera:** 22,2–22,8 s antes, **10,5–11,6 s** después.

Coste:

- **Imagen:** 445 MB antes, 508 MB después (`app.aot` ocupa 64 MB).
- **Construcción:** unos 9 s más por plataforma (el entrenamiento); más en
  ARM64, que en la *release* se construye emulado.

## Conclusiones

- **El arranque se reduce a la mitad**, y donde más se nota es en la máquina
  lenta: de 22 s a 11 s hasta poder jugar.
- Con media CPU la JVM elige el recolector Serial, y el entrenamiento se hizo
  con G1. La caché se usa igual (comprobado con `-XX:AOTMode=on`).
- Los avisos `[warning][aot]` del entrenamiento son esperables: *proxies*
  dinámicos y clases opcionales que no se pueden verificar sin sus
  dependencias (Log4j2, FreeMarker, Reactor). Esas clases simplemente no van
  a la caché.
- Nada cambia en el código ni en las pruebas: es solo la imagen. Quien
  ejecute el jar fuera de Docker arranca como antes.
