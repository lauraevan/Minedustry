package mindustry.web.teavm.compiler;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.substitution.*;

/** Browser replacements only for native/JVM-only boundaries; gameplay is not substituted. */
@Autoregistered
public final class MindustrySubstitutionPolicy extends SimpleSubstitutionPolicy{
    @Override public void contribute(SubstitutionSink sink){
        replace(sink, "arc.util.ArcNativesLoader", "arc.util");
        replace(sink, "arc.util.OS", "arc.util");
        replace(sink, "arc.audio.Soloud", "arc.audio");
        replace(sink, "arc.audio.Music", "arc.audio");
        replace(sink, "mindustry.ui.Fonts", "mindustry.ui");
        replace(sink, "mindustry.mod.Scripts", "mindustry.mod");
    }

    private void replace(SubstitutionSink sink, String className, String sourcePackage){
        sink.selectClasses(named(className))
            .replacePackage(sourcePackage, "mindustry.web.teavm.substitute." + sourcePackage);
    }
}
