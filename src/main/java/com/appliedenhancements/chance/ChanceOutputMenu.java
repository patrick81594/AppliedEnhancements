package com.appliedenhancements.chance;

/**
 * Implemented (by Mixin) on AE2's PatternEncodingTermMenu: the "chance outputs" toggle of the
 * processing encoding panel. Lets client code reach the Mixin-added state without compile-time
 * access to the target.
 */
public interface ChanceOutputMenu {
    boolean appliedenhancements$isChanceOutput();

    /** Client: asks the server to change the toggle. Server: sets it. */
    void appliedenhancements$setChanceOutput(boolean on);
}
