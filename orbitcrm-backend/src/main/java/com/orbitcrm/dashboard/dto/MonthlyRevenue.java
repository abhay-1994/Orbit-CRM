package com.orbitcrm.dashboard.dto;

import java.math.BigDecimal;

/**
 * One month of the won-revenue trend chart.
 *
 * <p>Purpose: show revenue development over the last 12 months (months bucketed in the tenant time zone).
 *
 * @author Abhay
 * @param month    the month as {@code yyyy-MM}
 * @param wonCount deals won in the month
 * @param revenue  amount of deals won in the month
 */
public record MonthlyRevenue(String month, long wonCount, BigDecimal revenue) {
}
