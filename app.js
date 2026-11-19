const apps = {
 A:[['Alarm','◷'],['Amazon','a'],['Authenticator','⬡']], B:[['Books','▤'],['Browser','◎']], C:[['Calculator','▦'],['Calendar','▣'],['Camera','◉'],['Clock','◷'],['Contacts','♙']], D:[['Downloads','⇩'],['Drive','△']], F:[['Files','▱']], G:[['Gallery','▧'],['Gmail','✉']], K:[['Keep','◇']], M:[['Maps','⌖'],['Messages','▰'],['Music','♫']], N:[['Notes','≡']], P:[['Phone','☎'],['Photos','✿'],['Play Store','▷']], R:[['Recorder','●']], S:[['Settings','⚙'],['Spotify','≋']], T:[['Tasks','✓']], W:[['Weather','☀'],['WhatsApp','◌']], Y:[['YouTube','▶']]
};
const $ = id => document.getElementById(id);
const list = $('list'), rail = $('alphabet'), search = $('search'), dialog = $('menu');
const all = Object.values(apps).flat(), icons = new Map(all);
const demoWidgets = new Map([['Clock', '9:41'], ['Date', 'Thursday, November 19']]);
const read = (key, fallback) => JSON.parse(localStorage.getItem(`still-${key}`) ?? JSON.stringify(fallback));
let favorites = read('favorites', ['Camera', 'Maps', 'Messages']);
let widgets = read('widgets', []), hidden = read('hidden', []), heights = read('widget-heights', {});
let appearance = read('appearance', {}), locked = read('locked', []);
let timer, beforeSearch = 0, filtering = false;
const filterKey = value => value.trim().toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '');
function save() {
  for (const [key, value] of Object.entries({favorites, widgets, hidden, 'widget-heights': heights, appearance, locked})) {
    localStorage.setItem(`still-${key}`, JSON.stringify(value));
  }
}
function button(text, action, label = text) {
  const node = document.createElement('button');
  node.type = 'button'; node.textContent = text; node.setAttribute('aria-label', label); node.onclick = action;
  return node;
}
function alignDialog() {
  const rect = document.querySelector('.screen').getBoundingClientRect();
  dialog.style.setProperty('--phone-left', `${rect.left}px`);
  dialog.style.setProperty('--phone-width', `${rect.width}px`);
  const top = Math.max(0, rect.top), bottom = Math.min(innerHeight, rect.bottom);
  dialog.style.setProperty('--phone-bottom', `${Math.max(0, innerHeight - bottom)}px`);
  dialog.style.setProperty('--phone-center', `${(top + bottom) / 2}px`);
  dialog.style.setProperty('--phone-dialog-height', `${Math.max(100, (bottom - top) * .88)}px`);
}
function menu(title, entries, extra, bottom = false) {
  alignDialog();
  dialog.classList.toggle('bottom-panel', bottom);
  $('menu-close').hidden = bottom;
  $('menu-title').classList.toggle('sr-only', bottom);
  $('menu-title').textContent = title;
  $('menu-items').replaceChildren();
  if (extra) $('menu-items').append(extra);
  for (const [label, run] of entries) $('menu-items').append(button(label, () => { dialog.close(); run(); }));
  if (!dialog.open) dialog.showModal();
}
function update(nextY = list.scrollTop) {
  save(); render(nextY);
}
function moves(order, name, visible = order) {
  const index = visible.indexOf(name);
  return [-1, 1].filter(step => index >= 0 && visible[index + step] !== undefined).map(step => [step < 0 ? 'Move up' : 'Move down', () => {
    const from = order.indexOf(name), to = order.indexOf(visible[index + step]);
    [order[from], order[to]] = [order[to], order[from]];
    update();
  }]);
}
function favoriteMenu(name) {
  const entries = moves(favorites, name, favorites.filter(app => !hidden.includes(app)));
  menu(name, entries, entries.length ? undefined : hint('Add another favorite before reordering.'));
}
function launcherOptions() {
  if (dialog.open) return;
  search.blur();
  menu('Launcher options', [['Widgets', widgetSettings], ['Launcher settings', launcherSettings]], undefined, true);
}
function widgetSettings() {
  menu('Widgets', [['Add widget', () => menu('Add widget', [...demoWidgets.keys()].filter(name => !widgets.includes(name)).map(name => [name, () => {
    widgets.push(name); update(); widgetSettings();
  }]), widgets.length >= demoWidgets.size ? hint('All demo widgets are already added.') : undefined)],
  ...widgets.map(name => [`Manage ${name}`, () => widgetMenu(name)])]);
}
function launcherSettings() {
  menu('Launcher settings', [
    ['Icon style', () => appearanceSetting('Icon style', 'iconStyle', ['Original', 'Monochrome', 'Initials'])],
    ['Icon size', () => appearanceSetting('Icon size', 'iconSize', ['Small', 'Default', 'Large'], 1)],
    ['Icon shape', () => appearanceSetting('Icon shape', 'iconShape', ['Original', 'Rounded square', 'Circle', 'Square'])],
    ['Font size', () => appearanceSetting('Font size', 'fontSize', ['Small', 'Default', 'Large', 'Extra large'], 1)],
    ['Font shape', () => appearanceSetting('Font shape', 'fontShape', ['System', 'Rounded', 'Serif', 'Monospace'])],
    ['Hidden apps', () => appChoices('Hidden apps', 'Checked apps are hidden from Still.', hidden, values => { hidden = values; })],
    ['App lock', () => appChoices('App lock', 'Preview only. The native app uses your Android screen lock.', locked, values => { locked = values; })],
    ['Reorder Favorites', () => {
      const visible = favorites.filter(name => !hidden.includes(name));
      menu('Reorder Favorites', visible.map(name => [name, () => favoriteMenu(name)]), visible.length ? undefined : hint('Star apps to add them to Favorites.'));
    }],
  ]);
}
function appearanceSetting(title, key, labels, fallback = 0) {
  const checked = appearance[key] ?? fallback;
  menu(title, labels.map((label, index) => [`${index === checked ? '✓  ' : ''}${label}`, () => {
    appearance[key] = index; update(); launcherSettings();
  }]));
}
function appChoices(title, detail, selected, apply) {
  const values = new Set(selected), content = document.createElement('div'), choices = document.createElement('div');
  choices.className = 'app-choices';
  for (const [name] of all) {
    const label = document.createElement('label'), input = document.createElement('input');
    input.type = 'checkbox'; input.checked = values.has(name);
    input.onchange = () => { if (input.checked) values.add(name); else values.delete(name); };
    label.append(input, name); choices.append(label);
  }
  content.append(hint(detail), choices);
  menu(title, [['Save', () => { apply([...values]); update(); }]], content);
}
function openDemo(name) {
  $('toast').textContent = `${name} · Demo app`;
  clearTimeout(timer); timer = setTimeout(() => $('toast').textContent = '', 2200);
}
function makeRow(name, icon, inFavorites = false) {
  const row = document.createElement('div'); row.className = 'app-row';
  const isLocked = locked.includes(name);
  const open = button(`${name}${isLocked ? '  🔒' : ''}`, () => {
    if (!isLocked) openDemo(name);
    else menu(`Unlock ${name}`, [['Simulate unlock', () => openDemo(name)]], hint('Preview only. No device credential is requested here.'));
  }, `${name}${isLocked ? ', locked' : ''}`);
  open.className = 'app';
  const glyph = document.createElement('span'); glyph.className = 'icon';
  glyph.textContent = appearance.iconStyle === 2 ? [...name][0] : icon;
  glyph.style.setProperty('--icon-hue', String(name.charCodeAt(0) * 37 % 360));
  glyph.setAttribute('aria-hidden', 'true'); open.prepend(glyph);
  if (inFavorites) open.oncontextmenu = event => { event.preventDefault(); favoriteMenu(name); };
  const selected = favorites.includes(name);
  const toggle = button(selected ? '★' : '☆', () => {
    favorites = selected ? favorites.filter(app => app !== name) : [...favorites, name];
    update();
    const replacement = [...list.querySelectorAll('.favorite-toggle')].find(node => node.dataset.name === name);
    replacement?.focus({preventScroll: true});
  }, `${selected ? 'Unfavorite' : 'Favorite'} ${name}`);
  toggle.className = 'favorite-toggle'; toggle.dataset.name = name; toggle.setAttribute('aria-pressed', selected);
  row.append(open, toggle); return row;
}
function widgetMenu(name) {
  menu(name, [
    ...moves(widgets, name),
    ['Resize height', () => {
      const control = document.createElement('label');
      const amount = document.createElement('span');
      const height = document.createElement('input'); height.type = 'range'; height.min = '50'; height.max = '140'; height.value = heights[name] ?? 56;
      height.setAttribute('aria-label', 'Widget height'); height.oninput = () => { amount.textContent = `${height.value} px tall`; }; height.oninput();
      control.append(amount, height);
      menu('Resize widget', [['Save size', () => { heights[name] = Number(height.value); update(); }]], control);
    }],
    ['Remove widget', () => { widgets = widgets.filter(widget => widget !== name); delete heights[name]; update(); }],
  ]);
}
function widgetRow(name) {
  const widget = document.createElement('div'); widget.className = 'widget';
  widget.style.minHeight = `${heights[name] ?? 56}px`;
  const title = document.createElement('div'); title.className = 'widget-title';
  const label = document.createElement('small'); label.textContent = name;
  const value = document.createElement('strong'); value.textContent = demoWidgets.get(name);
  title.append(label, button('⋯', () => widgetMenu(name), `Manage ${name} widget`));
  widget.append(title, value); return widget;
}
function section(title, shortcut) {
  const group = document.createElement('section');
  const heading = document.createElement('div'); heading.className = 'letter'; heading.textContent = title;
  group.append(heading); list.append(group);
  if (shortcut) rail.append(button(shortcut, () => {
    const top = group.getBoundingClientRect().top - list.getBoundingClientRect().top + list.scrollTop;
    list.scrollTo({top});
  }, shortcut === '★' ? 'Jump to Favorites' : `Jump to ${shortcut}`));
  return group;
}
function hint(value) {
  const note = document.createElement('p'); note.className = 'favorite-hint'; note.textContent = value;
  return note;
}
function render(y) {
  const launcher = $('launcher');
  launcher.dataset.iconStyle = appearance.iconStyle ?? 0;
  launcher.dataset.iconShape = appearance.iconShape ?? 0;
  launcher.style.setProperty('--preview-icon-size', `${[19, 23, 32][appearance.iconSize ?? 1]}px`);
  launcher.style.setProperty('--preview-font-size', `${[11, 13, 16, 19][appearance.fontSize ?? 1]}px`);
  launcher.style.setProperty('--preview-font', ['Arial, sans-serif', 'ui-rounded, "Trebuchet MS", sans-serif', 'Georgia, serif', 'monospace'][appearance.fontShape ?? 0]);
  list.replaceChildren(); rail.replaceChildren();
  const query = filterKey(search.value);
  rail.hidden = Boolean(query);
  const visible = all.filter(([name]) => !hidden.includes(name));
  if (query) {
    const results = visible.filter(([name]) => filterKey(name).includes(query));
    const group = section('SEARCH RESULTS');
    for (const [name, icon] of results) group.append(makeRow(name, icon));
    if (!results.length) group.append(hint('No matching apps.'));
    $('count').textContent = `${results.length} FOUND`;
  } else {
    const group = section('★  FAVORITES', '★');
    const pinned = favorites.filter(name => icons.has(name) && !hidden.includes(name));
    for (const name of pinned) group.append(makeRow(name, icons.get(name), true));
    if (!pinned.length) group.append(hint('Tap an app’s star to pin it here.'));
    for (const name of widgets) if (demoWidgets.has(name)) group.append(widgetRow(name));
    for (const [letter, entries] of Object.entries(apps)) {
      const shown = entries.filter(([name]) => !hidden.includes(name));
      if (!shown.length) continue;
      const group = section(letter, letter);
      for (const [name, icon] of shown) group.append(makeRow(name, icon));
    }
    if (!visible.length) list.append(hint('Restore apps from Launcher settings.'));
    $('count').textContent = `${visible.length} APPS`;
  }
  list.scrollTop = y;
}
function searchChanged() {
  const next = Boolean(filterKey(search.value));
  if (!filtering && next) beforeSearch = list.scrollTop;
  filtering = next; $('clear-search').hidden = !search.value;
  render(next ? 0 : beforeSearch);
}
search.addEventListener('input', searchChanged);
$('clear-search').onclick = () => { search.value = ''; searchChanged(); search.focus({preventScroll: true}); };
const launcher = $('launcher'), emptyArea = $('empty-area');
let press;
function cancelPress() {
  if (!press) return;
  clearTimeout(press.timer); press = null;
}
function isBlank(target) {
  return target === emptyArea || !target.closest('button, input, label, .app-row, .widget, nav');
}
launcher.addEventListener('pointerdown', event => {
  if (!event.isPrimary || event.button !== 0 || !isBlank(event.target)) return;
  cancelPress();
  press = {x: event.clientX, y: event.clientY, timer: setTimeout(() => { cancelPress(); launcherOptions(); }, 500)};
});
for (const event of ['resize', 'scroll']) window.addEventListener(event, () => { if (dialog.open) alignDialog(); }, {passive: true});
window.addEventListener('pointermove', event => {
  if (press && Math.hypot(event.clientX - press.x, event.clientY - press.y) > 10) cancelPress();
});
for (const event of ['pointerup', 'pointercancel', 'blur']) window.addEventListener(event, cancelPress);
list.addEventListener('scroll', cancelPress, {passive: true});
launcher.addEventListener('contextmenu', event => {
  if (isBlank(event.target)) { event.preventDefault(); cancelPress(); launcherOptions(); }
});
emptyArea.onclick = event => { if (event.detail === 0) launcherOptions(); };
dialog.addEventListener('click', event => {
  if (event.target !== dialog) return;
  const rect = dialog.getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
});
function showWelcome(show) {
  $('welcome').hidden = !show; $('launcher').hidden = show;
  $('toast').textContent = '';
}
render(0);
showWelcome(!sessionStorage.getItem('still-welcomed'));
$('begin').onclick = () => { sessionStorage.setItem('still-welcomed', '1'); showWelcome(false); list.focus({preventScroll:true}); };
$('restart').onclick = () => { showWelcome(true); $('begin').focus({preventScroll:true}); };
