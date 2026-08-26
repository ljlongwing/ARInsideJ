package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.WorkflowReferenceIndex;

/**
 * Java port of DocMain.cpp's NotificationList - every Notify action across filters/escalations
 * (matches the C++, which doesn't scan active links here), sourced from
 * WorkflowReferenceIndex.notifications() (no extra fetch - see MessageListPage's javadoc for the
 * same reasoning). notifyMechanism reuses AREnumLabels.defaultNotify since both map the same
 * AR_NOTIFY_VIA_* constant set.
 */
public final class NotificationListPage {
    private final AppConfig appConfig;
    private final WorkflowReferenceIndex workflowIndex;

    public NotificationListPage(AppConfig appConfig, WorkflowReferenceIndex workflowIndex) {
        this.appConfig = appConfig;
        this.workflowIndex = workflowIndex;
    }

    public void render() {
        PagePath page = Naming.notificationList();
        WebPage webPage = new WebPage(page.fileName(), "Notifications", page.rootLevel(), appConfig);

        Table tbl = new Table("fieldListAll", "TblObjectList");
        tbl.addColumn(25, "Object Name");
        tbl.addColumn(5, "Details");
        tbl.addColumn(10, "Type");
        tbl.addColumn(60, "Text");
        for (var n : workflowIndex.notifications()) {
            tbl.addRow(new TableRow().addCellList(
                URLLink.to(n.owner().name(), n.owner().link(), n.owner().icon(), page.rootLevel()).toHtml(),
                n.detail(),
                AREnumLabels.defaultNotify(n.notifyMechanism()),
                n.text() == null ? "" : n.text()));
        }
        if (tbl.numRows() > 0) {
            webPage.addContent(tbl.toXHtml());
        } else {
            webPage.addContent("No notifications loaded.");
        }
        webPage.saveInFolder(page.path());
    }
}
