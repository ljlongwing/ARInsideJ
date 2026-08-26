package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/** Builds {@link ActiveLink}, {@link Filter}, and {@link Escalation} from their top-level XML elements. */
final class WorkflowXmlBuilder {
    private WorkflowXmlBuilder() {}

    /** c positioned at the &lt;activeLink&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static ActiveLink buildActiveLink(XmlCursor c) throws XMLStreamException {
        ActiveLink al = new ActiveLink();
        int executeMask = 0;
        String name = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "activeLinkName" -> name = c.elementText();
                case "owner" -> al.setOwner(c.elementText());
                case "lastModifiedBy" -> al.setLastChangedBy(c.elementText());
                case "modifiedDate" -> arinside.ar.ObjectTimestamp.set(al, XmlTimestamp.parse(c.elementText()));
                case "helpText" -> al.setHelpText(c.elementText());
                case "objectProperties" -> al.setProperties(PropertyMapXmlBuilder.build(c, new ObjectPropertyMap()));
                case "executionOrder" -> al.setOrder(c.intText());
                case "workflowConnect" -> setFormConnect(c, al::setFormList, al::setPrimaryForm);
                case "accessList" -> al.setGroupList(buildGroupIds(c));
                case "executeOn" -> {
                    for (String tok : c.elementText().trim().split("\\s+")) {
                        if (!tok.isEmpty()) executeMask |= XmlEnums.activeLinkExecuteOnBit(tok);
                    }
                }
                case "controlFieldID" -> al.setControlField(c.intText());
                case "focusFieldID" -> al.setFocusField(c.intText());
                case "enabled" -> al.setEnable(Boolean.parseBoolean(c.elementText()));
                case "runIfQualification" -> al.setQualifier(QualifierXmlBuilder.build(c));
                case "ifActions" -> al.setActionList(castActive(ActionXmlBuilder.buildActionList(c, true, name)));
                case "elseActions" -> al.setElseList(castActive(ActionXmlBuilder.buildActionList(c, true, name)));
                case "errorHandlerOptions" -> al.setErrorActlinkOptions(c.intText());
                case "errorHandlerName" -> al.setErrorActlinkName(c.elementText());
                default -> c.skipSubtree();
            }
        }
        if (name != null) al.setName(name);
        al.setExecuteMask(executeMask);
        return al;
    }

    /** c positioned at the &lt;filter&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static Filter buildFilter(XmlCursor c) throws XMLStreamException {
        Filter f = new Filter();
        int opSet = 0;
        String name = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "filterName" -> name = c.elementText();
                case "owner" -> f.setOwner(c.elementText());
                case "lastModifiedBy" -> f.setLastChangedBy(c.elementText());
                case "modifiedDate" -> arinside.ar.ObjectTimestamp.set(f, XmlTimestamp.parse(c.elementText()));
                case "helpText" -> f.setHelpText(c.elementText());
                case "objectProperties" -> f.setProperties(PropertyMapXmlBuilder.build(c, new ObjectPropertyMap()));
                case "executionOrder" -> f.setOrder(c.intText());
                case "workflowConnect" -> setFormConnect(c, f::setFormList, f::setPrimaryForm);
                case "executeOn" -> {
                    for (String tok : c.elementText().trim().split("\\s+")) {
                        if (!tok.isEmpty()) opSet |= XmlEnums.filterOpBit(tok);
                    }
                }
                case "enabled" -> f.setEnable(Boolean.parseBoolean(c.elementText()));
                case "runIfQualification" -> f.setQualifier(QualifierXmlBuilder.build(c));
                case "ifActions" -> f.setActionList(castFilter(ActionXmlBuilder.buildActionList(c, false, name)));
                case "elseActions" -> f.setElseList(castFilter(ActionXmlBuilder.buildActionList(c, false, name)));
                case "errorHandlerOptions" -> f.setErrorFilterOptions(c.intText());
                case "errorHandlerName" -> f.setErrorHandlingFilter(c.elementText());
                default -> c.skipSubtree();
            }
        }
        if (name != null) f.setName(name);
        f.setOpSet(opSet);
        return f;
    }

    /** c positioned at the &lt;escalation&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static Escalation buildEscalation(XmlCursor c) throws XMLStreamException {
        Escalation e = new Escalation();
        String name = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "escalationName" -> name = c.elementText();
                case "owner" -> e.setOwner(c.elementText());
                case "lastModifiedBy" -> e.setLastChangedBy(c.elementText());
                case "modifiedDate" -> arinside.ar.ObjectTimestamp.set(e, XmlTimestamp.parse(c.elementText()));
                case "helpText" -> e.setHelpText(c.elementText());
                case "objectProperties" -> e.setProperties(PropertyMapXmlBuilder.build(c, new ObjectPropertyMap()));
                case "escalationTime" -> e.setEscalationTm(buildEscalationTime(c));
                case "workflowConnect" -> setFormConnect(c, e::setFormList, e::setPrimaryForm);
                case "enabled" -> e.setEnable(Boolean.parseBoolean(c.elementText()));
                case "runIfQualification" -> e.setQualifier(QualifierXmlBuilder.build(c));
                case "ifActions" -> e.setActionList(castFilter(ActionXmlBuilder.buildActionList(c, false, name)));
                case "elseActions" -> e.setElseList(castFilter(ActionXmlBuilder.buildActionList(c, false, name)));
                default -> c.skipSubtree();
            }
        }
        if (name != null) e.setName(name);
        return e;
    }

    private interface FormListSetter { void set(List<String> forms); }
    private interface PrimaryFormSetter { void set(String form); }

    private static void setFormConnect(XmlCursor c, FormListSetter formList, PrimaryFormSetter primary) throws XMLStreamException {
        List<String> forms = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if ("formNameList".equals(c.localName())) {
                while (c.nextTag() == START_ELEMENT) {
                    if ("formName".equals(c.localName())) forms.add(c.elementText());
                    else c.skipSubtree();
                }
            } else {
                c.skipSubtree();
            }
        }
        formList.set(forms);
        if (!forms.isEmpty()) primary.set(forms.get(0));
    }

    private static List<Integer> buildGroupIds(XmlCursor c) throws XMLStreamException {
        List<Integer> ids = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if ("groupID".equals(c.localName())) ids.add(c.intText());
            else c.skipSubtree();
        }
        return ids;
    }

    private static EscalationTimeCriteria buildEscalationTime(XmlCursor c) throws XMLStreamException {
        EscalationTimeCriteria result = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "time" -> result = buildTime(c);
                case "interval" -> result = buildInterval(c);
                default -> c.skipSubtree();
            }
        }
        return result != null ? result : new EscalationTime();
    }

    private static EscalationTime buildTime(XmlCursor c) throws XMLStreamException {
        EscalationTime t = new EscalationTime();
        // DocEscalationDetails renders monthDays/weekDays/hours unconditionally (calling .size() on
        // each), so all three need a non-null BitSet even when the export omits the corresponding
        // element entirely (an escalation not restricted by weekday, for instance, has no
        // <daysOfWeek> element at all - confirmed via the real tag name string-scanned out of the
        // server's own libarxmlutil.so, not otherwise seen in any sampled export).
        t.setMonthDays(new java.util.BitSet());
        t.setWeekDays(new java.util.BitSet());
        t.setHours(new java.util.BitSet());
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "daysOfMonth" -> t.setMonthDays(parseIntBitSet(c.elementText(), 1));
                case "hoursOfDay" -> t.setHours(parseIntBitSet(c.elementText(), 0));
                case "daysOfWeek" -> t.setWeekDays(parseWeekDayBitSet(c.elementText()));
                case "minute" -> t.setMinute(c.intText());
                default -> c.skipSubtree();
            }
        }
        return t;
    }

    private static BitSet parseIntBitSet(String spaceSeparated, int bitOffset) {
        BitSet bits = new BitSet();
        for (String tok : spaceSeparated.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            try {
                bits.set(Integer.parseInt(tok) - bitOffset);
            } catch (NumberFormatException e) {
                System.out.println("[WARN] xmlfile: non-numeric day/hour token '" + tok + "', skipping");
            }
        }
        return bits;
    }

    /** Confirmed against a real export: &lt;daysOfWeek&gt; is space-separated day *names* (e.g. "sunday monday"), unlike daysOfMonth/hoursOfDay which are numeric - sunday=bit0, matching AR's usual weekday-mask convention. */
    private static BitSet parseWeekDayBitSet(String spaceSeparated) {
        BitSet bits = new BitSet();
        for (String tok : spaceSeparated.trim().split("\\s+")) {
            int bit = switch (tok.toLowerCase()) {
                case "sunday" -> 0;
                case "monday" -> 1;
                case "tuesday" -> 2;
                case "wednesday" -> 3;
                case "thursday" -> 4;
                case "friday" -> 5;
                case "saturday" -> 6;
                default -> -1;
            };
            if (bit >= 0) bits.set(bit);
            else if (!tok.isEmpty()) System.out.println("[WARN] xmlfile: unrecognized daysOfWeek token '" + tok + "', skipping");
        }
        return bits;
    }

    private static EscalationInterval buildInterval(XmlCursor c) throws XMLStreamException {
        EscalationInterval interval = new EscalationInterval();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "days" -> interval.setDays(c.intText());
                case "hours" -> interval.setHours(c.intText());
                case "minutes" -> interval.setMinutes(c.intText());
                default -> c.skipSubtree();
            }
        }
        return interval;
    }

    @SuppressWarnings("unchecked")
    private static List<ActiveLinkAction> castActive(List<Action> actions) {
        List<ActiveLinkAction> result = new ArrayList<>();
        for (Action a : actions) {
            if (a instanceof ActiveLinkAction ala) result.add(ala);
            else System.out.println("[WARN] xmlfile: action " + a.getClass().getSimpleName() + " is not a valid ActiveLink action, dropping");
        }
        return result;
    }

    private static List<FilterAction> castFilter(List<Action> actions) {
        List<FilterAction> result = new ArrayList<>();
        for (Action a : actions) {
            if (a instanceof FilterAction fa) result.add(fa);
            else System.out.println("[WARN] xmlfile: action " + a.getClass().getSimpleName() + " is not a valid Filter/Escalation action, dropping");
        }
        return result;
    }
}
