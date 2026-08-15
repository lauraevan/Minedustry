package mindustry.web.teavm.substitute.arc.util;
/** TeaVM browser substitution: force Arc's pure-Java Pixmap/PNG paths. */
public class ArcNativesLoader{
    public static boolean disableNativesLoading=true;
    public static boolean loaded=false;
    public static synchronized void load(){ loaded=false; }
}
