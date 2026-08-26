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
}
