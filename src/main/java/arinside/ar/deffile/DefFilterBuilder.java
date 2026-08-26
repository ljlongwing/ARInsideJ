package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@code com.bmc.arsys.api.Filter}'s object-level (non-action) fields from a {@code
 * begin filter ... end} block, matching {@code arinside.ar.xmlfile.WorkflowXmlBuilder.buildFilter}'s
 * target shape. Action-body tags are delegated to {@link DefActionBuilder}.
 *
 * <p>Unlike ActiveLink, Filter's {@code permission:} tag IS a {@code groupId\permissionLevel}
 * pair - but the client {@code Filter} type has no permissions accessor at all, so
 * PERMISSION/ADD_PERMISSION tags are simply not applicable here.
 *
 * <p>Error handler fields (ERRORHANDLER_OPTIONS/NAME) map directly onto {@code
 * Filter.setErrorFilterOptions}/{@code setErrorHandlingFilter}, since the client type carries
 * these fields directly.
 */
final class DefFilterBuilder {
    private enum ClauseState { NONE, ACTION, ELSE }

    private final Filter filter = new Filter();
    private ClauseState state = ClauseState.NONE;
    private DefActionBuilder actionBuilder;

    Filter build() {
        return filter;
    }

    void item(DefItemLabel item, String raw, Charset charset) {
        if (state != ClauseState.NONE) {
            if (actionBuilder != null) actionBuilder.item(item, raw, charset);
            return;
        }
        switch (item) {
            case NAME -> filter.setName(raw);
            case OWNER -> filter.setOwner(raw);
            case LAST_CHANGED -> filter.setLastChangedBy(raw);
            case HELP -> filter.setHelpText(raw);
            case OBJECT_PROP, SMOPROP_LIST -> {
                ObjectPropertyMap existing = filter.getProperties();
                filter.setProperties(DefPropertyDecoder.decode(raw, charset, existing != null ? existing : new ObjectPropertyMap()));
            }
            case ENABLE -> filter.setEnable("1".equals(raw));
            case FILTER_OP -> filter.setOpSet(ParseUtil.intValue(raw));
            case FILTER_ORD -> filter.setOrder(ParseUtil.intValue(raw));
            case FILTER_QRY -> filter.setQualifier(DefQualificationDecoder.decode(raw, charset));
            case SCHEMA_NAME, ADD_SCHEMA_NAME -> {
                List<String> forms = filter.getFormList();
                if (forms == null) { forms = new ArrayList<>(); filter.setFormList(forms); }
                forms.add(raw);
                filter.setPrimaryForm(forms.get(0));
            }
            case ERRORHANDLER_OPTIONS -> filter.setErrorFilterOptions(ParseUtil.intValue(raw));
            case ERRORHANDLER_NAME -> filter.setErrorHandlingFilter(raw);
            default -> { /* CHANGE_DIARY/TIMESTAMP - no client setter */ }
        }
    }

    void beginAction() {
        state = ClauseState.ACTION;
        actionBuilder = new DefActionBuilder(false);
    }

    void beginElse() {
        state = ClauseState.ELSE;
        actionBuilder = new DefActionBuilder(false);
    }

    void endActionClause() {
        if (actionBuilder != null) {
            FilterAction action = (FilterAction) actionBuilder.build();
            if (action != null) {
                if (state == ClauseState.ACTION) {
                    List<FilterAction> list = filter.getActionList();
                    if (list == null) { list = new ArrayList<>(); filter.setActionList(list); }
                    list.add(action);
                } else if (state == ClauseState.ELSE) {
                    List<FilterAction> list = filter.getElseList();
                    if (list == null) { list = new ArrayList<>(); filter.setElseList(list); }
                    list.add(action);
                }
            }
        }
        state = ClauseState.NONE;
        actionBuilder = null;
    }
}
