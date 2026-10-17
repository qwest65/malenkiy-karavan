/*
 * Веб-версия переехала в qwest65/malenkiy-karavan-web. Этот файл заменяет старый
 * service worker у тех, кто уже открывал /malenkiy-karavan/app/: стирает его кэш
 * (karavan-app-…), снимает себя и перезагружает страницу, а она ведёт на новый адрес.
 */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) {
      if (key.startsWith('karavan-app-')) await caches.delete(key);
    }
    await self.registration.unregister();
    for (const client of await self.clients.matchAll({ type: 'window' })) client.navigate(client.url);
  })());
});
