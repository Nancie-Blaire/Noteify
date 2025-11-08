package com.example.testtasksync;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class PhilippineHolidays {

    public static class Holiday {
        public String name;
        public int month; // 0-based (0 = January)
        public int day;
        public boolean isRegular;

        public Holiday(String name, int month, int day, boolean isRegular) {
            this.name = name;
            this.month = month;
            this.day = day;
            this.isRegular = isRegular;
        }
    }

    // Returns holidays for a specific year
    public static List<Holiday> getHolidays(int year) {
        List<Holiday> holidays = new ArrayList<>();

        // Regular Holidays (Red Days)
        holidays.add(new Holiday("New Year's Day", 0, 1, true));
        holidays.add(new Holiday("Araw ng Kagitingan", 3, 9, true));
        holidays.add(new Holiday("Labor Day", 4, 1, true));
        holidays.add(new Holiday("Independence Day", 5, 12, true));
        holidays.add(new Holiday("Ninoy Aquino Day", 7, 21, true));
        holidays.add(new Holiday("Bonifacio Day", 10, 30, true));
        holidays.add(new Holiday("Christmas Day", 11, 25, true));
        holidays.add(new Holiday("Rizal Day", 11, 30, true));

        // Add National Heroes Day (Last Monday of August)
        holidays.add(new Holiday("National Heroes Day", 7, getLastMondayOfMonth(year, 7), true));

        // Special Non-Working Days
        holidays.add(new Holiday("EDSA Revolution", 1, 25, false));
        holidays.add(new Holiday("All Saints' Day", 10, 1, false));
        holidays.add(new Holiday("Immaculate Conception", 11, 8, false));
        holidays.add(new Holiday("New Year's Eve", 11, 31, false));

        // Note: Maundy Thursday, Good Friday, Black Saturday, Eid al-Adha,
        // and Chinese New Year are movable holidays - you'd need an API or
        // manual update each year for these

        return holidays;
    }

    // Helper to get last Monday of a month
    private static int getLastMondayOfMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));

        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }

        return cal.get(Calendar.DAY_OF_MONTH);
    }

    // Check if a specific date is a holiday
    public static boolean isHoliday(int year, int month, int day) {
        List<Holiday> holidays = getHolidays(year);
        for (Holiday holiday : holidays) {
            if (holiday.month == month && holiday.day == day) {
                return true;
            }
        }
        return false;
    }

    // Get holiday name for a specific date
    public static String getHolidayName(int year, int month, int day) {
        List<Holiday> holidays = getHolidays(year);
        for (Holiday holiday : holidays) {
            if (holiday.month == month && holiday.day == day) {
                return holiday.name;
            }
        }
        return null;
    }

    // Check if holiday is regular (red day)
    public static boolean isRegularHoliday(int year, int month, int day) {
        List<Holiday> holidays = getHolidays(year);
        for (Holiday holiday : holidays) {
            if (holiday.month == month && holiday.day == day) {
                return holiday.isRegular;
            }
        }
        return false;
    }
}