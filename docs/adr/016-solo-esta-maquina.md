# ADR-016: Solo responde a esta máquina: `Host` local y cambios de páginas locales

- **Estado:** aceptado
- **Fecha:** 2026-10-27

## Contexto

Kopi Chess es una aplicación local sin usuarios ni contraseñas: Compose
publica el puerto solo en `127.0.0.1` y nadie de la red llega a ella. Pero el
navegador de la persona sí, y con él **cualquier página que visite**:

- **Formularios de otros sitios.** Una página puede enviar un `POST` a
  `http://localhost:8080/games` (crear o importar partidas) sin que se note.
  No puede leer la respuesta, pero el cambio ya está hecho. Desde que se
  pueden borrar partidas, el daño posible es mayor.
- **DNS rebinding.** Una página en `evil.example` hace que su propio dominio
  resuelva a `127.0.0.1`. Para el navegador sigue siendo el mismo origen, así
  que puede **leer** la API (`/api/v1/games`), conocer los identificadores y
  borrar cada partida.

Además, ejecutado con `java -jar` o desde el IDE, Spring Boot escuchaba en
todas las interfaces (`0.0.0.0`): cualquiera en la red local llegaba a la
aplicación. El diseño (§15) pide escuchar en `localhost` por defecto.

Spring Security resolvería lo primero con *tokens* CSRF, pero añade una
dependencia grande, cambia todos los formularios y no protege del *rebinding*.

## Decisión

Un filtro propio, `LocalAccessFilter` en `kopi-bootstrap`, delante de todo
(páginas, API y Actuator):

- Solo responde si el `Host` es un nombre de esta máquina: `localhost`,
  `127.0.0.1` o `[::1]` por defecto, ampliable con `KOPI_ALLOWED_HOSTS`. Una
  página que use *rebinding* manda `Host: evil.example` y se rechaza.
- `POST`, `PUT`, `PATCH` y `DELETE` se rechazan si su `Origin` es otro sitio
  o, sin `Origin`, si `Sec-Fetch-Site` dice que los envió otro sitio. Sin
  ninguna de las dos cabeceras no es un navegador (`curl`, un *script*) y
  pasa.

Y `server.address` es `127.0.0.1` por defecto; la imagen Docker lo cambia a
`0.0.0.0` dentro de su contenedor (`KOPI_ADDRESS`), donde lo que decide quién
llega es el puerto publicado.

## Consecuencias

- Una página ajena ya no puede cambiar ni leer las partidas, sin *tokens* en
  los formularios ni dependencias nuevas.
- Quien quiera usar Kopi Chess con otro nombre (una IP de la red, un *proxy*)
  tiene que añadirlo a `KOPI_ALLOWED_HOSTS`. Una petición rechazada deja una
  línea en el *log* que lo dice.
- Desde el jar, `http://[::1]:8080` ya no conecta (escucha en IPv4);
  `localhost` y `127.0.0.1` sí.
- Si un día Kopi Chess tuviera usuarios o se publicara en una red, esto no
  basta: haría falta autenticación, y con ella Spring Security y CSRF. Se
  decidiría en un ADR nuevo.
