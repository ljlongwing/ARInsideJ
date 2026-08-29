/*
 * ARInsideJ overview-list engine. Plain ES (runs from file://). Replaces object_list.js +
 * the ten per-type *List.js files + arshelper.js's client-side enum helpers.
 *
 * Each overview page still emits its rows inline as `var <type>List = [[...]]` (unchanged
 * contract). This script picks up whichever of those globals exists and wires the matching
 * table: text filter, numeric-id match, optional type-checkbox filter, chunked rendering.
 */
(function () {
  "use strict";

  var ROOT = (window.ARI && window.ARI.root) || document.documentElement.getAttribute("data-root") || "";

  /* ---- AR enum label helpers (ported from arshelper.js) ---- */
  function enabled(v) { return v ? "Enabled" : "Disabled"; }
  function schemaType(t) {
    return ({ 1: "Regular", 2: "Join", 3: "View", 4: "Dialog", 5: "Vendor", 100: "Audit", 101: "Archive" })[t] || "unknown";
  }
  function menuType(t) {
    return ({ 1: "Character", 2: "Search", 3: "File", 4: "SQL", 6: "Data Dictionary" })[t] || "";
  }
  function menuConnect(t) {
    return ({ 1: "On Connect", 2: "On Open", 3: "On 15-minute Interval" })[t] || "";
  }
  function groupType(t) { return ({ 1: "View", 2: "Change" })[t] || "None"; }
  function groupCategory(c) { return ({ 0: "Regular", 1: "Dynamic", 2: "Computed" })[c] || "None"; }
  function pool(n) { n = Number(n); return (!isNaN(n) && n > 0) ? n : ""; }

  var SCHEMA_ICON = { 2: "schema-join", 3: "schema-view", 4: "schema-dialog", 5: "schema-vendor" };
  var CONTAINER_ICON = { 1: "al-guide", 2: "application", 3: "packing-list", 4: "filter-guide", 5: "webservice" };

  function iconEl(name, overlayType) {
    var base = window.ARI ? window.ARI.iconSvg(name) : document.createElement("span");
    if (overlayType === 2 || overlayType === 4) {
      var wrap = document.createElement("span");
      wrap.className = "ico-badge";
      wrap.appendChild(base);
      wrap.appendChild(window.ARI.iconSvg(overlayType === 4 ? "custom" : "overlay", "ico ico-over"));
      return wrap;
    }
    return base;
  }
  var DATETIME = /^(\d{4}-\d\d-\d\d) \d\d:\d\d:\d\d$/;
  function td(content) {
    var c = document.createElement("td");
    if (content instanceof Node) { c.appendChild(content); return c; }
    if (content == null) return c;
    var s = "" + content, m = s.match(DATETIME);
    if (m) { c.textContent = m[1]; c.title = s; }          // list shows just the date; full timestamp on hover
    else { c.textContent = s; if (s.length > 14) c.title = s; }
    return c;
  }
  function linkTd(icon, href, text, extraHtml) {
    var c = document.createElement("td");
    if (icon) c.appendChild(icon);
    var a = document.createElement("a");
    a.href = href;  // data links are already page-relative (URLLink.relativeUrl)
    a.textContent = text;
    c.appendChild(a);
    if (extraHtml) c.insertAdjacentHTML("beforeend", extraHtml);
    return c;
  }
  function row(cells) {
    var tr = document.createElement("tr");
    cells.forEach(function (c) { tr.appendChild(c instanceof Node ? c : td(c)); });
    return tr;
  }

  function fmt(n) { return (+n).toLocaleString(); }

  /* ---- generic filterable table ---- */
  function FilterableTable(cfg) {
    this.cfg = cfg;
    this.table = document.getElementById(cfg.tableId);
    this.input = document.getElementById(cfg.inputId);
    this.count = document.getElementById(cfg.countId);
    this.nameIndex = cfg.nameIndex || 0;
    this.max = 250;                 // rows rendered per chunk
    this.data = window[cfg.key] || [];
    this.checkboxes = cfg.typeFilter ? Array.prototype.slice.call(document.querySelectorAll(cfg.typeFilter.selector + ' input[type="checkbox"]')) : [];
    if (this.count) this.count.classList.add("ari-liststatus");
    this.reset();
  }
  FilterableTable.prototype.reset = function () { this.lastIndex = 0; this.rendered = 0; this.totalMatches = 0; };
  FilterableTable.prototype.tbody = function () {
    var b = this.table.tBodies[0];
    if (!b) { b = document.createElement("tbody"); this.table.appendChild(b); }
    return b;
  };
  FilterableTable.prototype.hasTypeFilter = function () {
    if (!this.cfg.typeFilter || !this.checkboxes.length) return false;
    var on = 0;
    this.checkboxes.forEach(function (c) { if (c.checked) on++; });
    return on > 0 && on < this.checkboxes.length;
  };
  FilterableTable.prototype.rowPassesType = function (r) {
    return this.cfg.typeFilter.match(r, this.checkboxes);
  };
  FilterableTable.prototype.rowMatches = function (r, rx, numSearch, typeActive, raw) {
    if (typeActive && !this.rowPassesType(r)) return false;
    return rx.test("" + r[this.nameIndex]) || (numSearch && ("" + r[0]) === raw);
  };
  FilterableTable.prototype.filter = function (mode) {
    if (!this.table || !this.input) return;
    var raw = this.input.value.trim();
    var search = this.input.value.replace(/ +/g, " ").replace(/ /g, ".*");
    var rx = new RegExp(search, "i");
    var numSearch = /^-?\d+$/.test(search);
    var typeActive = this.hasTypeFilter();
    this.hasFilterActive = search.length > 0 || typeActive;
    var body = this.tbody();
    if (this.table.tFoot) this.table.tFoot.remove();

    if (mode !== "next") {
      body.innerHTML = ""; this.reset();
      // full scan (cheap - just regex, no DOM) so the status line can show an accurate total
      this.totalMatches = 0;
      for (var k = 0; k < this.data.length; k++) if (this.rowMatches(this.data[k], rx, numSearch, typeActive, raw)) this.totalMatches++;
      this.rendered = 0;
    }

    var addedThisCall = 0;
    for (var i = this.lastIndex; i < this.data.length; i++) {
      if (!this.rowMatches(this.data[i], rx, numSearch, typeActive, raw)) continue;
      body.appendChild(this.cfg.render(this.data[i]));
      addedThisCall++; this.rendered++;
      if (addedThisCall >= this.max) { this.lastIndex = i + 1; break; }
      this.lastIndex = i + 1;
    }
    if (this.rendered < this.totalMatches) this.appendMoreRow();
    this.updateStatus();
  };
  FilterableTable.prototype.updateStatus = function () {
    if (!this.count) return;
    var total = this.data.length;
    if (this.hasFilterActive) {
      this.count.textContent = " — " + fmt(this.totalMatches) + (this.totalMatches === 1 ? " match" : " matches");
    } else if (this.rendered < total) {
      this.count.textContent = " — showing " + fmt(this.rendered) + " of " + fmt(total);
    } else {
      this.count.textContent = "";
    }
  };
  FilterableTable.prototype.appendMoreRow = function () {
    var self = this;
    var foot = self.table.createTFoot();
    foot.innerHTML = "";
    var cell = foot.insertRow().insertCell();
    cell.colSpan = 99;
    cell.className = "ari-loadmore";
    cell.appendChild(document.createTextNode("Showing " + fmt(self.rendered) + " of " + fmt(self.totalMatches) + "  "));
    var more = document.createElement("button");
    more.type = "button";
    more.textContent = "Load " + Math.min(self.max, self.totalMatches - self.rendered) + " more";
    more.addEventListener("click", function () { self.filter("next"); });
    cell.appendChild(more);
  };
  FilterableTable.prototype.wire = function () {
    var self = this;
    var run = function () { self.filter(); };
    if (this.input) {
      var timer;
      this.input.addEventListener("input", function () { clearTimeout(timer); timer = setTimeout(run, 200); });
    }
    this.checkboxes.forEach(function (c) { c.addEventListener("change", run); });
    var clear = document.getElementById("typeFilterNone");
    if (clear) clear.addEventListener("click", function () {
      self.checkboxes.forEach(function (c) { c.checked = false; });
      run();
    });
    run();
    if (this.input) this.input.focus();
  };

  /* ---- per-type configs ---- */
  var CONFIGS = [
    {
      key: "schemaList", tableId: "schemaList", inputId: "formFilter", countId: "schemaListFilterResultCount", nameIndex: 1,
      typeFilter: {
        selector: "#listMultiFilter",
        match: function (r) { var cb = document.querySelector('#listMultiFilter input[value="' + r[5] + '"]'); return !!(cb && cb.checked); }
      },
      render: function (r) {
        return row([
          linkTd(iconEl(SCHEMA_ICON[r[5]] || "schema", r[9]), r[8], r[1]),
          td(r[2]), td(r[3]), td(r[4]), td(schemaType(r[5])), td(r[6]), td(r[7])
        ]);
      }
    },
    {
      key: "alList", tableId: "alList", inputId: "actlinkFilter", countId: "actlinkListFilterResultCount", nameIndex: 0,
      render: function (r) {
        return row([
          linkTd(iconEl("active-link", r[10]), r[9], r[0]),
          td(enabled(r[1])), td(r[2]), td(r[3]), td(r[4]), td(r[11] ? "Yes" : ""), td(r[5]), td(r[6]), td(r[7]), td(r[8])
        ]);
      }
    },
    {
      key: "filterList", tableId: "filterList", inputId: "filterFilter", countId: "filterListFilterResultCount", nameIndex: 0,
      typeFilter: {
        selector: "#multiFilter",
        match: function (r, boxes) {
          for (var i = 0; i < boxes.length; i++) {
            if (!boxes[i].checked) continue;
            var v = boxes[i].value;
            if (v === "N" ? Number(r[9]) === 0 : (Number(r[9]) & Number(v))) return true;
          }
          return false;
        }
      },
      render: function (r) {
        return row([
          linkTd(iconEl("filter", r[10]), r[8], r[0]),
          td(enabled(r[1])), td(r[2]), td(r[3]), td(r[11] ? "Yes" : ""), td(r[4]), td(r[5]), td(r[6]), td(r[7])
        ]);
      }
    },
    {
      key: "escalationList", tableId: "escalationList", inputId: "escalationFilter", countId: "escalationListFilterResultCount", nameIndex: 0,
      render: function (r) {
        var hasPool = false;
        var head = this.table && this.table.tHead && this.table.tHead.rows[0];
        if (head && head.cells[2]) hasPool = head.cells[2].textContent.trim() === "Pool";
        var cells = [linkTd(iconEl("escalation", r[9]), r[7], r[0]), td(enabled(r[1]))];
        if (hasPool) cells.push(td(pool(r[8])));
        cells.push(td(r[2]), td(r[10] ? "Yes" : ""), td(r[3]), td(r[4]), td(r[5]), td(r[6]));
        return row(cells);
      }
    },
    {
      key: "menuList", tableId: "menuList", inputId: "menuFilter", countId: "menuListFilterResultCount", nameIndex: 0,
      typeFilter: {
        selector: "#multiFilter",
        match: function (r) { var cb = document.querySelector('#multiFilter input[value="' + r[1] + '"]'); return !!(cb && cb.checked); }
      },
      render: function (r) {
        return row([
          linkTd(iconEl("menu", r[6]), r[5], r[0], r[7] === 0 ? " (<b>!</b>)" : ""),
          td(menuType(r[1])), td(menuConnect(r[2])), td(r[3]), td(r[4])
        ]);
      }
    },
    {
      key: "containerList", tableId: "containerList", inputId: "containerFilter", countId: "containerListResultCount", nameIndex: 0,
      render: function (r) {
        var ct = window.containerType;
        return row([
          linkTd(iconEl(CONTAINER_ICON[ct] || "application", r[4]), r[3], r[0], (r.length > 5 && r[5] === 0) ? " (<b>!</b>)" : ""),
          td(r[1]), td(r[2])
        ]);
      }
    },
    {
      key: "groupList", tableId: "groupList", inputId: "groupFilter", countId: "groupListFilterResultCount", nameIndex: 1,
      typeFilter: {
        selector: "#multiFilter",
        match: function (r, boxes) {
          for (var i = 0; i < boxes.length; i++) if (boxes[i].checked && Number(r[3]) === i) return true;
          return false;
        }
      },
      render: function (r) {
        return row([
          linkTd(iconEl("group"), r[6], r[1]),
          td(r[0]), td(groupType(r[2])), td(groupCategory(r[3])), td(r[4]), td(r[5])
        ]);
      }
    },
    {
      key: "roleList", tableId: "roleList", inputId: "roleFilter", countId: "roleListFilterResultCount", nameIndex: 1,
      typeFilter: {
        selector: "#multiFilter",
        match: function (r, boxes) {
          for (var i = 0; i < boxes.length; i++) if (boxes[i].checked && Number(r[3]) === i) return true;
          return false;
        }
      },
      render: function (r) {
        var appCell = document.createElement("td");
        appCell.appendChild(iconEl("application"));
        if (r[6]) { var a = document.createElement("a"); a.href = r[6]; a.textContent = r[2]; appCell.appendChild(a); }
        else appCell.appendChild(document.createTextNode(r[2] || ""));
        return row([
          linkTd(iconEl("role"), r[5], r[1]),
          td(r[0]), appCell, td(r[3]), td(r[4])
        ]);
      }
    },
    {
      key: "imageList", tableId: "imageList", inputId: "imageFilter", countId: "imageListFilterResultCount", nameIndex: 0,
      render: function (r) {
        return row([
          linkTd(iconEl("image", r[5]), r[4], r[0]),
          td(r[1]), td(r[2]), td(r[3])
        ]);
      }
    }
  ];

  function boot() {
    CONFIGS.forEach(function (cfg) {
      if (!Array.isArray(window[cfg.key])) return;
      if (!document.getElementById(cfg.tableId)) return;
      var ft = new FilterableTable(cfg);
      // bind render's `this` to the instance so escalation's header probe works
      var origRender = cfg.render;
      cfg.render = function (r) { return origRender.call(ft, r); };
      ft.wire();
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
