package net.sneakyprototypekit.api;

import net.sneakyprototypekit.ability.AbilityManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Lets other plugins fire configured prototype abilities. */
public final class PrototypeAbilities {
    private PrototypeAbilities() {}

    /** True if {@code abilities.<id>} exists and has native {@code actions:}. */
    public static boolean has(@NotNull String abilityId) {
        return AbilityManager.INSTANCE.hasNativeActions(abilityId);
    }

    /**
     * Runs ability {@code abilityId} with {@code player} as caster, exactly as the Prototype Test Kit casts:
     * no item, charges or cooldown. Main thread only. Returns false if the id is unknown or has no native
     * actions (a {@code command-console}-only ability is never run).
     */
    public static boolean cast(@NotNull Player player, @NotNull String abilityId) {
        return AbilityManager.INSTANCE.castById(player, abilityId);
    }
}
