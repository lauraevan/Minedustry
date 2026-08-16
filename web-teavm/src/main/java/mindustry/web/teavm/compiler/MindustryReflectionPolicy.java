package mindustry.web.teavm.compiler;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

import java.util.function.Predicate;

/**
 * Current Arc JSON/editor and Mindustry's real DataPatcher reflect fields,
 * constructors, generic field metadata and arrays. Preserve those members while
 * avoiding blanket reflective retention of every method in the game.
 *
 * <p>Field + constructor reflection is registered demand-driven: TeaVM only keeps the
 * members of classes it actually reaches through a reflective access site, so this does
 * not by itself force-retain the whole game. It deliberately does NOT enable
 * {@code foundByName()} across all of {@code arc.*}/{@code mindustry.*}: a broad
 * class-found-by-name policy makes TeaVM retain every matching class (defeating dead-code
 * elimination) so it can service dynamic {@code Class.forName} lookups. On a memory-capped
 * build that pushes the whole-program optimizer's live set past ~11 GB and the compile
 * OOMs. The first browser milestone is vanilla and offline (no JAR/JSON mods, save/load
 * keys content by numeric id, not class name), so nothing reachable needs dynamic
 * class-by-name resolution. Re-introduce a narrowed {@code foundByName()} for the specific
 * packages a later mod/runtime milestone proves it needs.
 *
 * <p>The package selectors use a null-safe predicate on purpose. TeaVM's
 * DefaultReflectionSupplier.fillMembers calls {@code env.findClass(name)} and hands the
 * result straight to the policy predicate; when a class name in the reflection graph
 * cannot be resolved, that result is {@code null}. The built-in
 * {@code selectPackage(...)}/{@code inPackage(...)} predicate dereferences
 * {@code cls.name()} without a null check, which aborts the entire browser compile with
 * an NPE. Treating an unresolvable class as having no reflectable members is both correct
 * and keeps the whole-program compile alive.
 */
@Autoregistered
public final class MindustryReflectionPolicy extends SimpleReflectionPolicy{
    @Override protected void setup(){
        selectClasses(inPackageSafe("arc"))
            .reflectableFields(field -> true)
            .reflectableMethods(method -> method.isConstructor());
        selectClasses(inPackageSafe("mindustry"))
            .reflectableFields(field -> true)
            .reflectableMethods(method -> method.isConstructor());
    }

    private static Predicate<IntrospectClass<?>> inPackageSafe(String pkg){
        String prefix = pkg + ".";
        return cls -> {
            if(cls == null) return false;
            String name = cls.name();
            return name != null && name.startsWith(prefix);
        };
    }
}
