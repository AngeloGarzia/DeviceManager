package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TodoWeekCalendarResponse {

    private LocalDate weekStart;
    private LocalDate weekEnd;
    private List<Day> days;

    @Data
    @Builder
    public static class Day {
        private LocalDate date;
        /** 1 = lundi … 7 = dimanche (ISO). */
        private int dayOfWeek;
        private String label;
        private int recurringCount;
        private int overdueCount;
    }
}
