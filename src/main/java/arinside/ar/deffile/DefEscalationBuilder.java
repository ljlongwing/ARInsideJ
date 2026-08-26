package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code EscalationParseEventHandler}'s object-level (non-action) tag handling, targeting {@code com.bmc.arsys.api.Escalation}
 * directly (the exact shape {@code arinside.ar.xmlfile.WorkflowXmlBuilder.buildEscalation} already
 * builds). Action-body tags are delegated to {@link DefActionBuilder}.
 *
 * <p>ESCALATION_TMTYPE (1=interval/2=calendar) picks which {@link EscalationTimeCriteria} subtype
 * to build, matching the real handler's own {@code switch(type){case 1: EscalationInterval; case 2:
 * EscalationTime}} - but the client {@link EscalationInterval#setValue(long)} accepts the DEF's raw
 * packed value directly, unlike the domain model's separate day/hour/minute decomposition (confirmed
 * via javap - a genuine simplification).
 */
final class DefEscalationBuilder {
    private enum ClauseState { NONE, ACTION, ELSE }

    private final Escalation esc = new Escalation();
    private ClauseState state = ClauseState.NONE;
    private DefActionBuilder actionBuilder;

    Escalation build() {
        return esc;
    }

    void item(DefItemLabel item, String raw, Charset charset) {
        if (state != ClauseState.NONE) {
            if (actionBuilder != null) actionBuilder.item(item, raw, charset);
            return;
        }
        switch (item) {
            case NAME -> esc.setName(raw);
            case OWNER -> esc.setOwner(raw);
            case LAST_CHANGED -> esc.setLastChangedBy(raw);
            case HELP -> esc.setHelpText(raw);
            case OBJECT_PROP, SMOPROP_LIST -> {
                ObjectPropertyMap existing = esc.getProperties();
                esc.setProperties(DefPropertyDecoder.decode(raw, charset, existing != null ? existing : new ObjectPropertyMap()));
            }
            case ENABLE -> esc.setEnable("1".equals(raw));
            case ESCALATION_QRY -> esc.setQualifier(DefQualificationDecoder.decode(raw, charset));
            case ESCALATION_TMTYPE -> {
                if (esc.getEscalationTm() == null) {
                    int type = ParseUtil.intValue(raw);
                    if (type == 1) esc.setEscalationTm(new EscalationInterval());
                    else if (type == 2) esc.setEscalationTm(new EscalationTime());
                }
            }
            case ESCALATION_INT -> {
                if (!(esc.getEscalationTm() instanceof EscalationInterval)) esc.setEscalationTm(new EscalationInterval());
                ((EscalationInterval) esc.getEscalationTm()).setValue(ParseUtil.longValue(raw));
            }
            case ESCALATION_MIN -> { ensureTime(); ((EscalationTime) esc.getEscalationTm()).setMinute(ParseUtil.intValue(raw)); }
            case ESCALATION_MON -> { ensureTime(); ((EscalationTime) esc.getEscalationTm()).setMonthDays(ParseUtil.intValue(raw)); }
            case ESCALATION_WEEK -> { ensureTime(); ((EscalationTime) esc.getEscalationTm()).setWeekDays(ParseUtil.intValue(raw)); }
            case ESCALATION_HOUR -> { ensureTime(); ((EscalationTime) esc.getEscalationTm()).setHours(ParseUtil.intValue(raw)); }
            case SCHEMA_NAME, ADD_SCHEMA_NAME -> {
                List<String> forms = esc.getFormList();
                if (forms == null) { forms = new ArrayList<>(); esc.setFormList(forms); }
                forms.add(raw);
                esc.setPrimaryForm(forms.get(0));
            }
            default -> { /* CHANGE_DIARY/TIMESTAMP - no client setter, matches Form's identical documented gap */ }
        }
    }

    private void ensureTime() {
        if (!(esc.getEscalationTm() instanceof EscalationTime)) esc.setEscalationTm(new EscalationTime());
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
            FilterAction action = (FilterAction) actionBuilder.build(); // EscalationAction has no distinct client type - Escalation.setActionList takes the same FilterAction-implementing types, confirmed via WorkflowXmlBuilder.buildEscalation
            if (action != null) {
                if (state == ClauseState.ACTION) {
                    List<FilterAction> list = esc.getActionList();
                    if (list == null) { list = new ArrayList<>(); esc.setActionList(list); }
                    list.add(action);
                } else if (state == ClauseState.ELSE) {
                    List<FilterAction> list = esc.getElseList();
                    if (list == null) { list = new ArrayList<>(); esc.setElseList(list); }
                    list.add(action);
                }
            }
        }
        state = ClauseState.NONE;
        actionBuilder = null;
    }
}
