package naming;

import common.Path;

import java.util.Arrays;

public class Utilities {

    public static String[] getPathComponents(Path path) {

        String[] components;
        Path[] paths;

        components = Arrays
                .stream(path.toString().split("/"))
                .filter(e -> e.length() > 0)
                .toArray(String[]::new);

        if(components.length == 0) {
            return new String[]{};
        }
//
//        paths = new Path[components.length];
//        paths[0] = new Path("/" + components[0]);
//
//        for(int i = 1 ; i < components.length ; i++) {
//            paths[i] = new Path(paths[i-1], components[i]);
//        }

        return components;
    }
}
