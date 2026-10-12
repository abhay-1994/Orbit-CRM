package com.orbitcrm.dashboard.dto;

import java.util.List;

/**
 * The activities section of the dashboard.
 *
 * <p>Purpose: show the user's workload at a glance.
 *
 * @author Abhay
 * @param dueToday         the user's open activities due today
 * @param overdue          the user's open activities past their due time
 * @param upcomingMeetings the next open meetings within 7 days (up to 5)
 */
public record ActivityStats(long dueToday, long overdue, List<UpcomingMeeting> upcomingMeetings) {
}
