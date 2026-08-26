package arinside.doc;

/**
 * Java port of WorkflowReferenceTable::LinkToAlRef/LinkToFilterRef's " (N)" order suffix
 * (RefItem.cpp/WorkflowReferenceTable.cpp) - appended after an Active Link or Filter reference's
 * link text, sourced from that object's own Order. Never applied to any other reference type
 * (Schema/Field/VUI/Menu/Container/Escalation) - CRefItem::GetObjectOrder() itself only handles
 * AR_STRUCT_ITEM_XML_ACTIVE_LINK/AR_STRUCT_ITEM_XML_FILTER (returning -1 for everything else), and
 * WorkflowReferenceTable::LinkToObjByRefItem only special-cases those same two types to begin with.
 */
final class WorkflowRefLabel {
    private WorkflowRefLabel() {}

    static String orderSuffix(String typeLabel, Integer order) {
        if (order == null) return "";
        if (!"Active Link".equals(typeLabel) && !"Filter".equals(typeLabel)) return "";
        return " (" + order + ")";
    }
}
