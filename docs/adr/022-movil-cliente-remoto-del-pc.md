# ADR-022: El móvil como cliente remoto del PC

- **Estado:** aceptado
- **Fecha:** 2026-11-07

## Contexto

La app Android ([plan](../plan-app-android.md), fase F) sirve para jugar
desde el móvil contra los bots del propio PC. Kopi Chess solo atiende a su
propio equipo ([ADR-016](016-solo-esta-maquina.md)): abrirlo a la red de casa
obligaría a añadir autenticación y a tocar el router, y no serviría fuera de
casa.

El relé ya tiene un canal entre dispositivos de la misma cuenta Kopi
(`protocolo-rele.md` §10): el PC se conecta como `server`, el móvil como
`device`, y el relé reenvía mensajes entre los dos sin guardarlos ni mirar
dentro. La app tiene su propio cliente de Cognito (`kopi-chess-mobile-client`,
§8).

## Decisión

- **El móvil es un cliente más de los casos de uso del PC,** como las páginas
  web y la API REST. No sabe de ajedrez: dibuja lo que el PC le dice
  (posición, jugadas legales, estado) y envía la jugada elegida.
- **«Modo servidor» en el PC,** apagado por defecto. Se enciende en la página
  de cuenta, con la cuenta Kopi con la que se entró. **Se recuerda al
  reiniciar** (decisión D7) y, mientras está encendido, todas las páginas lo
  avisan. Con él encendido, el PC mantiene una conexión saliente
  `role=server` con el relé.
- **Lista blanca de operaciones:** el PC solo atiende `hello`, `bots`,
  `games`, `new`, `game`, `move` y `resign`, todas sobre partidas contra un
  bot. Cualquier otra responde `NOT_ALLOWED`: desde el móvil no se borra
  nada, ni se cambia la configuración, ni se ve el historial (decisión D2).
- **Protocolo remoto propio,** dentro del `payload` del relé y con su propia
  versión (`"v": 1`), en [`protocolo-remoto.md`](../protocolo-remoto.md). El
  relé no lo mira, y así el PC y la app pueden avisar de que hay que
  actualizar uno de los dos.
- **Un adaptador nuevo, `kopi-adapter-remote`,** traduce los mensajes a los
  casos de uso. Sus respuestas tienen la misma forma que las de la API REST,
  pero con sus propios `record`: los adaptadores no se conocen entre sí
  (ArchUnit, ADR-001).
- **La app vive en este repositorio** (`android-app/`) y lleva la misma
  versión que Kopi Chess: se etiquetan y se publican juntos (D6). Es
  software libre, GPL-3.0-or-later como el resto, gratis y sin anuncios
  (D8).

## Consecuencias

- ADR-016 sigue en pie: la interfaz web sigue siendo solo local, y la
  conexión con el relé es saliente.
- Mientras el modo servidor está encendido, quien tenga la cuenta Kopi puede
  jugar contra los bots de ese PC desde su móvil. Por eso está apagado por
  defecto, se avisa en todas las páginas y la lista blanca es corta.
- El PC tiene que estar encendido y con Kopi Chess abierto: fuera de casa,
  lo normal es que no lo esté, y la app debe decirlo con claridad.
- Jugar en línea contra un amigo desde el móvil queda para después de la
  primera versión (D3): se haría por el mismo canal, con el PC haciendo de
  cliente del relé.
