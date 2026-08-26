package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Container;

import java.util.List;

/**
 * Shape every Doc*Page/scan class needs from "wherever containers come from" - implemented by
 * ContainerRepository (live server) and FileModeContainerRepository (a def-file export). See
 * SchemaSource's javadoc for why this split exists.
 */
public interface ContainerSource {
    List<String> listContainerNames(int containerType) throws ARException;
    Container getContainer(String name) throws ARException;
}
