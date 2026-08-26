package arinside.doc;

import com.bmc.arsys.api.EscalationTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Java port of core/CARDayStructHelper's calendar-schedule rendering (readable text instead of raw
 * minute/hour/weekday/monthday bitmask numbers) - shared by EscalationDetailPage's schedule column
 * and SchemaDetailPage's Archive "Times" row, since AR System's ArchiveInfo.getArchiveTmInfo() uses
 * the exact same EscalationTime calendar-schedule shape as an escalation's own calendar mode.
 * Weekday bit-to-name mapping (0=Sunday...6=Saturday) matches core/AREnum.cpp's
 * CAREnum::WeekDayName exactly; month-day bit numbering (1-31) is AR System's own convention, not
 * separately documented in the C++.
 */
final class ScheduleFormat {
    private static final String[] WEEKDAY_NAMES = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private ScheduleFormat() {}

    static String calendar(EscalationTime cal) {
        if (cal == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(":").append(String.format("%02d", cal.getMinute())).append(" past ");
        int hourMask = cal.getHourMask();
        List<String> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            if ((hourMask & (1 << h)) != 0) hours.add(String.format("%02d:00", h));
        }
        sb.append(hours.isEmpty() ? "(no hours set)" : String.join(", ", hours));

        int weekDayMask = cal.getWeekDayMask();
        List<String> days = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            if ((weekDayMask & (1 << d)) != 0) days.add(WEEKDAY_NAMES[d]);
        }
        if (!days.isEmpty() && days.size() < 7) sb.append(" on ").append(String.join(", ", days));

        int monthDayMask = cal.getMonthDayMask();
        List<String> monthDays = new ArrayList<>();
        for (int d = 0; d < 31; d++) {
            if ((monthDayMask & (1 << d)) != 0) monthDays.add(Integer.toString(d + 1));
        }
        if (!monthDays.isEmpty() && monthDays.size() < 31) sb.append(" (days ").append(String.join(", ", monthDays)).append(" of month)");

        return sb.toString();
    }
}
