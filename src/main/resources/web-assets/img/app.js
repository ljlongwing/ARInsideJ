/*
 * ARInsideJ generated-site core script. Plain ES (no modules - must run from file://).
 * Loaded on every page with `defer`. Responsibilities: theme toggle, sidebar nav render,
 * global search, table sort, tab widget, letter-filter + clearable-input glue.
 */
(function () {
  "use strict";

  var ROOT = document.documentElement.getAttribute("data-root") || "";

  /* Icon sprite, inlined here rather than shipped as icons.svg: external-file <use href>
     references are blocked under file:// in Chrome. Injected once, before first paint. */
  var SPRITE = '<svg xmlns="http://www.w3.org/2000/svg" style="display:none" aria-hidden="true">'
    + '<symbol id="i-document" viewBox="0 0 16 16"><path fill="currentColor" d="M4 1h5l3 3v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1zm5 1.5V4h1.5L9 2.5zM5 7h6v1H5V7zm0 2.5h6v1H5v-1zM5 12h4v1H5v-1z"/></symbol>'
    + '<symbol id="i-folder" viewBox="0 0 16 16"><path fill="currentColor" d="M2 3.5A1.5 1.5 0 0 1 3.5 2h3l1.5 1.6H13A1.5 1.5 0 0 1 14.5 5v6A1.5 1.5 0 0 1 13 12.5H3.5A1.5 1.5 0 0 1 2 11V3.5z"/></symbol>'
    + '<symbol id="i-schema" viewBox="0 0 16 16"><path fill="currentColor" d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3zm1.5.5v2h9v-2h-9zm0 3.5v2h4V7h-4zm5.5 0v2h3.5V7H9zm-5.5 3.5v2h4v-2h-4zm5.5 0v2h3.5v-2H9z"/></symbol>'
    + '<symbol id="i-schema-join" viewBox="0 0 16 16"><path fill="currentColor" d="M6 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6zm4 4a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/></symbol>'
    + '<symbol id="i-schema-view" viewBox="0 0 16 16"><path fill="currentColor" d="M8 3.5C4.5 3.5 2 8 2 8s2.5 4.5 6 4.5S14 8 14 8s-2.5-4.5-6-4.5zm0 2A2.5 2.5 0 1 1 8 10.5 2.5 2.5 0 0 1 8 5.5z"/></symbol>'
    + '<symbol id="i-schema-dialog" viewBox="0 0 16 16"><path fill="currentColor" d="M2 4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H8l-3 2.5V11H3a1 1 0 0 1-1-1V4z"/></symbol>'
    + '<symbol id="i-schema-vendor" viewBox="0 0 16 16"><path fill="currentColor" d="M8 1.5l6 3v3c0 3.5-2.4 6-6 6.5-3.6-.5-6-3-6-6.5v-3l6-3zm0 2.2L4 5.4v2.1c0 2.4 1.6 4.2 4 4.7 2.4-.5 4-2.3 4-4.7V5.4L8 3.7z"/></symbol>'
    + '<symbol id="i-active-link" viewBox="0 0 16 16"><path fill="currentColor" d="M9 1L3 9h4l-1 6 7-9H8l1-5z"/></symbol>'
    + '<symbol id="i-al-guide" viewBox="0 0 16 16"><path fill="currentColor" d="M4 2h5l3 3v3H9.5L4 2zm0 4l3.5 3.5V13a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1zm7 1.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z"/></symbol>'
    + '<symbol id="i-filter" viewBox="0 0 16 16"><path fill="currentColor" d="M2 3h12l-4.5 6v4.5L6.5 15V9L2 3z"/></symbol>'
    + '<symbol id="i-filter-guide" viewBox="0 0 16 16"><path fill="currentColor" d="M2 3h10l-3.7 5v3.6L6 13V8L2 3zm10 3.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z"/></symbol>'
    + '<symbol id="i-escalation" viewBox="0 0 16 16"><path fill="currentColor" d="M8 2a6 6 0 1 0 0 12A6 6 0 0 0 8 2zm-.75 2.5h1.5V8l2.6 1.5-.75 1.3L7.25 9V4.5z"/></symbol>'
    + '<symbol id="i-menu" viewBox="0 0 16 16"><path fill="currentColor" d="M2 3.5h12v2H2v-2zm0 4h12v2H2v-2zm0 4h7v2H2v-2zm10.5 3L15 12h-5l2.5 2.5z"/></symbol>'
    + '<symbol id="i-application" viewBox="0 0 16 16"><path fill="currentColor" d="M2 2h5v5H2V2zm7 0h5v5H9V2zM2 9h5v5H2V9zm7 0h5v5H9V9z"/></symbol>'
    + '<symbol id="i-packing-list" viewBox="0 0 16 16"><path fill="currentColor" d="M8 1L2 4v8l6 3 6-3V4L8 1zm0 1.7l4 2-4 2-4-2 4-2zM3.5 5.8L7.3 7.7v5.1L3.5 11V5.8zm9 0V11l-3.8 1.9V7.7l3.8-1.9z"/></symbol>'
    + '<symbol id="i-webservice" viewBox="0 0 16 16"><path fill="currentColor" d="M8 2a6 6 0 1 0 0 12A6 6 0 0 0 8 2zM3.5 8a4.5 4.5 0 0 1 .3-1.6h2.3c-.05.5-.08 1.05-.08 1.6s.03 1.1.08 1.6H3.8A4.5 4.5 0 0 1 3.5 8zm4.5 4.5c-.6 0-1.3-1-1.7-2.4h3.4C9.3 11.5 8.6 12.5 8 12.5zm-1.9-3.9C6.05 9.1 6 8.55 6 8s.05-1.1.1-1.6h3.8c.05.5.1 1.05.1 1.6s-.05 1.1-.1 1.6H6.1zM8 3.5c.6 0 1.3 1 1.7 2.4H6.3C6.7 4.5 7.4 3.5 8 3.5zm2.98 2.9h2.22a4.5 4.5 0 0 1 0 3.2h-2.22c.05-.5.08-1.05.08-1.6s-.03-1.1-.08-1.6z"/></symbol>'
    + '<symbol id="i-image" viewBox="0 0 16 16"><path fill="currentColor" d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3zm1.5.5v6l3-3 2.5 2.5L11 6l1.5 1.5v-4h-9zm0 8.5h9v-1.4L11 8.6 8.9 10.7 6.5 8.3l-3 3V12z"/></symbol>'
    + '<symbol id="i-user" viewBox="0 0 16 16"><path fill="currentColor" d="M8 2a3 3 0 1 1 0 6 3 3 0 0 1 0-6zm0 7c3 0 5.5 1.6 5.5 3.6V14H2.5v-1.4C2.5 10.6 5 9 8 9z"/></symbol>'
    + '<symbol id="i-group" viewBox="0 0 16 16"><path fill="currentColor" d="M5.5 3a2.4 2.4 0 1 1 0 4.8 2.4 2.4 0 0 1 0-4.8zm5.4.6a2 2 0 1 1 0 4 2 2 0 0 1 0-4zM5.5 8.6c2.6 0 4.7 1.3 4.7 3v1.3H.8v-1.3c0-1.7 2.1-3 4.7-3zm5.4.2c2 0 3.9 1 4.3 2.5V13h-3.8v-.4c0-1.2-.6-2.3-1.6-3.1.3-.05.7-.08 1.1-.08z"/></symbol>'
    + '<symbol id="i-role" viewBox="0 0 16 16"><path fill="currentColor" d="M8 1.5l5 2v3.7c0 3-2 5.6-5 6.3-3-.7-5-3.3-5-6.3V3.5l5-2zm0 3a1.8 1.8 0 1 0 0 3.6A1.8 1.8 0 0 0 8 4.5zm0 4.3c-1.7 0-3 .9-3 2v.6h6v-.6c0-1.1-1.3-2-3-2z"/></symbol>'
    + '<symbol id="i-server" viewBox="0 0 16 16"><path fill="currentColor" d="M3 2h10a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zm1.5 2v1h1V4h-1zM3 9h10a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1zm1.5 2v1h1v-1h-1z"/></symbol>'
    + '<symbol id="i-association" viewBox="0 0 16 16"><path fill="currentColor" d="M6.5 4.5a3.5 3.5 0 0 0 0 7H8v-1.6H6.5a1.9 1.9 0 0 1 0-3.8H8V4.5H6.5zm3 0V6.1H11a1.9 1.9 0 0 1 0 3.8H9.5v1.6H11a3.5 3.5 0 0 0 0-7H9.5zM6 7.2h4v1.6H6V7.2z"/></symbol>'
    + '<symbol id="i-hidden" viewBox="0 0 16 16"><path fill="currentColor" d="M2.3 2.3l11.4 11.4-1 1-2-2A7.7 7.7 0 0 1 8 13C4.5 13 2 8 2 8a12 12 0 0 1 2.5-3.2L1.3 3.3l1-1zM8 5.5c.3 0 .6 0 .9.1L6.4 8.1A2.5 2.5 0 0 1 8 5.5zm0 5c-.3 0-.6 0-.9-.1l2.5-2.5A2.5 2.5 0 0 1 8 10.5zm5.4-4.7C13.9 6.6 14 8 14 8s-2.5 5-6 5c-.4 0-.8 0-1.1-.1l1.4-1.4A4 4 0 0 0 12 8a4 4 0 0 0-.1-.9l1.5-1.3z"/></symbol>'
    + '<symbol id="i-visible" viewBox="0 0 16 16"><path fill="currentColor" d="M8 3C4.5 3 2 8 2 8s2.5 5 6 5 6-5 6-5-2.5-5-6-5zm0 2a3 3 0 1 1 0 6 3 3 0 0 1 0-6zm0 1.5A1.5 1.5 0 1 0 8 9.5 1.5 1.5 0 0 0 8 6.5z"/></symbol>'
    + '<symbol id="i-edit" viewBox="0 0 16 16"><path fill="currentColor" d="M11.5 1.7l2.8 2.8-1.4 1.4-2.8-2.8 1.4-1.4zM9.4 3.8l2.8 2.8-6.5 6.5H2.9v-2.8l6.5-6.5z"/></symbol>'
    + '<symbol id="i-up" viewBox="0 0 16 16"><path fill="currentColor" d="M8 4l5 6H3z"/></symbol>'
    + '<symbol id="i-down" viewBox="0 0 16 16"><path fill="currentColor" d="M8 12L3 6h10z"/></symbol>'
    + '<symbol id="i-prev" viewBox="0 0 16 16"><path fill="currentColor" d="M10 3L5 8l5 5z"/></symbol>'
    + '<symbol id="i-next" viewBox="0 0 16 16"><path fill="currentColor" d="M6 3l5 5-5 5z"/></symbol>'
    + '<symbol id="i-sort-asc" viewBox="0 0 16 16"><path fill="currentColor" d="M8 3l4 5H4z"/></symbol>'
    + '<symbol id="i-sort-desc" viewBox="0 0 16 16"><path fill="currentColor" d="M8 13L4 8h8z"/></symbol>'
    + '<symbol id="i-overlay" viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" fill="currentColor"/></symbol>'
    + '<symbol id="i-custom" viewBox="0 0 16 16"><rect x="1" y="1" width="14" height="14" rx="3" fill="currentColor"/></symbol>'
    + '</svg>';
  (function injectSprite() {
    if (document.getElementById("ari-sprite")) return;
    var host = document.createElement("div");
    host.id = "ari-sprite";
    host.style.display = "none";
    host.innerHTML = SPRITE;
    (document.body || document.documentElement).insertBefore(host, (document.body || document.documentElement).firstChild);
  })();

  function el(tag, attrs, kids) {
    var e = document.createElement(tag);
    if (attrs) for (var k in attrs) {
      if (k === "class") e.className = attrs[k];
      else if (k === "text") e.textContent = attrs[k];
      else if (attrs[k] != null) e.setAttribute(k, attrs[k]);
    }
    (kids || []).forEach(function (c) { if (c) e.appendChild(c); });
    return e;
  }

  function iconSvg(name, cls) {
    var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", cls || "ico");
    svg.setAttribute("aria-hidden", "true");
    var use = document.createElementNS("http://www.w3.org/2000/svg", "use");
    use.setAttribute("href", "#i-" + name);
    svg.appendChild(use);
    return svg;
  }

  /* current page's path relative to the output root, e.g. "schema/User__o/index.htm".
     Derived from location.pathname + the depth encoded in data-root ("../../" => 2). */
  function currentRootRelPath() {
    var p = location.pathname;
    try { p = decodeURIComponent(p); } catch (e) {}
    p = p.replace(/\\/g, "/").replace(/\/+$/, "");
    var segs = p.split("/").filter(Boolean);
    var depth = (ROOT.match(/\.\.\//g) || []).length; // 0 at root, 1, 2, ...
    return segs.slice(segs.length - (depth + 1)).join("/");
  }
  function navMatch(here, href) {
    if (!href) return false;
    if (here === href) return true;
    // a form/menu/... detail page highlights its section entry (e.g. schema/X/index.htm -> "Forms"
    // at schema/index.htm), but only when the section dir is object-type-specific, not the shared
    // "overview/" bucket that many unrelated entries live in.
    var m = href.match(/^([^/]+)\/index\.htm$/);
    return !!m && m[1] !== "overview" && here.indexOf(m[1] + "/") === 0;
  }

  /* ---------- theme ---------- */
  function initTheme() {
    var root = document.documentElement, btn = document.getElementById("ari-theme");
    var stored = null;
    try { stored = localStorage.getItem("ari-theme"); } catch (e) {}
    if (stored === "dark" || stored === "light") root.setAttribute("data-theme", stored);
    syncThemeIcon();
    if (btn) btn.addEventListener("click", function () {
      var cur = root.getAttribute("data-theme");
      if (!cur) cur = matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
      var next = cur === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      try { localStorage.setItem("ari-theme", next); } catch (e) {}
      syncThemeIcon();
    });
  }
  function syncThemeIcon() {
    var cur = document.documentElement.getAttribute("data-theme");
    var dark = cur ? cur === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
    var d = document.querySelector(".ari-theme-dark"), l = document.querySelector(".ari-theme-light");
    if (d) d.hidden = dark;
    if (l) l.hidden = !dark;
  }

  /* ---------- sidebar nav ---------- */
  function buildNavList(nodes, here) {
    var ul = el("ul");
    nodes.forEach(function (n) {
      var li = el("li");
      if (n.href) {
        var a = el("a", { href: ROOT + n.href });
        a.appendChild(iconSvg(n.icon || "document"));
        a.appendChild(document.createTextNode(n.label));
        if (navMatch(here, n.href)) a.classList.add("active");
        li.appendChild(a);
      } else {
        var span = el("span", { class: "nav-group-label" });
        span.appendChild(iconSvg(n.icon || "folder"));
        span.appendChild(document.createTextNode(n.label));
        li.appendChild(span);
      }
      if (n.children && n.children.length) li.appendChild(buildNavList(n.children, here));
      ul.appendChild(li);
    });
    return ul;
  }
  function initNav() {
    var host = document.getElementById("ari-nav");
    if (!host || !window.ARI_NAV) return;
    var here = currentRootRelPath();
    host.appendChild(buildNavList(window.ARI_NAV, here));

    var toggle = document.getElementById("ari-navtoggle");
    if (toggle) toggle.addEventListener("click", function () {
      document.body.classList.toggle("nav-open");
    });
    document.addEventListener("click", function (e) {
      if (!document.body.classList.contains("nav-open")) return;
      if (e.target.closest && (e.target.closest("#ari-nav") || e.target.closest("#ari-navtoggle"))) return;
      document.body.classList.remove("nav-open");
    });
  }

  /* ---------- global search ---------- */
  var searchLoad = null; // Promise, set on first use
  function loadSearchIndex() {
    if (searchLoad) return searchLoad;
    searchLoad = new Promise(function (resolve) {
      if (window.ARI_SEARCH) { resolve(window.ARI_SEARCH); return; }
      var s = document.createElement("script");
      s.src = ROOT + "img/search-index.js";
      s.onload = function () { resolve(window.ARI_SEARCH || []); };
      s.onerror = function () { resolve([]); };
      document.head.appendChild(s);
    });
    return searchLoad;
  }
  function flattenNav(nodes, out, parentLabel) {
    (nodes || []).forEach(function (n) {
      if (n.href) out.push({ kind: "nav", label: n.label, sub: parentLabel, href: n.href, icon: n.icon || "document" });
      if (n.children) flattenNav(n.children, out, n.label);
    });
    return out;
  }
  /* subsequence fuzzy score; -1 = no match. Rewards contiguous runs + word-boundary hits + earliness. */
  function fuzzyScore(hay, needle) {
    hay = ("" + hay).toLowerCase();
    needle = needle.toLowerCase();
    if (!needle) return 0;
    var hi = 0, score = 0, run = 0, prev = -2;
    for (var ni = 0; ni < needle.length; ni++) {
      var c = needle.charAt(ni), found = hay.indexOf(c, hi);
      if (found < 0) return -1;
      if (found === prev + 1) { run++; score += 6 + run * 2; } else { run = 0; score += 1; }
      if (found === 0 || /[\s:_\-.\/]/.test(hay.charAt(found - 1))) score += 12;
      prev = found; hi = found + 1;
    }
    return score - (hi - needle.length) * 0.5;
  }

  function initPalette() {
    var trigger = document.getElementById("ari-search");
    var modal = el("div", { class: "ari-palette", id: "ari-palette", hidden: "" });
    var panel = el("div", { class: "ari-palette-panel", role: "dialog", "aria-label": "Command palette" });
    var input = el("input", { type: "text", class: "ari-palette-input", placeholder: "Jump to an object, page, or action…", autocomplete: "off", spellcheck: "false", "aria-label": "Command palette" });
    var list = el("div", { class: "ari-palette-list", role: "listbox" });
    panel.appendChild(input); panel.appendChild(list);
    modal.appendChild(panel);
    document.body.appendChild(modal);

    var NAV = flattenNav(window.ARI_NAV, [], "");
    var ACTIONS = [
      { kind: "action", label: "Toggle light / dark theme", icon: "visible", run: function () { var b = document.getElementById("ari-theme"); if (b) b.click(); } },
      { kind: "action", label: "Toggle sidebar", icon: "menu", run: function () { document.body.classList.toggle("nav-open"); } },
      { kind: "action", label: "Scroll to top", icon: "up", run: function () { window.scrollTo({ top: 0, behavior: "smooth" }); } }
    ];
    var objects = null, rows = [], cur = -1, timer;

    function open() {
      modal.hidden = false;
      input.value = ""; input.focus(); render();
      if (!objects) loadSearchIndex().then(function (d) { objects = d; if (!modal.hidden) render(); });
    }
    function closeP() { modal.hidden = true; }
    function activate(r) { closeP(); if (r.kind === "action") r.run(); else location.href = ROOT + r.href; }
    function setCur(i) {
      if (rows[cur]) rows[cur].classList.remove("active");
      cur = Math.max(0, Math.min(i, rows.length - 1));
      if (rows[cur]) { rows[cur].classList.add("active"); rows[cur].scrollIntoView({ block: "nearest" }); }
    }
    function render() {
      var q = input.value.trim(), scored = [];
      if (!q) {
        NAV.slice(0, 12).forEach(function (r) { scored.push({ r: r, s: 0 }); });
      } else {
        NAV.concat(ACTIONS).forEach(function (r) { var s = fuzzyScore(r.label, q); if (s >= 0) scored.push({ r: r, s: s + 25 }); });
        if (objects) for (var i = 0; i < objects.length; i++) {
          var s = fuzzyScore(objects[i][0], q);
          if (s >= 0) scored.push({ r: { kind: "obj", label: objects[i][0], icon: objects[i][1] || "document", href: objects[i][2] }, s: s });
        }
        scored.sort(function (a, b) { return b.s - a.s || a.r.label.length - b.r.label.length; });
        scored = scored.slice(0, 40);
      }
      list.innerHTML = ""; rows = []; cur = -1;
      if (!scored.length) { list.appendChild(el("div", { class: "ari-palette-empty", text: objects || !q ? "No matches" : "Loading…" })); return; }
      scored.forEach(function (x) {
        var r = x.r;
        var item = el("div", { class: "ari-palette-item", role: "option" });
        item.appendChild(iconSvg(r.icon || "document"));
        item.appendChild(el("span", { class: "ari-palette-label", text: r.label }));
        var tag = r.kind === "nav" ? (r.sub || "page") : r.kind === "action" ? "action" : "";
        if (tag) item.appendChild(el("span", { class: "ari-palette-tag", text: tag }));
        item.addEventListener("click", function () { activate(r); });
        item.addEventListener("mousemove", function () { setCur(rows.indexOf(item)); });
        list.appendChild(item); rows.push(item);
      });
      setCur(0);
    }

    input.addEventListener("input", function () { clearTimeout(timer); timer = setTimeout(render, 70); });
    input.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown") { e.preventDefault(); setCur(cur + 1); }
      else if (e.key === "ArrowUp") { e.preventDefault(); setCur(cur - 1); }
      else if (e.key === "Enter") { e.preventDefault(); if (rows[cur]) rows[cur].click(); }
      else if (e.key === "Escape") { e.preventDefault(); closeP(); }
    });
    modal.addEventListener("click", function (e) { if (e.target === modal) closeP(); });
    if (trigger) trigger.addEventListener("click", open);
    document.addEventListener("keydown", function (e) {
      if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) { e.preventDefault(); modal.hidden ? open() : closeP(); return; }
      if (e.key === "/" && !e.metaKey && !e.ctrlKey && !e.altKey) {
        var t = e.target.tagName;
        if (t === "INPUT" || t === "TEXTAREA" || t === "SELECT" || e.target.isContentEditable) return;
        e.preventDefault(); open();
      }
    });
  }

  /* ---------- table sort (port of sortscript.js) ---------- */
  function cellText(td) { return (td.textContent || "").trim(); }
  function sortComparator(sample) {
    if (/^-?[\d.,]+$/.test(sample.replace(/[$€£]/g, ""))) {
      return function (a, b) { return num(a) - num(b); };
    }
    if (/^\d{1,4}[\/-]\d{1,2}[\/-]\d{1,4}/.test(sample)) {
      return function (a, b) { return Date.parse(a.replace(/-/g, "/")) - Date.parse(b.replace(/-/g, "/")); };
    }
    return function (a, b) { return a.toLowerCase() < b.toLowerCase() ? -1 : a.toLowerCase() > b.toLowerCase() ? 1 : 0; };
  }
  function num(s) { var n = parseFloat(("" + s).replace(/[^0-9.\-]/g, "")); return isNaN(n) ? 0 : n; }

  function decorateSortHeaders(table) {
    var head = table.tHead && table.tHead.rows[0];
    if (!head || head.dataset.sortReady) return;
    head.dataset.sortReady = "1";
    Array.prototype.forEach.call(head.cells, function (th, idx) {
      th.classList.add("sortheader");
      if (!th.querySelector(".sortarrow")) th.appendChild(el("span", { class: "sortarrow" }));
      th.addEventListener("click", function () { sortBy(table, idx, th); });
    });
  }
  function sortBy(table, idx, th) {
    var body = table.tBodies[0];
    if (!body) return;
    var rows = Array.prototype.filter.call(body.rows, function (r) { return !r.querySelector("td[colspan]"); });
    if (rows.length < 2) return;
    var asc = th.getAttribute("aria-sort") !== "ascending";
    var cmp = sortComparator(cellText(rows[0].cells[idx] || document.createElement("td")));
    rows.sort(function (a, b) {
      var r = cmp(cellText(a.cells[idx] || a), cellText(b.cells[idx] || b));
      return asc ? r : -r;
    });
    var head = table.tHead.rows[0];
    Array.prototype.forEach.call(head.cells, function (c) { c.removeAttribute("aria-sort"); });
    th.setAttribute("aria-sort", asc ? "ascending" : "descending");
    rows.forEach(function (r) { body.appendChild(r); });
  }
  function initSort() {
    document.querySelectorAll("table.TblObjectList").forEach(decorateSortHeaders);
  }

  /* ---------- tabs (TabControl markup) ---------- */
  function tabForHash(wrap, h) {
    if (!h || !/^[\w-]+$/.test(h)) return null;
    return wrap.querySelector('[role="tab"][data-panel="' + h + '"]');
  }
  function initTabs() {
    document.querySelectorAll(".ari-tabs").forEach(function (wrap) {
      var tabs = Array.prototype.slice.call(wrap.querySelectorAll('[role="tab"]'));
      if (!tabs.length) return;
      function show(tab, push) {
        tabs.forEach(function (t) {
          var sel = t === tab;
          t.setAttribute("aria-selected", sel ? "true" : "false");
          t.tabIndex = sel ? 0 : -1;
          var panel = document.getElementById(t.getAttribute("aria-controls"));
          if (panel) panel.hidden = !sel;
        });
        var pid = tab.getAttribute("data-panel");
        if (push && pid) history.replaceState(null, "", "#" + pid);
        if (push) storeTab((tab.textContent || "").trim());
        wrap.dispatchEvent(new CustomEvent("ari:tabshown", { detail: { id: pid }, bubbles: true }));
      }
      tabs.forEach(function (t) {
        t.addEventListener("click", function (e) { e.preventDefault(); show(t, true); });
        t.addEventListener("keydown", function (e) {
          var i = tabs.indexOf(t), n = tabs.length;
          if (e.key === "ArrowRight") { e.preventDefault(); tabs[(i + 1) % n].focus(); tabs[(i + 1) % n].click(); }
          if (e.key === "ArrowLeft") { e.preventDefault(); tabs[(i - 1 + n) % n].focus(); tabs[(i - 1 + n) % n].click(); }
        });
      });
      var initial = tabForHash(wrap, location.hash.slice(1));
      if (!initial) {
        var remembered = rememberedTab();
        if (remembered) initial = tabs.filter(function (t) { return (t.textContent || "").trim() === remembered; })[0];
      }
      show(initial || tabs[0], false);
      window.addEventListener("hashchange", function () {
        var t = tabForHash(wrap, location.hash.slice(1));
        if (t) show(t, false);
      });
    });
  }

  /* ---------- letter filter + clearable inputs ---------- */
  function pageFilterInput() { return document.querySelector("input.data_field"); }
  function initLetterFilter() {
    var bar = document.getElementById("formLetterFilter");
    if (!bar) return;
    bar.addEventListener("click", function (e) {
      var a = e.target.closest("a");
      if (!a) return;
      e.preventDefault();
      var inp = pageFilterInput();
      if (!inp) return;
      inp.value = "^" + a.textContent.trim();
      inp.dispatchEvent(new Event("input", { bubbles: true }));
    });
  }
  function initClearable() {
    document.querySelectorAll(".clearable").forEach(function (wrap) {
      var inp = wrap.querySelector("input.data_field");
      if (!inp) return;
      var btn = wrap.querySelector(".icon_clear");
      if (!btn) { btn = el("button", { class: "icon_clear", type: "button", "aria-label": "Clear", text: "×" }); wrap.appendChild(btn); }
      function sync() { wrap.classList.toggle("has-value", inp.value.length > 0); }
      inp.addEventListener("input", sync);
      inp.addEventListener("keydown", function (e) { if (e.key === "Escape") { inp.value = ""; inp.dispatchEvent(new Event("input", { bubbles: true })); } });
      btn.addEventListener("click", function () { inp.value = ""; inp.dispatchEvent(new Event("input", { bubbles: true })); inp.focus(); });
      sync();
    });
  }

  /* ---------- sticky filter controls on list pages ---------- */
  function initStickyControls() {
    if (!document.body.classList.contains("list-page")) return;
    var inner = document.querySelector(".ari-main-inner");
    var wrap = inner && inner.querySelector(".ari-tablewrap");
    if (!inner || !wrap) return;
    var bar = el("div", { class: "ari-listcontrols" });
    while (inner.firstChild && inner.firstChild !== wrap) bar.appendChild(inner.firstChild);
    inner.insertBefore(bar, wrap);
  }

  /* ---------- ¶ anchor links on section headings ---------- */
  function slugify(s) {
    return s.toLowerCase().replace(/[^\w]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 60) || "section";
  }
  function initHeadingAnchors() {
    var seen = {};
    document.querySelectorAll(".ari-main-inner h2, .ari-main-inner h3").forEach(function (h) {
      if (h.closest(".ari-palette") || h.closest("summary")) return;
      var id = h.id;
      if (!id) {
        id = slugify(h.textContent);
        while (seen[id] || document.getElementById(id)) id += "-x";
        h.id = id;
      }
      seen[id] = true;
      var a = el("a", { class: "ari-anchor", href: "#" + id, "aria-label": "Link to this section", text: "¶" });
      h.appendChild(a);
    });
  }

  /* ---------- remember the last-viewed tab label ---------- */
  var LAST_TAB = "ari-last-tab";
  function rememberedTab() { try { return localStorage.getItem(LAST_TAB); } catch (e) { return null; } }
  function storeTab(label) { try { localStorage.setItem(LAST_TAB, label); } catch (e) {} }

  window.ARI = { decorateSortHeaders: decorateSortHeaders, iconSvg: iconSvg, root: ROOT, rememberedTab: rememberedTab, storeTab: storeTab };

  function boot() {
    initTheme();
    initNav();
    initPalette();
    initStickyControls();
    initSort();
    initTabs();
    initHeadingAnchors();
    initLetterFilter();
    initClearable();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
