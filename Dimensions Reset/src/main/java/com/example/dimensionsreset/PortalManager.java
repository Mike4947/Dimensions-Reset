package com.example.dimensionsreset;

import org.bukkit.World;

/**
 * This interface is a "contract" that ensures any class managing portals
 * MUST have these methods. This universally prevents "Cannot resolve method" errors.
 */
public interface PortalManager {

    /**
     * Checks if a dimension was recently reset and needs its portal intercepted.
     * @param dimensionName The name of the dimension (e.g., "the_end").
     * @return True if the portal needs to be handled manually, false otherwise.
     */
    boolean wasDimensionJustReset(String dimensionName);

    /**
     * Acknowledges that a player has successfully used a portal to a reset dimension,
     * so it no longer needs to be intercepted.
     * @param dimensionName The name of the dimension.
     */
    void acknowledgeReset(String dimensionName);

    /**
     * Finds a world based on its environment type, needed for teleportation.
     * @param dimensionName The name of the dimension.
     * @return The World object, or null if not found.
     */
    World findWorld(String dimensionName);
}