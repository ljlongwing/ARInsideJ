package arinside.ar;

import com.bmc.arsys.api.Constants;

/** Java port of the subset of core/AREnum.cpp's label tables needed so far (grows per phase). */
public final class AREnumLabels {
    private AREnumLabels() {}

    private static final String UNKNOWN = "Unknown"; // core/AREnum.h EnumDefault

    /** core/AREnum.cpp's CAREnum::OpenWindowModeMapped - collapses the List/Detail/Split sub-variants of Modify/Display(/Direct) down to their base mode, used to gate which sections DocOpenWindowAction shows (Display Type is then derived from the *original*, unmapped mode). */
    public static int openWindowModeMapped(int mode) {
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DETAIL || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_SPLIT) {
            return Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY;
        }
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_DETAIL || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_SPLIT) {
            return Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY;
        }
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_DETAIL || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_SPLIT) {
            return Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT;
        }
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_DETAIL || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_SPLIT) {
            return Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT;
        }
        return mode;
    }

    /** core/AREnum.cpp's CAREnum::OpenWindowMode - the mapped window mode's display label. */
    public static String openWindowMode(int mode) {
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DLG) return "Dialog";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SEARCH) return "Search";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SUBMIT) return "Submit";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_LST) return "Modify (List)";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DETAIL) return "Modify (Details)";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_SPLIT) return "Modify (Split)";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_LST) return "Display (List)";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_DETAIL) return "Display (Detail)";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_SPLIT) return "Display (Split)";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_REPORT) return "Report";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY) return "Modify";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY) return "Display";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT) return "Modify Directly";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT) return "Display Directly";
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_POPUP) return "Popup";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::OpenWindowDisplayType - takes the *unmapped* window mode (List/Detail/Split variants only), unlike openWindowMode. */
    public static String openWindowDisplayType(int unmappedWindowMode) {
        if (unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_LST || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_LST
            || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_LST || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_LST) {
            return "List Only";
        }
        if (unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DETAIL || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_DETAIL
            || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_DETAIL || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_DETAIL) {
            return "Details Only";
        }
        if (unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_SPLIT || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_SPLIT
            || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_SPLIT || unmappedWindowMode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_SPLIT) {
            return "Split Window";
        }
        return "&lt;Clear&gt;";
    }

    /** core/AREnum.cpp's CAREnum::ReportLocation. */
    public static String reportLocation(int type) {
        if (type == Constants.AR_REPORT_LOCATION_EMBEDDED) return "Embedded";
        if (type == Constants.AR_REPORT_LOCATION_LOCAL) return "Local";
        if (type == Constants.AR_REPORT_LOCATION_REPORTING_FORM) return "Report Form";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::ReportOperation - not an AR_* named constant in the real tool either, raw 1/2/3. */
    public static String reportOperation(int type) {
        if (type == 1) return "Edit";
        if (type == 2) return "Run";
        if (type == 3) return "Create";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::SchemaSortOrder. */
    public static String schemaSortOrder(int type) {
        if (type == Constants.AR_SORT_ASCENDING) return "Ascending";
        if (type == Constants.AR_SORT_DESCENDING) return "Descending";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::ColumnDataSourceType - a Table field's Column child's data-source kind. */
    public static String columnDataSourceType(int type) {
        if (type == Constants.AR_COLUMN_LIMIT_DATASOURCE_DATA_FIELD) return "Data Field";
        if (type == Constants.AR_COLUMN_LIMIT_DATASOURCE_DISPLAY_FIELD) return "Display Field";
        if (type == Constants.AR_COLUMN_LIMIT_DATASOURCE_CONTROL_FIELD) return "Control Field";
        if (type == Constants.AR_COLUMN_LIMIT_DATASOURCE_TRIM_FIELD) return "Trim Field";
        if (type == Constants.AR_COLUMN_LIMIT_DATASOURCE_VIEW_FIELD) return "View Field";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::CallGuideMode - CallGuideAction's "Table Loop" mode when guideTableId > 0. */
    public static String callGuideMode(int mode) {
        if (mode == 0) return "All Rows";
        if (mode == Constants.AR_CALL_GUIDE_FORM_HIDDEN) return "Form Hidden";
        if (mode == Constants.AR_CALL_GUIDE_LOOP_SELECTED_ONLY) return "Selected Rows";
        if (mode == Constants.AR_CALL_GUIDE_LOOP_ALL_ROWS_VISIBLE) return "All Visible Rows";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::NoMatchRequest - Set/Push Fields "If No Requests Match" option. */
    public static String noMatchOption(int type) {
        if (type == Constants.AR_NO_MATCH_ERROR) return "Display 'No Match' Error";
        if (type == Constants.AR_NO_MATCH_SET_NULL) return "Set Fields to $NULL$";
        if (type == Constants.AR_NO_MATCH_NO_ACTION) return "Take No Action";
        if (type == Constants.AR_NO_MATCH_SUBMIT) return "Create a New Request";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::MultiMatchRequest - Set/Push Fields "If Multiple Requests Match" option. */
    public static String multiMatchOption(int type) {
        if (type == Constants.AR_MULTI_MATCH_ERROR) return "Display 'Multiple Match' Error";
        if (type == Constants.AR_MULTI_MATCH_SET_NULL) return "Set Fields to $NULL$";
        if (type == Constants.AR_MULTI_MATCH_USE_FIRST) return "Use First Matching Request";
        if (type == Constants.AR_MULTI_MATCH_PICKLIST) return "Display a List";
        if (type == Constants.AR_MULTI_MATCH_MODIFY_ALL) return "Modify All Matching Requests";
        if (type == Constants.AR_MULTI_MATCH_NO_ACTION) return "Take No Action";
        if (type == Constants.AR_MULTI_MATCH_USE_LOCALE) return "Use First Matching Request Based on Locale";
        return "";
    }

    /** core/AREnum.cpp's CAREnum::MessageType - Message/Notify action severity, used by MessageListPage. */
    public static String messageType(int type) {
        if (type == Constants.AR_RETURN_OK) return "Note";
        if (type == Constants.AR_RETURN_WARNING) return "Warning";
        if (type == Constants.AR_RETURN_ERROR) return "Error";
        if (type == Constants.AR_RETURN_PROMPT) return "Prompt";
        if (type == Constants.AR_RETURN_ACCESSIBLE) return "Accessible";
        if (type == Constants.AR_RETURN_TOOLTIP) return "Tooltip";
        return UNKNOWN;
    }

    /** core/AREnum.cpp's CAREnum::NotifyMechanism - a Notify action's delivery mechanism. Not the same mapping as {@link #defaultNotify} (CAREnum::UserGetDefNotify, a user's own notify preference) - that one labels AR_NOTIFY_VIA_NOTIFIER "Notifier" and has no default case; this one labels it "Alert" and falls back to "Other" for anything unrecognized, confirmed via source, not assumed identical. */
    public static String notifyMechanism(int type) {
        if (type == Constants.AR_NOTIFY_NONE) return "None";
        if (type == Constants.AR_NOTIFY_VIA_NOTIFIER) return "Alert";
        if (type == Constants.AR_NOTIFY_VIA_EMAIL) return "Email";
        if (type == Constants.AR_NOTIFY_VIA_DEFAULT) return "Default";
        return "Other";
    }

    /** core/AREnum.cpp's CAREnum::NotifyFieldList - a Notify action's "Include Fields" mode. */
    public static String notifyFieldList(int type) {
        if (type == Constants.AR_FILTER_FIELD_IDS_NONE) return "None";
        if (type == Constants.AR_FILTER_FIELD_IDS_ALL) return "All";
        if (type == Constants.AR_FILTER_FIELD_IDS_LIST) return "Selected";
        if (type == Constants.AR_FILTER_FIELD_IDS_CHANGED) return "Changed";
        return UNKNOWN;
    }

    /** core/AREnum.cpp's CAREnum::MenuItemType - List menu item kind (Label = has a submenu, Value = a leaf selection). */
    public static String menuItemType(int type) {
        if (type == Constants.AR_MENU_TYPE_VALUE) return "Value";
        if (type == Constants.AR_MENU_TYPE_MENU) return "Label";
        return UNKNOWN;
    }

    /**
     * Association is new (post-C++) functionality with no core/AREnum.cpp counterpart to port -
     * these are plain, human-readable labels for the jar's own AssociationCardinality/
     * AssociationEnforcement enums (whose default toString() returns raw constant names like
     * "ONETOMANY"/"ENFORCE_YES"), matching this port's established label-table style elsewhere
     * rather than a source-fidelity fix.
     */
    public static String associationCardinality(com.bmc.arsys.api.AssociationCardinality c) {
        if (c == null) return "";
        if (c == com.bmc.arsys.api.AssociationCardinality.ONETOONE) return "One to One";
        if (c == com.bmc.arsys.api.AssociationCardinality.ONETOMANY) return "One to Many";
        if (c == com.bmc.arsys.api.AssociationCardinality.MANYTOMANY) return "Many to Many";
        return UNKNOWN;
    }

    public static String associationEnforcement(com.bmc.arsys.api.AssociationEnforcement e) {
        if (e == null) return "";
        if (e == com.bmc.arsys.api.AssociationEnforcement.ENFORCE_YES) return "Yes";
        if (e == com.bmc.arsys.api.AssociationEnforcement.ENFORCE_NO) return "No";
        return UNKNOWN;
    }

    /**
     * Java port of core/ARActiveLink.cpp's CARActiveLink::GetExecuteOn(singleLine=false, props) -
     * a real AR_EXECUTE_ON_* bitmask decoded to a "&lt;br/&gt;"-joined label list (matching the
     * detail page's non-singleLine call), in the C++'s curated order exactly (not alphabetical/
     * declaration order). AR_EXECUTE_ON_MODIFY_ALL/MENU_OPEN are deliberately excluded, matching
     * the C++'s own comments ("its now a $OPERATION$" / "unsupported"). AR_EXECUTE_ON_INTERVAL is
     * deliberately excluded too - the C++ only surfaces it via a separate AR_OPROP_INTERVAL_VALUE
     * object-property check, not this bitmask decode; see ActiveLinkDetailPage.generalInfo for
     * where that property is appended as "Interval: N" (and excluded from the page's generic
     * Object Properties table - see ObjectPropertiesTable's class javadoc). No
     * AR_CURRENT_API_VERSION gating needed here (unlike the C++) since this jar always has the
     * full modern constant set.
     */
    public static String activeLinkExecuteOn(int mask) {
        if (mask == 0) return "None";
        StringBuilder sb = new StringBuilder();
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_BUTTON, "Button/MenuField");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_RETURN, "Return");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_SUBMIT, "Submit");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_MODIFY, "Modify");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_DISPLAY, "Display");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_MENU_CHOICE, "Menu Choice");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_LOSE_FOCUS, "Loose Focus");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_SET_DEFAULT, "Set Default");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_QUERY, "Search");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_AFTER_MODIFY, "After Modify");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_AFTER_SUBMIT, "After Submit");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_GAIN_FOCUS, "Gain Focus");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_WINDOW_OPEN, "Window Open");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_WINDOW_CLOSE, "Window Close");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_UNDISPLAY, "Un-Display");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_COPY_SUBMIT, "Copy To New");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_LOADED, "Window Loaded");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_EVENT, "Event");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_TABLE_CONTENT_CHANGE, "Table Refresh");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_HOVER_FIELD_LABEL, "Hover On Label");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_HOVER_FIELD_DATA, "Hover On Data");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_HOVER_FIELD, "Hover On Field");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_PAGE_EXPAND, "Expand");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_PAGE_COLLAPSE, "Collapse");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_DRAG, "Drag");
        appendIfSet(sb, mask, Constants.AR_EXECUTE_ON_DROP, "Drop");
        return sb.length() == 0 ? "None" : sb.toString();
    }

    /** Java port of core/ARFilter.cpp's CARFilter::GetExecuteOn - a real AR_OPERATION_* bitmask, completely different bits from the AL version above (filters run on data operations, not UI events). */
    public static String filterExecuteOn(int opSet) {
        if (opSet == 0) return "None";
        StringBuilder sb = new StringBuilder();
        appendIfSet(sb, opSet, Constants.AR_OPERATION_GET, "Get Entry");
        appendIfSet(sb, opSet, Constants.AR_OPERATION_SET, "Modify");
        appendIfSet(sb, opSet, Constants.AR_OPERATION_CREATE, "Submit");
        appendIfSet(sb, opSet, Constants.AR_OPERATION_DELETE, "Delete");
        appendIfSet(sb, opSet, Constants.AR_OPERATION_MERGE, "Merge");
        appendIfSet(sb, opSet, Constants.AR_OPERATION_SERVICE, "Service");
        return sb.length() == 0 ? "None" : sb.toString();
    }

    /** "&lt;br/&gt;"-joined, matching CARActiveLink::GetExecuteOn(false,...)/CARFilter::GetExecuteOn(false) - both real callers (the detail pages) pass singleLine=false; only the (unported) overview-table columns would need the comma-joined singleLine=true style. */
    private static void appendIfSet(StringBuilder sb, int mask, int bit, String label) {
        if ((mask & bit) == 0) return;
        if (sb.length() > 0) sb.append("<br/>");
        sb.append(label);
    }

    public static String schemaType(int type) {
        if (type == Constants.AR_SCHEMA_NONE) return "None";
        if (type == Constants.AR_SCHEMA_REGULAR) return "Regular";
        if (type == Constants.AR_SCHEMA_JOIN) return "Join";
        if (type == Constants.AR_SCHEMA_VIEW) return "View";
        if (type == Constants.AR_SCHEMA_DIALOG) return "Dialog";
        if (type == Constants.AR_SCHEMA_VENDOR) return "Vendor";
        return UNKNOWN;
    }

    /** Same as schemaType() but also handles the 100/101 synthetic Audit/Archive override from {@link arinside.scan.SchemaTypeIndex} - not real AR_SCHEMA_* values, so not folded into schemaType() itself. */
    public static String internalSchemaType(int type) {
        if (type == 100) return "Audit";
        if (type == 101) return "Archive";
        return schemaType(type);
    }

    public static arinside.output.ImageTag.Id schemaImage(int type) {
        if (type == Constants.AR_SCHEMA_JOIN) return arinside.output.ImageTag.Id.SchemaJoin;
        if (type == Constants.AR_SCHEMA_VIEW) return arinside.output.ImageTag.Id.SchemaView;
        if (type == Constants.AR_SCHEMA_DIALOG) return arinside.output.ImageTag.Id.SchemaDialog;
        if (type == Constants.AR_SCHEMA_VENDOR) return arinside.output.ImageTag.Id.SchemaVendor;
        return arinside.output.ImageTag.Id.Schema;
    }

    public static String dataType(int type) {
        if (type == Constants.AR_DATA_TYPE_NULL) return "Null";
        if (type == Constants.AR_DATA_TYPE_KEYWORD) return "Keyword";
        if (type == Constants.AR_DATA_TYPE_INTEGER) return "Integer";
        if (type == Constants.AR_DATA_TYPE_REAL) return "Real";
        if (type == Constants.AR_DATA_TYPE_CHAR) return "Character";
        if (type == Constants.AR_DATA_TYPE_DIARY) return "Diary";
        if (type == Constants.AR_DATA_TYPE_ENUM) return "Selection";
        if (type == Constants.AR_DATA_TYPE_TIME) return "Date/Time";
        if (type == Constants.AR_DATA_TYPE_BITMASK) return "Bitmask";
        if (type == Constants.AR_DATA_TYPE_BYTES) return "Bytes";
        if (type == Constants.AR_DATA_TYPE_DECIMAL) return "Decimal";
        if (type == Constants.AR_DATA_TYPE_ATTACH) return "Attach";
        if (type == Constants.AR_DATA_TYPE_CURRENCY) return "Currency";
        if (type == Constants.AR_DATA_TYPE_DATE) return "Date";
        if (type == Constants.AR_DATA_TYPE_TIME_OF_DAY) return "Time of Day";
        if (type == Constants.AR_DATA_TYPE_JOIN) return "Join";
        if (type == Constants.AR_DATA_TYPE_TRIM) return "Trim";
        if (type == Constants.AR_DATA_TYPE_CONTROL) return "Control";
        if (type == Constants.AR_DATA_TYPE_TABLE) return "Table";
        if (type == Constants.AR_DATA_TYPE_COLUMN) return "Column";
        if (type == Constants.AR_DATA_TYPE_PAGE) return "Page";
        if (type == Constants.AR_DATA_TYPE_PAGE_HOLDER) return "Page Holder";
        if (type == Constants.AR_DATA_TYPE_ATTACH_POOL) return "Attach Pool";
        if (type == Constants.AR_DATA_TYPE_ULONG) return "Long";
        if (type == Constants.AR_DATA_TYPE_COORDS) return "Coords";
        if (type == Constants.AR_DATA_TYPE_VIEW) return "View";
        if (type == Constants.AR_DATA_TYPE_DISPLAY) return "Display";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::ActiveLinkAction. */
    public static String activeLinkActionType(int type) {
        if (type == Constants.AR_ACTIVE_LINK_ACTION_NONE) return "None";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_MACRO) return "Run Macro";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_FIELDS) return "Set Fields";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_PROCESS) return "Run Process";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_MESSAGE) return "Message";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_SET_CHAR) return "Change Field";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_DDE) return "DDE";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_FIELDP) return "Push Fields";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_SQL) return "Direct SQL";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_AUTO) return "OLE Automation";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_OPENDLG) return "Open Window";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_COMMITC) return "Commit Changes";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_CLOSEWND) return "Close Windows";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_CALLGUIDE) return "Call Guide";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_EXITGUIDE) return "Exit Guide";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_GOTOGUIDELABEL) return "Go To Guide Label";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_WAIT) return "Wait";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_GOTOACTION) return "Goto";
        if (type == Constants.AR_ACTIVE_LINK_ACTION_SERVICE) return "Service";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::FilterAction (also used for escalations, same action set). */
    public static String filterActionType(int type) {
        if (type == Constants.AR_FILTER_ACTION_NONE) return "None";
        if (type == Constants.AR_FILTER_ACTION_NOTIFY) return "Notify";
        if (type == Constants.AR_FILTER_ACTION_MESSAGE) return "Message";
        if (type == Constants.AR_FILTER_ACTION_LOG) return "Log to File";
        if (type == Constants.AR_FILTER_ACTION_FIELDS) return "Set Fields";
        if (type == Constants.AR_FILTER_ACTION_PROCESS) return "Run Process";
        if (type == Constants.AR_FILTER_ACTION_FIELDP) return "Push Fields";
        if (type == Constants.AR_FILTER_ACTION_SQL) return "Direct SQL";
        if (type == Constants.AR_FILTER_ACTION_GOTOACTION) return "Goto";
        if (type == Constants.AR_FILTER_ACTION_CALLGUIDE) return "Call Guide";
        if (type == Constants.AR_FILTER_ACTION_EXITGUIDE) return "Exit Guide";
        if (type == Constants.AR_FILTER_ACTION_GOTOGUIDELABEL) return "Go To Guide Label";
        if (type == Constants.AR_FILTER_ACTION_SERVICE) return "Service";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::ObjectEnable. */
    public static String objectEnable(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }

    /** Ported from core/AREnum.cpp CAREnum::MenuType. */
    public static String menuType(int type) {
        if (type == Constants.AR_CHAR_MENU_NONE) return "None";
        if (type == Constants.AR_CHAR_MENU_LIST) return "Character";
        if (type == Constants.AR_CHAR_MENU_QUERY) return "Search";
        if (type == Constants.AR_CHAR_MENU_FILE) return "File";
        if (type == Constants.AR_CHAR_MENU_SQL) return "SQL";
        if (type == Constants.AR_CHAR_MENU_SS) return "SS";
        if (type == Constants.AR_CHAR_MENU_DATA_DICTIONARY) return "Data Dictionary";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::MenuRefresh. */
    public static String menuRefresh(int type) {
        if (type == Constants.AR_MENU_REFRESH_CONNECT) return "On Connect";
        if (type == Constants.AR_MENU_REFRESH_OPEN) return "On Open";
        if (type == Constants.AR_MENU_REFRESH_INTERVAL) return "On 15-minute Interval";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::MenuDDLabelFormat (DataDictionaryMenu.getNameType()). */
    public static String menuDDLabelFormat(int type) {
        if (type == Constants.AR_CHAR_MENU_DD_DB_NAME) return "Name";
        if (type == Constants.AR_CHAR_MENU_DD_LOCAL_NAME) return "Label";
        if (type == Constants.AR_CHAR_MENU_DD_ID) return "ID";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::MenuDDValueFormat (DataDictionaryMenu.getValueFormat()). */
    public static String menuDDValueFormat(int type) {
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_NONE) return "None";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_ID) return "ID";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_NAME) return "Name";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_QUOTES) return "'Name'";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_DOLLARS) return "$Name$";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_ID_NAME) return "ID;Name";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_NAMEL) return "Label";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_QUOTESL) return "'Label'";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_DOLLARSL) return "$Label$";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_ID_L) return "ID;Label";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_NAME_L) return ";Name;Label";
        if (type == Constants.AR_CHAR_MENU_DD_FORMAT_L_NAME) return ";Label;Name";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::???/DocCharMenuDetails::GetFieldTypes - bitmask (Data/Trim/Control/Page/PageHolder/Table/Column/Attachment/AttachmentPool), no named AR_* constants exist for these bits so the fixed 9-entry table is hardcoded, matching the C++ 1:1. */
    public static String menuDDFieldTypes(int fieldMask) {
        String[] names = {"Data", "Trim", "Control", "Page", "Page Holder", "Table", "Column", "Attachment", "Attachment Pool"};
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < names.length; k++) {
            if ((fieldMask & (1 << k)) != 0) sb.append(names[k]).append("<br/>\n");
        }
        return sb.toString();
    }

    /**
     * Friendly label for the object-reference types most useful on a container's Members tab.
     * com.bmc.arsys.api.ReferenceType.toString() prints "[Type=N]" rather than a name (confirmed
     * empirically against real container data - not documented behavior, don't assume otherwise),
     * so this covers the "this is a real referenced object" types plus the app/packing-list
     * grouping types actually observed in real data; the many config/metadata reference types
     * (help file names, icons, ...) intentionally fall through to the raw numeric label - they're
     * container properties, not member objects, and aren't worth naming individually here.
     */
    public static String referenceType(com.bmc.arsys.api.ReferenceType type) {
        if (type == null) return "";
        int code = type.toInt();
        if (code == com.bmc.arsys.api.ReferenceType.SCHEMA.toInt()) return "Schema";
        if (code == com.bmc.arsys.api.ReferenceType.FILTER.toInt()) return "Filter";
        if (code == com.bmc.arsys.api.ReferenceType.ESCALATION.toInt()) return "Escalation";
        if (code == com.bmc.arsys.api.ReferenceType.ACTIVELINK.toInt()) return "Active Link";
        if (code == com.bmc.arsys.api.ReferenceType.CONTAINER.toInt()) return "Container";
        if (code == com.bmc.arsys.api.ReferenceType.CHAR_MENU.toInt()) return "Menu";
        if (code == com.bmc.arsys.api.ReferenceType.IMAGE.toInt()) return "Image";
        if (code == com.bmc.arsys.api.ReferenceType.ASSOCIATION.toInt()) return "Association";
        if (code == com.bmc.arsys.api.ReferenceType.APPLICATION_FORMS.toInt()) return "Application Form";
        if (code == com.bmc.arsys.api.ReferenceType.PACKINGLIST_GUIDE.toInt()) return "Packing List: Guide";
        if (code == com.bmc.arsys.api.ReferenceType.PACKINGLIST_APP.toInt()) return "Packing List: Application";
        if (code == com.bmc.arsys.api.ReferenceType.PACKINGLIST_PACK.toInt()) return "Packing List: Packing List";
        if (code == com.bmc.arsys.api.ReferenceType.PACKINGLIST_FILTER_GUIDE.toInt()) return "Packing List: Filter Guide";
        if (code == com.bmc.arsys.api.ReferenceType.PACKINGLIST_WEBSERVICE.toInt()) return "Packing List: Web Service";
        if (code == com.bmc.arsys.api.ReferenceType.WS_OPERATION.toInt()) return "Web Service Operation";
        if (code == com.bmc.arsys.api.ReferenceType.WS_WSDL.toInt()) return "Web Service WSDL";
        return "[Type=" + code + "]";
    }

    /** Java port of core/AREnum.cpp's CAREnum::ContainerType - the container's OWN subtype label (as opposed to {@link #referenceType}, which labels a reference/content-list entry's type). */
    public static String containerType(int containerType) {
        return switch (containerType) {
            case Constants.ARCON_GUIDE -> "Active Link Guide";
            case Constants.ARCON_APP -> "Application";
            case Constants.ARCON_PACK -> "Packing List";
            case Constants.ARCON_FILTER_GUIDE -> "Filter Guide";
            case Constants.ARCON_WEBSERVICE -> "Webservice";
            default -> "";
        };
    }

    /** Ported from core/AREnum.cpp CAREnum::GroupType. */
    public static String groupType(int type) {
        if (type == Constants.AR_GROUP_TYPE_VIEW) return "View";
        if (type == Constants.AR_GROUP_TYPE_CHANGE) return "Change";
        return "None";
    }

    /** Ported from core/AREnum.cpp CAREnum::GroupCategory. */
    public static String groupCategory(int category) {
        if (category == Constants.AR_GROUP_CATEGORY_REGULAR) return "Regular";
        if (category == Constants.AR_GROUP_CATEGORY_DYNAMIC) return "Dynamic";
        if (category == Constants.AR_GROUP_CATEGORY_COMPUTED) return "Computed";
        return "None";
    }

    /** Ported from core/AREnum.cpp CAREnum::FieldType. */
    public static String fieldType(int type) {
        if (type == Constants.AR_FIELD_TYPE_DATA) return "Data";
        if (type == Constants.AR_FIELD_TYPE_TRIM) return "Trim";
        if (type == Constants.AR_FIELD_TYPE_CONTROL) return "Control";
        if (type == Constants.AR_FIELD_TYPE_PAGE) return "Page";
        if (type == Constants.AR_FIELD_TYPE_PAGE_HOLDER) return "Page Holder";
        if (type == Constants.AR_FIELD_TYPE_TABLE) return "Table";
        if (type == Constants.AR_FIELD_TYPE_COLUMN) return "Column";
        if (type == Constants.AR_FIELD_TYPE_ATTACH) return "Attachment";
        if (type == Constants.AR_FIELD_TYPE_ATTACH_POOL) return "Attachment Pool";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::FieldOption. */
    public static String fieldOption(int option) {
        if (option == Constants.AR_FIELD_OPTION_REQUIRED) return "Required";
        if (option == Constants.AR_FIELD_OPTION_OPTIONAL) return "Optional";
        if (option == Constants.AR_FIELD_OPTION_SYSTEM) return "System";
        if (option == Constants.AR_FIELD_OPTION_DISPLAY) return "Display Only";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::QBEMatch. */
    public static String qbeMatch(int match) {
        if (match == Constants.AR_QBE_MATCH_ANYWHERE) return "Anywhere";
        if (match == Constants.AR_QBE_MATCH_LEADING) return "Leading Substring";
        if (match == Constants.AR_QBE_MATCH_EQUAL) return "Equal";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::VUIType. */
    public static String vuiType(int type) {
        if (type == Constants.AR_VUI_TYPE_WINDOWS) return "Windows";
        if (type == Constants.AR_VUI_TYPE_STANDARD) return "Standard (Java)";
        if (type == Constants.AR_VUI_TYPE_WEB) return "Web (Relative Positioning)";
        if (type == Constants.AR_VUI_TYPE_WEB_ABS_POS) return "Web (Absolute Positioning)";
        if (type == Constants.AR_VUI_TYPE_WIRELESS) return "Wireless";
        if (type == Constants.AR_VUI_TYPE_WEB_AUTOGEN) return "Web (Auto-generated)";
        if (type == Constants.AR_VUI_TYPE_WEB_ABS_POS_AUTOGEN) return "Web (Absolute, Auto-generated)";
        if (type == Constants.AR_VUI_TYPE_PROGRESSIVE) return "Progressive";
        return "None";
    }

    /**
     * Best human-readable display string for a VUI - NOT a port of anything in DocVuiDetails.cpp
     * (the real C++'s own VUI detail page title is just {@code vui.GetName()}, the raw internal
     * name, which for every out-of-the-box/auto-generated VUI is itself just a number, e.g.
     * "399990344" - genuinely not more useful than a bare ID there). A deliberate improvement over
     * both the C++ and this port's own prior placeholder title ("VUI &lt;id&gt;"): prefers the
     * AR_DPROP_LABEL display property - the text AR Developer Studio actually shows to a user (e.g.
     * "Best Practice View") - since that's what a real person recognizes a VUI by, falling back to
     * the raw {@code getName()} (same precedent already established and justified in
     * SchemaDetailPage's defaultViewCell()), then to a bare numeric-ID label only if neither is set.
     */
    public static String vuiDisplayName(com.bmc.arsys.api.View view) {
        String label = arinside.ar.PropertyHelper.stringProperty(view.getDisplayProperties(), Constants.AR_DPROP_LABEL);
        if (!label.isEmpty()) return label;
        String name = view.getName();
        if (name != null && !name.isEmpty()) return name;
        return "VUI " + view.getVUIId();
    }

    /** Ported from core/AREnum.cpp CAREnum::UserGetLicType. */
    public static String licenseType(int type) {
        if (type == 0) return "Read";
        if (type == 1) return "Fixed";
        if (type == 2) return "Floating";
        if (type == 3) return "Restricted";
        return UNKNOWN;
    }

    /** Ported from core/AREnum.cpp CAREnum::UserGetDefNotify. */
    public static String defaultNotify(int type) {
        if (type == Constants.AR_NOTIFY_NONE) return "None";
        if (type == Constants.AR_NOTIFY_VIA_NOTIFIER) return "Notifier";
        if (type == Constants.AR_NOTIFY_VIA_EMAIL) return "Email";
        if (type == Constants.AR_NOTIFY_VIA_DEFAULT) return "Default";
        if (type == Constants.AR_NOTIFY_VIA_XREF) return "Cross-Reference Field";
        return UNKNOWN;
    }

    /**
     * Java port of core/AREnum.cpp's {@code CAREnum::FieldPropertiesValue} (a ~680-line, 111+-case
     * property-id -&gt; value -&gt; label lookup, `core/AREnum.cpp:606-1288`) - the field/VUI
     * display-property enum-value decode table {@link arinside.doc.ObjectPropertiesTable} previously
     * left almost entirely unported (only {@code AR_SMOPROP_OVERLAY_PROPERTY} was done, handled
     * separately there - deliberately NOT duplicated here, see that class's own javadoc). Every
     * {@code AR_DPROP_*}/{@code AR_DVAL_*} constant referenced was cross-checked against the API
     * jar (`javap -constants com.bmc.arsys.api.Constants`) before use; 253 of 257
     * distinct names matched by direct name lookup. Version-gated blocks in the C++ (`#if
     * AR_CURRENT_API_VERSION &gt;= ...`) are all included unconditionally, matching this port's
     * established practice elsewhere of not threading server-version context into doc/ pages (a
     * pre-gate server simply never produces that enum value, which renders identically to the gate
     * being absent).
     *
     * <p>Returns null (not {@link #UNKNOWN}) for a property id or value not in this table - matches
     * the real C++'s own {@code return ""} miss-fallback, letting the caller apply its own "show the
     * raw value instead" fallback rather than this method asserting an opinion about it.
     *
     * <p><b>One real, confirmed API-version divergence, not ported</b>: {@code
     * AR_DPROP_AUTO_FIELD_TYPE}'s value set. The C header this C++ tool targets defines
     * REGULAR=0/NAV=1/ACTION=2/GROUPTITLE=3/PAGETITLE=4/APPTITLE=5 (`thirdparty/arapi/include/ar.h:1953-1958`
     * in the C++ repo), but the modern Java client jar's same-prefixed constants
     * ({@code AR_DVAL_AUTO_FIELD_TITLE=0/GROUP=1/REGULAR=2/NAV=3/BUTTON=4}) are a different,
     * non-corresponding value set - not simply a rename (the numbers don't line up either), and
     * genuinely ambiguous which one a real modern server actually sends for this property. Guessing
     * either mapping risks showing a confidently wrong label (worse than the existing raw-value
     * fallback), so this property is left out of the table entirely rather than guessed.
     */
    public static String fieldPropertiesValue(int propId, int val) {
        switch (propId) {
            case Constants.AR_DPROP_NAVBAR_WORKFLOW_ON_SELECTED_ITEM:
                if (val == Constants.AR_DVAL_NAVBAR_SELITEM_NOFIRE) return "Do Not Fire Workflow";
                if (val == Constants.AR_DVAL_NAVBAR_SELITEM_FIRE) return "Fire Workflow";
                return null;
            case Constants.AR_DPROP_TABLE_COL_WRAP_TEXT:
                if (val == Constants.AR_DVAL_TABLE_COL_WRAP_TEXT_DISABLE) return "Disable";
                if (val == Constants.AR_DVAL_TABLE_COL_WRAP_TEXT_ENABLE) return "Enable";
                return null;
            case Constants.AR_DPROP_VIEWFIELD_BORDERS:
                if (val == Constants.AR_DVAL_VIEWFIELD_BORDERS_DEFAULT) return "Default";
                if (val == Constants.AR_DVAL_VIEWFIELD_BORDERS_NONE) return "None";
                if (val == Constants.AR_DVAL_VIEWFIELD_BORDERS_ENABLE) return "Enable";
                return null;
            case Constants.AR_DPROP_VIEWFIELD_SCROLLBARS:
                if (val == Constants.AR_DVAL_VIEWFIELD_SCROLLBARS_AUTO) return "Auto";
                if (val == Constants.AR_DVAL_VIEWFIELD_SCROLLBARS_ON) return "On";
                if (val == Constants.AR_DVAL_VIEWFIELD_SCROLLBARS_HIDDEN) return "Hidden";
                return null;
            case Constants.AR_DPROP_FIXED_TABLE_HEADERS:
                if (val == Constants.AR_DVAL_FIXED_TABLE_HEADERS_DISABLE) return "Disabled";
                if (val == Constants.AR_DVAL_FIXED_TABLE_HEADERS_ENABLE) return "Enabled";
                return null;
            case Constants.AR_DPROP_TABLE_COL_DISPLAY_TYPE:
                if (val == Constants.AR_DVAL_TABLE_COL_DISPLAY_NONEDITABLE) return "Not editable";
                if (val == Constants.AR_DVAL_TABLE_COL_DISPLAY_EDITABLE) return "Editable";
                if (val == Constants.AR_DVAL_TABLE_COL_DISPLAY_HTML) return "Read Only HTML";
                if (val == Constants.AR_DVAL_TABLE_COL_DISPLAY_PAGE_DATA) return "Page Data";
                if (val == Constants.AR_DVAL_TABLE_COL_DISPLAY_DROPDOWN_MENU) return "Drop-Down Menu";
                return null;
            case Constants.AR_DPROP_TABLE_SELROWS_DISABLE:
                if (val == Constants.AR_DVAL_TABLE_SELROWS_MULTI_SELECT) return "Multiple Selection";
                if (val == Constants.AR_DVAL_TABLE_SELROWS_DISABLE_YES) return "Disable Selection";
                if (val == Constants.AR_DVAL_TABLE_SELROWS_SINGLE_SELECT) return "Single Select";
                return null;
            // Real, confirmed C++ quirk kept exactly as-is (not "fixed"): these two case labels are
            // OTHER properties' own ids (AR_DPROP_TABLE_AUTOREFRESH=5010/AR_DPROP_TABLE_DRILL_COL=5011),
            // not real AR_DVAL_* values of AR_DPROP_TABLE_ENTRIES_RETURNED - almost certainly
            // unreachable dead code in the original tool (a numeric "how many entries" property
            // value would never coincidentally equal another property's own id), but this is a
            // faithful port, not a redesign.
            case Constants.AR_DPROP_TABLE_ENTRIES_RETURNED:
                if (val == Constants.AR_DPROP_TABLE_AUTOREFRESH) return "Auto Refresh";
                if (val == Constants.AR_DPROP_TABLE_DRILL_COL) return "Drill Down";
                return null;
            case Constants.AR_DPROP_TABLE_SELREFRESH:
                if (val == Constants.AR_DVAL_TABLE_SELREFRESH_RETFIRE) return " Retain Select, Fire Workflow";
                if (val == Constants.AR_DVAL_TABLE_SELREFRESH_RETNOFIRE) return "Retain Select, No Workflow";
                if (val == Constants.AR_DVAL_TABLE_SELREFRESH_FIRSTFIRE) return "Select 1st, Fire Workflow";
                if (val == Constants.AR_DVAL_TABLE_SELREFRESH_FIRSTNOFIRE) return "Select 1st, No Workflow";
                if (val == Constants.AR_DVAL_TABLE_SELREFRESH_NOSEL) return "No Selection, No Workflow";
                return null;
            case Constants.AR_DPROP_TABLE_SELINIT:
                if (val == Constants.AR_DVAL_TABLE_SELINIT_SELFIRE) return "Select 1st, Fire Workflow";
                if (val == Constants.AR_DVAL_TABLE_SELINIT_SELNOFIRE) return "Select 1st, No Workflow";
                if (val == Constants.AR_DVAL_TABLE_SELINIT_NOSEL) return "No Select, No Workflow";
                return null;
            case Constants.AR_DPROP_TABLE_DISPLAY_TYPE:
                if (val == Constants.AR_DVAL_TABLE_DISPLAY_TABLE) return "Table";
                if (val == Constants.AR_DVAL_TABLE_DISPLAY_RESULTS_LIST) return "Results List";
                if (val == Constants.AR_DVAL_TABLE_DISPLAY_NOTIFICATION) return "Alert List";
                if (val == Constants.AR_DVAL_TABLE_DISPLAY_SINGLE_TABLE_TREE) return "Single Table Tree";
                if (val == Constants.AR_DVAL_TABLE_DISPLAY_MULTI_TABLE_TREE) return "Multi Table Tree";
                if (val == Constants.AR_DVAL_TABLE_DISPLAY_PAGE_ARRAY) return "Table Page Array Field";
                return null;
            case Constants.AR_DPROP_EXPAND_COLLAPSE_TREE_LEVELS:
                if (val == Constants.AR_DVAL_EXPAND_ALL_LEVELS) return "Expand All";
                if (val == Constants.AR_DVAL_COLLAPSE_ALL_LEVELS) return "Collapse All";
                return null;
            case Constants.AR_DPROP_AUTO_FIELD_NEW_SECTION:
                if (val == Constants.AR_DVAL_AUTO_FIELD_NEW_SECTION_OFF) return "Off";
                if (val == Constants.AR_DVAL_AUTO_FIELD_NEW_SECTION_ON) return "On";
                return null;
            case Constants.AR_DPROP_AUTO_FIELD_NEW_COLUMN:
                if (val == Constants.AR_DVAL_AUTO_FIELD_NEW_COLUMN_OFF) return "Off";
                if (val == Constants.AR_DVAL_AUTO_FIELD_NEW_COLUMN_ON) return "On";
                return null;
            case Constants.AR_DPROP_FORMACTION_FLDS_EXCLUDE:
                if (val == Constants.AR_DVAL_FORMACTION_FLDS_EXCLUDE_OFF) return "Off";
                if (val == Constants.AR_DVAL_FORMACTION_FLDS_EXCLUDE_ON) return "On";
                return null;
            case Constants.AR_DPROP_AUTO_FIELD_ALIGN:
                if (val == Constants.AR_DVAL_AUTO_FIELD_ALIGN_LEFT) return "Left";
                if (val == Constants.AR_DVAL_AUTO_FIELD_ALIGN_RIGHT) return "Right";
                return null;
            case Constants.AR_DPROP_AUTO_FIELD_SPACER:
                if (val == Constants.AR_DVAL_AUTO_FIELD_SPACER_OFF) return "Off";
                if (val == Constants.AR_DVAL_AUTO_FIELD_SPACER_ON) return "On";
                return null;
            case Constants.AR_DPROP_AUTO_FIELD_NAVPROP:
                if (val == Constants.AR_DVAL_AUTO_FIELD_LEVEL1) return "Level1";
                if (val == Constants.AR_DVAL_AUTO_FIELD_LEVEL2) return "Level2";
                if (val == Constants.AR_DVAL_AUTO_FIELD_LEVEL3) return "Level3";
                return null;
            case Constants.AR_DPROP_AUTO_LAYOUT_VUI_NAV:
                if (val == Constants.AR_DVAL_AUTO_LAYOUT_VUI_NAV_OFF) return "Off";
                if (val == Constants.AR_DVAL_AUTO_LAYOUT_VUI_NAV_ON) return "On";
                return null;
            case Constants.AR_DPROP_AUTO_LAYOUT:
                if (val == Constants.AR_DVAL_AUTO_LAYOUT_OFF) return "Off";
                if (val == Constants.AR_DVAL_AUTO_LAYOUT_ON) return "On";
                return null;
            // AR_DPROP_AUTO_FIELD_TYPE deliberately omitted - see method javadoc.
            case Constants.AR_DPROP_AUTOFIT_COLUMNS:
                if (val == Constants.AR_DVAL_AUTOFIT_COLUMNS_NONE) return "None";
                if (val == Constants.AR_DVAL_AUTOFIT_COLUMNS_SET) return "Set";
                return null;
            case Constants.AR_DPROP_REFRESH:
                if (val == Constants.AR_DVAL_REFRESH_NONE) return "None";
                if (val == Constants.AR_DVAL_REFRESH_TABLE_MAX) return "Refresh";
                return null;
            case Constants.AR_DPROP_DRILL_DOWN:
                if (val == Constants.AR_DVAL_DRILL_DOWN_NONE) return "None";
                if (val == Constants.AR_DVAL_DRILL_DOWN_ENABLE) return "Enable";
                return null;
            case Constants.AR_DPROP_SORT_DIR:
                if (val == Constants.AR_DVAL_SORT_DIR_ASCENDING) return "Ascending";
                if (val == Constants.AR_DVAL_SORT_DIR_DESCENDING) return "Descending";
                return null;
            case Constants.AR_DPROP_PANE_VISIBILITY_OPTION:
                if (val == Constants.AR_DVAL_PANE_VISIBILITY_USER_CHOICE) return "User Choice";
                if (val == Constants.AR_DVAL_PANE_VISIBILITY_ADMIN) return "Administrator defined";
                return null;
            case Constants.AR_DPROP_PAGE_ARRANGEMENT:
                if (val == Constants.AR_DVAL_PAGE_SCROLL) return "Scroll";
                if (val == Constants.AR_DVAL_PAGE_LAYER) return "Layer";
                return null;
            case Constants.AR_DPROP_PAGE_LABEL_DISPLAY:
                if (val == Constants.AR_DVAL_PAGE_DISPLAY_TOP) return "Top";
                if (val == Constants.AR_DVAL_PAGE_DISPLAY_BOTTOM) return "Bottom";
                if (val == Constants.AR_DVAL_PAGE_DISPLAY_LEFT) return "Left";
                if (val == Constants.AR_DVAL_PAGE_DISPLAY_RIGHT) return "Right";
                if (val == Constants.AR_DVAL_PAGE_DISPLAY_NONE) return "None";
                return null;
            case Constants.AR_DPROP_DETAIL_PANE_VISIBILITY:
                if (val == Constants.AR_DVAL_PANE_ALWAYS_HIDDEN) return "Always Hidden";
                if (val == Constants.AR_DVAL_PANE_HIDDEN) return "Hidden";
                if (val == Constants.AR_DVAL_PANE_VISIBLE) return "Visible";
                if (val == Constants.AR_DVAL_PANE_ALWAYS_VISIBLE) return "Always Visible";
                return null;
            case Constants.AR_DPROP_BACKGROUND_MODE:
                if (val == Constants.AR_DVAL_BKG_MODE_OPAQUE) return "Default";
                if (val == Constants.AR_DVAL_BKG_MODE_TRANSPARENT) return "Transparent";
                return null;
            case Constants.AR_DPROP_DATETIME_POPUP:
                if (val == Constants.AR_DVAL_DATETIME_BOTH) return "Time and Date";
                if (val == Constants.AR_DVAL_DATETIME_TIME) return "Time Only";
                if (val == Constants.AR_DVAL_DATETIME_DATE) return "Date Only";
                return null;
            case Constants.AR_DPROP_MENU_MODE:
                if (val == Constants.AR_DVAL_CNTL_ITEM) return "Item";
                if (val == Constants.AR_DVAL_CNTL_ON) return "On";
                if (val == Constants.AR_DVAL_CNTL_SEPARATOR) return "Separator";
                if (val == Constants.AR_DVAL_CNTL_CHOICE) return "Choice";
                if (val == Constants.AR_DVAL_CNTL_DIALOG) return "Dialog";
                if (val == Constants.AR_DVAL_CNTL_A_MENU) return "Menu";
                return null;
            case Constants.AR_DPROP_BUTTON_IMAGE_POSITION:
                if (val == Constants.AR_DVAL_IMAGE_CENTER) return "Center";
                if (val == Constants.AR_DVAL_IMAGE_LEFT) return "Left";
                if (val == Constants.AR_DVAL_IMAGE_RIGHT) return "Right";
                if (val == Constants.AR_DVAL_IMAGE_ABOVE) return "Above";
                if (val == Constants.AR_DVAL_IMAGE_BELOW) return "Below";
                return null;
            case Constants.AR_DPROP_LABEL_POS_SECTOR:
                return labelPosSector(val);
            case Constants.AR_DPROP_CHARFIELD_DISPLAY_TYPE:
                if (val == Constants.AR_DVAL_CHARFIELD_EDIT) return "Edit";
                if (val == Constants.AR_DVAL_CHARFIELD_DROPDOWN) return "Dropdown";
                if (val == Constants.AR_DVAL_CHARFIELD_MASKED) return "Masked";
                if (val == Constants.AR_DVAL_CHARFIELD_FILE) return "File";
                return null;
            case Constants.AR_DPROP_DATA_RADIO:
                if (val == Constants.AR_DVAL_RADIO_DROPDOWN) return "Dropdown";
                if (val == Constants.AR_DVAL_RADIO_RADIO) return "Radio";
                if (val == Constants.AR_DVAL_RADIO_CHECKBOX) return "Checkbox";
                return null;
            case Constants.AR_DPROP_ENDCAP_END:
                if (val == Constants.AR_DVAL_ENDCAP_ROUND) return "Rounded";
                if (val == Constants.AR_DVAL_ENDCAP_FLUSH) return "Flush";
                if (val == Constants.AR_DVAL_ENDCAP_EXTENDED) return "Extended";
                if (val == Constants.AR_DVAL_ENDCAP_ARROW1) return "Arrow1";
                return null;
            case Constants.AR_DPROP_JOINT_STYLE:
                if (val == Constants.AR_DVAL_JOINT_EXTENDED) return "Extended";
                if (val == Constants.AR_DVAL_JOINT_SHARP) return "Sharp";
                if (val == Constants.AR_DVAL_JOINT_ROUNDED) return "Rounded";
                if (val == Constants.AR_DVAL_JOINT_SMOOTH) return "Smooth";
                if (val == Constants.AR_DVAL_JOINT_MAX_SMOOTH) return "Max. Smooth";
                return null;
            case Constants.AR_DPROP_ALIGN:
            case Constants.AR_DPROP_LABEL_POS_ALIGN:
            case Constants.AR_DPROP_LABEL_ALIGN:
                if (val == Constants.AR_DVAL_ALIGN_DEFAULT) return "Default";
                if (val == Constants.AR_DVAL_ALIGN_TOP) return "Top";
                if (val == Constants.AR_DVAL_ALIGN_MIDDLE) return "Middle";
                if (val == Constants.AR_DVAL_ALIGN_FILL) return "Fill";
                if (val == Constants.AR_DVAL_ALIGN_BOTTOM) return "Bottom";
                if (val == Constants.AR_DVAL_ALIGN_TILE) return "Tile";
                return null;
            case Constants.AR_DPROP_JUSTIFY:
            case Constants.AR_DPROP_LABEL_POS_JUSTIFY:
            case Constants.AR_DPROP_LABEL_JUSTIFY:
                if (val == Constants.AR_DVAL_JUSTIFY_DEFAULT) return "Default";
                if (val == Constants.AR_DVAL_JUSTIFY_LEFT) return "Left";
                if (val == Constants.AR_DVAL_JUSTIFY_CENTER) return "Center";
                if (val == Constants.AR_DVAL_JUSTIFY_FILL) return "Fill";
                if (val == Constants.AR_DVAL_JUSTIFY_RIGHT) return "Right";
                if (val == Constants.AR_DVAL_JUSTIFY_TILE) return "Tile";
                return null;
            case Constants.AR_DPROP_DEPTH_EFFECT:
                if (val == Constants.AR_DVAL_DEPTH_EFFECT_FLAT) return "Flat";
                if (val == Constants.AR_DVAL_DEPTH_EFFECT_RAISED) return "Raised";
                if (val == Constants.AR_DVAL_DEPTH_EFFECT_SUNKEN) return "Sunken";
                if (val == Constants.AR_DVAL_DEPTH_EFFECT_FLOATING) return "Floating";
                if (val == Constants.AR_DVAL_DEPTH_EFFECT_ETCHED) return "Etched";
                return null;
            case Constants.AR_DPROP_ENABLE:
                if (val == Constants.AR_DVAL_ENABLE_DEFAULT) return "Default";
                if (val == Constants.AR_DVAL_ENABLE_READ_ONLY) return "Read Only";
                if (val == Constants.AR_DVAL_ENABLE_READ_WRITE) return "Read/Write";
                if (val == Constants.AR_DVAL_ENABLE_DISABLE) return "Disabled";
                return null;
            case Constants.AR_DPROP_TRIM_TYPE:
                return trimType(val);
            case Constants.AR_DPROP_MANAGE_EXPAND_BOX:
                if (val == Constants.AR_DVAL_EXPAND_BOX_DEFAULT) return "Default";
                if (val == Constants.AR_DVAL_EXPAND_BOX_HIDE) return "Hide";
                if (val == Constants.AR_DVAL_EXPAND_BOX_SHOW) return "Show";
                return null;
            case Constants.AR_DPROP_CNTL_TYPE:
                return controlType(val);
            case Constants.AR_DPROP_LAYOUT_POLICY:
                if (val == Constants.AR_DVAL_LAYOUT_XY) return "XY";
                if (val == Constants.AR_DVAL_LAYOUT_FILL) return "Fill";
                return null;
            case Constants.AR_DPROP_PAGEHOLDER_DISPLAY_TYPE:
                if (val == Constants.AR_DVAL_PAGEHOLDER_DISPLAY_TYPE_TABCTRL) return "TabControl";
                if (val == Constants.AR_DVAL_PAGEHOLDER_DISPLAY_TYPE_STACKEDVIEW) return "StackedView";
                if (val == Constants.AR_DVAL_PAGEHOLDER_DISPLAY_TYPE_SPLITTERVIEW) return "SplitterView";
                if (val == Constants.AR_DVAL_PAGEHOLDER_DISPLAY_TYPE_ACCORDION) return "Accordion";
                return null;
            case Constants.AR_DPROP_ORIENTATION:
                if (val == Constants.AR_DVAL_ORIENTATION_HORIZONTAL) return "Horizontal";
                if (val == Constants.AR_DVAL_ORIENTATION_VERTICAL) return "Vertical";
                if (val == Constants.AR_DVAL_ORIENTATION_VERTICAL_UP) return "Vertical Reverse";
                return null;
            case Constants.AR_DPROP_PAGE_HEADER_STATE:
                if (val == Constants.AR_DVAL_PAGE_HEADER_HIDDEN) return "Hidden";
                if (val == Constants.AR_DVAL_PAGE_HEADER_VISIBLE) return "Visible";
                return null;
            case Constants.AR_DPROP_PAGE_BODY_STATE:
                if (val == Constants.AR_DVAL_PAGE_BODY_COLLAPSE) return "Collapse";
                if (val == Constants.AR_DVAL_PAGE_BODY_EXPAND) return "Expand";
                return null;
            case Constants.AR_DPROP_PANELHOLDER_SPLITTER:
                if (val == Constants.AR_DVAL_SPLITTER_SHOW) return "Show";
                if (val == Constants.AR_DVAL_SPLITTER_HIDE) return "Hide";
                if (val == Constants.AR_DVAL_SPLITTER_INVISIBLE) return "Invisible";
                return null;
            case Constants.AR_DPROP_ALIGNED:
                if (val == Constants.AR_DVAL_ALIGNED_LEFT) return "Left";
                if (val == Constants.AR_DVAL_ALIGNED_RIGHT) return "Right";
                return null;
            case Constants.AR_DPROP_LOCALIZE_VIEW:
                if (val == Constants.AR_DVAL_LOCALIZE_VIEW_SKIP) return "Skip";
                if (val == Constants.AR_DVAL_LOCALIZE_VIEW_ALL) return "All";
                return null;
            case Constants.AR_DPROP_LOCALIZE_FIELD:
                if (val == Constants.AR_DVAL_LOCALIZE_FIELD_SKIP) return "Skip";
                if (val == Constants.AR_DVAL_LOCALIZE_FIELD_ALL) return "All";
                return null;
            case Constants.AR_DPROP_AUTO_RESIZE:
                if (val == Constants.AR_DVAL_RESIZE_NONE) return "None";
                if (val == Constants.AR_DVAL_RESIZE_VERT) return "Vertical";
                if (val == Constants.AR_DVAL_RESIZE_HORZ) return "Horizontal";
                if (val == Constants.AR_DVAL_RESIZE_BOTH) return "Both";
                return null;
            case Constants.AR_DPROP_NAVIGATION_MODE:
                if (val == Constants.AR_DVAL_NAV_EXPANDABLE) return "Expandable";
                if (val == Constants.AR_DVAL_NAV_FLYOUT) return "Flyout";
                return null;
            case Constants.AR_DPROP_APPLIST_MODE:
                if (val == Constants.AR_DVAL_APP_TRADITIONAL) return "Tranditional"; // sic - matches the real C++'s own typo verbatim
                if (val == Constants.AR_DVAL_APP_FLYOUT) return "Flyout";
                return null;
            case Constants.AR_DPROP_FIELD_PROCESS_ENTRY_MODE:
                if (val == Constants.AR_DVAL_FIELD_PROCESS_NOT_REQUIRED) return "Not Required";
                if (val == Constants.AR_DVAL_FIELD_PROCESS_REQUIRED) return "Required";
                return null;
            case Constants.AR_DPROP_FIELD_FLOAT_STYLE:
                if (val == Constants.AR_DVAL_FLOAT_STYLE_NONE) return "None";
                if (val == Constants.AR_DVAL_FLOAT_STYLE_MODELESS) return "Modeless";
                if (val == Constants.AR_DVAL_FLOAT_STYLE_DIALOG) return "Dialog";
                if (val == Constants.AR_DVAL_FLOAT_STYLE_TOOLTIP) return "Tooltip";
                return null;
            case Constants.AR_DPROP_FIELD_FLOAT_EFFECT:
                if (val == Constants.AR_DVAL_FLOAT_EFFECT_NONE) return "None";
                if (val == Constants.AR_DVAL_FLOAT_EFFECT_APPEAR_DISAPPEAR) return "Appear/Disappear";
                if (val == Constants.AR_DVAL_FLOAT_EFFECT_GROW_SHRINK) return "Grow/Shrink";
                if (val == Constants.AR_DVAL_FLOAT_EFFECT_FADEIN_FADEOUT) return "Fadein/Fadeout";
                return null;
            case Constants.AR_DPROP_SORT_AGGREGATION_TYPE:
                if (val == Constants.AR_DVAL_SORT_AGGREGATION_NONE) return "None";
                if (val == Constants.AR_DVAL_SORT_AGGREGATION_COUNT) return "Count";
                return null;
            // AR_SMOPROP_OVERLAY_PROPERTY deliberately excluded - already handled by
            // arinside.doc.ObjectPropertiesTable's own dedicated check, kept there rather than
            // duplicated here.
            case Constants.AR_DPROP_TABLE_COLUMN_HEADER_ALIGNMENT:
                if (val == Constants.AR_DVAL_TABLE_COLUMN_ALIGNMENT_RIGHT) return "Right";
                if (val == Constants.AR_DVAL_TABLE_COLUMN_ALIGNMENT_CENTER) return "Center";
                if (val == Constants.AR_DVAL_TABLE_COLUMN_ALIGNMENT_LEFT) return "Left";
                return null;
            case Constants.AR_DPROP_MOUSEOVER_EFFECT:
                if (val == Constants.AR_DVAL_MOUSEOVER_EFFECT_NONE) return "None";
                if (val == Constants.AR_DVAL_MOUSEOVER_EFFECT_CURSOR) return "Cursor";
                if (val == Constants.AR_DVAL_MOUSEOVER_EFFECT_HIGHLIGHT) return "Highlight";
                return null;
            case Constants.AR_DPROP_COLUMN_INITIAL_STATE:
                if (val == Constants.AR_DVAL_COLUMN_INITIAL_STATE_REMOVED) return "Removed";
                if (val == Constants.AR_DVAL_COLUMN_INITIAL_STATE_SHOWN) return "Shown";
                return null;
            default:
                return null;
        }
    }

    /** Ported from core/AREnum.cpp CAREnum::TrimType. */
    private static String trimType(int val) {
        if (val == Constants.AR_DVAL_TRIM_NONE) return UNKNOWN;
        if (val == Constants.AR_DVAL_TRIM_LINE) return "Line";
        if (val == Constants.AR_DVAL_TRIM_SHAPE) return "Shape";
        if (val == Constants.AR_DVAL_TRIM_TEXT) return "Multi-Row text";
        if (val == Constants.AR_DVAL_TRIM_IMAGE) return "Image";
        return "";
    }

    /**
     * Ported from core/AREnum.cpp CAREnum::ControlType - a real, odd "last matching bit wins"
     * quirk (the C++ overwrites its result string on every matching bit rather than concatenating
     * them, so a multi-bit mask silently shows only the highest-bit label), kept exactly as-is
     * rather than "fixed" to a joined list.
     */
    private static String controlType(int bMaskIn) {
        int[] bitmask = {1, 1 << 1, 1 << 2, 1 << 3, 1 << 4, 1 << 5, 1 << 6, 1 << 7, 1 << 8, 1 << 9};
        String[] executeText = {"Button", "Menu", "Toolbar", "Tab Switch", "Url", "Chart", "Meter", "Horiz-Nav", "Vert-Nav", "Nav-Item"};
        String result = "Control";
        for (int k = 0; k < bitmask.length; k++) {
            if ((bMaskIn & bitmask[k]) != 0) result = executeText[k];
        }
        return result;
    }

    /** Ported from the AR_DPROP_LABEL_POS_SECTOR case inline in core/AREnum.cpp's FieldPropertiesValue - unlike controlType(), this one DOES concatenate every matching bit's label (no separator between them, matching the C++'s bare stringstream appends exactly). */
    private static String labelPosSector(int val) {
        int[] bitmask = {1, 1 << 1, 1 << 2, 1 << 3, 1 << 4, 1 << 5};
        String[] sectText = {"None", "Center", "North", "East", "South", "West"};
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < bitmask.length; k++) {
            if ((val & bitmask[k]) != 0) sb.append(sectText[k]);
        }
        return sb.toString();
    }
}
