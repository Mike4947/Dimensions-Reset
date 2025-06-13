package com.example.dimensionsreset;

/**
 * This interface is a "contract" to ensure that any class managing data
 * for the scheduler MUST provide these methods. This universally prevents
 * "Cannot resolve method" errors in the SchedulerTask.
 */
public interface SchedulerDataHandler {

    /**
     * Gets the last reset time for a specific automated schedule ID.
     * @param scheduleId The unique ID of the schedule.
     * @return The timestamp of the last reset for that schedule.
     */
    long getLastResetTimeForSchedule(String scheduleId);

    /**
     * Sets the last reset time for a specific automated schedule ID.
     * @param scheduleId The unique ID of the schedule.
     * @param timestamp The current timestamp to save.
     */
    void setLastResetTimeForSchedule(String scheduleId, long timestamp);
}