package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code com.bmc.arsys.server.domain.imports.def.impl.FormParseEventHandler} + {@code VuiParseEventHandler}
 * (small) - targeting {@code com.bmc.arsys.api.Form}/{@code Field}/{@code View}
 * directly rather than the server-internal domain model, matching {@code arinside.ar.xmlfile.
 * FormXmlBuilder}'s existing target shape (used directly as the reference for what client-API
 * setters exist).
 *
 * <p>One instance per {@code begin schema ... end} block. {@link DefFileParser} owns clause
 * nesting (routing {@code field {}/vui {}} events here) and struct boundaries; this class only
 * tracks per-form/per-field/per-vui state.
 *
 * <p>JOIN_QRY/ARCHIVEINFO_QRY/AUDITINFO_QRY are decoded via {@link DefQualificationDecoder} (added
 * after this class's first pass, then retrofitted in - see that class's own javadoc for the format).
 *
 * <p><b>Deliberately not ported</b> (disclosed, not silently dropped): granular-overlay extend/inherit-mask bookkeeping
 * (the real handler's separate {@code addedPermissions}/{@code addedIndexInfos}/{@code
 * addedSubAdminGrpIds} tracking, merged conditionally in {@code ensureDefinitionIsComplete()} based
 * on {@code GranularOverlayType}) - confirmed via the architecture memory that none of this is
 * rendered anywhere in this port's {@code Doc*Page} classes; {@code ADD_*} tags are treated
 * identically to their non-ADD counterparts (append to the same list) rather than tracked
 * separately, a deliberate simplification.
 */
final class DefFormBuilder {
    record FormResult(String formName, Form form, List<Field> fields, List<View> views) {}

    private Form form = new RegularForm();
    private int schemaTypeRaw = Constants.AR_SCHEMA_REGULAR;
    private final List<Field> fields = new ArrayList<>();
    private final List<View> views = new ArrayList<>();

    private DefFieldBuilder currentField;
    private View currentView;

    private enum ClauseState { NONE, FIELD, VUI }
    private ClauseState clauseState = ClauseState.NONE;

    /** Unified dispatch matching the other Def*Builder classes' shape - routes to formItem/fieldItem/vuiItem based on which clause is currently open. */
    void item(DefItemLabel item, String raw, Object decoded, Charset charset) {
        switch (clauseState) {
            case FIELD -> fieldItem(item, raw, decoded, charset);
            case VUI -> vuiItem(item, raw, decoded, charset);
            case NONE -> formItem(item, raw, decoded, charset);
        }
    }

    /** Ends whichever clause is currently open (field/vui), matching the other builders' endActionClause()-style single exit point; a no-op if neither is open. */
    void endClause() {
        if (clauseState == ClauseState.FIELD) endField();
        else if (clauseState == ClauseState.VUI) endVui();
        clauseState = ClauseState.NONE;
    }

    /**
     * A real, confirmed-via-live-data quirk (not a decoding bug - verified against the raw .def
     * bytes for HPD:Help Desk, a real overlaid form): an overlaid object's plain-named schema block
     * carries {@code AR_SMOPROP_OVERLAY_PROPERTY=1} (AR_OVERLAID_OBJECT, the hidden base layer) with
     * no separate active-layer ({@code =2}) block anywhere in the export under any name - unlike the
     * live server, which always resolves the plain name to the active layer. This parser (like the
     * unrelated live-RPC {@code FileModeSchemaRepository} before it - see its own javadoc for the
     * identical, already-accepted precedent) only ever sees one layer per name and has no way to
     * reconstruct the other offline, so a lone base-layer object is stripped of its overlay marker
     * here and documented under its plain name rather than disappearing behind an unreachable "__o"
     * suffix with nothing left to fill the plain-name slot - the same "one layer only, no merge
     * attempted" scope cut already established for this exact situation, just hit via a different
     * data source this time.
     */
    FormResult build() {
        ObjectPropertyMap props = form.getProperties();
        if (props != null) {
            Value overlay = props.get(Constants.AR_SMOPROP_OVERLAY_PROPERTY);
            if (overlay != null && overlay.getValue() instanceof Number n && n.intValue() == Constants.AR_OVERLAID_OBJECT) {
                props.remove(Constants.AR_SMOPROP_OVERLAY_PROPERTY);
            }
        }
        return new FormResult(form.getName(), form, fields, views);
    }

    // ---- form-level items (directly inside `begin schema`, not inside field{}/vui{}) ----

    void formItem(DefItemLabel item, String raw, Object decoded, Charset charset) {
        switch (item) {
            case NAME -> form.setName(raw);
            case OWNER -> form.setOwner(raw);
            case LAST_CHANGED -> form.setLastChangedBy(raw);
            case DEFAULT_VUI -> form.setDefaultVUI(raw);
            case HELP -> form.setHelpText(raw);
            case OBJECT_PROP, SMOPROP_LIST -> {
                // Both tags target the SAME merged property map on the client side - unlike the
                // server-internal domain model (which splits "properties" vs "serverManagedProperties"),
                // com.bmc.arsys.api.Form.getProperties() already carries overlay/SMOPROP-style entries
                // in one map elsewhere in this port (confirmed: OverlaySupport reads overlay type
                // straight off form.getProperties()), so both tags accumulate into one target map.
                ObjectPropertyMap existing = form.getProperties();
                ObjectPropertyMap props = DefPropertyDecoder.decode(raw, charset, existing != null ? existing : new ObjectPropertyMap());
                form.setProperties(props);
            }
            case GET_LIST_FLDS -> form.setEntryListFieldInfo(decodeEntryListFields(raw));
            case INDEX, ADD_INDEX -> {
                IndexInfo idx = decodeIndex(raw);
                if (idx != null) {
                    List<IndexInfo> list = form.getIndexInfo();
                    if (list == null) { list = new ArrayList<>(); form.setIndexInfo(list); }
                    list.add(idx);
                }
            }
            case SORT_LIST -> form.setSortInfo(decodeSortList(raw));
            case PERMISSION, ADD_PERMISSION -> {
                int sep = raw.indexOf('\\');
                if (sep > 0) {
                    List<PermissionInfo> perms = form.getPermissions();
                    if (perms == null) { perms = new ArrayList<>(); form.setPermissions(perms); }
                    perms.add(new PermissionInfo(ParseUtil.intValue(raw.substring(0, sep)), ParseUtil.intValue(raw.substring(sep + 1))));
                }
            }
            case SCHEMA_SUBADM, ADD_SCHEMA_SUBADM -> {
                List<Integer> grpIds = form.getAdminGrpList();
                if (grpIds == null) { grpIds = new ArrayList<>(); form.setAdminGrpList(grpIds); }
                for (String tok : raw.trim().split(" ")) {
                    if (!tok.isBlank()) grpIds.add(ParseUtil.intValue(tok));
                }
            }
            case SCHEMA_TYPE -> setSchemaType(ParseUtil.intValue(raw));
            // Raw DEF int passed straight through - matches the client Constants.AR_JOIN_OPTION_NONE=0/OUTER=1 scheme directly, same as every other AR_SCHEMA_*/AR_DATA_TYPE_* tag in this file (DEF files are a direct C-API-era artifact, same numbering).
            case JOIN_OPTION -> { if (form instanceof JoinForm j) j.setJoinOption(ParseUtil.intValue(raw)); }
            case JOIN_PRIMARY -> { if (form instanceof JoinForm j) j.setMemberA(raw); }
            case JOIN_SECONDARY -> { if (form instanceof JoinForm j) j.setMemberB(raw); }
            case JOIN_QRY -> { if (form instanceof JoinForm j) j.setJoinQualification(DefQualificationDecoder.decode(raw, charset)); }
            case ARCHIVEINFO_QRY -> { ArchiveInfo a = ensureArchive(); if (a != null) a.setQualifier(DefQualificationDecoder.decode(raw, charset)); }
            case AUDITINFO_QRY -> { AuditInfo a = form.getAuditInfo(); if (a != null) a.setQualifier(DefQualificationDecoder.decode(raw, charset)); }
            case VIEW_KEYFIELD -> { if (form instanceof ViewForm v) v.setKeyField(raw); }
            case VIEW_NAME -> { if (form instanceof ViewForm v) v.setTableName(raw); }
            case EXT_NAME -> { if (form instanceof VendorForm v) v.setVendorName(raw); }
            case EXT_TABLE -> { if (form instanceof VendorForm v) v.setTableName(raw); }
            case ARCHIVEINFO -> setArchiveInfo(raw);
            case ARCHIVEINFO_TIME -> setArchiveTime(raw);
            case ARCHIVEINFO_FROM -> {
                ArchiveInfo a = ensureArchive();
                if (a != null && raw != null && !raw.isEmpty()) a.setArchiveFrom(raw);
            }
            case ARCHIVEINFO_MODIFIEDTIME_QUALIFIER -> { ArchiveInfo a = ensureArchive(); if (a != null) a.setAgeQualifierInDays(ParseUtil.intValue(raw)); }
            case ARCHIVEINFO_MODIFIEDTIME_QUALIFIER_FIELD -> { ArchiveInfo a = ensureArchive(); if (a != null) a.setAgeQualifierFieldId(ParseUtil.intValue(raw)); }
            case ARCHIVEINFO_APPEAR_IN_ARCHIVE_POLICY -> { ArchiveInfo a = ensureArchive(); if (a != null) a.setAppearInArchivePolicy(ParseUtil.intValue(raw) == 1); }
            case ARCHIVEINFO_DESCRIPTION -> { ArchiveInfo a = ensureArchive(); if (a != null) a.setDescription(raw); }
            case AUDITINFO -> setAuditInfo(raw);
            case AUDITINFO_FORM -> setAuditForm(raw);
            default -> { /* not rendered anywhere in this port's doc/ layer, or deferred (qualifications) - see class javadoc */ }
        }
    }

    private void setSchemaType(int type) {
        Form old = form;
        this.schemaTypeRaw = type;
        form = switch (type) {
            case Constants.AR_SCHEMA_JOIN -> new JoinForm();
            case Constants.AR_SCHEMA_VIEW -> new ViewForm();
            case Constants.AR_SCHEMA_DIALOG -> new DisplayOnlyForm();
            case Constants.AR_SCHEMA_VENDOR -> new VendorForm();
            default -> old;
        };
        if (form != old) {
            form.setName(old.getName());
            form.setOwner(old.getOwner());
            form.setLastChangedBy(old.getLastChangedBy());
            form.setHelpText(old.getHelpText());
            form.setDefaultVUI(old.getDefaultVUI());
            form.setProperties(old.getProperties());
        }
    }

    private ArchiveInfo ensureArchive() {
        ArchiveInfo a = form.getArchiveInfo();
        if (a == null) {
            a = new ArchiveInfo();
            form.setArchiveInfo(a);
        }
        return a;
    }

    /** archive: <enable>\<type>\<len>\<targetName if len>0>\ - type==0 && enable==0 && len==0 means "no archive" (matches the real handler's own null-out check). */
    private void setArchiveInfo(String raw) {
        String[] t = raw.trim().split("\\\\", -1);
        if (t.length < 3) return;
        int enable = ParseUtil.intValue(t[0]);
        int type = ParseUtil.intValue(t[1]);
        int len = ParseUtil.intValue(t[2]);
        if (type == 0 && enable == 0 && len == 0) return;
        ArchiveInfo a = ensureArchive();
        a.setEnable(enable == 1);
        a.setArchiveType(type);
        if (len > 0 && t.length > 3) a.setArchiveDest(t[3]);
    }

    /** arch-time: <monthday>\<weekday>\<hourmask>\<minute>\ */
    private void setArchiveTime(String raw) {
        String[] t = raw.trim().split("\\\\", -1);
        if (t.length < 4) return;
        ArchiveInfo a = ensureArchive();
        if (a != null) {
            a.setArchiveTmInfo(new EscalationTime(ParseUtil.intValue(t[0]), ParseUtil.intValue(t[1]), ParseUtil.intValue(t[2]), ParseUtil.intValue(t[3])));
        }
    }

    /**
     * audit: <enable>\<type>\<auditMask>\...\<name if present>\ - the real handler's own token
     * count varies by export version (5 tokens for export-version&gt;10 with a mask, 4 without);
     * this port reads whatever's present positionally (enable, type, then the last non-empty
     * remaining token as the audit-target form name if the token count suggests one was written)
     * rather than replicating the version branch exactly, since {@code com.bmc.arsys.api.AuditInfo}
     * is a flat type either way (see class javadoc) - auditMask is set when a 3rd numeric token
     * is present.
     */
    private void setAuditInfo(String raw) {
        String[] t = raw.trim().split("\\\\", -1);
        if (t.length < 3) return;
        int enable = ParseUtil.intValue(t[0]);
        int style = ParseUtil.intValue(t[1]);
        AuditInfo audit = new AuditInfo();
        audit.setEnable(enable == 1);
        audit.setAuditStyle(style);
        if (t.length >= 3) audit.setAuditMask(ParseUtil.intValue(t[2]));
        if (t.length >= 5 && !t[4].isEmpty()) audit.setAuditForm(t[4]);
        else if (t.length == 4 && !t[3].isEmpty()) audit.setAuditForm(t[3]);
        form.setAuditInfo(audit);
    }

    /** audt-form: <shadowType>\<len>\<name-if-len&gt;0>\ - the alternate shadow-audit-only tag; builds the same flat AuditInfo, defaulting to the LOG_SHADOW style since that's what this tag specifically represents. */
    private void setAuditForm(String raw) {
        String[] t = raw.trim().split("\\\\", -1);
        if (t.length < 3) return;
        int len = ParseUtil.intValue(t[1]);
        if (len <= 0) return;
        AuditInfo audit = form.getAuditInfo();
        if (audit == null) {
            audit = new AuditInfo();
            audit.setEnable(true);
            audit.setAuditStyle(Constants.AR_AUDIT_LOG_SHADOW);
        }
        audit.setAuditForm(t[2]);
        form.setAuditInfo(audit);
    }

    private List<EntryListFieldInfo> decodeEntryListFields(String raw) {
        // get-list-flds: <numFields>\(<fieldId>\<colWidth>\<separator-len>\<separator>\)*
        List<EntryListFieldInfo> list = new ArrayList<>();
        DefValueDecoder d = new DefValueDecoder(raw, null);
        int num = d.readInt();
        for (int i = 0; i < num && !d.isEmpty(); i++) {
            EntryListFieldInfo info = new EntryListFieldInfo();
            info.setFieldId(d.readInt());
            info.setColumnWidth(d.readInt());
            int sepLen = d.readInt();
            info.setSeparator(d.readString(sepLen));
            list.add(info);
        }
        return list;
    }

    /** index: <numFields>\"<fieldId> <fieldId> ..."\<unique 0/1>\<indexType>\ */
    private IndexInfo decodeIndex(String raw) {
        String[] t = raw.trim().split("\\\\", -1);
        if (t.length < 2) return null;
        int numFields = ParseUtil.intValue(t[0]);
        if (numFields <= 0 || numFields > 16) return null;
        List<Integer> fieldIds = new ArrayList<>();
        for (String tok : t[1].trim().split(" ")) {
            if (!tok.isBlank()) fieldIds.add(ParseUtil.intValue(tok));
        }
        if (fieldIds.size() != numFields) return null;
        boolean unique = t.length >= 3 && ParseUtil.intValue(t[2]) == 1;
        int indexType = t.length >= 4 ? ParseUtil.intValue(t[3]) : 0;
        IndexInfo info = new IndexInfo();
        info.setIndexName("");
        info.setIndexFields(fieldIds);
        info.setUnique(unique);
        info.setIndexType(indexType);
        return info;
    }

    /** sort-list: <numItems>\(<fieldId>\<order>\)* */
    private List<SortInfo> decodeSortList(String raw) {
        List<SortInfo> list = new ArrayList<>();
        DefValueDecoder d = new DefValueDecoder(raw, null);
        int num = d.readInt();
        for (int i = 0; i < num && !d.isEmpty(); i++) {
            list.add(new SortInfo(d.readInt(), d.readInt()));
        }
        return list;
    }

    // ---- field { } clause ----

    void beginField() {
        clauseState = ClauseState.FIELD;
        currentField = new DefFieldBuilder();
    }

    void fieldItem(DefItemLabel item, String raw, Object decoded, Charset charset) {
        if (currentField != null) currentField.item(item, raw, decoded, charset);
    }

    void endField() {
        if (currentField != null) {
            Field f = currentField.build();
            FieldMapping mapping = currentField.mapping();
            if (mapping != null) f.setFieldMap(mapping);
            fields.add(f);
        }
        currentField = null;
    }

    // ---- vui { } clause ----

    void beginVui() {
        clauseState = ClauseState.VUI;
        currentView = new View();
        currentView.setVUIType(Constants.AR_VUI_TYPE_WINDOWS);
    }

    void vuiItem(DefItemLabel item, String raw, Object decoded, Charset charset) {
        if (currentView == null) return;
        switch (item) {
            case NAME -> currentView.setName(raw);
            case ID -> currentView.setVUIId(ParseUtil.intValue(raw));
            case OWNER -> currentView.setOwner(raw);
            case LAST_CHANGED -> currentView.setLastChangedBy(raw);
            case VUI_LOCALE -> currentView.setLocale(raw);
            case VUI_TYPE -> currentView.setVUIType(ParseUtil.intValue(raw));
            case OBJECT_PROP, SMOPROP_LIST -> {
                ObjectPropertyMap existing = currentView.getObjectProperties();
                currentView.setObjectProperties(DefPropertyDecoder.decode(raw, charset, existing != null ? existing : new ObjectPropertyMap()));
            }
            case DISPLAY_PROPLIST -> currentView.setDisplayProperties(DefPropertyDecoder.decode(raw, charset, new ViewDisplayPropertyMap()));
            default -> { /* not rendered anywhere in this port's doc/ layer - see FieldDetailPage/VuiDetailPage's own established scope */ }
        }
    }

    void endVui() {
        if (currentView != null) views.add(currentView);
        currentView = null;
    }
}
