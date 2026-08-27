package arinside.output;

/**
 * Java port of output/FileNaming.cpp's ObjectNameFileNamingStrategy (the C++'s default since
 * 3.0.1 / OldNaming=FALSE). Grown incrementally, one page type at a time, as each doc/ page is
 * ported - see PagePath's javadoc for why this replaces the C++'s CPageParams+switch dispatch.
 */
public final class Naming {
    private Naming() {}

    // directory name constants (output/FileNaming.cpp DIR_* / FILE_* constants)
    private static final String DIR_SCHEMA = "schema";
    private static final String DIR_ACTLINK = "active_link";
    private static final String DIR_FILTER = "filter";
    private static final String DIR_ESCALATION = "escalation";
    private static final String DIR_MENU = "menu";
    private static final String DIR_ALGUIDE = "active_link_guide";
    private static final String DIR_APPLICATION = "application";
    private static final String DIR_PACKINGLIST = "packing_list";
    private static final String DIR_FLTGUIDE = "filter_guide";
    private static final String DIR_WEBSERVICE = "webservice";
    private static final String DIR_USER = "user";
    private static final String DIR_GROUP = "group";
    private static final String DIR_ROLE = "role";
    private static final String DIR_IMAGE = "image";
    private static final String DIR_ASSOCIATION = "association";
    private static final String DIR_OTHER = "other";
    private static final String DIR_OVERVIEW = "overview";
    private static final String FILE_INDEX = "index";
    private static final String AR_RESERVED_OVERLAY_SUFFIX = "__o";

    public static PagePath mainHome() {
        return new PagePath("", FILE_INDEX, 0);
    }

    public static PagePath serverInfo() {
        return new PagePath(DIR_OTHER, "server", 1);
    }

    public static PagePath globalFields() {
        return new PagePath(DIR_OTHER, "global_field_list", 1);
    }

    public static PagePath validatorMain() {
        return new PagePath(DIR_OTHER, "validation_main", 1);
    }

    public static PagePath validatorFormGroups() {
        return new PagePath(DIR_OTHER, "validation_group_form", 1);
    }

    public static PagePath validatorFieldGroups() {
        return new PagePath(DIR_OTHER, "validation_group_field", 1);
    }

    /**
     * Matches ValidatorFieldGroupDetails::GetFileName ("validation_group_field_" + obj->FileID()),
     * but FileID() is the C++'s internal load-order insideId, which has no Java equivalent (see
     * ArClient's javadoc on collapsing core/+lists/) - uses the sanitized schema name instead, so
     * the exact filename won't byte-match the C++'s for this one page type, but the page itself
     * (and its content) is real and stable across runs, which a numeric-only id wouldn't be anyway.
     */
    public static PagePath validatorFieldGroupDetails(String schemaName) {
        return new PagePath(DIR_OTHER, "validation_group_field_" + fileNameOfObjectName(schemaName, false), 1);
    }

    public static PagePath validatorActiveLinkGroups() {
        return new PagePath(DIR_OTHER, "validation_group_al", 1);
    }

    public static PagePath validatorContainerGroups() {
        return new PagePath(DIR_OTHER, "validation_group_container", 1);
    }

    /** Matches ValidatorMissingFields - DocValidator.cpp's FieldReferenceValidator. */
    public static PagePath validatorFieldReferences() {
        return new PagePath(DIR_OTHER, "validation_field_references", 1);
    }

    /** Matches ValidatorMissingMenus - DocValidator.cpp's MenuReferenceValidator. */
    public static PagePath validatorMenuReferences() {
        return new PagePath(DIR_OTHER, "validation_menu_references", 1);
    }

    public static PagePath analyzerMain() {
        return new PagePath(DIR_OTHER, "analyzer_main", 1);
    }

    public static PagePath analyzerQbeCheck() {
        return new PagePath(DIR_OTHER, "analyzer_schema_index", 1);
    }

    public static PagePath customWorkflow() {
        return new PagePath(DIR_OTHER, "custom_workflow", 1);
    }

    public static PagePath messageList() {
        return new PagePath(DIR_OTHER, "message_list", 1);
    }

    public static PagePath notificationList() {
        return new PagePath(DIR_OTHER, "notification_list", 1);
    }

    public static PagePath schemaOverview() {
        return new PagePath(DIR_SCHEMA, FILE_INDEX, 1);
    }

    public static PagePath schemaDetail(String schemaName, boolean isOverlaid) {
        return new PagePath(DIR_SCHEMA + "/" + directoryNameOfObjectName(schemaName, isOverlaid), FILE_INDEX, 2);
    }

    public static PagePath schemaFieldsCsv(String schemaName, boolean isOverlaid) {
        return new PagePath(DIR_SCHEMA + "/" + directoryNameOfObjectName(schemaName, isOverlaid), "form_fields", 2);
    }

    /**
     * Matches ObjectNameSchemaFieldDetail's "fld_" + FileID() pattern, but FileID() is the C++'s
     * internal load-order insideId (no Java equivalent - see ArClient's javadoc). Uses the field's
     * real, stable AR System field ID instead - more meaningful than insideId ever was, though the
     * exact filename won't byte-match the C++'s (same tradeoff already accepted for
     * validatorFieldGroupDetails).
     */
    public static PagePath schemaFieldDetail(String schemaName, boolean isOverlaid, int fieldId) {
        return new PagePath(DIR_SCHEMA + "/" + directoryNameOfObjectName(schemaName, isOverlaid), "fld_" + fieldId, 2);
    }

    /** Matches ObjectNameSchemaVUIDetail - same insideId-vs-real-ID tradeoff as schemaFieldDetail, using the VUI's real VUIId. */
    public static PagePath schemaVuiDetail(String schemaName, boolean isOverlaid, int vuiId) {
        return new PagePath(DIR_SCHEMA + "/" + directoryNameOfObjectName(schemaName, isOverlaid), "vui_" + vuiId, 2);
    }

    public static PagePath activeLinkOverview() {
        return new PagePath(DIR_ACTLINK, FILE_INDEX, 1);
    }

    public static PagePath activeLinkDetail(String name, boolean isOverlaid) {
        return new PagePath(DIR_ACTLINK, fileNameOfObjectName(name, isOverlaid), 1);
    }

    public static PagePath filterOverview() {
        return new PagePath(DIR_FILTER, FILE_INDEX, 1);
    }

    public static PagePath filterDetail(String name, boolean isOverlaid) {
        return new PagePath(DIR_FILTER, fileNameOfObjectName(name, isOverlaid), 1);
    }

    public static PagePath escalationOverview() {
        return new PagePath(DIR_ESCALATION, FILE_INDEX, 1);
    }

    public static PagePath escalationDetail(String name, boolean isOverlaid) {
        return new PagePath(DIR_ESCALATION, fileNameOfObjectName(name, isOverlaid), 1);
    }

    /** New (post-C++) functionality - no equivalent naming class in FileNaming.cpp to match against, so this just follows the same flat-file pattern every other non-schema type already uses. Not overlay-aware: no evidence associations participate in the overlay system the way forms/AL/filter/escalation do, and this jar's Association model has no overlay-property accessor the way ObjectBase-derived types elsewhere in this port do. */
    public static PagePath associationOverview() {
        return new PagePath(DIR_ASSOCIATION, FILE_INDEX, 1);
    }

    public static PagePath associationDetail(String name) {
        return new PagePath(DIR_ASSOCIATION, fileNameOfObjectName(name, false), 1);
    }

    public static PagePath menuOverview() {
        return new PagePath(DIR_MENU, FILE_INDEX, 1);
    }

    public static PagePath menuDetail(String name, boolean isOverlaid) {
        return new PagePath(DIR_MENU, fileNameOfObjectName(name, isOverlaid), 1);
    }

    /**
     * Matches output/FileNaming.cpp's per-ARCON_* directory selection for containers.
     * Values confirmed via the real Constants class (NOT sequential from 0 - verify before
     * touching): ARCON_ALL=0, ARCON_GUIDE=1, ARCON_APP=2, ARCON_PACK=3, ARCON_FILTER_GUIDE=4,
     * ARCON_WEBSERVICE=5.
     */
    private static String containerDir(int containerType) {
        return switch (containerType) {
            case 1 -> DIR_ALGUIDE;         // ARCON_GUIDE
            case 2 -> DIR_APPLICATION;     // ARCON_APP
            case 3 -> DIR_PACKINGLIST;     // ARCON_PACK
            case 4 -> DIR_FLTGUIDE;        // ARCON_FILTER_GUIDE
            case 5 -> DIR_WEBSERVICE;      // ARCON_WEBSERVICE
            default -> "container";
        };
    }

    public static PagePath containerOverview(int containerType) {
        return new PagePath(containerDir(containerType), FILE_INDEX, 1);
    }

    public static PagePath containerDetail(int containerType, String name, boolean isOverlaid) {
        return new PagePath(containerDir(containerType), fileNameOfObjectName(name, isOverlaid), 1);
    }

    public static PagePath userOverview() {
        return new PagePath(DIR_USER, FILE_INDEX, 1);
    }

    /** Matches UserDetail::GetFileName (FileNaming.cpp) - the sanitized login name, no overlay suffix (users aren't overlayable objects). */
    public static PagePath userDetail(String userName) {
        return new PagePath(DIR_USER, fileNameOfObjectName(userName, false), 1);
    }

    public static PagePath groupOverview() {
        return new PagePath(DIR_GROUP, FILE_INDEX, 1);
    }

    /** Matches ObjectNameGroupDetail::GetFileName - the plain group ID, no padding (e.g. "group/0.htm" for the Public group, id 0). */
    public static PagePath groupDetail(int groupId) {
        return new PagePath(DIR_GROUP, Integer.toString(groupId), 1);
    }

    public static PagePath roleOverview() {
        return new PagePath(DIR_ROLE, FILE_INDEX, 1);
    }

    /**
     * Matches ObjectNameRoleDetail::GetFileName, which uses the role's AR System "Request ID"
     * core-field string (e.g. "000000000000001") - now sourced directly from Entry.getEntryId()
     * via RawEntryQuery (see RoleRecord/IdentityRepository), the same field the C++ reads.
     */
    public static PagePath roleDetail(String requestId) {
        return new PagePath(DIR_ROLE, fileNameOfObjectName(requestId, false), 1);
    }

    /** Matches ObjectNameRoleSchemaList - "Form Permission" companion page. */
    public static PagePath roleFormList(String requestId) {
        return new PagePath(DIR_ROLE, fileNameOfObjectName(requestId, false) + "list_form", 1);
    }

    /** Matches ObjectNameRoleFieldList - "Field Permission" companion page. */
    public static PagePath roleFieldList(String requestId) {
        return new PagePath(DIR_ROLE, fileNameOfObjectName(requestId, false) + "list_field", 1);
    }

    /** Matches ObjectNameRoleALList - "Active Link Permission" companion page. */
    public static PagePath roleActiveLinkList(String requestId) {
        return new PagePath(DIR_ROLE, fileNameOfObjectName(requestId, false) + "list_active_link", 1);
    }

    /** Matches ObjectNameRolePackListList/ALGuideList/WebserviceList - one companion page per container type (ARCON_PACK/GUIDE/WEBSERVICE only). */
    public static PagePath roleContainerList(String requestId, int containerType) {
        String suffix = switch (containerType) {
            case 1 -> "list_al_guide";     // ARCON_GUIDE
            case 3 -> "list_packing_list"; // ARCON_PACK
            case 5 -> "list_webservice";   // ARCON_WEBSERVICE
            default -> throw new IllegalArgumentException("no role container-list page for containerType " + containerType);
        };
        return new PagePath(DIR_ROLE, fileNameOfObjectName(requestId, false) + suffix, 1);
    }

    /**
     * Group companion pages use an underscore separator ("<id>_list_X") - unlike role companion
     * pages, which concatenate directly with no separator ("<requestId>list_X"). Confirmed by
     * reading both ObjectNameGroup*List and ObjectNameRole*List in FileNaming.cpp side by side -
     * easy to get wrong since they look identical apart from this one character.
     */
    /** Matches ObjectNameGroupUserList - "Group Members" companion page (groups only, roles have no members). */
    public static PagePath groupUserList(int groupId) {
        return new PagePath(DIR_GROUP, groupId + "_list_user", 1);
    }

    /** Matches ObjectNameGroupSchemaList - "Form Permission" companion page. */
    public static PagePath groupFormList(int groupId) {
        return new PagePath(DIR_GROUP, groupId + "_list_form", 1);
    }

    /** Matches ObjectNameGroupFieldList - "Field Permission" companion page. */
    public static PagePath groupFieldList(int groupId) {
        return new PagePath(DIR_GROUP, groupId + "_list_field", 1);
    }

    /** Matches ObjectNameGroupALList - "Active Link Permission" companion page. */
    public static PagePath groupActiveLinkList(int groupId) {
        return new PagePath(DIR_GROUP, groupId + "_list_active_link", 1);
    }

    /** Matches ObjectNameGroupPackListList/ALGuideList/WebserviceList - one companion page per container type (ARCON_PACK/GUIDE/WEBSERVICE only). */
    public static PagePath groupContainerList(int groupId, int containerType) {
        String suffix = switch (containerType) {
            case 1 -> "_list_al_guide";     // ARCON_GUIDE
            case 3 -> "_list_packing_list"; // ARCON_PACK
            case 5 -> "_list_webservice";   // ARCON_WEBSERVICE
            default -> throw new IllegalArgumentException("no group container-list page for containerType " + containerType);
        };
        return new PagePath(DIR_GROUP, groupId + suffix, 1);
    }

    public static PagePath imageOverview() {
        return new PagePath(DIR_IMAGE, FILE_INDEX, 1);
    }

    public static PagePath imageDetail(String name, boolean isOverlaid) {
        return new PagePath(DIR_IMAGE, fileNameOfObjectName(name, isOverlaid), 1);
    }

    /** Matches PAGE_IMAGE_DATA - the raw image bytes are a flat sibling of the image's own .htm page, e.g. "image/Active.png" next to "image/Active.htm". */
    public static String imageDataFileName(String name, boolean isOverlaid, String extension) {
        return fileNameOfObjectName(name, isOverlaid) + "." + extension;
    }

    /**
     * Ported from FileNaming.cpp's GetFileNameOfObjectName: keep filesystem-safe characters
     * as-is, HEX-escape everything else ("~" + lowercase hex, matching the C++'s
     * "strmTmp << hex; ... strmTmp << '~' << (int)c;" - the stream-wide `<< hex` applies to the
     * escape codes too, easy to miss on a first read), and append the overlay marker when the
     * object is an overlaid base object being displayed under the overlay's name (see
     * IsObjectOverlaid in the C++ for the overlayMode semantics).
     *
     * **Corrected 2026-08-14** after diffing real output against the actual C++ binary: this
     * originally escaped in decimal (e.g. ':' / ASCII 58 -> "~58"), not hex ("~3a") - confirmed by
     * comparing a real form directory name byte-for-byte (C++ produced "AAS~3aActivity", Java
     * produced "AAS~58Activity" for the same "AAS:Activity" form). Decimal vs. hex only look the
     * same for escaped characters 0-9, so this was silently wrong for every non-ASCII-alphanumeric
     * character with a hex representation using a-f - i.e. most of them - across every object type
     * in the whole tool. Comparing against a real baseline caught what code review of the port
     * alone did not.
     *
     * Deviation from the C++ that's intentional, not a bug: a trailing space or dot in a path
     * segment is legal on Linux but rejected outright by Windows (the OS silently strips it;
     * java.nio.file.Path validates and throws InvalidPathException instead of silently stripping
     * it like the C++'s Windows build does via _mkdir/CreateFile) - found via a real object name
     * ("...ModifySet ") on the test server. Escaping trailing space/dot runs keeps this
     * deterministic on every platform instead of inheriting the C++'s latent, silently
     * platform-dependent behavior.
     */
    /**
     * Flat-file variant (everything except schema): the C++'s CWebUtil::DocName just appends
     * ".htm" with no further sanitization, so a name ending in space/dot (e.g. "ENT:PED:ModifySet ")
     * comes through unescaped in the real output ("ENT~3aPED~3aModifySet .htm") - confirmed by
     * diffing against the real C++ baseline (2026-08-14). Only schemaDetail's directory-component
     * variant below needs the trailing-space/dot escape, since a bare trailing space/dot is invalid
     * as a Windows *directory name* (java.nio.file.Path rejects it outright, whereas C++'s Win32
     * _mkdir silently strips it - see schemaDetail's escaping variant).
     */
    static String fileNameOfObjectName(String objName, boolean isOverlaid) {
        StringBuilder sb = sanitize(objName);
        if (isOverlaid) sb.append(AR_RESERVED_OVERLAY_SUFFIX);
        return sb.toString();
    }

    /** Directory-component variant - see fileNameOfObjectName's javadoc for why only this one escapes trailing spaces/dots. */
    private static String directoryNameOfObjectName(String objName, boolean isOverlaid) {
        StringBuilder sb = sanitize(objName);
        if (isOverlaid) sb.append(AR_RESERVED_OVERLAY_SUFFIX);
        escapeTrailingSpacesAndDots(sb);
        return sb.toString();
    }

    private static StringBuilder sanitize(String objName) {
        StringBuilder sb = new StringBuilder(objName.length());
        for (int i = 0; i < objName.length(); i++) {
            char c = objName.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == ',' || c == ' ' || c == '_') {
                sb.append(c);
            } else {
                sb.append('~').append(Integer.toHexString(c));
            }
        }
        return sb;
    }

    /**
     * The C++'s letter-filtered overview/ navigation system (output/FileNaming.cpp's
     * ObjectName*Overview classes under DIR_OVERVIEW) - a second, alternate landing page per
     * workflow-object type that the nav template links to, alongside the `<type>/index.htm` pages
     * this port already builds. Confirmed against the real C++ baseline that only `users` actually
     * splits into separate per-letter files on this server (overview/users_a.htm etc.) - every
     * other type (active links, filters, escalations, menus, containers, images) renders as a
     * single overview/<name>.htm page despite FileNaming.cpp declaring a *LetterOverview class for
     * each of them too; likely client-side JS filtering for the large types in the real tool, not
     * reproduced here - these are static, single-page duplicates of the same list content
     * `<type>/index.htm` already renders, just saved under DIR_OVERVIEW too, matching the real
     * per-type file names 1:1. The `overview/*_action*.htm` action-type breakdown pages and
     * `overview/error_handler.htm` are ported too - see activeLinkActionOverview() etc. below.
     */
    public static PagePath overviewActiveLinks() {
        return new PagePath(DIR_OVERVIEW, "actlinks", 1);
    }

    public static PagePath overviewFilters() {
        return new PagePath(DIR_OVERVIEW, "filters", 1);
    }

    public static PagePath overviewEscalations() {
        return new PagePath(DIR_OVERVIEW, "escalations", 1);
    }

    public static PagePath overviewMenus() {
        return new PagePath(DIR_OVERVIEW, "menus", 1);
    }

    public static PagePath overviewImages() {
        return new PagePath(DIR_OVERVIEW, "images", 1);
    }

    /** Matches ObjectName{ALGuide,FilterGuide,PackingList,Application,Webservice}Overview's per-type file names. */
    public static PagePath overviewContainer(int containerType) {
        String name = switch (containerType) {
            case 1 -> "actlink_guides";     // ARCON_GUIDE
            case 2 -> "apps";               // ARCON_APP
            case 3 -> "packlists";          // ARCON_PACK
            case 4 -> "filter_guides";      // ARCON_FILTER_GUIDE
            case 5 -> "webservices";        // ARCON_WEBSERVICE
            default -> throw new IllegalArgumentException("no overview page for containerType " + containerType);
        };
        return new PagePath(DIR_OVERVIEW, name, 1);
    }

    public static PagePath overviewUsers() {
        return new PagePath(DIR_OVERVIEW, "users", 1);
    }

    /** Matches ObjectNameUserLetterOverview - one page per real starting letter/digit found among registered user names, plus "users_other" for anything outside a-z0-9. */
    public static PagePath overviewUsersLetter(char letter) {
        return new PagePath(DIR_OVERVIEW, "users_" + letter, 1);
    }

    public static PagePath overviewUsersOther() {
        return new PagePath(DIR_OVERVIEW, "users_other", 1);
    }

    /** Java port of CDocMain::ActiveLinkActionList/FilterActionList/EscalationActionList + FilterErrorHandlers's file naming - matches the C++'s overview/*_action*.htm and overview/error_handler.htm 1:1. */
    public static PagePath activeLinkActionOverview() {
        return new PagePath(DIR_OVERVIEW, "actlinks_action", 1);
    }

    public static PagePath activeLinkActionDetail(int actionType) {
        return new PagePath(DIR_OVERVIEW, "actlinks_action_" + actionType, 1);
    }

    /** Set Fields sub-type breakdown page (see {@link arinside.doc.SetFieldsSubtype}) - no C++ counterpart, hence no 1:1 file-name to match. */
    public static PagePath activeLinkActionSubtypeDetail(int actionType, String subKey) {
        return new PagePath(DIR_OVERVIEW, "actlinks_action_" + actionType + "_" + subKey, 1);
    }

    public static PagePath filterActionOverview() {
        return new PagePath(DIR_OVERVIEW, "filters_action", 1);
    }

    public static PagePath filterActionDetail(int actionType) {
        return new PagePath(DIR_OVERVIEW, "filters_action_" + actionType, 1);
    }

    public static PagePath filterActionSubtypeDetail(int actionType, String subKey) {
        return new PagePath(DIR_OVERVIEW, "filters_action_" + actionType + "_" + subKey, 1);
    }

    public static PagePath escalationActionOverview() {
        return new PagePath(DIR_OVERVIEW, "escalations_action", 1);
    }

    public static PagePath escalationActionDetail(int actionType) {
        return new PagePath(DIR_OVERVIEW, "escalations_action_" + actionType, 1);
    }

    public static PagePath escalationActionSubtypeDetail(int actionType, String subKey) {
        return new PagePath(DIR_OVERVIEW, "escalations_action_" + actionType + "_" + subKey, 1);
    }

    public static PagePath filterErrorHandlers() {
        return new PagePath(DIR_OVERVIEW, "error_handler", 1);
    }

    private static void escapeTrailingSpacesAndDots(StringBuilder sb) {
        int end = sb.length();
        int start = end;
        while (start > 0 && (sb.charAt(start - 1) == ' ' || sb.charAt(start - 1) == '.')) {
            start--;
        }
        if (start == end) return; // no trailing space/dot run
        StringBuilder escaped = new StringBuilder();
        for (int i = start; i < end; i++) {
            escaped.append('~').append(Integer.toHexString(sb.charAt(i)));
        }
        sb.replace(start, end, escaped.toString());
    }
}
