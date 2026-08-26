package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.AssociationSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MissingFieldReferenceIndex;
import com.bmc.arsys.api.*;

import java.util.Set;

/** New (post-C++) functionality - see AssociationSource's javadoc. */
public final class AssociationDetailPage {
    private final AssociationSource repo;
    private final AppConfig appConfig;
    private final GlobalFieldIndex fieldIndex;
    private final FieldReferenceIndex fieldRefs;
    private final MissingFieldReferenceIndex missingFieldRefs;
    private final Set<String> knownUserNames;

    public AssociationDetailPage(AssociationSource repo, AppConfig appConfig, GlobalFieldIndex fieldIndex,
                                  FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs, Set<String> knownUserNames) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.fieldIndex = fieldIndex;
        this.fieldRefs = fieldRefs;
        this.missingFieldRefs = missingFieldRefs;
        this.knownUserNames = knownUserNames;
    }

    /** The fetch half - safe to run on a pooled read connection. */
    public Association fetch(AssociationSource repo, String name) throws ARException {
        return repo.getAssociation(name);
    }

    /** Fused fetch+render, for callers that don't route through the parallel read/write pools. */
    public void render(String name) throws ARException {
        render(name, fetch(repo, name));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(String name, Association a) throws ARException {
        PagePath page = Naming.associationDetail(name);
        WebPage webPage = new WebPage(page.fileName(), name, page.rootLevel(), appConfig);

        String head = URLLink.to("Associations", Naming.associationOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.Association, page.rootLevel()).toHtml() + WebUtil.objName(name);
        webPage.addContentHead(head);

        // Own qualifications aren't tied to a single form the way an active link's Run If is - every
        // renderer built below shares the same sink regardless of which form it's rendering against,
        // same as ActiveLinkDetailPage/FilterDetailPage/EscalationDetailPage's pattern. Associations
        // have no If/Else action list the way those three do, but they DO have real distinct
        // reference sites (Primary/Secondary Qualification, Key Mapping, Association Form
        // Qualification, Key Mapping to Association Form) - each call site below now passes its own
        // explicit detail label through render()/fieldRef() rather than relying on
        // QualificationRenderer's page-wide default (which used to make every one of these
        // indistinguishable "Association" entries, same class of bug as the AL/Filter/Escalation fix).
        PagePath associationLink = Naming.associationDetail(name);
        QualificationRenderer.FieldReferenceSink sink = (formName, fieldId, fieldExists, detail) -> {
            FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(name, "Association", ImageTag.Id.Association, associationLink, detail);
            fieldRefs.add(formName, fieldId, ref);
            if (!fieldExists) missingFieldRefs.add(formName, fieldId, ref);
        };
        QualificationRenderer primaryRenderer = new QualificationRenderer(nullToEmpty(a.getPrimaryFormName()), page.rootLevel(), fieldIndex, sink);
        QualificationRenderer secondaryRenderer = new QualificationRenderer(nullToEmpty(a.getSecondaryFormName()), page.rootLevel(), fieldIndex, sink);

        Table tbl = new Table("associationGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Type", a instanceof DirectAssociation ? "Direct" : "Indirect"));
        tbl.addRow(new TableRow().addCellList("Enabled", AREnumLabels.objectEnable(a.isEnable())));
        tbl.addRow(new TableRow().addCellList("Cardinality", AREnumLabels.associationCardinality(a.getCardinality())));
        tbl.addRow(new TableRow().addCellList("Enforcement", AREnumLabels.associationEnforcement(a.getEnforcement())));
        tbl.addRow(new TableRow().addCellList("Primary Form", WebUtil.validate(nullToEmpty(a.getPrimaryFormName()))));
        tbl.addRow(new TableRow().addCellList("Secondary Form", WebUtil.validate(nullToEmpty(a.getSecondaryFormName()))));
        if (a.getPrimaryFormQualification() != null) {
            tbl.addRow(new TableRow().addCellList("Primary Qualification", primaryRenderer.render(a.getPrimaryFormQualification(), "Primary Qualification")));
        }
        if (a.getSecondaryFormQualification() != null) {
            tbl.addRow(new TableRow().addCellList("Secondary Qualification", secondaryRenderer.render(a.getSecondaryFormQualification(), "Secondary Qualification")));
        }
        if (a.getDescription() != null && !a.getDescription().isEmpty()) {
            tbl.addRow(new TableRow().addCellList("Description", WebUtil.validate(a.getDescription())));
        }
        webPage.addContent(tbl.toXHtml());

        if (a instanceof DirectAssociation da) {
            Table mapTbl = new Table("associationMapping", "TblObjectList");
            mapTbl.description = "Key Mapping";
            mapTbl.addColumn(50, "Primary Form Field");
            mapTbl.addColumn(50, "Secondary Form Field");
            if (da.getPkfkMappingSet() != null) {
                for (PKFKMapping m : da.getPkfkMappingSet()) {
                    mapTbl.addRow(new TableRow().addCellList(
                        primaryRenderer.fieldRef(nullToEmpty(a.getPrimaryFormName()), m.getPrimaryKeyFieldId(), "Key Mapping (Primary Form Field)"),
                        secondaryRenderer.fieldRef(nullToEmpty(a.getSecondaryFormName()), m.getForeignKeyFieldId(), "Key Mapping (Secondary Form Field)")));
                }
            }
            webPage.addContent(mapTbl.toXHtml());
        } else if (a instanceof IndirectAssociation ia) {
            Table assocFormTbl = new Table("associationForm", "TblObjectList");
            assocFormTbl.addColumn(30, "Property");
            assocFormTbl.addColumn(70, "Value");
            assocFormTbl.addRow(new TableRow().addCellList("Association Form", WebUtil.validate(nullToEmpty(ia.getAssociationFormName()))));
            if (ia.getAssociationFormQualification() != null) {
                QualificationRenderer assocFormRenderer = new QualificationRenderer(nullToEmpty(ia.getAssociationFormName()), page.rootLevel(), fieldIndex, sink);
                assocFormTbl.addRow(new TableRow().addCellList("Qualification", assocFormRenderer.render(ia.getAssociationFormQualification(), "Association Form Qualification")));
            }
            webPage.addContent(assocFormTbl.toXHtml());

            AssociationFormKeyMapping km = ia.getKeyMappingToAssociationForm();
            if (km != null) {
                Table mapTbl = new Table("associationMapping", "TblObjectList");
                mapTbl.description = "Key Mapping to Association Form";
                mapTbl.addColumn(50, "Form Field");
                mapTbl.addColumn(50, "Association Form Field");
                if (km.getPrimaryFormKeyMapping() != null) {
                    for (PKFKMapping m : km.getPrimaryFormKeyMapping()) {
                        mapTbl.addRow(new TableRow().addCellList("Primary: " +
                            primaryRenderer.fieldRef(nullToEmpty(a.getPrimaryFormName()), m.getPrimaryKeyFieldId(), "Key Mapping to Association Form (Primary)"),
                            "Field " + m.getForeignKeyFieldId()));
                    }
                }
                if (km.getSecondaryFormKeyMapping() != null) {
                    for (PKFKMapping m : km.getSecondaryFormKeyMapping()) {
                        mapTbl.addRow(new TableRow().addCellList("Secondary: " +
                            secondaryRenderer.fieldRef(nullToEmpty(a.getSecondaryFormName()), m.getPrimaryKeyFieldId(), "Key Mapping to Association Form (Secondary)"),
                            "Field " + m.getForeignKeyFieldId()));
                    }
                }
                webPage.addContent(mapTbl.toXHtml());
            }
        }

        webPage.addContent(ServerObjectHistoryWidget.render(a, knownUserNames, page.rootLevel()));

        webPage.saveInFolder(page.path());
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
