package arinside.ar;

import arinside.ar.xmlfile.ParsedObjects;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SchemaSource backed by a genuinely offline parse of an AR System Administrator .xml export - see
 * arinside.ar.xmlfile.ArsXmlFileParser's javadoc for how the parse itself works. Unlike
 * FileModeSchemaRepository (the .def-format path, which still needs a live connection because the
 * jar's *FromDef calls turn out to be server RPCs), this holds no ArClient at all: everything was
 * already fully parsed into plain AR API objects up front by ArsXmlFileParser, so every lookup here
 * is a plain in-memory map read.
 */
public final class XmlFileSchemaRepository implements SchemaSource {
    private final ParsedObjects parsed;

    public XmlFileSchemaRepository(ParsedObjects parsed) {
        this.parsed = parsed;
    }

    @Override
    public List<String> listFormNames() {
        List<String> names = new ArrayList<>(parsed.forms.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Form getForm(String name) {
        return parsed.forms.get(name);
    }

    @Override
    public List<Field> getFields(String formName) {
        return parsed.fieldsByForm.getOrDefault(formName, List.of());
    }

    @Override
    public int getViewCount(String formName) {
        return getViews(formName).size();
    }

    @Override
    public List<View> getViews(String formName) {
        return parsed.viewsByForm.getOrDefault(formName, List.of());
    }
}
