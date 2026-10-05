package world;

/**
 * Helper for calendar calculations: given a world minute, return calendar values.
 * Keeps the math in one place.
 */
public final class WorldCalendar {
    public static final int MINUTES_IN_HOUR = 60;
    public static final int HOURS_IN_DAY = 24;
    public static final int MINUTES_IN_DAY = MINUTES_IN_HOUR * HOURS_IN_DAY;
    public static final int DAYS_IN_MONTH = 30;
    public static final int MONTHS_IN_YEAR = 12;
    public static final int MINUTES_IN_MONTH = MINUTES_IN_DAY * DAYS_IN_MONTH;
    public static final long MINUTES_IN_YEAR = (long) MINUTES_IN_MONTH * MONTHS_IN_YEAR;

    private WorldCalendar() {}

    public static long toMinute(int year, int month, int day, int hour, int minute) {
        return ((long) year * MINUTES_IN_YEAR)
                + ((long) month * MINUTES_IN_MONTH)
                + ((long) day * MINUTES_IN_DAY)
                + ((long) hour * MINUTES_IN_HOUR)
                + minute;
    }

    public static long toMinute(int dayOfYear, int hourOfDay, int minuteOfHour) {
        return ((long) dayOfYear * MINUTES_IN_DAY)
                + ((long) hourOfDay * MINUTES_IN_HOUR)
                + minuteOfHour;
    }

    public static int getYear(long totalMinutes) {
        return (int) (totalMinutes / MINUTES_IN_YEAR);
    }

    public static int getMonth(long totalMinutes) {
        return (int) ((totalMinutes % MINUTES_IN_YEAR) / MINUTES_IN_MONTH);
    }

    public static int getDay(long totalMinutes) {
        return (int) ((totalMinutes % MINUTES_IN_MONTH) / MINUTES_IN_DAY);
    }

    public static int getHour(long totalMinutes) {
        return (int) ((totalMinutes % MINUTES_IN_DAY) / MINUTES_IN_HOUR);
    }

    public static int getMinuteOfHour(long totalMinutes) {
        return (int) (totalMinutes % MINUTES_IN_HOUR);
    }

    public static int getDayOfWeek(long totalMinutes) {
        // Assuming minute 0 is a Monday for simplicity
        long days = totalMinutes / MINUTES_IN_DAY;
        return (int) (days % 7); // 0 = Monday, 6 = Sunday
    }
}