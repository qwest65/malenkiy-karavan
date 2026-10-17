/*
 * «Маленький караван» — веб-версия для iPhone и любых браузеров.
 * Логика повторяет Android-приложение (KaravanController.kt, Journey.kt, Arrival.kt,
 * WalkPath.kt); тексты, линии, наклейки и озвучка готовит app/tools/build_web.py.
 */
'use strict';

const STORE_KEY = 'karavan.journey2';
const HINT_KEY = 'karavan.installHint';
const MAP_STYLE = 'https://tiles.openfreemap.org/styles/liberty';

/** Радиус прибытия по умолчанию, м. */
const DEFAULT_RADIUS_M = 20;
/** С какого расстояния Троша говорит «точка уже близко». */
const NEAR_METERS = 70;
/** Ближе этого к пешеходной линии стрелка «притягивается» к ней. */
const ON_PATH_METERS = 25;
const DWELL_MS = 5000;
const WALKING_SPEED = 0.7;
const STILL_SPREAD_M = 10;
const LEAVE_FIXES = 2;
const KID_STEP_M = 0.45;
const KID_WALK_M_PER_MIN = 50;
const MINUTES_PER_STOP = 7;

const $app = document.getElementById('app');
const $overlay = document.getElementById('overlay');
const $mapScreen = document.getElementById('map-screen');

let route = null;
let legs = new Map();

const S = {
  screen: 'home',
  journey: null,
  opened: 0,
  quizChoice: null,
  optionOrder: [],
  revealed: false,
  heard: false,
  parentOpen: false,
  result: null,
  fix: null,
  shown: null,
  distance: null,
  panelOpen: true,
  editing: null,
  celebrated: new Set(),
};

/* ================= Слова и числа ================= */

function plural(n, one, few, many) {
  const m100 = n % 100, m10 = n % 10;
  if (m100 >= 11 && m100 <= 14) return many;
  if (m10 === 1) return one;
  if (m10 >= 2 && m10 <= 4) return few;
  return many;
}
const pointsWord = n => plural(n, 'точка', 'точки', 'точек');
const stepsWord = n => plural(n, 'шаг', 'шага', 'шагов');
const placesWord = n => plural(n, 'место', 'места', 'мест');

function kidSteps(meters) {
  const steps = meters / KID_STEP_M;
  if (steps < 20) return Math.round(steps);
  if (steps < 200) return Math.round(steps / 10) * 10;
  return Math.round(steps / 50) * 50;
}

function formatDistance(m) {
  if (m < 10) return `${Math.round(m)} м`;
  if (m < 995) return `${Math.round(m / 10) * 10} м`;
  if (m < 10000) return `${(m / 1000).toFixed(1).replace('.', ',')} км`;
  return `${Math.round(m / 1000)} км`;
}

function formatEstimate(min) {
  if (min < 55) return `около ${Math.max(5, Math.floor((min + 4) / 5) * 5)} мин`;
  if (min < 75) return 'около часа';
  if (min < 105) return 'около 1,5 часа';
  if (min < 135) return 'около 2 часов';
  return `около ${String(Math.floor((min + 15) / 30) / 2).replace('.', ',')} часа`;
}

const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

/* ================= Геометрия ================= */

const R = 6371000;
const rad = d => d * Math.PI / 180;

function distanceMeters(lat1, lon1, lat2, lon2) {
  const dLat = rad(lat2 - lat1), dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/** Пешеходная линия между точками маршрута; точки — [lat, lon]. */
class WalkPath {
  constructor(points) {
    this.points = points;
    this.seg = [];
    for (let i = 0; i < points.length - 1; i++) {
      this.seg.push(distanceMeters(points[i][0], points[i][1], points[i + 1][0], points[i + 1][1]));
    }
    this.length = this.seg.reduce((a, b) => a + b, 0);
  }

  reversed() { return new WalkPath(this.points.slice().reverse()); }

  pointAt(along) {
    let left = Math.min(Math.max(along, 0), this.length);
    for (let i = 0; i < this.seg.length; i++) {
      const s = this.seg[i];
      if (left <= s || i === this.seg.length - 1) {
        const t = s === 0 ? 0 : Math.min(Math.max(left / s, 0), 1);
        const a = this.points[i], b = this.points[i + 1];
        return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
      }
      left -= s;
    }
    return this.points[this.points.length - 1];
  }

  remainingFrom(along) {
    const out = [this.pointAt(along)];
    let passed = 0;
    for (let i = 0; i < this.seg.length; i++) {
      passed += this.seg[i];
      if (passed > along) out.push(this.points[i + 1]);
    }
    if (out.length < 2) out.push(this.points[this.points.length - 1]);
    return out;
  }

  progress(lat, lon) {
    let bestOff = Infinity, bestRemaining = this.length, after = this.length;
    for (let i = 0; i < this.seg.length; i++) {
      after -= this.seg[i];
      const a = this.points[i], b = this.points[i + 1];
      const kx = 111320 * Math.cos(rad(a[0]));
      const bx = (b[1] - a[1]) * kx, by = (b[0] - a[0]) * 111320;
      const px = (lon - a[1]) * kx, py = (lat - a[0]) * 111320;
      const len2 = bx * bx + by * by;
      const t = len2 === 0 ? 0 : Math.min(Math.max((px * bx + py * by) / len2, 0), 1);
      const off = Math.hypot(px - t * bx, py - t * by);
      if (off < bestOff) {
        bestOff = off;
        bestRemaining = (1 - t) * this.seg[i] + after;
      }
    }
    return { off: bestOff, remaining: bestRemaining, along: this.length - bestRemaining };
  }
}

const between = (a, b) => legs.get(`${a}-${b}`) || null;

function planMeters(plan) {
  let sum = 0;
  for (let i = 0; i < plan.length - 1; i++) {
    const p = between(plan[i], plan[i + 1]);
    if (!p) return null;
    sum += p.length;
  }
  return sum;
}

function planSummary(plan) {
  const meters = plan.length > 1 ? planMeters(plan) : null;
  const parts = [`${plan.length} ${pointsWord(plan.length)}`];
  if (meters != null) parts.push(`${formatDistance(meters)} пешком`);
  parts.push(formatEstimate(Math.round((meters || 0) / KID_WALK_M_PER_MIN) + plan.length * MINUTES_PER_STOP));
  return parts.join(' · ');
}

/* ================= Прогулка и альбом ================= */

function newJourney() {
  return { plan: [], position: 0, found: [], walkFound: [], badge: false };
}

function loadJourney() {
  try {
    const j = JSON.parse(localStorage.getItem(STORE_KEY) || 'null');
    if (j && j.route === route.id) {
      const n = route.stops.length;
      const ok = a => (Array.isArray(a) ? a : []).filter(i => Number.isInteger(i) && i >= 0 && i < n);
      const plan = [...new Set(ok(j.plan))];
      return {
        plan,
        position: Math.min(Math.max(j.position | 0, 0), plan.length),
        found: [...new Set(ok(j.found))],
        walkFound: [...new Set(ok(j.walkFound))],
        badge: !!j.badge,
      };
    }
  } catch (_) { /* хранилище недоступно — начинаем с чистого альбома */ }
  return newJourney();
}

function saveJourney() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify({ route: route.id, ...S.journey }));
  } catch (_) { /* приватный режим: прогресс живёт до закрытия вкладки */ }
}

const J = {
  get inProgress() { return S.journey.plan.length > 0; },
  get complete() { return this.inProgress && S.journey.position === S.journey.plan.length; },
  get active() { const j = S.journey; return j.position < j.plan.length ? j.plan[j.position] : null; },
  get previous() { const j = S.journey; return j.position > 0 ? j.plan[j.position - 1] : null; },
  isFound: i => S.journey.found.includes(i),
};

function setJourney(j) {
  S.journey = j;
  saveJourney();
}

/* ================= Озвучка ================= */

const player = {
  el: new Audio(),
  queue: [],
  current: null,
  play(...clips) { this.queue = clips.slice(); this.next(); },
  enqueue(clip) { if (this.current) this.queue.push(clip); else this.play(clip); },
  stop() { this.queue = []; this.current = null; this.el.pause(); onAudio(); },
  next() {
    this.current = this.queue.shift() || null;
    onAudio();
    if (!this.current) { this.el.pause(); return; }
    this.el.src = `audio/${this.current}.mp3`;
    const p = this.el.play();
    if (p) p.catch(() => { this.queue = []; this.current = null; onAudio(); });
  },
};
player.el.preload = 'auto';
player.el.addEventListener('ended', () => player.next());
player.el.addEventListener('error', () => player.next());

const Clips = {
  narrator: i => `stop${i + 1}_narrator`,
  trosha: i => `stop${i + 1}_trosha`,
  task: i => `stop${i + 1}_task`,
  parent: i => `stop${i + 1}_parent`,
  arrival: i => ['bell', 'phrase_arrived', `stop${i + 1}_narrator`, `stop${i + 1}_trosha`, `stop${i + 1}_task`],
};

function onAudio() {
  if (S.screen === 'stop') {
    const i = S.opened, c = player.current;
    if (c) S.heard = true;
    if (!S.revealed && (c === Clips.trosha(i) || c === Clips.task(i) || (!c && S.heard))) reveal();
  }
  document.querySelectorAll('[data-speaker]').forEach(b => {
    const parent = b.dataset.speaker === 'parent';
    b.textContent = parent
      ? (player.current === Clips.parent(S.opened) ? 'Остановить' : 'Подробная история голосом')
      : (player.current ? '⏹' : '🔊');
  });
}

/* ================= Не гасить экран ================= */

let wakeLock = null;
async function keepAwake(on) {
  try {
    if (on && !wakeLock && 'wakeLock' in navigator) {
      wakeLock = await navigator.wakeLock.request('screen');
      wakeLock.addEventListener('release', () => { wakeLock = null; });
    } else if (!on && wakeLock) {
      await wakeLock.release();
      wakeLock = null;
    }
  } catch (_) { /* не поддерживается — экран может погаснуть */ }
}
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && S.screen === 'walk') keepAwake(true);
});

/* ================= Геолокация и прибытие ================= */

class ArrivalDetector {
  constructor(radius) { this.radius = radius; this.reset(); }
  reset() { this.stillSince = null; this.anchor = null; this.outside = 0; }
  onFix(lat, lon, dist, speed, now) {
    if (dist > this.radius) {
      if (++this.outside >= LEAVE_FIXES) this.reset();
      return this.check(now);
    }
    this.outside = 0;
    if (speed != null && speed >= WALKING_SPEED) {
      this.stillSince = null;
      this.anchor = [lat, lon];
    } else if (this.anchor && distanceMeters(this.anchor[0], this.anchor[1], lat, lon) > STILL_SPREAD_M) {
      this.stillSince = now;
      this.anchor = [lat, lon];
    } else if (this.stillSince == null) {
      this.stillSince = now;
      this.anchor = this.anchor || [lat, lon];
    }
    return this.check(now);
  }
  check(now) { return this.stillSince != null && now - this.stillSince >= DWELL_MS; }
}

const G = { watch: null, arrival: null, target: null, near: false, ahead: null, speed: null, denied: false };

function startTracking() {
  if (G.watch != null || !('geolocation' in navigator)) return;
  G.watch = navigator.geolocation.watchPosition(onFix, err => {
    if (err.code === err.PERMISSION_DENIED) {
      G.denied = true;
      stopTracking();
      toast('Без геолокации нажимайте «Мы на месте!», когда подойдёте к точке');
      if (S.screen === 'walk') render();
    }
  }, { enableHighAccuracy: true, maximumAge: 2000, timeout: 30000 });
}

function stopTracking() {
  if (G.watch != null) navigator.geolocation.clearWatch(G.watch);
  G.watch = null;
}

function onFix(pos) {
  const c = pos.coords;
  S.fix = { lat: c.latitude, lon: c.longitude, acc: c.accuracy };
  G.speed = Number.isFinite(c.speed) ? c.speed : null;
  updateGuidance();
}

function updateGuidance() {
  const fix = S.fix;
  const target = J.active;
  if (!fix || target == null) {
    S.distance = null;
    S.shown = fix;
    return;
  }
  const stop = route.stops[target];
  const radius = stop.radius || DEFAULT_RADIUS_M;
  if (G.target !== target) {
    G.target = target;
    G.near = false;
    G.arrival = new ArrivalDetector(radius);
  }
  const straight = distanceMeters(fix.lat, fix.lon, stop.lat, stop.lon);
  const prev = J.previous;
  const path = prev != null ? between(prev, target) : null;
  const pr = path ? path.progress(fix.lat, fix.lon) : null;
  if (pr && pr.off <= ON_PATH_METERS) {
    const p = path.pointAt(pr.along);
    S.shown = { lat: p[0], lon: p[1] };
    S.distance = pr.remaining;
    G.ahead = path.remainingFrom(pr.along);
  } else {
    S.shown = fix;
    S.distance = straight;
    G.ahead = [[fix.lat, fix.lon], [stop.lat, stop.lon]];
  }
  if (!G.near && S.distance <= NEAR_METERS && S.distance > radius) {
    G.near = true;
    if (S.screen === 'walk') player.enqueue('phrase_near');
  }
  const arrived = G.arrival.onFix(fix.lat, fix.lon, straight, G.speed, Date.now());
  if (S.screen === 'walk') {
    if (arrived) { arrive(); return; }
    updateWalkView();
  }
}

setInterval(() => {
  if (S.screen === 'walk' && G.arrival && G.arrival.check(Date.now())) arrive();
}, 1000);

/* ================= Карта ================= */

const M = { map: null, ready: false, markers: [], me: null, following: false, fitted: false, fittedWithMe: false };

const BLANK_STYLE = {
  version: 8, sources: {},
  layers: [{ id: 'bg', type: 'background', paint: { 'background-color': '#efe6d2' } }],
};

function ensureMap() {
  if (M.map || !window.maplibregl) return;
  const first = route.stops[route.default_order[0]];
  M.map = new maplibregl.Map({
    container: 'map',
    style: MAP_STYLE,
    center: [first.lon, first.lat],
    zoom: 15,
    attributionControl: { compact: true },
    pitchWithRotate: false,
  });
  M.map.dragRotate.disable();
  M.map.touchZoomRotate.disableRotation();
  let fellBack = false;
  M.map.on('error', e => {
    // Нет интернета и тайлов в кэше — показываем линии и точки на простом фоне.
    if (!fellBack && !M.ready && e && e.error && /style|Failed to fetch|NetworkError|Load failed/i.test(String(e.error.message || e.error))) {
      fellBack = true;
      M.map.setStyle(BLANK_STYLE);
    }
  });
  M.map.on('style.load', () => {
    M.ready = true;
    addLayers();
    drawMap();
  });
  M.map.on('dragstart', () => {
    M.following = false;
    updateWalkView();
  });
}

function addLayers() {
  const m = M.map;
  const empty = { type: 'FeatureCollection', features: [] };
  if (!m.getSource('plan')) m.addSource('plan', { type: 'geojson', data: empty });
  if (!m.getSource('ahead')) m.addSource('ahead', { type: 'geojson', data: empty });
  if (!m.getLayer('plan')) {
    m.addLayer({ id: 'plan', type: 'line', source: 'plan', layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#d2463c', 'line-opacity': 0.35, 'line-width': 5, 'line-dasharray': [1, 1.6] } });
  }
  if (!m.getLayer('ahead')) {
    m.addLayer({ id: 'ahead', type: 'line', source: 'ahead', layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#3e9c6a', 'line-width': 8 } });
  }
}

const lineFeature = pts => ({ type: 'Feature', geometry: { type: 'LineString', coordinates: pts.map(p => [p[1], p[0]]) } });

function drawMap() {
  if (!M.map) return;
  const j = S.journey;
  const target = J.active;

  M.markers.forEach(mk => mk.remove());
  M.markers = j.plan.map((i, pos) => {
    const stop = route.stops[i];
    const el = document.createElement('div');
    if (J.isFound(i) && pos < j.position) {
      el.className = 'stop-pin done';
      el.innerHTML = `<img src="stickers/${stop.sticker}.webp" alt="">`;
    } else {
      el.className = 'stop-pin' + (i === target ? ' target' : pos < j.position ? ' dim' : '');
      el.textContent = String(pos + 1);
    }
    el.title = stop.title;
    return new maplibregl.Marker({ element: el }).setLngLat([stop.lon, stop.lat]).addTo(M.map);
  });

  if (M.ready) {
    const plan = [];
    for (let k = 0; k < j.plan.length - 1; k++) {
      const p = between(j.plan[k], j.plan[k + 1]);
      plan.push(lineFeature(p ? p.points : [[route.stops[j.plan[k]].lat, route.stops[j.plan[k]].lon], [route.stops[j.plan[k + 1]].lat, route.stops[j.plan[k + 1]].lon]]));
    }
    M.map.getSource('plan').setData({ type: 'FeatureCollection', features: plan });
  }
  drawLive();
  if (!M.fitted) {
    M.fitted = true;
    fitAll();
  }
}

/** То, что меняется с каждым замером GPS: стрелка «я» и путь впереди. */
function drawLive() {
  if (!M.map) return;
  const shown = S.shown;
  if (shown) {
    if (!M.me) {
      const el = document.createElement('div');
      el.className = 'me';
      M.me = new maplibregl.Marker({ element: el }).setLngLat([shown.lon, shown.lat]).addTo(M.map);
    } else {
      M.me.setLngLat([shown.lon, shown.lat]);
    }
    if (!M.fittedWithMe) {
      // Первый замер: показываем сразу и нас, и точки прогулки.
      M.fittedWithMe = true;
      fitAll();
    } else if (M.following) {
      M.map.easeTo({ center: [shown.lon, shown.lat], duration: 600 });
    }
  }
  if (M.ready) {
    const ahead = G.ahead && J.active != null ? [lineFeature(G.ahead)] : [];
    M.map.getSource('ahead').setData({ type: 'FeatureCollection', features: ahead });
  }
}

function fitAll() {
  if (!M.map) return;
  const pts = S.journey.plan.map(i => [route.stops[i].lon, route.stops[i].lat]);
  if (S.shown) pts.push([S.shown.lon, S.shown.lat]);
  if (!pts.length) return;
  const b = pts.reduce((bb, p) => bb.extend(p), new maplibregl.LngLatBounds(pts[0], pts[0]));
  M.following = false;
  const h = M.map.getContainer().clientHeight;
  const bottom = Math.min(400, Math.round(h * 0.45));
  M.map.fitBounds(b, { padding: { top: 170, bottom, left: 50, right: 50 }, maxZoom: 17, duration: 500 });
}

function follow() {
  M.following = true;
  if (S.shown && M.map) M.map.easeTo({ center: [S.shown.lon, S.shown.lat], zoom: 17, duration: 500 });
  updateWalkView();
}

/* ================= Действия ================= */

function go(screen) {
  S.screen = screen;
  if (screen !== 'home' && history.state?.inner !== true) history.pushState({ inner: true }, '');
  render();
}

function openChooser() { player.stop(); S.editing = null; go('choose'); }

function startWalk(selection) {
  const chosen = [...new Set(selection)].filter(i => i >= 0 && i < route.stops.length);
  if (!chosen.length) return;
  player.stop();
  setJourney({ ...S.journey, plan: chosen, position: 0, walkFound: [] });
  S.celebrated.clear();
  G.target = null;
  startTracking();
  go('walk');
  player.play('intro_trosha', 'phrase_road');
}

function resumeWalk() {
  if (!J.inProgress) return;
  player.stop();
  G.target = null;
  startTracking();
  go('walk');
  player.play('phrase_go');
}

function pause() { player.stop(); go('home'); }

function finishWalk() {
  if (J.inProgress && confirm('Завершить прогулку?\n\nНайденные вещи останутся в альбоме. Остальные точки можно пройти в другой раз.')) {
    endWalk(false);
  }
}

function skipStop() {
  player.stop();
  const j = S.journey;
  if (J.active == null) return;
  const next = { ...j, position: j.position + 1 };
  if (next.position === next.plan.length && next.walkFound.length) next.badge = true;
  setJourney(next);
  if (J.complete) {
    endWalk(S.journey.walkFound.length > 0);
  } else {
    go('walk');
    player.play('phrase_go');
  }
}

function arrive() {
  const stop = J.active;
  if (stop == null) return;
  if (G.arrival) G.arrival.reset();
  S.opened = stop;
  S.quizChoice = null;
  S.revealed = false;
  S.heard = false;
  S.parentOpen = false;
  S.optionOrder = route.stops[stop].question.options.map((_, i) => i).sort(() => Math.random() - 0.5);
  go('stop');
  window.scrollTo(0, 0);
  player.play(...Clips.arrival(stop));
}

function reveal() {
  S.revealed = true;
  if (S.screen !== 'stop') return;
  render();
  if (!S.celebrated.has(S.opened)) {
    S.celebrated.add(S.opened);
    const stop = route.stops[S.opened];
    celebrate(stop.sticker, 'Ура! Нашлось!', stop.item, () => {
      const bp = document.querySelector('.backpack');
      if (bp) {
        bp.querySelector('.cnt').textContent = ` ${new Set([...S.journey.walkFound, S.opened]).size} из ${S.journey.plan.length}`;
        bp.classList.add('bounce');
      }
    });
  }
}

function answer(option) {
  S.quizChoice = option;
  const right = option === route.stops[S.opened].question.answer;
  player.play(...(right ? ['phrase_right', 'phrase_photo'] : ['phrase_wrong']));
  render();
}

function completeStop() {
  player.stop();
  const j = S.journey;
  const i = S.opened;
  if (i !== J.active) return;
  const next = {
    ...j,
    found: [...new Set([...j.found, i])],
    walkFound: [...new Set([...j.walkFound, i])],
    position: j.position + 1,
  };
  if (next.position === next.plan.length) next.badge = true;
  setJourney(next);
  if (J.complete) {
    endWalk(true);
  } else {
    go('walk');
    player.play('phrase_found', 'phrase_go');
  }
}

function endWalk(complete) {
  player.stop();
  const j = S.journey;
  S.result = { complete, found: j.plan.filter(i => j.walkFound.includes(i)) };
  setJourney({ ...j, plan: [], position: 0, walkFound: [] });
  S.distance = null;
  G.ahead = null;
  stopTracking();
  go('finale');
  if (complete) player.play('bell', 'finale_trosha'); else player.play('phrase_later');
  if (complete && !S.celebrated.has(-1)) {
    S.celebrated.add(-1);
    celebrate(route.badge, 'Ура!', 'Ты — Юный караванщик!');
  }
}

function backToWalk() { player.stop(); go('walk'); }
function goHome() { player.stop(); go('home'); }
function openAlbum() { player.stop(); go('album'); }

function resetAlbum() {
  if (!confirm('Очистить альбом?\n\nВсе найденные вещи и значок исчезнут, и Троша снова потеряется.')) return;
  player.stop();
  setJourney(newJourney());
  go('home');
}

function toggleStopStory() {
  const i = S.opened;
  if (player.current) player.stop(); else player.play(Clips.narrator(i), Clips.trosha(i), Clips.task(i));
}

function toggleWalkHint() {
  if (player.current) player.stop(); else player.play('phrase_go');
}

function toggleParentStory() {
  if (player.current === Clips.parent(S.opened)) player.stop(); else player.play(Clips.parent(S.opened));
}

/** Назад: жест «назад» в браузере и на iPhone ведёт себя как кнопка «Назад» в Android. */
window.addEventListener('popstate', () => {
  switch (S.screen) {
    case 'home': return;
    case 'stop': backToWalk(); break;
    case 'walk': pause(); break;
    case 'choose':
      if (S.editing) { S.editing = null; render(); } else goHome();
      break;
    default: goHome();
  }
  if (S.screen !== 'home') history.pushState({ inner: true }, '');
});

/* ================= Праздник ================= */

function celebrate(sticker, title, subtitle, onDone) {
  const colors = ['#d2463c', '#f5c542', '#3e9c6a', '#2f6fb5', '#ec4f8a', '#8e5bd6'];
  let confetti = '';
  for (let k = 0; k < 40; k++) {
    confetti += `<i class="confetti" style="left:${Math.random() * 100}%;background:${colors[k % colors.length]};` +
      `animation-duration:${1.6 + Math.random() * 1.6}s;animation-delay:${Math.random() * 0.5}s"></i>`;
  }
  $overlay.innerHTML = `<div class="celebrate">${confetti}<div class="box">` +
    `<img class="sticker" src="stickers/${sticker}.webp" alt="">` +
    `<div class="t">${esc(title)}</div><div class="s">${esc(subtitle)}</div></div></div>`;
  setTimeout(() => {
    $overlay.innerHTML = '';
    if (onDone) onDone();
  }, 2600);
}

let toastTimer = null;
function toast(text) {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    el.style.cssText = 'position:fixed;left:16px;right:16px;bottom:calc(24px + env(safe-area-inset-bottom));z-index:60;' +
      'background:#3b2415;color:#fff;border-radius:16px;padding:14px 16px;font-size:16px;text-align:center;box-shadow:0 6px 18px rgba(0,0,0,.3)';
    document.body.appendChild(el);
  }
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 4500);
}

/* ================= Экраны ================= */

const sticker = (name, cls = '', size = '') =>
  `<img class="sticker ${cls}" src="stickers/${name}.webp" alt="" ${size ? `style="width:${size}px;height:${size}px"` : ''}>`;
const mystery = (size = 40, font = 22) => `<div class="mystery" style="width:${size}px;height:${size}px;font-size:${font}px">?</div>`;

function stickerRow(stops) {
  return `<div class="stickers">${stops.map(i => J.isFound(i) ? sticker(route.stops[i].sticker) : mystery()).join('')}</div>`;
}

const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
let installEvent = null;
window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault();
  installEvent = e;
  if (S.screen === 'home') render();
});

function installHint() {
  let hidden = false;
  try { hidden = localStorage.getItem(HINT_KEY) === 'hidden'; } catch (_) { /* нет хранилища */ }
  if (standalone || hidden) return '';
  if (isIOS) {
    return `<div class="hint"><button class="x" data-act="hideHint" aria-label="Скрыть">✕</button>
      <b>Установите на экран «Домой»</b><br>Нажмите внизу Safari кнопку «Поделиться» <b>⎋</b>, затем «На экран "Домой"».
      Так приложение откроется без интернета и не потеряет альбом.</div>`;
  }
  if (installEvent) {
    return `<div class="hint"><button class="x" data-act="hideHint" aria-label="Скрыть">✕</button>
      <b>Установите приложение</b> — оно будет открываться с экрана телефона, даже без интернета.
      <div style="margin-top:10px"><button class="btn soft" data-act="install">📲 Установить</button></div></div>`;
  }
  return '';
}

function homeScreen() {
  const j = S.journey;
  return `<div class="page">
    <h1 class="h1">Маленький караван</h1>
    ${sticker('trosha', 'trosha-home')}
    <div class="panel">
      <div class="h2">${esc(route.title)}</div>
      <p style="font-size:17px">${esc(route.subtitle)}</p>
      <div class="muted" style="font-size:15px">${esc(planSummary(route.default_order))}</div>
      ${j.found.length ? `<div class="red bold" style="font-size:17px">В альбоме ${j.found.length} из ${route.stops.length}</div>
        ${stickerRow(route.stops.map((_, i) => i))}` : ''}
    </div>
    ${J.inProgress ? `
      <div class="panel gold">
        <div class="bold" style="font-size:20px">⏸ Прогулка на паузе</div>
        <div style="font-size:16px">Пройдено точек: ${j.position} из ${j.plan.length}</div>
      </div>
      <button class="btn" data-act="resume">Продолжить прогулку</button>
      <button class="btn soft" data-act="finish">Завершить прогулку</button>`
    : `<button class="btn" data-act="choose">Начать прогулку</button>`}
    <button class="btn soft" data-act="album">Мой альбом</button>
    ${installHint()}
    ${j.found.length || j.badge ? `<button class="link" data-act="reset">Очистить альбом</button>` : ''}
  </div>`;
}

function chooseScreen() {
  if (S.editing) return builderScreen();
  return `<div class="page">
    <div class="back-row"><button class="link" data-act="home">← Назад</button></div>
    <h1 class="h1">Куда пойдём?</h1>
    <p class="sub">Выберите готовый маршрут или соберите свой.</p>
    ${route.presets.map((p, k) => `
      <div class="panel">
        <div class="preset-head"><div class="emoji">${p.emoji}</div>
          <div><div class="t">${esc(p.title)}</div>${p.subtitle ? `<div class="s">${esc(p.subtitle)}</div>` : ''}</div></div>
        ${stickerRow(p.stops)}
        <div style="font-size:15px">${esc(planSummary(p.stops))}</div>
        <div class="row">
          <button class="btn grow" data-act="go" data-preset="${k}">Идём!</button>
          <button class="btn out" data-act="edit" data-preset="${k}">Изменить</button>
        </div>
      </div>`).join('')}
    <button class="btn soft" data-act="edit" data-preset="-1">✏️ Собрать свой маршрут</button>
  </div>`;
}

function thumb(i) {
  return J.isFound(i) ? sticker(route.stops[i].sticker, 'thumb') : mystery(40, 22);
}

function builderScreen() {
  const plan = S.editing.plan;
  const rest = route.stops.map((_, i) => i).filter(i => !plan.includes(i));
  return `<div class="page">
    <div class="back-row"><button class="link" data-act="unedit">← К маршрутам</button></div>
    <h1 class="h1">Свой маршрут</h1>
    <p class="sub">Добавляйте точки из списка ниже и меняйте порядок стрелками.</p>
    ${plan.length ? `<button class="link" data-act="clearPlan" style="color:var(--ink)">Очистить</button>`
      : `<div class="red bold" style="font-size:17px">Пока пусто — нажмите ＋ у нужных точек</div>`}
    ${plan.map((i, pos) => `
      <div class="plan-card">
        <div class="n">${pos + 1}</div>${thumb(i)}
        <div class="t">${esc(route.stops[i].title)}${J.isFound(i) ? '<small>Уже в альбоме</small>' : ''}</div>
        <button class="small" data-act="up" data-pos="${pos}" ${pos === 0 ? 'disabled' : ''}>↑</button>
        <button class="small" data-act="down" data-pos="${pos}" ${pos === plan.length - 1 ? 'disabled' : ''}>↓</button>
        <button class="small" data-act="remove" data-pos="${pos}">✕</button>
      </div>`).join('')}
    ${rest.length ? `<div class="bold" style="font-size:20px;width:100%">Добавить точку</div>
      ${rest.map(i => `<button class="add-card" data-act="add" data-stop="${i}">${thumb(i)}<span class="t">${esc(route.stops[i].title)}</span><span class="small" style="display:grid;place-items:center">＋</span></button>`).join('')}` : ''}
    <div class="bold ${plan.length ? '' : 'red'}" style="font-size:17px">${plan.length ? esc(planSummary(plan)) : 'Выберите хотя бы одну точку'}</div>
    <button class="btn" data-act="goPlan" ${plan.length ? '' : 'disabled'}>Идём!</button>
  </div>`;
}

function walkScreen() {
  const j = S.journey;
  const active = J.active;
  if (active == null) return '';
  const stop = route.stops[active];
  return `<div class="walk-top">
      <div class="bar">
        <button class="pill" data-act="pause">⏸ Пауза</button>
        <span style="flex:1"></span>
        <button class="round" data-act="follow" id="follow-btn" ${M.following ? 'hidden' : ''}>🧭</button>
        <button class="round" data-act="fit">⤢</button>
      </div>
      <div id="banner"></div>
    </div>
    <div class="walk-panel" id="walk-panel">
      <button class="grip" data-act="panel" aria-label="Свернуть или развернуть"><span></span></button>
      ${S.panelOpen ? `
        ${stickerRow(j.plan)}
        <div class="target">Точка ${j.position + 1} из ${j.plan.length}: ${esc(stop.title)}</div>
        <div id="dist-big"></div>
        <div style="font-size:16px">🤝 Держи взрослого за руку</div>
        <div class="row">
          <button class="round" data-act="walkHint" data-speaker="walk">${player.current ? '⏹' : '🔊'}</button>
          <button class="btn grow" data-act="arrive">🔔 Мы на месте!</button>
        </div>
        <div class="links">
          <button class="link" data-act="skip">Пропустить точку</button>
          <button class="link" data-act="finish">Завершить прогулку</button>
        </div>`
      : `<div class="row"><div class="steps-summary" id="dist-small"></div><button class="round" data-act="arrive">🔔</button></div>`}
    </div>`;
}

/** Обновление прогулки без перерисовки всего экрана: каждые несколько секунд приходит GPS. */
function updateWalkView() {
  if (S.screen !== 'walk') return;
  const d = S.distance;
  const big = document.getElementById('dist-big');
  const small = document.getElementById('dist-small');
  if (big) {
    big.innerHTML = d != null
      ? `<div class="steps-big">≈ ${kidSteps(d)} ${stepsWord(kidSteps(d))}</div><div class="muted" style="font-size:14px">${formatDistance(d)} для взрослых</div>`
      : `<div class="muted" style="font-size:20px">${G.denied ? 'Геолокация выключена' : 'Ищем, где мы…'}</div>`;
  }
  if (small) {
    small.textContent = d != null ? `≈ ${kidSteps(d)} ${stepsWord(kidSteps(d))}` : 'Ищем, где мы…';
    small.style.color = d != null ? '' : 'var(--muted)';
  }
  const banner = document.getElementById('banner');
  if (banner) {
    banner.innerHTML = d != null && d <= NEAR_METERS
      ? `<div class="banner"><div class="arrow">📍</div><div><div class="t">Точка рядом!</div><div class="s">Смотри по сторонам · ${formatDistance(d)}</div></div></div>`
      : '';
  }
  const fb = document.getElementById('follow-btn');
  if (fb) fb.hidden = M.following;
  drawLive();
}

function stopScreen() {
  const i = S.opened;
  const stop = route.stops[i];
  const j = S.journey;
  const last = j.position === j.plan.length - 1;
  const q = stop.question;
  const choice = S.quizChoice;
  const answeredRight = choice != null && choice === q.answer;
  const walkFound = new Set(j.walkFound);
  if (S.revealed && S.celebrated.has(i)) walkFound.add(i);
  return `<div class="page">
    <div class="back-row">
      <button class="link" data-act="backToWalk">← К карте</button>
      <div class="backpack"><span class="ico">🎒</span><span class="cnt"> ${walkFound.size} из ${j.plan.length}</span></div>
    </div>
    <div class="muted" style="font-size:15px">Точка ${j.position + 1} из ${j.plan.length}</div>
    <h1 class="h1" style="font-size:26px">${esc(stop.title)}</h1>
    <div class="found-item">${S.revealed ? sticker(stop.sticker) : mystery(140, 80)}</div>
    ${S.revealed ? `<div class="red bold" style="font-size:24px;text-align:center">Нашлось: ${esc(stop.item)}!</div>`
      : `<button class="link" data-act="reveal">Показать находку</button>`}
    <div class="panel"><div class="who">Рассказчик</div><p>${esc(stop.narrator)}</p></div>
    <div class="panel"><div class="who">${sticker('trosha')}Троша</div><p>${esc(stop.trosha)}</p></div>
    <div class="panel gold">
      <div class="quiz-q">❓ ${esc(q.text)}</div>
      ${S.optionOrder.map(o => {
        const picked = choice === o;
        const cls = picked && o === q.answer ? 'right' : picked ? 'wrong' : answeredRight ? 'off' : '';
        return `<button class="opt ${cls}" data-act="answer" data-opt="${o}" ${answeredRight && !picked ? 'disabled' : ''}>${esc(q.options[o])}</button>`;
      }).join('')}
      ${answeredRight ? '<div class="green bold" style="font-size:20px">⭐ Правильно! Молодец!</div>'
        : choice != null ? '<div class="red" style="font-size:18px">Не совсем — попробуй ещё раз!</div>' : ''}
    </div>
    ${stop.activity ? `<div class="panel"><div class="who">🎲 Игра</div><p>${esc(stop.activity)}</p></div>` : ''}
    <div class="panel kraft">
      <button class="parent-toggle" data-act="parent">${S.parentOpen ? '▾' : '▸'} Для взрослых</button>
      ${S.parentOpen ? `<p style="font-size:16px">${esc(stop.parent)}</p>
        <button class="btn soft" data-act="parentStory" data-speaker="parent">${player.current === Clips.parent(i) ? 'Остановить' : 'Подробная история голосом'}</button>` : ''}
    </div>
    <div class="row">
      <button class="round" data-act="story" data-speaker="stop">${player.current ? '⏹' : '🔊'}</button>
      <button class="btn grow" data-act="complete">${last ? 'Ура! Завершить прогулку' : 'Готово! Идём дальше'}</button>
    </div>
  </div>`;
}

function finaleScreen() {
  const r = S.result || { complete: false, found: [] };
  return `<div class="page">
    ${r.complete ? `
      <div class="red" style="font-size:40px;font-weight:900">Ура!</div>
      ${sticker(route.badge, 'badge-big')}
      <h1 class="h1" style="font-size:28px">Ты — Юный караванщик!</h1>
      <p class="sub" style="color:var(--ink);font-size:18px">${esc(route.finale.trosha)}</p>`
    : `<h1 class="h1">Прогулка окончена</h1>
      ${sticker('trosha', '', 200)}
      <p class="sub" style="color:var(--ink);font-size:18px">Ничего, продолжим в другой раз! Остальные вещи Троша подождёт.</p>`}
    <div class="panel">
      ${r.found.length ? `<div class="bold" style="font-size:17px">Сегодня нашли:</div>
        <div class="stickers">${r.found.map(i => sticker(route.stops[i].sticker, '', 56)).join('')}</div>`
      : '<div class="muted" style="font-size:17px">Сегодня ничего не нашли</div>'}
    </div>
    <button class="btn" data-act="album">Мой альбом</button>
    <button class="btn soft" data-act="home">На главную</button>
  </div>`;
}

function albumScreen() {
  const j = S.journey;
  return `<div class="page">
    <div class="back-row"><button class="link" data-act="home">← Назад</button></div>
    <h1 class="h1">Мой альбом</h1>
    <div class="muted" style="font-size:17px">Найдено ${j.found.length} из ${route.stops.length}</div>
    <div class="album">
      ${route.stops.map((s, i) => {
        const found = J.isFound(i);
        return `<div class="album-card">${sticker(s.sticker, found ? '' : 'locked')}
          <div class="t ${found ? '' : 'muted'}">${found ? esc(s.item) : 'Ещё не нашли'}</div>
          <div class="s">${esc(s.title)}</div></div>`;
      }).join('')}
      <div class="album-card wide">${sticker(route.badge, j.badge ? '' : 'locked')}
        <div class="t" style="font-size:18px">${j.badge ? 'Юный караванщик' : 'Значок — за прогулку, пройденную до конца'}</div></div>
    </div>
  </div>`;
}

function render() {
  const walking = S.screen === 'walk';
  document.body.classList.toggle('walking', walking);
  $mapScreen.hidden = !walking;
  keepAwake(walking);
  const screens = { home: homeScreen, choose: chooseScreen, walk: walkScreen, stop: stopScreen, finale: finaleScreen, album: albumScreen };
  $app.innerHTML = screens[S.screen]();
  if (walking) {
    ensureMap();
    if (M.map) {
      M.map.resize();
      drawMap();
    }
    updateGuidance();
    updateWalkView();
  } else if (S.screen !== 'stop') {
    window.scrollTo(0, 0);
  }
}

/* ================= Нажатия ================= */

$app.addEventListener('click', e => {
  const b = e.target.closest('[data-act]');
  if (!b || b.disabled) return;
  const d = b.dataset;
  const ed = S.editing;
  switch (d.act) {
    case 'choose': openChooser(); break;
    case 'resume': resumeWalk(); break;
    case 'finish': finishWalk(); break;
    case 'album': openAlbum(); break;
    case 'home': goHome(); break;
    case 'reset': resetAlbum(); break;
    case 'go': startWalk(route.presets[+d.preset].stops); break;
    case 'edit': S.editing = { plan: +d.preset >= 0 ? route.presets[+d.preset].stops.slice() : [] }; render(); window.scrollTo(0, 0); break;
    case 'unedit': S.editing = null; render(); break;
    case 'clearPlan': ed.plan = []; render(); break;
    case 'add': ed.plan.push(+d.stop); render(); break;
    case 'remove': ed.plan.splice(+d.pos, 1); render(); break;
    case 'up': { const p = +d.pos; [ed.plan[p - 1], ed.plan[p]] = [ed.plan[p], ed.plan[p - 1]]; render(); break; }
    case 'down': { const p = +d.pos; [ed.plan[p + 1], ed.plan[p]] = [ed.plan[p], ed.plan[p + 1]]; render(); break; }
    case 'goPlan': startWalk(ed.plan); break;
    case 'pause': pause(); break;
    case 'follow': follow(); break;
    case 'fit': fitAll(); updateWalkView(); break;
    case 'panel': S.panelOpen = !S.panelOpen; render(); break;
    case 'walkHint': toggleWalkHint(); break;
    case 'arrive': arrive(); break;
    case 'skip': skipStop(); break;
    case 'backToWalk': backToWalk(); break;
    case 'reveal': reveal(); break;
    case 'answer': answer(+d.opt); break;
    case 'parent': S.parentOpen = !S.parentOpen; render(); break;
    case 'parentStory': toggleParentStory(); break;
    case 'story': toggleStopStory(); break;
    case 'complete': completeStop(); break;
    case 'hideHint': try { localStorage.setItem(HINT_KEY, 'hidden'); } catch (_) { /* нет хранилища */ } render(); break;
    case 'install': if (installEvent) { installEvent.prompt(); installEvent = null; render(); } break;
  }
});

/** Свайп по нижней панели прогулки: вниз — свернуть, вверх — развернуть. */
let swipeY = null;
$app.addEventListener('touchstart', e => {
  if (e.target.closest('#walk-panel')) swipeY = e.touches[0].clientY;
}, { passive: true });
$app.addEventListener('touchend', e => {
  if (swipeY == null) return;
  const dy = e.changedTouches[0].clientY - swipeY;
  swipeY = null;
  if (dy > 40 && S.panelOpen) { S.panelOpen = false; render(); }
  else if (dy < -40 && !S.panelOpen) { S.panelOpen = true; render(); }
}, { passive: true });

/* ================= Запуск ================= */

async function boot() {
  const [r, p] = await Promise.all([
    fetch('data/route.json').then(x => x.json()),
    fetch('data/paths.json').then(x => x.json()),
  ]);
  route = r;
  if (!route.presets || !route.presets.length) {
    route.presets = [{ emoji: '🐫', title: 'Весь маршрут', subtitle: '', stops: route.default_order }];
  }
  if (p.route === route.id) {
    for (const leg of p.legs) {
      const path = new WalkPath(leg.points.map(pt => [pt[1], pt[0]]));
      legs.set(`${leg.from}-${leg.to}`, path);
      legs.set(`${leg.to}-${leg.from}`, path.reversed());
    }
  }
  S.journey = loadJourney();
  history.replaceState({ inner: false }, '');
  render();
  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    navigator.serviceWorker.register('sw.js').catch(() => { /* без офлайна */ });
  }
}

boot().catch(() => {
  $app.innerHTML = '<div class="page"><h1 class="h1">Не получилось открыть</h1><p class="sub">Проверьте интернет и обновите страницу.</p></div>';
});
