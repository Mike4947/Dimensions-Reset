package com.example.dimensionsreset;

import org.bukkit.World;

public class DimensionHelper {

    /**
     * An alternative way to get a world's environment from a string.
     * This method avoids direct calls like "World.Environment.THE_NETHER"
     * and uses Java's built-in Enum.valueOf() lookup instead.
     *
     * @param dimensionName The name of the dimension (e.g., "the_nether").
     * @return The corresponding World.Environment, or null if invalid.
     */
    public static World.Environment getEnvironmentFromString(String dimensionName) {
        if (dimensionName == null) {
            return null;
        }

        // Convert "the_nether" into "THE_NETHER" for the lookup
        String enumName = dimensionName.toUpperCase();

        try {
            // This is the core of the alternative solution.
            // It finds the Environment value using its name as a string.
            return World.Environment.valueOf(enumName);
        } catch (IllegalArgumentException e) {
            // This will happen if the string (e.g., "THE_OVERWORLD") isn't a valid environment.
            System.out.println("Could not find a World.Environment called " + enumName);
            return null;
        }
    }
}