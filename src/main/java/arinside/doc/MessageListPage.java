package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.WorkflowReferenceIndex;

/**
 * Java port of DocMain.cpp's MessageList - every Message action across active links/filters/
 * escalations, sourced from WorkflowReferenceIndex.messages() (which already visits every one of
 * those objects for the form-reference index, so this adds no extra fetch). Field-substitution
 * ($Field$ -> field name) in the message text is not applied - shown raw, unlike the C++'s
 * TextFindFields pass - a small, deliberate content-fidelity cut given the scale of that feature.
 */
public final class MessageListPage {
    private final AppConfig appConfig;
    private final WorkflowReferenceIndex workflowIndex;

    public MessageListPage(AppConfig appConfig, WorkflowReferenceIndex workflowIndex) {
        this.appConfig = appConfig;
        this.workflowIndex = workflowIndex;
    }

    public void render() {
        PagePath page = Naming.messageList();
        WebPage webPage = new WebPage(page.fileName(), "Messages", page.rootLevel(), appConfig);

        Table tbl = new Table("fieldListAll", "TblObjectList");
        tbl.addColumn(5, "Number");
        tbl.addColumn(25, "Object Name");
        tbl.addColumn(5, "Details");
        tbl.addColumn(5, "Type");
        tbl.addColumn(60, "Text");
        for (var m : workflowIndex.messages()) {
            tbl.addRow(new TableRow().addCellList(
                Integer.toString(m.msgNumber()),
                URLLink.to(m.owner().name(), m.owner().link(), m.owner().icon(), page.rootLevel()).toHtml(),
                m.detail(),
                AREnumLabels.messageType(m.msgType()),
                m.msgText() == null ? "" : WebUtil.validate(m.msgText())));
        }
        if (tbl.numRows() > 0) {
            webPage.addContent(tbl.toXHtml());
        } else {
            webPage.addContent("No messages loaded.");
        }
        webPage.saveInFolder(page.path());
    }
}
