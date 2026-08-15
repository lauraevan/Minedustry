package mindustry.web.teavm.compiler;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/**
 * Current Arc JSON/editor and Mindustry's real DataPatcher reflect fields,
 * constructors, generic field metadata and arrays. Preserve those members while
 * avoiding blanket reflective retention of every method in the game. Class-by-name
 * lookup stays broad for the first runtime/compiler pass.
 */
@Autoregistered
public final class MindustryReflectionPolicy extends SimpleReflectionPolicy{
    @Override protected void setup(){
        selectPackage("arc", true)
            .reflectableFields(field -> true)
            .reflectableMethods(method -> method.isConstructor())
            .foundByName();
        selectPackage("mindustry", true)
            .reflectableFields(field -> true)
            .reflectableMethods(method -> method.isConstructor())
            .foundByName();
    }
}
