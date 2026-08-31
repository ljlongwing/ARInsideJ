package arinside;

import arinside.ar.is.IsBundle;
import arinside.ar.is.IsDefType;
import arinside.ar.is.IsDefinition;
import arinside.ar.is.IsRepository;
import arinside.config.AppConfig;
import arinside.doc.is.IsPages;
import arinside.output.NavigationPage.NavItem;
import arinside.output.SearchIndex;
import arinside.util.JsonReader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders a canned {@link IsRepository} through {@link IsPages} (no server) and checks the pages.
 * The HTTP client is exercised only against the live server; the rendering is verified here.
 */
class IsPagesTest {

    private static Path out;
    private static NavItem nav;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> raw(String json) {
        return (Map<String, Object>) JsonReader.parse(json);
    }

    private static IsDefinition def(IsDefType type, String name, Boolean enabled, String json) {
        Map<String, Object> raw = raw(json);
        return new IsDefinition(type, name, JsonReader.str(raw, "description"), enabled,
            "APL", 1_700_000_000L, "ARSERVER", "0", "PUBLIC", "GUID-" + name.hashCode(), raw);
    }

    @BeforeAll
    static void render() throws IOException {
        SearchIndex.clear();
        out = Files.createTempDirectory("arinsidej-is");

        AppConfig cfg = new AppConfig();
        cfg.targetFolder = out.toString();
        cfg.companyName = "IS Test";
        cfg.isServerUrl = "http://sandbox:8008";

        IsBundle bundle = new IsBundle("com.acme.helpdesk", "Acme Help Desk", "Acme Help Desk",
            "1.2.0", "Acme", "the help desk app", true, "2026-08-31T13:00:00.000+0000", Map.of());

        IsDefinition rule = def(IsDefType.RULE, "com.acme.helpdesk:Escalate Stale Incidents", true, """
            { "description": "bump priority when idle",
              "triggerEvent": { "resourceType": "com.bmc.arsys.rx.services.rule.domain.TimerTriggerEvent",
                "timeCriteria": { "days": 0, "hours": 1, "minutes": 0, "seconds": 0 } },
              "recordDefinitionNames": ["HPD:Help Desk", "NotDocumented:Form"],
              "qualification": { "expression": "${ruleContext.Status} = \\"Assigned\\"" },
              "actions": [ { "resourceType": "com.bmc.arsys.rx.services.rule.domain.CustomRuleAction",
                "name": "Raise Priority", "actionTypeName": "setFieldValue",
                "inputMap": [ { "assignTarget": "Priority", "expression": "\\"High\\"" } ],
                "outputMap": [] } ] }
            """);

        IsDefinition assoc = def(IsDefType.ASSOCIATION, "com.acme.helpdesk:Incident To Person", true, """
            { "nodeAName": "HPD:Help Desk", "nodeBName": "CTM:People",
              "nodeAKeys": [1000000000], "nodeBKeys": [1], "cardinality": "MANY_TO_ONE",
              "shouldCascadeDelete": false }
            """);

        IsDefinition namedList = def(IsDefType.NAMED_LIST, "com.acme.helpdesk:Open Incidents", null, """
            { "recordDefinitionName": "HPD:Help Desk", "labelFieldId": 8, "valueFieldId": 1,
              "queryCriteria": "'Status' < \\"Resolved\\"", "shouldSortOnLabel": true,
              "searchBehavior": "CONTAINS" }
            """);

        IsRepository repo = IsRepository.of(List.of(bundle),
            Map.of(IsDefType.RULE, List.of(rule), IsDefType.ASSOCIATION, List.of(assoc),
                IsDefType.NAMED_LIST, List.of(namedList)));

        nav = IsPages.render(cfg, repo, name ->
            "HPD:Help Desk".equals(name) ? "../../schema/HPD_Help_Desk/index.htm" : null);
    }

    private static String read(String rel) {
        Path p = out.resolve(rel);
        assertTrue(Files.isRegularFile(p), "expected " + rel);
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void overviewListsBundlesAndTypeCounts() {
        String idx = read("is/index.htm");
        assertTrue(idx.contains("Acme Help Desk"), "bundle friendly name missing");
        assertTrue(idx.contains("com.acme.helpdesk"), "bundle id missing");
        assertTrue(idx.contains(">Rules</a>") || idx.contains("Rules</a>"), "Rules link missing");
        assertTrue(idx.contains("3 definitions across 1 bundles"), "count line wrong: " + snippet(idx));
    }

    @Test
    void ruleDetailRendersTriggerQualificationAndActions() {
        String r = read("is/rule/com.acme.helpdesk_Escalate Stale Incidents.htm");
        assertTrue(r.contains("TimerTriggerEvent"), "trigger type missing");
        assertTrue(r.contains("0d 1h 0m 0s"), "interval missing");
        assertTrue(r.contains("${ruleContext.Status} = &quot;Assigned&quot;"), "Run If expression missing");
        assertTrue(r.contains("setFieldValue"), "action type name missing");
        assertTrue(r.contains("Priority = <code>&quot;High&quot;</code>"), "action input map missing");
        // cross-link: documented form is a link, the other is plain text
        assertTrue(r.contains("<a href=\"../../schema/HPD_Help_Desk/index.htm\">HPD:Help Desk</a>"),
            "record cross-link missing");
        assertTrue(r.contains("NotDocumented:Form") && !r.contains(">NotDocumented:Form</a>"),
            "undocumented record should be plain text");
        assertTrue(r.contains("Raw definition (JSON)"), "raw JSON fallback missing");
    }

    @Test
    void associationDetailRendersNodesAndCardinality() {
        String a = read("is/association/com.acme.helpdesk_Incident To Person.htm");
        assertTrue(a.contains("MANY_TO_ONE"), "cardinality missing");
        assertTrue(a.contains("<a href=\"../../schema/HPD_Help_Desk/index.htm\">HPD:Help Desk</a>"), "node A link missing");
        assertTrue(a.contains("CTM:People"), "node B name missing");
    }

    @Test
    void namedListDetailAndListFilter() {
        String nl = read("is/named_list/com.acme.helpdesk_Open Incidents.htm");
        assertTrue(nl.contains("<a href=\"../../schema/HPD_Help_Desk/index.htm\">HPD:Help Desk</a>"), "record link missing");
        assertTrue(nl.contains("'Status' &lt; &quot;Resolved&quot;"), "query criteria missing");
        assertTrue(nl.contains("CONTAINS"), "search behavior missing");
        // the per-type list page carries the generic client filter hook
        String list = read("is/named_list/index.htm");
        assertTrue(list.contains("data-filter-table=\"isList\""), "list page has no filter input");
        assertTrue(list.contains("class=\"list-page\""), "list page missing list-page body class");
    }

    @Test
    void feedsSearchIndexAndReturnsNav() {
        assertTrue(SearchIndex.size() >= 3, "IS defs + overview should be in the search index, got " + SearchIndex.size());
        assertEquals("Innovation Studio", nav.label());
        assertFalse(nav.children().isEmpty(), "nav should have per-type children");
        assertTrue(nav.children().stream().anyMatch(c -> c.label().startsWith("Rules")), "Rules nav child missing");
    }

    private static String snippet(String s) {
        int i = s.indexOf("definitions across");
        return i < 0 ? "(not found)" : s.substring(Math.max(0, i - 20), Math.min(s.length(), i + 40));
    }
}
