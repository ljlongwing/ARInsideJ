package arinside.diff;

import arinside.ar.*;
import arinside.ar.deffile.DefFileParser;
import arinside.ar.xmlfile.ArsXmlFileParser;
import arinside.ar.xmlfile.ParsedObjects;
import arinside.config.AppConfig;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MissingFieldReferenceIndex;

/**
 * One snapshot: the four repositories plus a {@link GlobalFieldIndex} (needed to render Run If
 * qualifications with resolved field names / enum labels in the diff report). Either side of a
 * {@code --diff} can be a {@code .xml}/{@code .def} export ({@link #load}) or the live server
 * ({@link #loadServer}) - see {@code Main.java}'s connectionless and server branches for the same
 * wiring.
 */
public record RepoSet(SchemaSource schemas, WorkflowSource workflow, ContainerSource containers,
                      ImageSource images, GlobalFieldIndex globalFields) {

    /**
     * The live server as one side of a diff. No blacklist is applied - a diff shows everything.
     * Uses the same bulk caches + parallel {@link ReadPool} field-index build the normal run does;
     * a sequential build over every form is far too slow on a real server.
     */
    public static RepoSet loadServer(ArClient client, AppConfig cfg) throws Exception {
        System.out.println("  loading snapshot from the live server " + cfg.serverName + " ...");
        BlackList none = BlackList.empty();
        int overlayMode = OverlaySupport.fetchServerOverlayMode(client);

        SchemaBulkCache schemaCache = SchemaBulkCache.load(client);
        WorkflowBulkCache workflowCache = WorkflowBulkCache.load(client);
        SchemaSource schemas = new SchemaRepository(client, none, schemaCache);
        WorkflowSource workflow = new WorkflowRepository(client, none, workflowCache);
        ContainerSource containers = new ContainerRepository(client, none);
        ImageSource images = new IdentityRepository(client, cfg, none);

        GlobalFieldIndex gfi;
        try (ReadPool reads = ReadPool.open(cfg, cfg.readConcurrency)) {
            gfi = GlobalFieldIndex.build(schemas, reads,
                c -> new SchemaRepository(c, none, schemaCache),
                overlayMode, cfg.overlaySupport,
                new FieldReferenceIndex(), new MissingFieldReferenceIndex());
        }
        return new RepoSet(schemas, workflow, containers, images, gfi);
    }

    public static RepoSet load(String path, boolean overlaySupport) throws Exception {
        boolean def = FileFormatSniffer.isDefFormat(path);
        System.out.println("  parsing " + (def ? ".def" : ".xml") + " snapshot: " + path);
        ParsedObjects parsed = def ? DefFileParser.parse(path) : ArsXmlFileParser.parse(path);

        SchemaSource schemas = new XmlFileSchemaRepository(parsed);
        WorkflowSource workflow = new XmlFileWorkflowRepository(parsed);
        ContainerSource containers = new XmlFileContainerRepository(parsed);
        ImageSource images = new XmlFileImageRepository(parsed);

        // serverOverlayMode = 1 matches the connectionless default elsewhere in this port.
        GlobalFieldIndex gfi = GlobalFieldIndex.build(schemas, 1, overlaySupport,
            new FieldReferenceIndex(), new MissingFieldReferenceIndex());

        return new RepoSet(schemas, workflow, containers, images, gfi);
    }
}
