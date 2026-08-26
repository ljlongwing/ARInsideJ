package arinside.ar.deffile;

import arinside.ar.xmlfile.ParsedObjects;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Container;
import com.bmc.arsys.api.DiaryListValue;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.Filter;
import com.bmc.arsys.api.Image;
import com.bmc.arsys.api.Menu;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Genuinely offline parser for the real AR System Administrator {@code .def} export format -
 * mirrors {@code arinside.ar.xmlfile.ArsXmlFileParser}'s role (produce a plain {@link
 * ParsedObjects}, reusing the existing {@code XmlFile*Repository} classes unchanged) but for the
 * packed line-oriented format instead of XML. {@link DefLineReader} does the tag/value tokenizing
 * (including the continuation-line logic), this class does the top-level {@code begin X ... end}
 * struct dispatch and {@code field {}/vui {}} clause nesting for schemas.
 *
 * <p><b>Scope: Form/Field/View, ActiveLink/Filter/Escalation, Menu, Container, and Image</b> - the
 * full vocabulary {@link ParsedObjects} itself carries (matching {@code arinside.ar.xmlfile.
 * ArsXmlFileParser}'s own identical scope). {@code Association} objects are deliberately out of
 * scope - {@link ParsedObjects} has no associations map at all, and the C++'s own file-mode
 * never populates associations either, so there is nothing downstream that would render them. The
 * rare standalone {@code begin vui} form-merge struct and the multi-object-bundle {@code begin
 * application} struct (a different, rarer top-level export shape than the {@code Container}-subtype
 * "Applications" this port documents) are recognized and their content correctly skipped so they
 * don't desync the parser for what follows, but are not built into {@link ParsedObjects}.
 *
 * <p>Per-object error recovery: an exception while building one schema's fields/properties is
 * logged and swallowed, with further events for that same object ignored until its {@code end} -
 * one bad object never aborts the whole file.
 */
public final class DefFileParser {
    private DefFileParser() {}

    public static ParsedObjects parse(String filePath) throws IOException {
        ParsedObjects result = new ParsedObjects();
        Charset charset = StandardCharsets.UTF_8; // full.def declares "char-set: UTF-8" itself - real per-file charset sniffing is a small future refinement, not needed for this server's real export

        try (Reader fr = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), charset), 1 << 20);
             DefLineReader reader = new DefLineReader(fr)) {
            parseTopLevel(reader, charset, result);
        }
        return result;
    }

    private static void parseTopLevel(DefLineReader reader, Charset charset, ParsedObjects result) throws IOException {
        DefStructLabel currentStruct = null;
        DefFormBuilder formBuilder = null;
        DefActiveLinkBuilder alBuilder = null;
        DefFilterBuilder filterBuilder = null;
        DefEscalationBuilder escBuilder = null;
        DefMenuBuilder menuBuilder = null;
        DefContainerBuilder containerBuilder = null;
        DefImageBuilder imageBuilder = null;
        boolean errorInCurrentObject = false;
        long formCount = 0, alCount = 0, filterCount = 0, escCount = 0, menuCount = 0, containerCount = 0, imageCount = 0, totalStructs = 0, skippedStructs = 0, errorCount = 0;

        DefLineReader.TagValue tv;
        while ((tv = reader.nextTagValue()) != null) {
            String tag = tv.tag();
            if (tag.equals("#")) continue; // comment line

            DefStructLabel struct = DefStructLabel.of(tag);
            if (struct != null) {
                if (struct == DefStructLabel.END) {
                    if (!errorInCurrentObject) {
                        try {
                            if (currentStruct == DefStructLabel.SCHEMA && formBuilder != null) {
                                DefFormBuilder.FormResult r = formBuilder.build();
                                if (r.formName() != null && !r.formName().isEmpty()) {
                                    result.forms.put(r.formName(), r.form());
                                    result.fieldsByForm.put(r.formName(), r.fields());
                                    result.viewsByForm.put(r.formName(), r.views());
                                    formCount++;
                                }
                            } else if (currentStruct == DefStructLabel.ACTIVE_LINK && alBuilder != null) {
                                ActiveLink al = alBuilder.build();
                                if (al.getName() != null && !al.getName().isEmpty()) { result.activeLinks.put(al.getName(), al); alCount++; }
                            } else if (currentStruct == DefStructLabel.FILTER && filterBuilder != null) {
                                Filter f = filterBuilder.build();
                                if (f.getName() != null && !f.getName().isEmpty()) { result.filters.put(f.getName(), f); filterCount++; }
                            } else if (currentStruct == DefStructLabel.ESCALATION && escBuilder != null) {
                                Escalation e = escBuilder.build();
                                if (e.getName() != null && !e.getName().isEmpty()) { result.escalations.put(e.getName(), e); escCount++; }
                            } else if (currentStruct == DefStructLabel.CHAR_MENU && menuBuilder != null) {
                                Menu m = menuBuilder.build();
                                if (m != null && m.getName() != null && !m.getName().isEmpty()) { result.menus.put(m.getName(), m); menuCount++; }
                            } else if (currentStruct == DefStructLabel.CONTAINER && containerBuilder != null) {
                                Container c = containerBuilder.build();
                                if (c != null && c.getName() != null && !c.getName().isEmpty()) { result.containers.put(c.getName(), c); containerCount++; }
                            } else if (currentStruct == DefStructLabel.IMAGE_OBJECT && imageBuilder != null) {
                                Image img = imageBuilder.build();
                                if (img != null && img.getName() != null && !img.getName().isEmpty()) { result.images.put(img.getName(), img); imageCount++; }
                            }
                        } catch (Exception e) {
                            errorCount++;
                            System.out.println("[WARN] deffile: failed finishing a " + currentStruct + " object, skipping it: " + e);
                        }
                    }
                    currentStruct = null;
                    formBuilder = null;
                    alBuilder = null;
                    filterBuilder = null;
                    escBuilder = null;
                    menuBuilder = null;
                    containerBuilder = null;
                    imageBuilder = null;
                    errorInCurrentObject = false;
                    continue;
                }
                // a new top-level block begins
                totalStructs++;
                currentStruct = struct;
                errorInCurrentObject = false;
                formBuilder = null;
                alBuilder = null;
                filterBuilder = null;
                escBuilder = null;
                menuBuilder = null;
                containerBuilder = null;
                imageBuilder = null;
                switch (struct) {
                    case SCHEMA -> formBuilder = new DefFormBuilder();
                    case ACTIVE_LINK -> alBuilder = new DefActiveLinkBuilder();
                    case FILTER -> filterBuilder = new DefFilterBuilder();
                    case ESCALATION -> escBuilder = new DefEscalationBuilder();
                    case CHAR_MENU -> menuBuilder = new DefMenuBuilder();
                    case CONTAINER -> containerBuilder = new DefContainerBuilder();
                    case IMAGE_OBJECT -> imageBuilder = new DefImageBuilder();
                    default -> skippedStructs++; // APP/ASSOCIATION/etc - out of scope, see class javadoc
                }
                if (totalStructs % 20000 == 0) {
                    System.out.println("[INFO] deffile: scanned " + totalStructs + " objects so far (" + formCount + " forms, "
                        + alCount + " active links, " + filterCount + " filters, " + escCount + " escalations parsed)...");
                }
                continue;
            }

            if (errorInCurrentObject) continue; // recovering from an earlier error in this object

            try {
                if (currentStruct == DefStructLabel.SCHEMA && formBuilder != null) {
                    parseSchemaEvent(tag, tv, formBuilder, charset);
                } else if (currentStruct == DefStructLabel.ACTIVE_LINK && alBuilder != null) {
                    parseWorkflowEvent(tag, tv, charset, alBuilder::beginAction, alBuilder::beginElse, alBuilder::endActionClause, alBuilder::item);
                } else if (currentStruct == DefStructLabel.FILTER && filterBuilder != null) {
                    parseWorkflowEvent(tag, tv, charset, filterBuilder::beginAction, filterBuilder::beginElse, filterBuilder::endActionClause, filterBuilder::item);
                } else if (currentStruct == DefStructLabel.ESCALATION && escBuilder != null) {
                    parseWorkflowEvent(tag, tv, charset, escBuilder::beginAction, escBuilder::beginElse, escBuilder::endActionClause, escBuilder::item);
                } else if (currentStruct == DefStructLabel.CHAR_MENU && menuBuilder != null) {
                    // Menu structs have no valid clauses, so items are dispatched directly with no
                    // clause routing needed.
                    DefItemLabel item = DefItemLabel.of(tag);
                    if (item != null) menuBuilder.item(item, tv.value(), charset);
                } else if (currentStruct == DefStructLabel.CONTAINER && containerBuilder != null) {
                    DefClauseLabel clause = DefClauseLabel.of(tag);
                    if (clause != null) {
                        switch (clause) {
                            case REFERENCE -> containerBuilder.beginReference();
                            case END -> containerBuilder.endReference();
                            default -> { /* not relevant to Container */ }
                        }
                    } else {
                        DefItemLabel item = DefItemLabel.of(tag);
                        if (item != null) containerBuilder.item(item, tv.value(), charset);
                    }
                } else if (currentStruct == DefStructLabel.IMAGE_OBJECT && imageBuilder != null) {
                    // Image structs have no clauses either (mirrors Menu).
                    DefItemLabel item = DefItemLabel.of(tag);
                    if (item != null) imageBuilder.item(item, tv.value(), charset);
                }
                // else: inside an out-of-scope struct type (application bundle/association/etc) - ignore its content
            } catch (Exception e) {
                errorCount++;
                errorInCurrentObject = true;
                System.out.println("[WARN] deffile: error parsing a " + currentStruct + " object, skipping its remaining content: " + e);
            }
        }

        System.out.println("[INFO] deffile: parsed " + formCount + " forms, " + alCount + " active links, " + filterCount
            + " filters, " + escCount + " escalations, " + menuCount + " menus, " + containerCount + " containers, "
            + imageCount + " images. " + skippedStructs + " out-of-scope objects (application bundle/association/etc) skipped."
            + (errorCount > 0 ? " " + errorCount + " objects had parse errors and were skipped." : ""));
    }

    private static void parseSchemaEvent(String tag, DefLineReader.TagValue tv, DefFormBuilder formBuilder, Charset charset) {
        DefClauseLabel clause = DefClauseLabel.of(tag);
        if (clause != null) {
            switch (clause) {
                case FIELD -> formBuilder.beginField();
                case VUI -> formBuilder.beginVui();
                case END -> formBuilder.endClause();
                default -> { /* REFERENCE/ACTION/ELSE/FILE/etc. - not relevant to Form/Field/View */ }
            }
            return;
        }
        DefItemLabel item = DefItemLabel.of(tag);
        if (item == null) return; // unrecognized tag - ignore
        Object decoded = item == DefItemLabel.CHANGE_DIARY ? decodeDiary(tv.value(), charset) : null;
        formBuilder.item(item, tv.value(), decoded, charset);
    }

    @FunctionalInterface private interface Begin { void run(); }
    @FunctionalInterface private interface ItemSink { void accept(DefItemLabel item, String raw, Charset charset); }

    private static void parseWorkflowEvent(String tag, DefLineReader.TagValue tv, Charset charset, Begin beginAction, Begin beginElse, Begin endClause, ItemSink itemSink) {
        DefClauseLabel clause = DefClauseLabel.of(tag);
        if (clause != null) {
            switch (clause) {
                case ACTION -> beginAction.run();
                case ELSE -> beginElse.run();
                case END -> endClause.run();
                default -> { /* REFERENCE/FIELD/VUI/FILE/etc. - not relevant to AL/Filter/Escalation */ }
            }
            return;
        }
        DefItemLabel item = DefItemLabel.of(tag);
        if (item == null) return; // unrecognized tag - ignore
        itemSink.accept(item, tv.value(), charset);
    }

    /** change-diary: {@code <len>\<raw diary text>\} - decoded via {@code DiaryListValue.decode}. */
    private static DiaryListValue decodeDiary(String raw, Charset charset) {
        DefValueDecoder d = new DefValueDecoder(raw, charset);
        int len = d.readInt();
        String s = d.readString(len);
        try {
            DiaryListValue v = DiaryListValue.decode(s);
            return v != null ? v : new DiaryListValue();
        } catch (Exception e) {
            return new DiaryListValue();
        }
    }
}
