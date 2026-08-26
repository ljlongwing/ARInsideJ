package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.View;

import java.util.List;

/**
 * Shape every Doc*Page/scan class needs from "wherever forms/fields/VUIs come from" - implemented
 * by SchemaRepository (live server) and FileModeSchemaRepository (a def-file export, see its
 * javadoc for why file mode still needs a live connection in this port despite the C++'s file
 * mode being fully offline). Extracted so the same doc/scan code works unchanged against either.
 */
public interface SchemaSource {
    List<String> listFormNames() throws ARException;

    Form getForm(String name) throws ARException;

    List<Field> getFields(String formName) throws ARException;

    int getViewCount(String formName) throws ARException;

    List<View> getViews(String formName) throws ARException;
}
