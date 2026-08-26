package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Java port of {@code MenuParseEventHandler} + {@code MenuPropertyDecoder} (ported from the
 * real AR Server),
 * targeting {@code com.bmc.arsys.api.Menu} subtypes directly - the exact shapes {@code
 * MenuDetailPage} (live server mode) already renders, used as the client-API reference for what's
 * actually read downstream (confirmed via {@code lm.getItems()} for List menus).
 *
 * <p>Every {@code char-menu:} occurrence, regardless of menu type, starts with the same
 * {@code <index>\<menuType>\...} prefix (confirmed by reading both {@code createMenu} - which
 * sniffs type from this prefix on the FIRST occurrence - and each per-type decoder, which discards
 * the same two leading tokens before its own real content). {@code CHAR_MENU} is a real jar
 * built-in decoder candidate ({@code MenuItem.decode(String)}, with {@code AR_DEF_MENU_*}
 * constants) - checked, but its {@code bufferToMenuItemList} expects tab-indented
 * newline-separated text, a DIFFERENT serialization than this format's index-prefixed
 * backslash-token lines, so it's not reusable here - this class replicates the real depth-stack
 * algorithm from {@code MenuPropertyDecoder.decodeListMenuProperties}/{@code updateSubMenuStack}
 * instead.
 *
 * <p>Query/Sql/File/DataDictionary menus arrive as ONE {@code char-menu:} occurrence (their whole
 * content is one packed value, no itemized index concept) and are decoded in a single pass; only
 * List menus are itemized across potentially many occurrences, tracked via {@link #stack}.
 */
final class DefMenuBuilder {
    private Menu menu;
    private final List<MenuItem> topLevelItems = new ArrayList<>();
    private final Deque<StackEntry> stack = new ArrayDeque<>();

    private record StackEntry(int depth, MenuItem item) {}

    private String name, owner, lastChangedBy, helpText;
    private ObjectPropertyMap properties;

    Menu build() {
        if (menu == null) return null;
        if (menu instanceof ListMenu lm) lm.setItems(topLevelItems);
        menu.setName(name);
        menu.setOwner(owner);
        menu.setLastChangedBy(lastChangedBy);
        menu.setHelpText(helpText);
        if (properties != null) menu.setProperties(properties);
        return menu;
    }

    void item(DefItemLabel item, String raw, Charset charset) {
        switch (item) {
            case NAME -> name = raw;
            case OWNER -> owner = raw;
            case LAST_CHANGED -> lastChangedBy = raw;
            case HELP -> helpText = raw;
            case OBJECT_PROP, SMOPROP_LIST -> {
                properties = DefPropertyDecoder.decode(raw, charset, properties != null ? properties : new ObjectPropertyMap());
            }
            case REFRESH_CODE -> { if (menu != null) menu.setRefreshCode(ParseUtil.intValue(raw)); }
            case CHAR_MENU -> decodeCharMenu(raw, charset);
            default -> { /* CHANGE_DIARY/TIMESTAMP/GUID/BUNDLE_VERSION - no client setter or not rendered, matches Form's identical documented gaps */ }
        }
    }

    private void decodeCharMenu(String raw, Charset charset) {
        DefValueDecoder d = new DefValueDecoder(raw, charset);
        int index = d.readInt();
        int menuType = d.readInt();
        if (menu == null) menu = createMenu(menuType, raw, d, charset);
        if (menu == null) return;

        switch (menuType) {
            case 1 -> decodeListItem(index, raw, d);
            case 2 -> decodeQuery((QueryMenu) menu, d, charset);
            case 3 -> decodeFile((FileMenu) menu, d);
            case 4 -> decodeSql((SqlMenu) menu, d);
            case 6 -> decodeDataDictionary((DataDictionaryMenu) menu, d);
            default -> { /* type 5 ("server-side") - real but content-less in the real handler too (parseServerSideMenu is an empty method), nothing to decode */ }
        }
    }

    /** &lt;index&gt;\&lt;menuType&gt;\... - menuType 6 (DataDictionary) carries a subtype at token index 6 (0-based) of the raw double-backslash split, matching createMenu's own sniff. */
    private Menu createMenu(int menuType, String raw, DefValueDecoder d, Charset charset) {
        return switch (menuType) {
            case 1 -> new ListMenu();
            case 2 -> newQueryMenu();
            case 3 -> new FileMenu();
            case 4 -> newSqlMenu();
            case 5 -> serverSideMenuPlaceholder();
            case 6 -> createDataDictionaryMenu(raw);
            default -> null;
        };
    }

    // ServerSideMenu has no distinct client type found in this jar's Menu hierarchy - real handler's
    // own parseServerSideMenu() is an empty no-op method too, confirmed via source, so there's no
    // real content to lose; a plain ListMenu stands in as a harmless placeholder container.
    private Menu serverSideMenuPlaceholder() { return new ListMenu(); }

    private QueryMenu newQueryMenu() {
        return new QueryMenu(null, null, null, List.of(), 0, false, null, null);
    }

    private SqlMenu newSqlMenu() {
        return new SqlMenu(null, null, List.of(), 0);
    }

    private Menu createDataDictionaryMenu(String raw) {
        String[] split = raw.split("\\\\", -1);
        int subType = split.length > 6 ? ParseUtil.intValue(split[6]) : -1;
        return switch (subType) {
            case 1 -> new FormDataDictionaryMenu(null, 0, 0, 0, false);
            case 2 -> new FieldDataDictionaryMenu(null, 0, 0, 0, null);
            case 3 -> new LicenseDataDictionaryMenu(null, 0, 0, 0);
            default -> null;
        };
    }

    /**
     * index\menuType\labelLen\label\(valueLen\value\ if leaf, nothing more if the raw value ends in
     * a literal trailing backslash - a submenu marker). index is a NESTING DEPTH, not a list
     * position - see class javadoc.
     *
     * <p>The real {@code decodeListMenuProperties} does {@code index=readInt(); readString()
     * [throwaway]; labelLength=readInt(); label=readString(labelLength);} - that one throwaway IS
     * the menuType token, already consumed by {@link #decodeCharMenu} before this method is called
     * (shared with Query/Sql/File/DataDictionary's own decoders, which correctly rely on the same
     * shared 2-token prefix) - an earlier version of this method added a SECOND, redundant throwaway
     * read here, shifting every subsequent field by one token and silently corrupting every list
     * item's label (all decoded as empty). Found by inspecting real output (every "MsgType Menu"
     * item showed a real, sequential integer value but a blank label) - fixed by removing the
     * duplicate read; do not add a throwaway here.
     */
    private void decodeListItem(int index, String raw, DefValueDecoder d) {
        int labelLen = d.readInt();
        String label = d.readString(labelLen);

        if (index == 0) stack.clear();

        MenuItem item;
        if (raw.endsWith("\\")) {
            item = new MenuItem(label, new ArrayList<MenuItem>());
        } else {
            int valueLen = d.readInt();
            String value = d.readString(valueLen);
            item = new MenuItem(label, value);
        }

        if (index == 0) {
            topLevelItems.add(item);
            if (isSubMenu(item)) stack.push(new StackEntry(0, item));
        } else if (!stack.isEmpty()) {
            attach(index, item);
        }
        // else: a nested item with no open parent on the stack - matches the real decoder's own silent-drop behavior for malformed/out-of-order data
    }

    private boolean isSubMenu(MenuItem item) {
        return item.getType() == 2; // MenuItem.getType(): 1=Value, 2=SubMenu (confirmed via javap)
    }

    private void attach(int depth, MenuItem item) {
        StackEntry top = stack.peek();
        if (depth <= top.depth()) {
            while (!stack.isEmpty() && stack.peek().depth() >= depth) stack.pop();
            if (stack.isEmpty()) return; // matches real code's implicit guard (no valid parent left)
            top = stack.peek();
        }
        top.item().setSubMenu(item); // MenuItem.setSubMenu(MenuItem) appends to an existing SubMenu-typed item's list
        if (isSubMenu(item)) stack.push(new StackEntry(depth, item));
    }

    /** throwaway\throwaway\formLen\form\serverLen\server\"i1 i2 i3 i4 i5"\valueField\sortOnLabel\<qualification, consumes remaining tokens>. */
    private void decodeQuery(QueryMenu q, DefValueDecoder d, Charset charset) {
        int formLen = d.readInt();
        q.setForm(d.readString(formLen));
        int serverLen = d.readInt();
        q.setServer(d.readString(serverLen));
        q.setLabelField(readIndexList(d.readString(), 5));
        q.setValueField(d.readInt());
        q.setSortOnLabel(d.readInt() == 1);
        q.setQualification(DefQualificationDecoder.decodeInline(d));
    }

    /** throwaway\throwaway\location\nameLen\fileName\. */
    private void decodeFile(FileMenu f, DefValueDecoder d) {
        f.setLocation(d.readInt());
        int nameLen = d.readInt();
        f.setFileName(d.readString(nameLen));
    }

    /** throwaway\throwaway\serverLen\server\"i1..i5"\valueIndex\sqlLen\sql\. */
    private void decodeSql(SqlMenu s, DefValueDecoder d) {
        int serverLen = d.readInt();
        s.setServer(d.readString(serverLen));
        s.setLabelIndex(readIndexList(d.readString(), 5));
        s.setValueIndex(d.readInt());
        int sqlLen = d.readInt();
        s.setSQLCommand(d.readString(sqlLen));
    }

    /** throwaway\throwaway\serverLen\server\nameType\valueFormat\throwaway\(subtype-specific fields). */
    private void decodeDataDictionary(DataDictionaryMenu menu, DefValueDecoder d) {
        int serverLen = d.readInt();
        menu.setServer(d.readString(serverLen));
        menu.setNameType(d.readInt());
        menu.setValueFormat(d.readInt());
        d.readString(); // throwaway - matches the real decoder's own unexplained skip
        if (menu instanceof FieldDataDictionaryMenu fm) {
            fm.setFieldType(d.readInt());
            int formLen = d.readInt();
            fm.setForm(d.readString(formLen));
        } else if (menu instanceof FormDataDictionaryMenu fm) {
            fm.setFormType(d.readInt());
            fm.setIncludeHidden(d.readInt() == 1);
        } else if (menu instanceof LicenseDataDictionaryMenu lm) {
            lm.setLicenseType(d.readInt());
        }
    }

    private List<Integer> readIndexList(String spaceSeparated, int minCount) {
        List<Integer> list = new ArrayList<>();
        String[] parts = spaceSeparated.trim().isEmpty() ? new String[0] : spaceSeparated.trim().split(" ");
        for (int i = 0; i < Math.max(minCount, parts.length); i++) {
            list.add(i < parts.length ? ParseUtil.intValue(parts[i]) : 0);
        }
        return list;
    }
}
