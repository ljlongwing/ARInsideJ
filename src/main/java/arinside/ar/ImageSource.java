package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Image;

import java.util.List;

/** Shape ImageOverviewPage/ImageDetailPage need from "wherever images come from" - implemented by IdentityRepository (live server) and FileModeImageRepository (a def-file export). See SchemaSource's javadoc for why this split exists. */
public interface ImageSource {
    List<String> listImageNames() throws ARException;
    Image getImage(String name) throws ARException;
}
