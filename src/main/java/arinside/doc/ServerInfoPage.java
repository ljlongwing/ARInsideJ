package arinside.doc;

import arinside.ar.ArClient;
import arinside.ar.ServerInfoLabels;
import arinside.config.AppConfig;
import arinside.output.*;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ServerInfoMap;
import com.bmc.arsys.api.Value;

/**
 * Java port of CDocMain::ServerInfoList - see ServerInfoLabels' javadoc for how the
 * operation-id-to-name table is derived.
 *
 * Deviation from the C++: the C++'s FillRequest() picks how many sequential operation IDs to
 * request based on the connected server's version (fewer IDs for older servers) specifically to
 * avoid asking for operations the server doesn't know about. The Java API's batch
 * getServerInfo(int[]) call rejects the *entire* request if even one ID is unrecognized
 * ("ERROR (123): Unrecognized server information tag") - there's no partial-success behavior.
 * Rather than replicate the C++'s version-based ID-count table (which would need to be kept in
 * sync with server version numbers), this queries one operation ID at a time and skips whichever
 * ones the server rejects - slower (one round trip per ID, ~350 of them) but robust to any
 * server/jar version mismatch.
 */
public final class ServerInfoPage {
    private final ArClient client;
    private final AppConfig appConfig;

    public ServerInfoPage(ArClient client, AppConfig appConfig) {
        this.client = client;
        this.appConfig = appConfig;
    }

    public void render() {
        PagePath page = Naming.serverInfo();

        Table tbl = new Table("serverDetailList", "TblObjectList");
        tbl.addColumn(40, "Operation");
        tbl.addColumn(60, "Value");

        int shown = 0;
        for (int id : ServerInfoLabels.allKnownIds()) {
            ServerInfoMap info;
            try {
                info = client.raw().getServerInfo(new int[]{id});
            } catch (ARException e) {
                continue; // server doesn't recognize/support this operation - skip it
            }
            Value v = info.get(id);
            String value = v == null || v.getValue() == null ? "" : WebUtil.validate(String.valueOf(v.getValue()));
            if (value.isEmpty()) continue;

            TableRow row = new TableRow();
            row.addCell(ServerInfoLabels.label(id));
            row.addCell(value);
            tbl.addRow(row);
            shown++;
        }
        if (shown > 0) tbl.removeEmptyMessageRow();

        WebPage webPage = new WebPage(page.fileName(), "Server details", page.rootLevel(), appConfig);
        webPage.addContent(new ImageTag(ImageTag.Id.Document, page.rootLevel()).toHtml() + "Server informations");
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }
}
