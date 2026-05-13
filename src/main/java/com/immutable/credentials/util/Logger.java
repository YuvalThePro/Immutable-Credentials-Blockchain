package com.immutable.credentials.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Simple logging utility for the blockchain application.
 * Provides timestamped console logging with different log levels.
 */
public class Logger {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    /**
     * Log an informational message.
     * 
     * @param message the message to log
     */
    public static void log(String message) {
        System.out.println("[" + dateFormat.format(new Date()) + "] INFO: " + message);
    }

    /**
     * Log an error message.
     * 
     * @param message the error message to log
     */
    public static void error(String message) {
        System.err.println("[" + dateFormat.format(new Date()) + "] ERROR: " + message);
    }

    /**
     * Log a warning message.
     * 
     * @param message the warning message to log
     */
    public static void warn(String message) {
        System.out.println("[" + dateFormat.format(new Date()) + "] WARN: " + message);
    }

    /**
     * Log a debug message.
     * 
     * @param message the debug message to log
     */
    public static void debug(String message) {
        System.out.println("[" + dateFormat.format(new Date()) + "] DEBUG: " + message);
    }
}