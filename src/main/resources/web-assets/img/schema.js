/*
 * ARInsideJ schema-detail-page script. Plain ES (runs from file://). Replaces schema_page.js.
 * Tab switching + <details> accordions are handled generically by app.js; this file owns the
 * schema-specific pieces:
 *   - the Fields tab instant name/id filter (#fieldNameFilter -> #fieldListAll)
 *   - the Workflow tab reference list, built lazily the first time that tab is shown
 *     (var referenceList = [...], emitted inline by SchemaDetailPage.workflowJson)
 *   - the field detail panel: a #field-<id> anchor (from anywhere in the site) opens the Fields
 *     tab and renders that field's detail, lazy-loaded from schema/<form>/fields.js
 *     (window.ARI_FIELDDETAIL = { "<id>": "<html>" }). The bulky per-view display-property dumps
 *     are split into schema/<form>/fields-vui.js (window.ARI_FIELDVUI), fetched only when a reader
 *     expands "Display properties per view" inside an open panel.
 *
 * Join/View/Vendor "Real Field" rendering in the *filtered* Fields view is reconstructed from the
 * slots SchemaDetailPage.realFieldJsonItems packs after index 7:
 *   view/vendor  -> [8]=name                                   (row length 9)
 *   join field   -> [8]=name [9]=link [10]=joinIndex("0"/"1")  (row length 11)
 *   join field 1 -> [8]=A name [9]=A link [10]=B name [11]=B link (row length 12)
 */
(function () {
  "use strict";

  var ROOT = (window.ARI && window.ARI.root) || document.documentElement.getAttribute("data-root") || "";

  function el(tag, text) { var e = document.createElement(tag); if (text != null) e.textContent = text; return e; }
  function fieldLink(name, href) {
    if (href) { var a = document.createElement("a"); a.href = href; a.textContent = name; return a; }
    return document.createTextNode(name);
  }
  function realFieldCell(f) {
    var td = document.createElement("td");
    if (f.length === 9) { td.appendChild(fieldLink(f[8], "")); return td; }
    if (f.length === 12) {
      td.appendChild(fieldLink(f[8], f[9]));
      td.appendChild(document.createTextNode(" / "));
      td.appendChild(fieldLink(f[10], f[11]));
      return td;
    }
    if (f.length === 11) {
      td.appendChild(fieldLink(f[8], f[9]));
      var member = document.getElementById(f[10] === "1" || f[10] === 1 ? "join-right" : "join-left");
      if (member) { td.appendChild(document.createTextNode(" → ")); td.appendChild(document.createTextNode(member.textContent.trim())); }
      return td;
    }
    return td;
  }
  function tbodyOf(table) {
    var b = table.tBodies[0];
    if (!b) { b = document.createElement("tbody"); table.appendChild(b); }
    return b;
  }
  function dataType(t) {
    return ({ 0: "Null", 1: "Keyword", 2: "Integer", 3: "Real", 4: "Character", 5: "Diary", 6: "Selection",
      7: "Date/Time", 8: "Bitmask", 9: "Bytes", 10: "Decimal", 11: "Attach", 12: "Currency", 13: "Date",
      14: "Time of Day", 30: "Join", 31: "Trim", 32: "Control", 33: "Table", 34: "Column", 35: "Page",
      36: "Page Holder", 37: "Attach Pool", 40: "Long", 41: "Coords", 42: "View", 43: "Display" })[t] || "unknown";
  }

  /* ---------- Fields tab filter ---------- */
  function initFieldFilter() {
    var list = window.schemaFieldList;
    var table = document.getElementById("fieldListAll");
    var input = document.getElementById("fieldNameFilter");
    var btn = document.getElementById("execFieldFilter");
    var count = document.getElementById("fieldListFilterResultCount");
    if (!list || !table || !input) return;
    if (count) { count.setAttribute("role", "status"); count.setAttribute("aria-live", "polite"); }

    var headCells = table.tHead ? table.tHead.rows[0].cells : [];
    var hasRealField = headCells[4] && /^Real Field/.test(headCells[4].textContent.trim());

    function run() {
      var raw = input.value.replace(/ +/g, " ").replace(/ /g, ".*");
      var rx = new RegExp(raw, "i");
      var numeric = /^\d+$/.test(raw);
      var body = tbodyOf(table);
      body.innerHTML = "";
      var matches = 0;
      list.forEach(function (f) {
        if (!(rx.test("" + f[1]) || (numeric && ("" + f[0]) === input.value.trim()))) return;
        matches++;
        var tr = document.createElement("tr");
        var nameTd = el("td");
        var a = el("a", f[1]); a.href = f[6]; nameTd.appendChild(a);
        tr.appendChild(nameTd);
        tr.appendChild(el("td", f[0]));
        tr.appendChild(el("td", dataType(f[2])));
        tr.appendChild(el("td", f[7] || ""));
        if (hasRealField) tr.appendChild(realFieldCell(f));
        var vc = el("td", f[3]);
        if (f[3] === 0) vc.className = "fieldInNoView";
        tr.appendChild(vc);
        tr.appendChild(el("td", f[4]));
        tr.appendChild(el("td", f[5]));
        body.appendChild(tr);
      });
      if (count) count.textContent = (input.value.length > 0 ? "showing " + matches + " out of " : "");
    }

    if (btn) btn.addEventListener("click", run);
    var timer;
    input.addEventListener("input", function () { clearTimeout(timer); timer = setTimeout(run, 250); });
    input.addEventListener("keydown", function (e) {
      if (e.key === "Escape") { input.value = ""; run(); }
      if (e.key === "Enter") { e.preventDefault(); run(); }
    });
    if (input.value !== "") run();
  }

  /* ---------- Workflow tab reference list (lazy) ---------- */
  var MAP = { 6: 1, 5: 2, 9: 3 };
  var CONT_MAP = { 1: 4, 4: 5, 2: 6, 3: 7, 5: 8 };
  var CONT_ICON = { 1: "al-guide", 2: "application", 3: "packing-list", 4: "filter-guide", 5: "webservice" };
  var OBJ_ICON = { 6: "active-link", 5: "filter", 9: "escalation" };
  var OBJ_NAME = { 6: "Active Link", 5: "Filter", 9: "Escalation" };
  var CONT_NAME = { 1: "Active Link Guide", 2: "Application", 3: "Packing List", 4: "Filter Guide", 5: "Webservice" };

  function alExecuteOn(v) {
    var bits = [[1, "Button/MenuField"], [2, "Return"], [4, "Submit"], [8, "Modify"], [16, "Display"],
      [128, "Menu Choice"], [256, "Lose Focus"], [512, "Set Default"], [1024, "Search"], [2048, "After Modify"],
      [4096, "After Submit"], [8192, "Gain Focus"], [16384, "Window Open"], [32768, "Window Close"],
      [65536, "Un-Display"], [131072, "Copy To New"], [262144, "Window Loaded"]];
    return joinBits(v, bits);
  }
  function filterOp(v) {
    return joinBits(v, [[1, "Get"], [2, "Modify"], [4, "Submit"], [8, "Delete"], [16, "Merge"], [64, "Service"]]);
  }
  function joinBits(v, bits) {
    var out = [];
    bits.forEach(function (b) { if (v & b[0]) out.push(b[1]); });
    return out.length ? out.join(", ") : "None";
  }
  function escTm(v) { return v === 1 ? "Interval" : "Time"; }

  var wfInit = false;
  function initWorkflowList() {
    if (wfInit) return;
    var list = window.referenceList, table = document.getElementById("referenceList");
    var input = document.getElementById("workflowFilter");
    var countSpan = document.getElementById("workflowFilterResult");
    if (!list || !table) return;
    if (countSpan) { countSpan.setAttribute("role", "status"); countSpan.setAttribute("aria-live", "polite"); }
    wfInit = true;
    var boxes = Array.prototype.slice.call(document.querySelectorAll('#referenceMultiFilter input[type="checkbox"]'));

    function typeActive() {
      var on = boxes.filter(function (b) { return b.checked; }).length;
      return on > 0 && on < boxes.length;
    }
    function passesType(r) {
      var slot = r[0] === 12 ? CONT_MAP[r[2]] : MAP[r[0]];
      var cb = document.querySelector('#referenceMultiFilter input[value="' + slot + '"]');
      return !!(cb && cb.checked);
    }
    function iconFor(r) {
      var name = r[0] === 12 ? (CONT_ICON[r[2]] || "application") : (OBJ_ICON[r[0]] || "document");
      return window.ARI ? window.ARI.iconSvg(name) : el("span");
    }
    function render() {
      var rx = new RegExp(input ? input.value.replace(/ +/g, " ").replace(/ /g, ".*") : "", "i");
      var ta = typeActive();
      var body = tbodyOf(table);
      body.innerHTML = "";
      var n = 0;
      list.forEach(function (r) {
        if (ta && !passesType(r)) return;
        if (!rx.test("" + r[1])) return;
        n++;
        var tr = document.createElement("tr");
        var nameTd = el("td");
        nameTd.appendChild(iconFor(r));
        var a = el("a", r[1]); a.href = r[9]; nameTd.appendChild(a);
        tr.appendChild(nameTd);
        tr.appendChild(el("td", r[0] === 12 ? "" : (r[2] ? "Enabled" : "Disabled")));
        tr.appendChild(el("td", r[3]));
        var eo = r[0] === 6 ? alExecuteOn(r[4]) : r[0] === 5 ? filterOp(r[4]) : r[0] === 9 ? escTm(r[4]) : "";
        tr.appendChild(el("td", eo));
        tr.appendChild(el("td", r[10] ? "Yes" : ""));
        tr.appendChild(el("td", r[5]));
        tr.appendChild(el("td", r[6]));
        tr.appendChild(el("td", r[7]));
        tr.appendChild(el("td", r[8]));
        body.appendChild(tr);
      });
      if (countSpan) countSpan.textContent = n;
    }
    if (input) {
      var timer;
      input.addEventListener("input", function () { clearTimeout(timer); timer = setTimeout(render, 200); });
    }
    boxes.forEach(function (b) { b.addEventListener("change", render); });
    var clear = document.getElementById("typeFilterNone");
    if (clear) clear.addEventListener("click", function () { boxes.forEach(function (b) { b.checked = false; }); render(); });
    render();
  }

  /* ---------- field detail panel (lazy, from schema/<form>/fields.js) ---------- */
  function lazyScript(src, globalName) {
    var p = null;
    return function () {
      if (p) return p;
      p = new Promise(function (resolve) {
        if (window[globalName]) { resolve(window[globalName]); return; }
        var s = document.createElement("script");
        s.src = src; // sibling of this schema page
        s.onload = function () { resolve(window[globalName] || {}); };
        s.onerror = function () { resolve({}); };
        document.head.appendChild(s);
      });
      return p;
    };
  }
  var loadFieldDetail = lazyScript("fields.js", "ARI_FIELDDETAIL");
  var loadFieldVui = lazyScript("fields-vui.js", "ARI_FIELDVUI"); // the bulky per-view property dumps

  function addVuiToggle(body, id) {
    var d = document.createElement("details");
    d.className = "ari-acc fd-vui";
    d.innerHTML = '<summary>Display properties per view</summary><div class="acc-body">Loading&hellip;</div>';
    body.appendChild(d);
    d.addEventListener("toggle", function onToggle() {
      if (!d.open) return;
      d.removeEventListener("toggle", onToggle);
      loadFieldVui().then(function (map) {
        var html = map && map[id];
        d.querySelector(".acc-body").innerHTML = html || "<p>This field has no per-view display properties.</p>";
      });
    });
  }
  function fieldName(id) {
    var list = window.schemaFieldList || [];
    for (var i = 0; i < list.length; i++) if (("" + list[i][0]) === ("" + id)) return list[i][1];
    return "Field " + id;
  }
  function fieldPanel() {
    var p = document.getElementById("fieldDetailPanel");
    if (p) return p;
    var host = document.getElementById("tab-2");
    if (!host) return null;
    p = document.createElement("section");
    p.id = "fieldDetailPanel";
    p.className = "ari-fielddetail";
    p.hidden = true;
    p.innerHTML = '<div class="fd-head"><h3 class="fd-name"></h3>'
      + '<button type="button" class="fd-close" aria-label="Close field detail">×</button></div>'
      + '<div class="fd-body"></div>';
    host.insertBefore(p, host.firstChild);
    p.querySelector(".fd-close").addEventListener("click", function () {
      p.hidden = true;
      if (/^#field-/.test(location.hash)) history.replaceState(null, "", location.pathname + location.search);
    });
    return p;
  }
  function activateFieldsTab() {
    var btn = document.querySelector('.ari-tablist [data-panel="tab-2"]');
    if (btn && btn.getAttribute("aria-selected") !== "true") btn.click();
  }
  function showField(id) {
    loadFieldDetail().then(function (map) {
      var p = fieldPanel();
      if (!p) return;
      activateFieldsTab();
      p.querySelector(".fd-name").textContent = fieldName(id);
      var body = p.querySelector(".fd-body");
      body.innerHTML = (map && map[id]) || "<p>No detail recorded for field " + id + ".</p>";
      if (map && map[id] && (window.ARI_FIELDVUI_IDS || []).indexOf(+id) >= 0) addVuiToggle(body, id);
      p.hidden = false;
      if (location.hash !== "#field-" + id) history.replaceState(null, "", "#field-" + id);
      p.scrollIntoView({ block: "start" });
    });
  }
  function fieldHashCheck() {
    var m = location.hash.match(/^#field-(-?\d+)$/);
    if (m) showField(m[1]);
  }

  function boot() {
    initFieldFilter();
    document.addEventListener("ari:tabshown", function (e) {
      var id = e.detail && e.detail.id;
      if (id === "tab-4") initWorkflowList();
      if (id === "tab-2") { var f = document.getElementById("fieldNameFilter"); if (f) f.focus(); }
    });
    window.addEventListener("hashchange", function () { initWorkflowListIfHash(); fieldHashCheck(); });
    function initWorkflowListIfHash() { if (location.hash === "#tab-4") initWorkflowList(); }
    initWorkflowListIfHash();
    fieldHashCheck();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
