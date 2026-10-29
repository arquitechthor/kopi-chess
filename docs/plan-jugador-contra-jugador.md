# Plan: jugador contra jugador

- **Estado:** propuesta. No está comprometida ni tiene fecha; recoge cómo se
  haría para que, si se retoma, se empiece con las decisiones a la vista.
- **Fecha:** 2026-10-29, sobre la versión 1.0.0.
- **Alcance:** dos modos nuevos, que se entregan **juntos** en una sola
  versión:
  - **Local:** dos personas en la misma máquina, cada una con su nombre.
  - **En línea:** dos personas, cada una con su Kopi Chess en *localhost*,
    con una cuenta Kopi.

---

## 1. Supuestos

Son las reglas de partida; si alguno cambia, el plan cambia con él.

- **Partidas amistosas, basadas en el honor.** No es competitivo: sin Elo ni
  clasificación. La plataforma **no protege de trampas**; cada jugador tiene
  Stockfish en su PC y nada impide consultarlo.
- **Coste mínimo.** Nada encendido todo el mes: cada jugador pone su CPU
  (reglas, Stockfish, análisis) y AWS solo guarda y reparte jugadas, sin
  servidor propio.
- **Tiempo ilimitado.** Sin reloj. Una partida puede quedarse a medias y
  retomarse cuando sea, tanto si el rival está conectado como si no; mientras
  tanto aparece **en curso**, como hoy una partida contra un bot.
- **Cuenta Kopi obligatoria para el modo en línea**, la misma de las otras
  apps Kopi (Cognito). Además, cada persona crea un **id de Kopi**, público,
  para invitar y ser invitada **sin mostrar nunca su correo**.
- **Las jugadas se validan siempre en local**, con el dominio de cada app:
  al hacer una jugada y al recibir la del rival. AWS no conoce las reglas del
  ajedrez.
- **El modo local no necesita cuenta** ni conexión.

---

## 2. Cómo encaja

```
 PC de Ana                              AWS (backend Kopi)                  PC de Luis
┌──────────────────────┐  HTTPS/wss   ┌──────────────────────────────┐   ┌──────────────────────┐
│ Kopi Chess (local)   │  salientes,  │ API Gateway (HTTP + WebSocket)│   │ Kopi Chess (local)   │
│ reglas, Stockfish,   │ ───────────► │ Lambda (relé, sin reglas)     │ ◄─│ reglas, Stockfish,   │
│ análisis, PostgreSQL │ ◄─────────── │ DynamoDB: ids, partidas,      │ ─►│ análisis, PostgreSQL │
│ solo en 127.0.0.1    │  token Kopi  │ jugadas, invitaciones         │   │ solo en 127.0.0.1    │
└──────────────────────┘              │ Cognito (cuenta Kopi)         │   └──────────────────────┘
                                      └──────────────────────────────┘
```

- **Solo conexiones salientes** desde cada PC: sin abrir puertos ni tocar el
  router.
- **ADR-016 se mantiene:** la interfaz sigue respondiendo solo a esta
  máquina. Lo nuevo es que la app también *habla* con el backend Kopi.
- **Cada PC guarda su copia** de la partida en su PostgreSQL: historial,
  revisión y análisis funcionan igual que hoy, sin conexión.
- **El relé** ordena y reparte jugadas; no decide si son legales.

---

## 3. El id de Kopi (común a todas las apps Kopi)

Una pieza del **backend compartido de Kopi**, no de Kopi Chess, para que
otras apps la puedan usar después.

- **Qué es:** un nombre público y único, asociado al `sub` de Cognito.
  - De 3 a 20 caracteres: letras, números y `_`.
  - Sin distinguir mayúsculas al comparar (`Ana_92` y `ana_92` son el
    mismo), pero se muestra como se escribió.
- **Dónde vive:** una tabla de DynamoDB (`KopiIds`), con una escritura
  condicional para garantizar que es único. **No** en un alias de Cognito: los
  alias de un User Pool ya creado no se pueden cambiar.
- **Operaciones** (rutas del API Gateway compartido, con el *authorizer* de
  Cognito):
  - Reservar el mío, si aún no tengo.
  - Consultar el mío.
  - Comprobar si uno existe (para invitar), sin devolver nada más que eso.
- **Privacidad:** el correo nunca sale del backend; entre jugadores solo
  viaja el id.
- **Cambiarlo:** fuera del primer alcance. Si se permite más adelante, las
  partidas guardan el `sub` y muestran el id vigente.
- **Borrar la cuenta:** libera el id (y entra en el procedimiento de borrado
  de cuenta que ya tienen las apps Kopi).

---

## 4. El relé en AWS

Lambdas pequeñas en el API Gateway compartido (`api.kopitools.link`), con el
*authorizer* de Cognito. **No contienen reglas de ajedrez.**

### Datos (DynamoDB bajo demanda)

- **`PvpGames`:** una fila por partida.
  - Jugadores (`sub` de cada color), estado (`INVITED`, `ACTIVE`,
    `FINISHED`, `DECLINED`), posición inicial, número de la última jugada,
    resultado y forma de terminar.
  - TTL: las terminadas se borran a los **30 días** (cada PC guarda la suya);
    las invitaciones no respondidas, también a los 30 días.
- **`PvpMoves`:** las jugadas, por partida y número (`ply`), con la jugada en
  UCI y el hash Zobrist de la posición resultante (§6).

### Operaciones

- **Invitar** a un id de Kopi, eligiendo color: blancas, negras o al azar.
- **Ver mis invitaciones** pendientes, **aceptar** o **rechazar**.
- **Listar mis partidas** en línea y **traer una partida** (sus jugadas desde
  un número dado: así se retoma).
- **Enviar una jugada** con su número: el relé solo comprueba que es mi
  partida, que me toca por color y que ese número es el siguiente. Una
  escritura condicional garantiza que **cada número se acepta una vez**.
- **Abandonar**, **ofrecer tablas** y **aceptar tablas**.
- **Anotar el final** por mate, ahogado o regla de tablas: lo comunica la
  app que hace la última jugada, junto con ella; el relé lo apunta sin
  comprobarlo, y la app del rival lo verifica al recibir esa jugada (§6).

### Tiempo real, solo si los dos están conectados

- Un **WebSocket** (API Gateway) por app abierta: cuando llega una jugada o
  una invitación, el relé avisa a quien esté conectado.
- Si no lo está, no pasa nada: lo recibe la próxima vez que abra la app. **Sin
  correos ni notificaciones *push*** (coste y complejidad).

---

## 5. Cambios en Kopi Chess, por anillos

### Dominio (`kopi-domain`)

- **Un jugador por color**, en lugar de `humanColor` + `botProfile`:

  ```java
  sealed interface Player permits Player.Bot, Player.Local, Player.Remote {
      record Bot(BotProfile profile) implements Player {}
      record Local(String name) implements Player {}        // modo local, o "yo" en línea
      record Remote(String kopiId) implements Player {}     // el rival en línea
  }
  ```

  Las partidas contra un bot siguen igual (`Local` contra `Bot`); las
  importadas, como hoy.
- **Tablas de mutuo acuerdo** como forma de terminar (hoy no existe: solo
  mate, ahogado, material insuficiente, repetición, 50 jugadas y abandono).
- **Quién puede mover:** en el modo local, cualquiera de las dos personas en
  su turno; en línea, solo `Local` en su turno, y `Remote` llega por el relé.
- *Perft*, SAN y las reglas no cambian.

### Aplicación (`kopi-application`)

- **Casos de uso nuevos:** crear partida local, invitar, aceptar o rechazar,
  recibir la jugada del rival, ofrecer y aceptar tablas, sincronizar.
- **Puertos nuevos:**
  - `KopiAccount`: quién soy (mi `sub` y mi id de Kopi), reservar el id.
  - `PvpRelay`: invitar, enviar jugada, traer jugadas, abandonar, tablas.
- **Recibir una jugada** = validarla con el dominio, como si la hiciera yo. Si
  no es legal o el hash no coincide, la partida se marca **desincronizada**
  (§6).

### Adaptadores

- **Web:**
  - Nueva partida: «Contra un bot», «Dos personas en este equipo» (dos
    nombres) y «Contra un amigo (en línea)».
  - Modo local: el tablero se gira en cada turno.
  - Una bandeja de invitaciones; la jugada del rival llega a la página por
    SSE, como ya llega la del bot.
  - Historial: el nombre o el id del rival; el balance contra cada bot no
    cuenta estas partidas.
  - «Entrar con Kopi» y la pantalla para crear el id.
- **Persistencia:** una migración de Flyway: los jugadores de cada color, el
  id de la partida en el relé, la última jugada sincronizada y la forma de
  terminar nueva. Las partidas existentes pasan a `Local` contra `Bot`.
- **Nuevo `kopi-adapter-relay`:** implementa `PvpRelay` y `KopiAccount` con
  HTTP y WebSocket hacia el backend Kopi.
- **Seguridad (`kopi-bootstrap`):** Spring Security `oauth2Login` con un
  **cliente de aplicación nuevo** en el User Pool de Kopi (*managed login*,
  PKCE, callback a `http://localhost:8080/…`). Los *tokens* se guardan en la
  base de datos local. El cliente que usan las otras apps no se toca.
- **REST:** las mismas operaciones en `/api/v1`, para scripts y una futura
  app Android.

---

## 6. Validación y coherencia

- **Cada app valida todo** con su dominio: la jugada propia antes de
  enviarla, la del rival al recibirla.
- **Orden:** el número de jugada (`ply`) lo fija el relé, que acepta cada
  número una sola vez. Si dos PCs con la misma cuenta mueven a la vez, gana el
  primero y el otro recibe la jugada aceptada al sincronizar.
- **Comprobación cruzada:** cada jugada viaja con el hash Zobrist de la
  posición resultante (el dominio ya lo calcula). Si al aplicarla el hash no
  coincide, o la jugada es ilegal, la partida se marca **desincronizada**: se
  muestra un aviso y se puede abandonar o archivar. No debería ocurrir entre
  dos versiones honestas de la app; si ocurre, es un fallo o una versión
  incompatible.
- **Versión del protocolo** en cada mensaje desde el principio: una app no
  acepta mensajes de una versión que no entiende y lo dice.

---

## 7. Flujos

- **Primera vez:** «Entrar con Kopi» → Cognito → crear mi id de Kopi.
- **Invitar:** escribo el id del amigo y el color → el amigo ve la invitación
  al abrir su app → la acepta → la partida aparece en curso para los dos.
- **Jugar con los dos conectados:** muevo → mi app valida y envía → el relé
  la acepta y avisa al rival → su app la valida y la muestra.
- **Jugar por correspondencia:** muevo y cierro la app → el rival la recibe
  cuando abre la suya, aunque sea días después.
- **Retomar:** al abrir una partida en línea, la app trae del relé las
  jugadas que le falten.
- **Terminar:** mate o ahogado (lo ven las dos apps), abandono, o tablas
  ofrecidas y aceptadas. Al terminar, cada uno puede analizarla en su PC.
- **Una partida que se enfría:** cada jugador puede **archivarla** en su
  lista; no se borra para el otro. (Abandonar sí la termina para los dos.)
- **Modo local:** «Dos personas en este equipo» → dos nombres → se juega en
  el mismo tablero, girándolo en cada turno → historial, PGN y análisis como
  cualquier otra partida.

---

## 8. Seguridad y privacidad

- **La interfaz sigue siendo local** (ADR-016). Lo que sale a Internet son
  llamadas con el *token* de la cuenta Kopi.
- **El correo no se muestra nunca:** entre jugadores solo viaja el id.
- **El relé solo deja tocar una partida a sus dos jugadores**, y solo mover
  en el turno propio.
- **Límites en el relé** (invitaciones y jugadas por minuto) para que nadie
  dispare el coste.
- **Trampas:** fuera de alcance por diseño (§1). Partidas entre conocidos.

---

## 9. Costes

Prácticamente **0 $/mes** con el uso esperado, como el resto del backend
Kopi:

- **Lambda, DynamoDB bajo demanda y Cognito** (hasta 10.000 usuarios activos
  al mes): dentro de las capas gratuitas.
- **API Gateway:** HTTP por petición y WebSocket por mensaje y minuto de
  conexión. Una partida son decenas de mensajes: céntimos.
- **Lo caro, Stockfish, lo pone cada jugador** en su PC.
- Sin servidor encendido, sin base de datos gestionada, sin balanceador.

---

## 10. Fases

Se entregan juntas en una versión, pero se construyen en este orden: cada
fase deja algo que funciona y se prueba.

### Fase A: el dominio con personas, y el modo local

- [ ] ADR-018: jugadores por color (`Player`) y tablas de mutuo acuerdo.
- [ ] `Player` en el dominio; `Game` con un jugador por color; tablas por
      acuerdo como terminación. Pruebas unitarias; *perft* intacto.
- [ ] Migración de Flyway; las partidas existentes, `Local` contra `Bot`.
- [ ] Caso de uso y páginas de «Dos personas en este equipo» (nombres,
      tablero que gira, historial, PGN con los nombres).
- [ ] Prueba E2E: una partida local completa hasta la revisión.

**Aceptación:** dos personas juegan en un PC una partida completa y la
analizan; las partidas contra bots y las importadas siguen igual.

### Fase B: cuenta Kopi e id de Kopi

- [ ] ADR-019: el id de Kopi, común a las apps Kopi.
- [ ] Backend Kopi: tabla `KopiIds` y sus rutas (reservar, consultar,
      comprobar), con el *authorizer* de Cognito.
- [ ] Cliente de aplicación nuevo en el User Pool, con callback a
      `localhost`.
- [ ] Kopi Chess: «Entrar con Kopi» (`oauth2Login`), *tokens* guardados en
      local, pantalla para crear el id. Puerto `KopiAccount`.

**Aceptación:** entro con mi cuenta Kopi desde mi Kopi Chess local y reservo
un id que nadie más puede tener.

### Fase C: el relé

- [ ] ADR-020: relé de partidas en AWS sin reglas, validación local, y su
      relación con ADR-016.
- [ ] Tablas `PvpGames` y `PvpMoves` con TTL.
- [ ] Lambdas: invitar, invitaciones, aceptar, rechazar, listar, traer,
      mover (escritura condicional por número), abandonar, tablas.
- [ ] WebSocket para avisar a quien esté conectado.
- [ ] Límites por usuario. Pruebas de las Lambdas.

**Aceptación:** con dos cuentas de prueba y `curl`, se juega una partida de
principio a fin; una jugada con número repetido o fuera de turno se rechaza.

### Fase D: Kopi Chess en línea

- [ ] `kopi-adapter-relay` (`PvpRelay`), con la versión del protocolo.
- [ ] Casos de uso: invitar, aceptar, recibir y validar, sincronizar al
      abrir, tablas, abandonar, archivar; marca de partida desincronizada.
- [ ] Páginas: «Contra un amigo», bandeja de invitaciones, jugada del rival
      por SSE.
- [ ] API REST equivalente.
- [ ] Prueba E2E con dos navegadores y dos cuentas de prueba contra un
      entorno del relé (o un relé falso en las pruebas).

**Aceptación:** dos personas en dos PCs juegan una partida con los dos
conectados y otra por correspondencia (cerrando la app entre jugadas), y
cada una la analiza en su PC.

### Fase E: publicación

- [ ] Documentación: README, CONTRIBUTING (cómo probar sin cuenta real),
      diseño y este plan actualizado.
- [ ] Changelog y versión (probablemente 1.1.0: no rompe nada de lo
      anterior).

---

## 11. Riesgos

- **El backend es compartido con otras apps Kopi.**
  - **Señal:** un cambio del relé o de las rutas del id afecta a otra app.
  - **Acción:** rutas y tablas propias con prefijo, desplegadas por separado;
    el cliente de aplicación nuevo, aparte del que usan las demás.
- **Versiones distintas de Kopi Chess en cada PC.**
  - **Señal:** partidas desincronizadas.
  - **Acción:** versión del protocolo en cada mensaje y aviso claro de
    «actualiza Kopi Chess».
- **Coste por abuso.**
  - **Señal:** picos en API Gateway o DynamoDB.
  - **Acción:** límites por usuario y alarma de facturación.
- **Partidas abandonadas para siempre.**
  - **Señal:** partidas en curso que nadie mueve en meses.
  - **Acción:** archivar en local; si hace falta, un TTL largo también para
    las activas sin movimiento.

---

## 12. Fuera de alcance

- Reloj y controles de tiempo (ver [`ideas-futuro.md`](ideas-futuro.md)).
- Elo, clasificaciones y emparejamiento con desconocidos.
- Protección contra trampas.
- Correos o notificaciones *push* de jugadas e invitaciones.
- Chat, espectadores y partidas públicas.
- Cambiar el id de Kopi.
