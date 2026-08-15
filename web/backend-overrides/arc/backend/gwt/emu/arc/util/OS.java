package arc.util;

import arc.*;
import arc.files.*;

/** Browser-safe super-source replacement for Arc's JVM OS helper. */
public class OS{
    public static final int cores = 1;
    public static final String username = "web";
    public static final String userHome = "";
    public static final String osName = "Web";
    public static final String osVersion = "";
    public static final String osArch = "wasm-js";
    public static final String osArchBits = "32";
    public static final String javaVersion = "17";
    public static final int javaVersionNumber = 17;

    public static boolean isWindows = false;
    public static boolean isLinux = false;
    public static boolean isMac = false;
    public static boolean isIos = false;
    public static boolean isAndroid = false;
    public static boolean isARM = false;
    public static boolean is64Bit = false;
    public static boolean isMobile = false;
    public static boolean isDesktop = true;

    public static String getAppDataDirectoryString(String appname){
        return "";
    }

    public static String getWindowsTmpDir(){
        return "";
    }

    public static String exec(boolean logErr, String... args){
        throw new UnsupportedOperationException("Process execution is unavailable in browsers.");
    }

    public static String exec(String... args){
        return exec(false, args);
    }

    public static boolean execSafe(String command){
        return false;
    }

    public static boolean execSafe(String... command){
        return false;
    }

    public static Fi getAppDataDirectory(String appname){
        return Core.files.local("");
    }

    public static boolean hasProp(String name){
        return false;
    }

    public static String prop(String name){
        return null;
    }

    public static boolean hasEnvFlag(String name){
        return false;
    }

    public static boolean hasEnv(String name){
        return false;
    }

    public static String env(String name){
        return null;
    }

    public static String propNoNull(String name){
        return "";
    }
}
