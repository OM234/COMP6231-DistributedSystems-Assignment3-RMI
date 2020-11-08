package naming;

import common.Path;

import java.util.Arrays;

/**
 * Utilities class for Naming Server. Contains helper methods for implementing services.
 */
public class Utilities {

    /**
     * Returns a String representation of the components of a Path. A path such as "/directory/file1"
     * will be return as new String[]{"directory", "file1"}
     *
     * @param path of the components
     * @return a String array of the components of the path
     */
    public static String[] getPathComponents(Path path) {

        //components of Path
        String[] components;

        //Split around "/", remove empty components, map to new String array
        components = Arrays
                .stream(path.toString().split("/"))
                .filter(e -> e.length() > 0)
                .toArray(String[]::new);

        //If components is empty, e.g. Path = "/", return an empty array
        if(components.length == 0) {
            return new String[]{};
        }

        return components;
    }
}
