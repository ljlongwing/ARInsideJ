package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@code com.bmc.arsys.api.ActiveLink}'s object-level (non-action) fields from a
 * {@code begin active link ... end} block, matching {@code
 * arinside.ar.xmlfile.WorkflowXmlBuilder.buildActiveLink}'s target shape. Action-body tags
 * ({@code action {}/else {}}) are delegated to {@link DefActionBuilder}.
 *
 * <p>Unlike Form/Field/Filter/Escalation, whose {@code permission:} tag value is a single
 * {@code groupId\permissionLevel} pair, an ActiveLink's is a plain space-separated list of group
 * ids with no permission-level concept at all - matches {@code ActiveLink.setGroupList
 * (List&lt;Integer&gt;)} in the client API, no PermissionInfo pairing to build here.
 *
 * <p>No error-handler fields (ERRORHANDLER_OPTIONS/NAME) - the format doesn't carry these for
 * Active Links even though {@code ActiveLink} the client type has setErrorActlinkOptions/Name
 * accessors. CHANGE_DIARY/TIMESTAMP are read but discarded - {@code ActiveLink} has no
 * diary/last-update-time setter in the client API.
 */
final class DefActiveLinkBuilder {
    private enum ClauseState { NONE, ACTION, ELSE }

    private final ActiveLink al = new ActiveLink();
    private ClauseState state = ClauseState.NONE;
    private DefActionBuilder actionBuilder;

    ActiveLink build() {
        return al;
    }

    void item(DefItemLabel item, String raw, Charset charset) {
        if (state != ClauseState.NONE) {
            if (actionBuilder != null) actionBuilder.item(item, raw, charset);
            return;
        }
        switch (item) {
            case NAME -> al.setName(raw);
            case OWNER -> al.setOwner(raw);
            case LAST_CHANGED -> al.setLastChangedBy(raw);
            case HELP -> al.setHelpText(raw);
            case OBJECT_PROP, SMOPROP_LIST -> {
                ObjectPropertyMap existing = al.getProperties();
                al.setProperties(DefPropertyDecoder.decode(raw, charset, existing != null ? existing : new ObjectPropertyMap()));
            }
            case ENABLE -> al.setEnable("1".equals(raw));
            case ACTLINK_FOCUS -> al.setFocusField(ParseUtil.intValue(raw));
            case ACTLINK_CONTROL -> al.setControlField(ParseUtil.intValue(raw));
            case ACTLINK_MASK -> al.setExecuteMask(ParseUtil.intValue(raw));
            case ACTLINK_QRY -> al.setQualifier(DefQualificationDecoder.decode(raw, charset));
            case ACTLINK_ORD -> al.setOrder(ParseUtil.intValue(raw));
            case PERMISSION, ADD_PERMISSION -> {
                List<Integer> ids = al.getGroupList();
                if (ids == null) { ids = new ArrayList<>(); al.setGroupList(ids); }
                for (String tok : raw.trim().split(" ")) if (!tok.isBlank()) ids.add(ParseUtil.intValue(tok));
            }
            case SCHEMA_NAME, ADD_SCHEMA_NAME -> {
                List<String> forms = al.getFormList();
                if (forms == null) { forms = new ArrayList<>(); al.setFormList(forms); }
                forms.add(raw);
                al.setPrimaryForm(forms.get(0));
            }
            default -> { /* CHANGE_DIARY/TIMESTAMP - no client setter */ }
        }
    }

    void beginAction() {
        state = ClauseState.ACTION;
        actionBuilder = new DefActionBuilder(true);
    }

    void beginElse() {
        state = ClauseState.ELSE;
        actionBuilder = new DefActionBuilder(true);
    }

    void endActionClause() {
        if (actionBuilder != null) {
            ActiveLinkAction action = (ActiveLinkAction) actionBuilder.build();
            if (action != null) {
                if (state == ClauseState.ACTION) {
                    List<ActiveLinkAction> list = al.getActionList();
                    if (list == null) { list = new ArrayList<>(); al.setActionList(list); }
                    list.add(action);
                } else if (state == ClauseState.ELSE) {
                    List<ActiveLinkAction> list = al.getElseList();
                    if (list == null) { list = new ArrayList<>(); al.setElseList(list); }
                    list.add(action);
                }
            }
        }
        state = ClauseState.NONE;
        actionBuilder = null;
    }
}
