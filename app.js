const apps = {
 A:[['Alarm','◷'],['Amazon','a'],['Authenticator','⬡']], B:[['Books','▤'],['Browser','◎']], C:[['Calculator','▦'],['Calendar','▣'],['Camera','◉'],['Clock','◷'],['Contacts','♙']], D:[['Downloads','⇩'],['Drive','△']], F:[['Files','▱']], G:[['Gallery','▧'],['Gmail','✉']], K:[['Keep','◇']], M:[['Maps','⌖'],['Messages','▰'],['Music','♫']], N:[['Notes','≡']], P:[['Phone','☎'],['Photos','✿'],['Play Store','▷']], R:[['Recorder','●']], S:[['Settings','⚙'],['Spotify','≋']], T:[['Tasks','✓']], W:[['Weather','☀'],['WhatsApp','◌']], Y:[['YouTube','▶']]
};
const $ = id => document.getElementById(id);
const list = $('list');
let timer;
$('count').textContent = `${Object.values(apps).flat().length} APPS`;
const favorites = new Set(JSON.parse(localStorage.getItem('still-favorites') ?? '["Camera","Maps","Messages"]'));
const widgets = new Set(JSON.parse(localStorage.getItem('still-widgets') ?? '[]'));
const favoriteSection = document.createElement('section');
list.append(favoriteSection);
const star = document.createElement('button');
star.textContent = '★'; star.setAttribute('aria-label', 'Jump to Favorites');
star.onclick = () => list.scrollTo({top: 0});
$('alphabet').append(star);
const favoriteButtons = new Map();
function makeRow(name, icon, manage = false) {
  const row = document.createElement('div'); row.className = 'app-row';
  const button = document.createElement('button'); button.className = 'app';
  button.innerHTML = `<span class="icon" aria-hidden="true">${icon}</span>${name}<span class="arrow" aria-hidden="true">↗</span>`;
  button.onclick = () => {
    $('toast').textContent = `${name} · Demo app`;
    clearTimeout(timer); timer = setTimeout(() => $('toast').textContent = '', 2200);
  };
  row.append(button);
  if (manage) {
    const toggle = document.createElement('button'); toggle.className = 'favorite-toggle';
    toggle.setAttribute('aria-label', `Favorite ${name}`);
    favoriteButtons.set(name, toggle);
    toggle.onclick = () => {
      if (!favorites.delete(name)) favorites.add(name);
      localStorage.setItem('still-favorites', JSON.stringify([...favorites]));
      renderFavorites();
    };
    row.append(toggle);
  }
  return row;
}
function renderFavorites() {
  favoriteSection.innerHTML = '<div class="letter">★ &nbsp; FAVORITES</div>';
  for (const [name, icon] of Object.values(apps).flat()) {
    const selected = favorites.has(name);
    if (selected) favoriteSection.append(makeRow(name, icon));
    const toggle = favoriteButtons.get(name);
    toggle.textContent = selected ? '★' : '☆';
    toggle.setAttribute('aria-pressed', selected);
  }
  if (!favorites.size) favoriteSection.insertAdjacentHTML('beforeend', '<p class="favorite-hint">Tap the star next to an app to add it here.</p>');
  for (const [name, value] of [['Clock', '9:41'], ['Date', 'Thursday, November 19']]) {
    if (widgets.has(name)) favoriteSection.insertAdjacentHTML('beforeend', `<div class="widget"><small>${name}</small><strong>${value}</strong></div>`);
  }
  const picker = document.createElement('details'); picker.className = 'widget-picker';
  picker.innerHTML = '<summary>Manage widgets</summary>';
  for (const name of ['Clock', 'Date']) {
    const label = document.createElement('label');
    const input = document.createElement('input'); input.type = 'checkbox'; input.checked = widgets.has(name);
    input.onchange = () => {
      if (input.checked) widgets.add(name); else widgets.delete(name);
      localStorage.setItem('still-widgets', JSON.stringify([...widgets]));
      renderFavorites();
      const updated = favoriteSection.querySelector('details'); updated.open = true;
      updated.querySelectorAll('input')[name === 'Clock' ? 0 : 1].focus();
    };
    label.append(input, name); picker.append(label);
  }
  favoriteSection.append(picker);
}
for (const [letter, entries] of Object.entries(apps)) {
  const group = document.createElement('section');
  const heading = document.createElement('div');
  heading.className = 'letter'; heading.textContent = letter;
  group.append(heading);
  for (const [name, icon] of entries) group.append(makeRow(name, icon, true));
  list.append(group);
  const jump = document.createElement('button'); jump.textContent = letter;
  jump.setAttribute('aria-label', `Jump to ${letter}`);
  jump.onclick = () => list.scrollTo({top:group.offsetTop - list.offsetTop});
  $('alphabet').append(jump);
}
renderFavorites();
function showWelcome(show) {
  $('welcome').hidden = !show; $('launcher').hidden = show;
  $('toast').textContent = '';
}
showWelcome(!sessionStorage.getItem('still-welcomed'));
$('begin').onclick = () => {sessionStorage.setItem('still-welcomed', '1'); showWelcome(false); list.focus({preventScroll:true});};
$('restart').onclick = () => {showWelcome(true); $('begin').focus({preventScroll:true});};
