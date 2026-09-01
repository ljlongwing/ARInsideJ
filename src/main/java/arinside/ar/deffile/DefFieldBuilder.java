package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code com.bmc.arsys.server.domain.imports.def.impl.FieldParseEventHandler},
 * targeting {@code com.bmc.arsys.api.Field} directly
 * (not the server-internal per-datatype domain subclasses the real class builds).
 *
 * <p>One instance per {@code field { ... }} clause. Mirrors the real handler's own quirk: a field
 * starts as a plain {@link CharacterField} (the DEF format's default before {@code DATA_TYPE}
 * arrives) and gets replaced wholesale once the real datatype is known, carrying over whatever
 * name/id/owner/lastChangedBy were already set - ported faithfully rather than assuming
 * {@code DATA_TYPE} always arrives first (real exports don't guarantee tag order within a field).
 *
 * <p><b>API-shape note</b>: {@code com.bmc.arsys.api.CharacterField}/{@code
 * IntegerField}/{@code RealField}/{@code AttachmentField} etc. carry NO type-specific
 * methods of their own (no {@code setMaxLength}/{@code setCharMenu}/{@code setHighRange}/...) -
 * every limit-shaped value (max length, range, precision, menu name, pattern, attach size/type,
 * enum items) lives exclusively on the separate {@link FieldLimit} subtype, attached via {@code
 * Field.setFieldLimit(FieldLimit)}. This class accumulates limit data into a lazily-created
 * {@code pendingLimit} (typed once the datatype is known) rather than the Field object itself.
 *
 * <p>Field-limit scope matches {@code arinside.ar.xmlfile.FieldLimitXmlBuilder} exactly
 * (character/integer/real/decimal/currency/attachment/enumeration) - the datatypes
 * {@code FieldDetailPage} actually renders limits for; everything else (date/dateTime/diary/
 * timeOfDay/table/column/display/view) is left with no {@link FieldLimit}, matching that
 * established, disclosed scope cut.
 */
final class DefFieldBuilder {
    private Field field = new CharacterField();
    private int dataType = Constants.AR_DATA_TYPE_CHAR;
    private FieldMapping mapping;
    private FieldLimit pendingLimit;
    private final List<EnumItem> enumItems = new ArrayList<>();

    Field build() {
        if (pendingLimit != null) {
            field.setFieldLimit(pendingLimit);
        } else if (field instanceof SelectionField && !enumItems.isEmpty()) {
            field.setFieldLimit(new SelectionFieldLimit(enumItems));
        }
        return field;
    }

    FieldMapping mapping() {
        return mapping;
    }

    void item(DefItemLabel item, String raw, Object decoded, Charset charset) {
        switch (item) {
            case NAME -> field.setName(raw);
            case ID -> field.setFieldID(ParseUtil.intValue(raw));
            case OWNER -> field.setOwner(raw);
            case LAST_CHANGED -> field.setLastChangedBy(raw);
            case HELP -> field.setHelpText(raw);
            case OPTION -> field.setFieldOption(ParseUtil.intValue(raw));
            case CREATE_MODE -> field.setCreateMode(ParseUtil.intValue(raw));
            case FIELDOPTION -> field.setAuditOption(ParseUtil.intValue(raw));
            case DATA_TYPE -> setDataType(ParseUtil.intValue(raw));
            case DEFAULT -> setDefaultValue(raw, charset);
            case CHANGE_DIARY -> { if (decoded instanceof DiaryListValue d) field.setDiary(d); }
            case OBJECT_PROP -> field.setObjectProperty(DefPropertyDecoder.decode(raw, charset, new ObjectPropertyMap()));
            case DISPLAY_PROPLIST -> {
                if (field.getFieldID() == Constants.AR_CORE_STATUS_HISTORY) break;
                DisplayPropertyMap props = DefPropertyDecoder.decode(raw, charset, new DisplayPropertyMap());
                DisplayInstanceMap instances = field.getDisplayInstance();
                if (instances == null) instances = new DisplayInstanceMap();
                instances.put(0, props);
                field.setDisplayInstance(instances);
            }
            case DISPLAY_INSTANCE -> {
                if (field.getFieldID() == Constants.AR_CORE_STATUS_HISTORY) break;
                int sep = raw.indexOf('\\');
                if (sep > 0) {
                    int vuiId = ParseUtil.intValue(raw.substring(0, sep));
                    DisplayPropertyMap props = DefPropertyDecoder.decode(raw.substring(sep + 1), charset, new DisplayPropertyMap());
                    DisplayInstanceMap instances = field.getDisplayInstance();
                    if (instances == null) instances = new DisplayInstanceMap();
                    instances.put(vuiId, props);
                    field.setDisplayInstance(instances);
                }
            }
            case PERMISSION, ADD_PERMISSION -> addPermission(raw); // granular-overlay "extended" bookkeeping not ported - treated the same as a plain PERMISSION, see DefFormBuilder's javadoc
            case FIELD_TYPE -> setFieldMapping(ParseUtil.intValue(raw));
            case EXT_FIELD -> {
                if (mapping instanceof ViewFieldMapping v) v.setFieldName(raw);
                else if (mapping instanceof VendorFieldMapping v) v.setFieldName(raw);
            }
            case MAP_FIELDID -> { if (mapping instanceof JoinFieldMapping j) j.setFieldID(ParseUtil.intValue(raw)); }
            case MAP_SCHEMA -> { if (mapping instanceof JoinFieldMapping j) j.setIndex(ParseUtil.intValue(raw)); }
            case CHAR_MENU -> withLimit(CharacterFieldLimit.class, CharacterFieldLimit::new, l -> l.setCharMenu(raw));
            case PATTERN -> withLimit(CharacterFieldLimit.class, CharacterFieldLimit::new, l -> l.setPattern(raw));
            case MAX_LENGTH -> setMaxLength(ParseUtil.intValue(raw));
            case QBE_MATCH_OP -> withLimit(CharacterFieldLimit.class, CharacterFieldLimit::new, l -> l.setQBEMatch(ParseUtil.intValue(raw)));
            case MENU_STYLE -> withLimit(CharacterFieldLimit.class, CharacterFieldLimit::new, l -> l.setMenuStyle(ParseUtil.intValue(raw)));
            case PRECISION -> setPrecision(ParseUtil.intValue(raw));
            case RANGE_HIGH -> setRange(raw, true);
            case RANGE_LOW -> setRange(raw, false);
            case MAXSIZE -> withLimit(AttachmentFieldLimit.class, AttachmentFieldLimit::new, l -> l.setMaxSize(ParseUtil.intValue(raw)));
            case ATTACH_TYPE -> withLimit(AttachmentFieldLimit.class, AttachmentFieldLimit::new, l -> l.setAttachType(ParseUtil.intValue(raw)));
            case ENUM_VALUE -> addEnumValue(raw);
            case ENUM_VALUE_NUM -> addEnumValueNum(raw);
            default -> { /* not rendered anywhere in this port's doc/ layer - see class javadoc for the confirmed scope */ }
        }
    }

    private void setDataType(int newType) {
        Field old = field;
        this.dataType = newType;
        field = switch (newType) {
            case Constants.AR_DATA_TYPE_INTEGER -> new IntegerField();
            case Constants.AR_DATA_TYPE_REAL -> new RealField();
            case Constants.AR_DATA_TYPE_CHAR -> new CharacterField();
            case Constants.AR_DATA_TYPE_DIARY -> new DiaryField();
            case Constants.AR_DATA_TYPE_ENUM -> new SelectionField();
            case Constants.AR_DATA_TYPE_TIME -> new DateTimeField();
            case Constants.AR_DATA_TYPE_DECIMAL -> new DecimalField();
            case Constants.AR_DATA_TYPE_ATTACH -> new AttachmentField();
            case Constants.AR_DATA_TYPE_CURRENCY -> new CurrencyField();
            case Constants.AR_DATA_TYPE_DATE -> new DateOnlyField();
            case Constants.AR_DATA_TYPE_TIME_OF_DAY -> new TimeOnlyField();
            case Constants.AR_DATA_TYPE_TRIM -> new TrimField();
            case Constants.AR_DATA_TYPE_CONTROL -> new ControlField();
            case Constants.AR_DATA_TYPE_TABLE -> new TableField();
            case Constants.AR_DATA_TYPE_COLUMN -> new ColumnField();
            case Constants.AR_DATA_TYPE_PAGE -> new PageField();
            case Constants.AR_DATA_TYPE_PAGE_HOLDER -> new PageHolderField();
            case Constants.AR_DATA_TYPE_ATTACH_POOL -> new AttachmentPoolField();
            case Constants.AR_DATA_TYPE_VIEW -> new ViewField();
            case Constants.AR_DATA_TYPE_DISPLAY -> new DisplayField();
            default -> old;
        };
        if (field != old) {
            field.setName(old.getName());
            field.setFieldID(old.getFieldID());
            field.setOwner(old.getOwner());
            field.setLastChangedBy(old.getLastChangedBy());
        }
    }

    private void setDefaultValue(String raw, Charset charset) {
        if (raw == null || raw.isEmpty()) return;
        // A leading "$-" marks a keyword default ($USER$-style, matching the real handler's own
        // check) - not resolved to a Keyword by name here (this port's value decoder only knows
        // the int-id form used by CURRENCY defaults below), a small disclosed scope gap; rare in
        // practice since most keyword defaults are field-level, not a per-field DEFAULT tag.
        if (raw.startsWith("$-")) return;
        Value value = dataType == Constants.AR_DATA_TYPE_CURRENCY
            ? new DefValueDecoder(raw, charset).decodeValue()
            : new Value(raw, DataType.toDataType(dataType));
        field.setDefaultValue(value);
    }

    private void addPermission(String raw) {
        int sep = raw.indexOf('\\');
        if (sep <= 0) return;
        int gid = ParseUtil.intValue(raw.substring(0, sep));
        int pid = ParseUtil.intValue(raw.substring(sep + 1));
        List<PermissionInfo> perms = field.getPermissions();
        if (perms == null) {
            perms = new ArrayList<>();
            field.setPermissions(perms);
        }
        perms.add(new PermissionInfo(gid, pid));
    }

    private void setFieldMapping(int fieldType) {
        mapping = switch (fieldType) {
            case Constants.AR_FIELD_JOIN -> new JoinFieldMapping();
            case Constants.AR_FIELD_VIEW -> new ViewFieldMapping();
            case Constants.AR_FIELD_VENDOR -> new VendorFieldMapping();
            case Constants.AR_FIELD_REGULAR -> new RegularFieldMapping();
            default -> null;
        };
    }

    private void setMaxLength(int len) {
        if (dataType == Constants.AR_DATA_TYPE_CHAR) withLimit(CharacterFieldLimit.class, CharacterFieldLimit::new, l -> l.setMaxLength(len));
    }

    private void setPrecision(int precision) {
        if (dataType == Constants.AR_DATA_TYPE_REAL) withLimit(RealFieldLimit.class, RealFieldLimit::new, l -> l.setPrecision(precision));
        else if (dataType == Constants.AR_DATA_TYPE_DECIMAL) withLimit(DecimalFieldLimit.class, DecimalFieldLimit::new, l -> l.setPrecision(precision));
        else if (dataType == Constants.AR_DATA_TYPE_CURRENCY) withLimit(CurrencyFieldLimit.class, CurrencyFieldLimit::new, l -> l.setPrecision(precision));
    }

    private void setRange(String raw, boolean high) {
        if (dataType == Constants.AR_DATA_TYPE_INTEGER) {
            int v = ParseUtil.intValue(raw);
            withLimit(IntegerFieldLimit.class, IntegerFieldLimit::new, l -> { if (high) l.setHighRange(v); else l.setLowRange(v); });
        } else if (dataType == Constants.AR_DATA_TYPE_REAL) {
            double v = ParseUtil.doubleValue(raw);
            withLimit(RealFieldLimit.class, RealFieldLimit::new, l -> { if (high) l.setHighRange(v); else l.setLowRange(v); });
        } else if (dataType == Constants.AR_DATA_TYPE_DECIMAL) {
            BigDecimal v = ParseUtil.decimalValue(raw);
            withLimit(DecimalFieldLimit.class, DecimalFieldLimit::new, l -> { if (high) l.setHighRange(v); else l.setLowRange(v); });
        } else if (dataType == Constants.AR_DATA_TYPE_CURRENCY) {
            BigDecimal v = ParseUtil.decimalValue(raw);
            withLimit(CurrencyFieldLimit.class, CurrencyFieldLimit::new, l -> { if (high) l.setHighRange(v); else l.setLowRange(v); });
        }
    }

    @SuppressWarnings("unchecked")
    private <L extends FieldLimit> void withLimit(Class<L> type, java.util.function.Supplier<L> factory, java.util.function.Consumer<L> apply) {
        if (pendingLimit == null || !type.isInstance(pendingLimit)) {
            pendingLimit = factory.get();
        }
        apply.accept((L) pendingLimit);
    }

    private void addEnumValue(String raw) {
        String[] tokens = raw.split("\\\\", -1);
        if (tokens.length >= 1) enumItems.add(new EnumItem(tokens[0], enumItems.size()));
    }

    private void addEnumValueNum(String raw) {
        String[] tokens = raw.split("\\\\", -1);
        if (tokens.length >= 2) {
            int id = ParseUtil.intValue(tokens[0]);
            enumItems.add(new EnumItem(tokens[1], id));
        }
    }
}
