package arinside.ar;

import com.bmc.arsys.api.ObjectBase;

import java.lang.reflect.Method;

/**
 * File-mode-only helper for setting an already-built {@code com.bmc.arsys.api.*} object's
 * last-modified timestamp - the one field every real .xml/.def export carries but the AR Java API's
 * object model provides no public way to attach post-construction. {@code ObjectBase} (the base
 * class of {@code Form}/{@code ActiveLink}/{@code Filter}/{@code Escalation}/{@code Container}/
 * {@code Menu}/{@code Image}, confirmed via {@code javap -p}) declares
 * {@code setLastUpdateTime(long)} as {@code protected}, and none of the concrete, instantiable
 * subclasses' public constructors forward to the (also {@code protected}) 7-arg constructor slot
 * that accepts one - a deliberate API design, since normally a client only ever RECEIVES this value
 * from a live server round-trip, never sets it locally. File mode has no server round-trip - the
 * value is parsed straight out of the export file - so reflection is the only way to reach it.
 *
 * <p>Deliberate, narrow, one-time use of reflection on the AR object model - not a pattern to
 * extend elsewhere in this port (every other file-mode field uses a normal public setter; this is
 * the sole exception, and only because no public path exists at all). {@code setAccessible(true)}
 * is safe here: the jar is a plain classpath dependency with no module-system encapsulation, and
 * the resolved {@link Method} is cached once rather than re-resolved per object. Every call site
 * this is used from is {@code ServerObjectHistoryWidget}'s "Last changed by X on Y" section, which
 * renders {@code getLastUpdateTime()} on all 7 file-mode-buildable object types - previously always
 * blank for every file-mode-imported object regardless of format, now populated from the real
 * parsed value.
 */
public final class ObjectTimestamp {
    private ObjectTimestamp() {}

    private static final Method SETTER = resolveSetter();

    private static Method resolveSetter() {
        try {
            Method m = ObjectBase.class.getDeclaredMethod("setLastUpdateTime", long.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException | SecurityException e) {
            // A future/older AR API jar without this exact method shouldn't crash file-mode import
            // over a cosmetic timestamp - degrade to leaving it unset, same as before this fix.
            return null;
        }
    }

    /** No-op if obj is null, epochSeconds isn't positive (matches every builder's existing "0/blank means absent" convention for parsed-but-unset numeric fields), or the setter couldn't be resolved. */
    public static void set(ObjectBase obj, long epochSeconds) {
        if (SETTER == null || obj == null || epochSeconds <= 0) return;
        try {
            SETTER.invoke(obj, epochSeconds);
        } catch (ReflectiveOperationException e) {
            // Best-effort - a timestamp this port can't set isn't worth failing the whole object over.
        }
    }
}
