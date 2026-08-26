// The C++ never tabs container/application pages (no MainObjectTabCtrl markup exists in the real
// tool's output for these types - confirmed via a real cpp-output diff), so there is no equivalent
// resource file to port for this one. ARInsideJ's ContainerDetailPage does render a TabControl for
// General/Members, so it needs its own (trivial) init - schema_page.js's tabs() call is specific to
// schema pages and not referenced here.
$(function() {
    $("#MainObjectTabCtrl").tabs();
});
