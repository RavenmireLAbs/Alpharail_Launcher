const apps = {
 A:[['Alarm','◷'],['Amazon','a'],['Authenticator','⬡']], B:[['Books','▤'],['Browser','◎']], C:[['Calculator','▦'],['Calendar','▣'],['Camera','◉'],['Clock','◷'],['Contacts','♙']], D:[['Downloads','⇩'],['Drive','△']], F:[['Files','▱']], G:[['Gallery','▧'],['Gmail','✉']], K:[['Keep','◇']], M:[['Maps','⌖'],['Messages','▰'],['Music','♫']], N:[['Notes','≡']], P:[['Phone','☎'],['Photos','✿'],['Play Store','▷']], R:[['Recorder','●']], S:[['Settings','⚙'],['Spotify','≋']], T:[['Tasks','✓']], W:[['Weather','☀'],['WhatsApp','◌']], Y:[['YouTube','▶']]
};
const $ = id => document.getElementById(id);
const list = $('list'), rail = $('alphabet'), search = $('search'), dialog = $('menu');
const all = Object.values(apps).flat(), icons = new Map(all);
const demoWidgets = new Map([['Clock', '9:41'], ['Date', 'Thursday, November 19'], ['Weather', '21° · Clear']]);
const read = (key, fallback) => JSON.parse(localStorage.getItem(`still-${key}`) ?? JSON.stringify(fallback));
let favorites = read('favorites', ['Camera', 'Maps', 'Messages']);
let widgets = read('widgets', []), hidden = read('hidden', []), heights = read('widget-heights', {});
let appearance = read('appearance', {}), locked = read('locked', []);
let collections = read('collections', []), stacked = read('stacked-widgets', false), stackWidget = read('stack-widget', null);
let timer, beforeSearch = 0, filtering = false;
const filterKey = value => value.trim().toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '');
function searchResults(entries, query) {
  const tokens = query.split(/\s+/);
  const buckets = [[], [], [], []];
  for (const item of entries) {
    const name = filterKey(item[0]);
    const rank = name === query ? 0 : name.startsWith(query) ? 1 : name.includes(query) ? 2 : tokens.every(word => name.includes(word)) ? 3 : -1;
    if (rank >= 0) buckets[rank].push(item);
  }
  return buckets.flat();
}
function save() {
  for (const [key, value] of Object.entries({favorites, widgets, hidden, 'widget-heights': heights, appearance, locked, collections, 'stacked-widgets': stacked, 'stack-widget': stackWidget})) {
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
  dialog.classList.remove('settings-page');
  dialog.querySelector('.settings-subtitle')?.remove();
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
let undoTimer;
function clearUndo() {
  clearTimeout(undoTimer);
  $('undo-bar').hidden = true;
  $('undo-button').onclick = null;
}
function showUndo(message, restore) {
  clearUndo();
  $('undo-message').textContent = message;
  $('undo-bar').hidden = false;
  $('undo-button').onclick = () => { clearUndo(); restore(); };
  undoTimer = setTimeout(clearUndo, 6500);
}
const sampleShortcuts = new Map([['Messages', 'New message'], ['Camera', 'Take photo'], ['Maps', 'Search map']]);
function appMenu(name, inFavorites) {
  const items = [['App info', () => menu('App info', [], hint(`${name} is a demo app. On Android this opens system App info.`))]];
  const shortcut = sampleShortcuts.get(name);
  if (shortcut) items.push([shortcut, () => openDemoTarget(name, shortcut)]);
  if (inFavorites) items.push(...moves(favorites, name, favorites.filter(app => !hidden.includes(app))));
  menu(name, items);
}
const appearanceMax = {iconStyle:3, iconSize:2, iconShape:3, fontSize:3, fontShape:3, railSide:1, theme:1};
function backupMenu() {
  const download = preset => {
    const backup = JSON.stringify(preset ? {format:'still-preview-appearance', version:1, appearance}
      : {format:'still-preview-settings', version:2, favorites, hidden, appearance, collections, stacked}, null, 2);
    const url = URL.createObjectURL(new Blob([backup], {type:'application/json'}));
    const link = document.createElement('a'); link.href = url; link.download = preset ? 'still-preview-appearance.json' : 'still-preview-settings.json'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
  menu('Backup and restore', [['Export settings', () => download(false)], ['Export appearance preset', () => download(true)],
    ['Import backup or preset', () => $('backup-file').click()]], hint('Preview backups are separate from Android backups. Widgets and App Lock stay unchanged.'));
}
$('backup-file').onchange = async event => {
  const file = event.target.files[0]; event.target.value = '';
  if (!file) return;
  let backup, preset, extended;
  try {
    if (file.size > 1024 * 1024) throw new Error();
    backup = JSON.parse(await file.text());
    preset = backup?.format === 'still-preview-appearance' && backup.version === 1;
    extended = backup?.format === 'still-preview-settings' && backup.version === 2;
    if (!preset && !extended && !(backup?.format === 'still-preview-settings' && backup.version === 1)) throw new Error();
    if (extended && (typeof backup.stacked !== 'boolean' || !Array.isArray(backup.collections) || backup.collections.length > 12 ||
      !backup.collections.every(group => typeof group?.name === 'string' && group.name.trim() && group.name.length <= 40 &&
        Array.isArray(group.apps) && group.apps.every(name => icons.has(name))))) throw new Error();
    for (const key of preset ? [] : ['favorites', 'hidden']) {
      if (!Array.isArray(backup[key]) || !backup[key].every(name => icons.has(name))) throw new Error();
    }
    if (!backup.appearance || Array.isArray(backup.appearance) || typeof backup.appearance !== 'object') throw new Error();
    for (const [key, value] of Object.entries(backup.appearance)) {
      if (!Object.hasOwn(appearanceMax, key) || !Number.isInteger(value) || value < 0 || value > appearanceMax[key]) throw new Error();
    }
  } catch {
    menu('Could not restore settings', [], hint('Choose a valid Still preview backup.')); return;
  }
  menu(preset ? 'Apply appearance preset?' : 'Restore settings?', [[preset ? 'Apply' : 'Restore', () => {
    clearUndo();
    if (!preset) { favorites = [...new Set(backup.favorites)]; hidden = [...new Set(backup.hidden)]; }
    if (extended) {
      collections = backup.collections.map(({name, apps}) => ({name, apps: [...new Set(apps)]})); stacked = backup.stacked;
    }
    appearance = backup.appearance; update();
  }]], hint(preset ? 'Apply theme, icons, fonts and rail settings only?'
    : `Replace Favorites, hidden apps and appearance${extended ? ', plus Collections and the widget stack preference' : ''}? Widgets and App Lock stay unchanged.`));
};
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
  [stacked ? 'Show widgets separately' : 'Stack widgets', () => { stacked = !stacked; update(); }],
  ...widgets.map(name => [`Manage ${name}`, () => widgetMenu(name)])]);
}
function previewTheme() {
  let selected = appearance.theme ?? 0;
  const controls = document.createElement('div'); controls.className = 'theme-controls';
  const mock = document.createElement('div'); mock.className = 'theme-sample';
  const time = document.createElement('strong'); time.textContent = '9:41';
  const apps = document.createElement('p'); apps.textContent = '★ Favorites\nCamera\nMessages';
  mock.append(time, apps);
  const choices = ['White', 'Black'].map((name, index) => button(name, () => { selected = index; redraw(); }));
  const redraw = () => {
    mock.dataset.theme = selected === 1 ? 'black' : 'white';
    choices.forEach((option,index) => option.setAttribute('aria-pressed', index === selected));
  };
  controls.append(...choices, mock); redraw();
  menu('Preview theme', [['Apply', () => { appearance.theme = selected; update(); }], ['Cancel', () => {}]], controls);
}
const settingCards = {
  Theme: ['◐', () => appearance.theme === 1 ? 'Black palette' : 'White palette'],
  Icons: ['▦', 'Style, size and shape'], Fonts: ['Aa', 'Typeface and text size'],
  'App list': ['A–Z', 'Favorites and alphabet placement'], Privacy: ['◇', 'Hidden apps and App Lock'],
  'Backup and restore': ['⇅', 'Move your settings to another device'], Back: ['‹', 'Launcher settings'],
};
function settingsPage(title, entries, subtitle) {
  const intro = document.createElement('p'); intro.className = 'settings-subtitle'; intro.textContent = subtitle;
  menu(title, entries);
  dialog.classList.add('settings-page');
  $('menu-title').after(intro);
  for (const node of $('menu-items').querySelectorAll('button')) {
    const label = node.textContent, [mark, detail] = settingCards[label] ?? ['·', ''];
    const icon = document.createElement('span'); icon.className = 'settings-glyph'; icon.textContent = mark; icon.setAttribute('aria-hidden', 'true');
    const copy = document.createElement('span'); copy.className = 'settings-copy';
    const name = document.createElement('strong'); name.textContent = label; copy.append(name);
    if (detail) { const desc = document.createElement('small'); desc.textContent = typeof detail === 'function' ? detail() : detail; copy.append(desc); }
    const arrow = document.createElement('span'); arrow.className = 'settings-arrow'; arrow.textContent = '›'; arrow.setAttribute('aria-hidden', 'true');
    node.replaceChildren(icon, copy, arrow);
  }
}
function launcherSettings() {
  settingsPage('Launcher settings', [
    ['Theme', previewTheme],
    ['Icons', iconSettings],
    ['Fonts', fontSettings],
    ['App list', listSettings],
    ['Privacy', privacySettings],
    ['Backup and restore', backupMenu],
  ], 'Make Still yours');
}
function settingsGroup(title, entries) { settingsPage(title, [...entries, ['Back', launcherSettings]], 'Customize your launcher'); }
function iconSettings() {
  settingsGroup('Icons', [
    ['Icon style', () => appearanceSetting('Icon style', 'iconStyle', ['Original', 'Monochrome', 'Initials', 'Dots'], 0, iconSettings)],
    ['Icon size', () => appearanceSetting('Icon size', 'iconSize', ['Small', 'Default', 'Large'], 1, iconSettings)],
    ['Icon shape', () => appearanceSetting('Icon shape', 'iconShape', ['Original', 'Rounded square', 'Circle', 'Square'], 0, iconSettings)],
  ]);
}
function fontSettings() {
  settingsGroup('Fonts', [
    ['Font size', () => appearanceSetting('Font size', 'fontSize', ['Small', 'Default', 'Large', 'Extra large'], 1, fontSettings)],
    ['Typeface', () => appearanceSetting('Typeface', 'fontShape', ['System', 'Rounded', 'Serif', 'Monospace'], 0, fontSettings)],
  ]);
}
function privacySettings() {
  settingsGroup('Privacy', [
    ['Hidden apps', () => appChoices('Hidden apps', 'Checked apps are hidden from Still.', hidden, values => {
      const added = values.filter(name => !hidden.includes(name)); hidden = values;
      if (added.length) showUndo(added.length === 1 ? 'App hidden' : 'Apps hidden', () => {
        hidden = hidden.filter(name => !added.includes(name)); update();
      });
    })],
    ['App lock', () => appChoices('App lock', 'Preview only. The native app uses your Android screen lock.', locked, values => { locked = values; })],
  ]);
}
function listSettings() {
  settingsGroup('App list', [
    ['Collections', manageCollections],
    ['Alphabet side', () => appearanceSetting('Alphabet side', 'railSide', ['Right', 'Left'], 0, listSettings)],
    ['Reorder Favorites', () => {
      const visible = favorites.filter(name => !hidden.includes(name));
      menu('Reorder Favorites', visible.map(name => [name, () => favoriteMenu(name)]), visible.length ? undefined : hint('Star apps to add them to Favorites.'));
    }],
  ]);
}
function manageCollections() {
  const entries = collections.length < 12 ? [['New collection', () => editCollection(-1)]] : [];
  for (let i = 0; i < collections.length; i++) entries.push([collections[i].name, () => menu(collections[i].name, [
    ['Edit', () => editCollection(i)], ['Remove collection', () => menu('Remove collection?', [['Remove', () => {
      collections.splice(i, 1); update(); manageCollections();
    }]], hint('Its apps stay installed.'))],
  ])]);
  menu('Collections', entries);
}
function editCollection(index) {
  const item = collections[index] ?? {name:'', apps:[]}, chosen = new Set(item.apps);
  const content = document.createElement('div');
  const name = document.createElement('input'); name.type = 'text'; name.maxLength = 40; name.value = item.name;
  name.className = 'collection-name'; name.placeholder = 'Collection name'; name.setAttribute('aria-label', 'Collection name');
  const apps = document.createElement('div'); apps.className = 'app-choices';
  for (const [app] of all) if (!hidden.includes(app)) {
    const label = document.createElement('label'), check = document.createElement('input');
    check.type = 'checkbox'; check.checked = chosen.has(app);
    check.onchange = () => { if (check.checked) chosen.add(app); else chosen.delete(app); };
    label.append(check, app); apps.append(label);
  }
  content.append(name, apps);
  menu(index < 0 ? 'New collection' : 'Edit collection', [], content);
  const error = hint(''); error.setAttribute('role', 'status'); content.append(error);
  $('menu-items').append(button('Save collection', () => {
    const title = name.value.trim();
    if (!title || !chosen.size) { error.textContent = 'Enter a name and choose at least one app.'; return; }
    const group = {name:title, apps:[...chosen]};
    if (index < 0) collections.push(group); else collections[index] = group;
    dialog.close(); update(); manageCollections();
  }));
}
function openCollection(group) {
  const visible = group.apps.filter(name => icons.has(name) && !hidden.includes(name));
  menu(group.name, visible.map(name => [name, () => openDemoTarget(name)]), visible.length ? undefined : hint('No visible apps in this collection.'));
}
function appearanceSetting(title, key, labels, fallback, parent) {
  const checked = appearance[key] ?? fallback;
  menu(title, [...labels.map((label, index) => [`${index === checked ? '✓  ' : ''}${label}`, () => {
    appearance[key] = index; update(); parent();
  }]), ['Back', parent]]);
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
function openDemoTarget(name, shortcut) {
  const run = () => openDemo(shortcut ? `${name}: ${shortcut}` : name);
  if (!locked.includes(name)) run();
  else menu(`Unlock ${name}`, [['Simulate unlock', run]], hint('Preview only. No device credential is requested here.'));
}
function makeRow(name, icon, inFavorites = false) {
  const row = document.createElement('div'); row.className = 'app-row';
  const isLocked = locked.includes(name);
  const open = button(`${name}${isLocked ? '  🔒' : ''}`, () => openDemoTarget(name), `${name}${isLocked ? ', locked' : ''}`);
  open.dataset.app = name;
  open.dataset.favoriteRow = inFavorites ? 'yes' : 'no';
  open.className = 'app';
  const glyph = document.createElement('span'); glyph.className = 'icon';
  glyph.textContent = appearance.iconStyle === 3 ? '●' : appearance.iconStyle === 2 ? [...name][0] : icon;
  glyph.style.setProperty('--icon-hue', String(name.charCodeAt(0) * 37 % 360));
  glyph.setAttribute('aria-hidden', 'true'); open.prepend(glyph);

  const selected = favorites.includes(name);
  const toggle = button(selected ? '★' : '☆', () => {
    const oldIndex = favorites.indexOf(name);
    favorites = selected ? favorites.filter(app => app !== name) : [...favorites, name];
    update();
    if (selected) showUndo('Removed from Favorites', () => {
      if (!favorites.includes(name)) favorites.splice(oldIndex, 0, name);
      update();
    });
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
function widgetStack(visible) {
  const panel = document.createElement('section'); panel.className = 'widget-stack';
  const controls = document.createElement('div'); controls.className = 'stack-controls';
  const status = document.createElement('span'); status.setAttribute('aria-live', 'polite');
  const surface = document.createElement('div'); surface.className = 'stack-swipe';
  let index = Math.max(0, visible.indexOf(stackWidget)), pointer;
  function flip(step) {
    index = (index + step + visible.length) % visible.length;
    if (step) { stackWidget = visible[index]; save(); }
    status.textContent = `Widget ${index + 1} / ${visible.length}`;
    surface.replaceChildren(widgetRow(visible[index]));
  }
  controls.append(button('‹', () => flip(-1), 'Previous widget'), status, button('›', () => flip(1), 'Next widget'));
  surface.onpointerdown = event => {
    if (!event.isPrimary || event.button !== 0 || event.target.closest('button')) return;
    pointer = [event.clientX, event.clientY, event.pointerId]; surface.setPointerCapture(event.pointerId);
  };
  surface.onpointercancel = () => { pointer = undefined; };
  surface.onpointerup = event => {
    if (!pointer || event.pointerId !== pointer[2]) return;
    const dx = event.clientX - pointer[0], dy = event.clientY - pointer[1]; pointer = undefined;
    if (Math.abs(dx) > 30 && Math.abs(dx) > Math.abs(dy) * 1.4) flip(dx < 0 ? 1 : -1);
  };
  panel.append(controls, surface); flip(0); return panel;
}
let railPointer = null, activeLetter = null, hideBubbleTimer;
const bubble = $('letter-bubble'), reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const railHeadings = new WeakMap();
function clearLetter() {
  clearTimeout(hideBubbleTimer);
  activeLetter?.classList.remove('active-letter'); activeLetter = null;
  bubble.classList.remove('visible');
}
function selectLetter(letter, animate = false) {
  if (!letter || letter === activeLetter) return;
  clearTimeout(hideBubbleTimer);
  const group = railHeadings.get(letter);
  const top = group.getBoundingClientRect().top - list.getBoundingClientRect().top + list.scrollTop;
  list.scrollTo({top, behavior: animate && !reducedMotion.matches ? 'smooth' : 'instant'});
  activeLetter?.classList.remove('active-letter'); activeLetter = letter;
  letter.classList.add('active-letter'); bubble.textContent = letter.textContent;
  const area = bubble.parentElement.getBoundingClientRect(), anchor = letter.getBoundingClientRect();
  bubble.style.top = `${Math.max(0, Math.min(area.height - 58, anchor.top - area.top + (anchor.height - 58) / 2))}px`;
  bubble.classList.add('visible');
  if (!reducedMotion.matches) bubble.animate([{transform:'scale(.84)'}, {transform:'scale(1)'}], {duration:135, easing:'ease-out'});
}
function scrubRail(y) {
  const letter = [...rail.children].find(node => y < node.getBoundingClientRect().bottom) ?? rail.lastElementChild;
  selectLetter(letter);
}
rail.addEventListener('pointerdown', event => {
  if (railPointer !== null || event.button !== 0) return;
  event.preventDefault(); railPointer = event.pointerId;
  rail.setPointerCapture(event.pointerId); scrubRail(event.clientY);
});
rail.addEventListener('pointermove', event => { if (railPointer === event.pointerId) scrubRail(event.clientY); });
for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) rail.addEventListener(type, event => {
  if (railPointer !== event.pointerId) return;
  railPointer = null; hideBubbleTimer = setTimeout(clearLetter, 180);
});
function section(title, shortcut) {
  const group = document.createElement('section');
  const heading = document.createElement('div'); heading.className = 'letter'; heading.textContent = title;
  group.append(heading); list.append(group);
  if (shortcut) {
    const jump = button(shortcut, event => {
      if (event.detail !== 0) return;
      selectLetter(jump, true);
      clearTimeout(hideBubbleTimer); hideBubbleTimer = setTimeout(clearLetter, 420);
    }, shortcut === '★' ? 'Jump to Favorites' : `Jump to ${shortcut}`);
    railHeadings.set(jump, group); rail.append(jump);
  }
  return group;
}
function hint(value) {
  const note = document.createElement('p'); note.className = 'favorite-hint'; note.textContent = value;
  return note;
}
function render(y) {
  const launcher = $('launcher');
  document.querySelector('.stage').dataset.theme = appearance.theme === 1 ? 'black' : 'white';
  launcher.dataset.railSide = appearance.railSide === 1 ? 'left' : 'right';
  launcher.dataset.iconStyle = appearance.iconStyle ?? 0;
  launcher.dataset.iconShape = appearance.iconShape ?? 0;
  launcher.style.setProperty('--preview-icon-size', `${[19, 23, 32][appearance.iconSize ?? 1]}px`);
  launcher.style.setProperty('--preview-font-size', `${[11, 13, 16, 19][appearance.fontSize ?? 1]}px`);
  launcher.style.setProperty('--preview-font', ['Arial, sans-serif', 'ui-rounded, "Trebuchet MS", sans-serif', 'Georgia, serif', 'monospace'][appearance.fontShape ?? 0]);
  clearLetter(); railPointer = null;
  list.replaceChildren(); rail.replaceChildren();
  const query = filterKey(search.value);
  rail.hidden = Boolean(query);
  const visible = all.filter(([name]) => !hidden.includes(name));
  if (query) {
    const results = searchResults(visible, query);
    const group = section('SEARCH RESULTS');
    for (const [name, icon] of results) group.append(makeRow(name, icon));
    if (!results.length) group.append(hint('No matching apps.'));
    $('count').textContent = `${results.length} FOUND`;
  } else {
    const group = section('★  FAVORITES', '★');
    const pinned = favorites.filter(name => icons.has(name) && !hidden.includes(name));
    for (const name of pinned) group.append(makeRow(name, icons.get(name), true));
    if (!pinned.length) group.append(hint('Tap an app’s star to pin it here.'));
    for (const item of collections) {
      const entry = button(`▦   ${item.name}  ›`, () => openCollection(item), `Open collection ${item.name}`);
      entry.className = 'collection-row'; group.append(entry);
    }
    const visibleWidgets = widgets.filter(name => demoWidgets.has(name));
    if (stacked && visibleWidgets.length > 1) group.append(widgetStack(visibleWidgets));
    else for (const name of visibleWidgets) group.append(widgetRow(name));
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
search.addEventListener('keydown', event => {
  const query = filterKey(search.value);
  if (event.key !== 'Enter' || event.isComposing || event.repeat || !query) return;
  event.preventDefault();
  const match = searchResults(all.filter(([name]) => !hidden.includes(name)), query)[0];
  if (match) { search.blur(); openDemoTarget(match[0]); }
});
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
let suppressAppClick;
launcher.addEventListener('pointerdown', event => {
  if (!event.isPrimary || event.button !== 0) return;
  const app = event.target.closest('.app');
  if (!app && !isBlank(event.target)) return;
  cancelPress(); suppressAppClick = undefined;
  press = {x: event.clientX, y: event.clientY, timer: setTimeout(() => {
    cancelPress();
    if (app) { suppressAppClick = app; appMenu(app.dataset.app, app.dataset.favoriteRow === 'yes'); }
    else launcherOptions();
  }, 500)};
});
launcher.addEventListener('click', event => {
  if (suppressAppClick && event.detail && event.target.closest('.app') === suppressAppClick) {
    event.preventDefault(); event.stopImmediatePropagation(); suppressAppClick = undefined;
  }
}, true);
for (const event of ['resize', 'scroll']) window.addEventListener(event, () => { if (dialog.open) alignDialog(); }, {passive: true});
window.addEventListener('pointermove', event => {
  if (press && Math.hypot(event.clientX - press.x, event.clientY - press.y) > 10) cancelPress();
});
for (const event of ['pointerup', 'pointercancel', 'blur']) window.addEventListener(event, cancelPress);
list.addEventListener('scroll', cancelPress, {passive: true});
launcher.addEventListener('contextmenu', event => {
  const app = event.target.closest('.app');
  if (app) {
    event.preventDefault(); cancelPress();
    if (!dialog.open) appMenu(app.dataset.app, app.dataset.favoriteRow === 'yes');
  } else if (isBlank(event.target)) { event.preventDefault(); cancelPress(); launcherOptions(); }
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
  if (show) clearUndo();
}
render(0);
showWelcome(!sessionStorage.getItem('still-welcomed'));
$('begin').onclick = () => { sessionStorage.setItem('still-welcomed', '1'); showWelcome(false); list.focus({preventScroll:true}); };
$('restart').onclick = () => { showWelcome(true); $('begin').focus({preventScroll:true}); };

// The preview uses fictional notifications and never asks for Android access.
const chatSamples = [
  {app:'WhatsApp', sender:'Sam', message:'Are we still meeting at six?', reply:true},
  {app:'Telegram', sender:'Alex', message:'I sent the details to the group.', reply:true},
  {app:'Instagram', sender:'Jordan', message:'Sent you a photo.', reply:false},
  {app:'Messenger', sender:'Taylor', message:'Thanks for checking in!', reply:true},
];
let chatEnabled = read('chats-enabled', false), chatPreviews = read('chat-previews', false), chatExcluded = read('chat-excluded', []);
function saveChats() {
  localStorage.setItem('still-chats-enabled', JSON.stringify(chatEnabled));
  localStorage.setItem('still-chat-previews', JSON.stringify(chatPreviews));
  localStorage.setItem('still-chat-excluded', JSON.stringify(chatExcluded));
}
function showChats() {
  if (!chatEnabled) {
    menu('Chats', [['Enable demo Chats', () => { chatEnabled = true; saveChats(); showChats(); }]], hint('See message notifications in one place. This preview uses fictional messages. Android requires optional notification access.'));
    return;
  }
  const rows = document.createElement('div'); rows.className = 'chat-rows';
  rows.append(hint('Demo inbox · Active notifications only'));
  const visible = chatSamples.filter(chat => !chatExcluded.includes(chat.app) && !hidden.includes(chat.app) && !locked.includes(chat.app));
  for (const chat of visible) {
    const row = document.createElement('section'); row.className = 'chat-row';
    const name = document.createElement('strong'); name.textContent = chat.app;
    const detail = hint(chatPreviews ? `${chat.sender}: ${chat.message}` : 'Message preview hidden');
    const actions = document.createElement('div'); actions.className = 'chat-actions';
    actions.append(button('Open app', () => { dialog.close(); openDemo(chat.app); }, `Open ${chat.app}`));
    actions.append(button('Open chat', () => { dialog.close(); openDemo(`${chat.app}: ${chat.sender}`); }, `Open chat in ${chat.app}`));
    if (chat.reply) actions.append(button('Reply', () => showDemoReply(chat), `Reply via ${chat.app}`));
    row.append(name, detail, actions); rows.append(row);
  }
  if (!visible.length) rows.append(hint('No active messages from included apps.'));
  menu('Chats', [['Chat settings', chatSettings]], rows);
}
function showDemoReply(chat) {
  const text = document.createElement('textarea'); text.placeholder = 'Write a text reply'; text.setAttribute('aria-label', 'Reply text');
  menu(`Reply via ${chat.app}`, [['Send demo reply', () => {
    if (!text.value.trim()) { showChats(); return; }
    $('toast').textContent = `Demo reply to ${chat.app}`;
    clearTimeout(timer); timer = setTimeout(() => $('toast').textContent = '', 2200);
  }], ['Back to Chats', showChats]], text);
  text.focus();
}
function chatSettings() {
  menu('Chat settings', [
    [chatPreviews ? 'Hide message previews' : 'Show message previews', () => { chatPreviews = !chatPreviews; saveChats(); showChats(); }],
    ['Included apps', () => {
      const choices = document.createElement('div'); choices.className = 'app-choices';
      const excluded = new Set(chatExcluded);
      for (const chat of chatSamples) {
        const label = document.createElement('label'), input = document.createElement('input');
        input.type = 'checkbox'; input.checked = !excluded.has(chat.app);
        input.onchange = () => { if (input.checked) excluded.delete(chat.app); else excluded.add(chat.app); };
        label.append(input, chat.app); choices.append(label);
      }
      menu('Included apps', [['Save', () => { chatExcluded = [...excluded]; saveChats(); showChats(); }]], choices);
    }],
    ['Turn off Chats', () => { chatEnabled = false; chatPreviews = false; saveChats(); }],
  ]);
}
$('chats').onclick = showChats;
$('home-clock').onclick = () => openDemoTarget('Clock');

$('home-date').onclick = () => openDemoTarget('Calendar');
