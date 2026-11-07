/**
 * The online page: when the relay brings news (an invitation, an answer, a
 * friend's move), the server says `changed` over Server-Sent Events and the
 * page reloads, which brings the games in step and shows what is new.
 */
const element = document.getElementById('online-events');

if (element) {
  new EventSource(element.dataset.events).addEventListener('changed', () => location.reload());
}
