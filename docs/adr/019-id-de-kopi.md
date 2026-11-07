# ADR-019: El id de Kopi y la cuenta Kopi en Kopi Chess

- **Estado:** aceptado
- **Fecha:** 2026-11-07

## Contexto

El modo en línea ([plan](../plan-jugador-contra-jugador.md), §3 y fase B)
necesita que dos personas se encuentren sin compartir su correo, y que cada
Kopi Chess, que corre en *localhost*, demuestre ante el backend Kopi quién es
su dueño.

El backend Kopi (Cognito, API Gateway, Lambda y DynamoDB, en el repositorio
`kopi-media-admin`) ya lo comparten varias apps. Los *tokens* de cada app
**solo deben abrir lo que esa app necesita**: un *token* guardado en la base
de datos local de Kopi Chess no tiene por qué leer las notas o las contraseñas
de Kopi Tools, ni un *token* de Kopi Tools mover piezas. Las webs de estudio
ya siguen ese patrón (cliente de aplicación y *authorizer* propios).

El repositorio de Kopi Chess es público: nada de lo que lleve puede ser un
secreto.

## Decisión

- **Un cliente de aplicación propio, `kopi-chess-client`,** en el User Pool
  de Kopi:
  - Público, **sin secreto**, con *Authorization Code* + PKCE y la página de
    login gestionada de Cognito. La contraseña nunca pasa por Kopi Chess.
  - Vuelta a `http://localhost:8080/login/oauth2/code/kopi` (la ruta por
    defecto de `oauth2Login` de Spring Security con el registro `kopi`).
  - *Scopes* `openid` y `email`; *access token* de 60 min y *refresh token* de
    30 días. Kopi Chess guarda los *tokens* en su base de datos local y
    renueva el de acceso con el de refresco.
- **Un *authorizer* propio, `kopi-chess-authorizer`,** con audiencia solo
  ese cliente. Protege las rutas `/kopi-id*` y `/chess/*`, y nada más. Las
  rutas del resto de apps no aceptan estos *tokens*, ni estas rutas los suyos.
- **La cuenta tiene que estar aprobada** (grupo `USERS` o `ADMIN`), como en
  el resto del backend. Si no, `403`.
- **El id de Kopi:**
  - De 3 a 20 caracteres: letras sin tildes, números y `_`. Único sin
    distinguir mayúsculas; se muestra como se escribió.
  - Algunos nombres reservados (`admin`, `kopi`, `soporte`…).
  - Se reserva una vez y **no se cambia** (primer alcance).
  - Vive en la tabla `kopi_ids` del backend, con dos ítems por id escritos en
    una sola transacción: `ID#<id en minúsculas>` → `sub`, y `SUB#<sub>` →
    id. Así la unicidad se garantiza en los dos sentidos (un id por cuenta, una
    cuenta por id) sin GSI. No es un alias de Cognito: los alias de un User
    Pool ya creado no se pueden activar.
  - Rutas: `GET /kopi-id` (el mío), `PUT /kopi-id` (reservarlo) y
    `GET /kopi-id/{kopiId}` (si existe, para invitar). Detalle en
    [`protocolo-rele.md`](../protocolo-rele.md).
- **Borrar la cuenta Kopi libera el id** y borra las partidas en línea del
  relé (`rejectUser`, en `kopi-media-admin`).

## Consecuencias

- Kopi Chess puede guardar *tokens* en local sin abrir el resto de Kopi: si
  se filtran, solo dan acceso a su id y a sus partidas en línea.
- El id es «común a todas las apps Kopi», pero hoy solo lo protege el
  *authorizer* de Kopi Chess. Si otra app lo necesita, se añade su cliente a
  la audiencia de un *authorizer* para `/kopi-id*`, y se decide en su ADR si
  puede ver también `/chess/*`.
- Comprobar si un id existe deja enumerar ids. Es aceptable: son públicos por
  diseño y no llevan a ningún dato. Los límites por usuario (ADR-020) frenan
  un barrido.
- La app Android (plan, §11) necesitará otra URL de vuelta, con el esquema
  de la app. Se añadirá al mismo cliente o a uno nuevo en su ADR.
- La creación en AWS la hace `documentation/scripts/bootstrap-chess-relay.ps1`
  en `kopi-media-admin`, que imprime el dominio de login y el Client ID que
  Kopi Chess pone en su configuración.
