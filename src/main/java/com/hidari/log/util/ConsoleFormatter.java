package com.hidari.log.util;

public final class ConsoleFormatter {

    private ConsoleFormatter() {}

    public static final String RESET = "\033[0m";
    public static final String RED = "\033[31m";
    public static final String RED_BOLD = "\033[1;31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String CYAN = "\033[36m";
    public static final String CYAN_BOLD = "\033[1;36m";
    public static final String DIM = "\033[2m";
    public static final String BOLD = "\033[1m";
    public static final String BLACK = "\033[30m";
    public static final String BG_YELLOW = "\033[43m";

    public static final String BAR_FULL = "\u2588";
    public static final String BAR_EMPTY = "\u2591";

    public static String bold(String text) {
        return BOLD + text + RESET;
    }

    public static String red(String text) {
        return RED + text + RESET;
    }

    public static String yellow(String text) {
        return YELLOW + text + RESET;
    }

    public static String green(String text) {
        return GREEN + text + RESET;
    }

    public static String cyan(String text) {
        return CYAN + text + RESET;
    }

    public static String progressBar(int percent, int width) {
        int filled = percent * width / 100;
        return "[" + BAR_FULL.repeat(filled) + BAR_EMPTY.repeat(width - filled) + "]";
    }
}
