package arinside.diff;

import arinside.ar.*;
import arinside.ar.deffile.DefFileParser;
import arinside.ar.xmlfile.ArsXmlFileParser;
import arinside.ar.xmlfile.ParsedObjects;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MissingFieldReferenceIndex;

/**
 * One parsed snapshot: the four offline repositories plus a {@link GlobalFieldIndex} (needed to
 * render Run If qualifications with resolved field names / enum labels in the diff report).
 * Built from a single {@code .xml} or {@code .def} export - see {@code Main.java}'s connectionless
 * branch for the same wiring.
 */
public record RepoSet(SchemaSource schemas, WorkflowSource workflow, ContainerSource containers,
                      ImageSource images, GlobalFieldIndex globalFields) {

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
