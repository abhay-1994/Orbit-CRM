package com.orbitcrm.deal.dto;

import java.math.BigDecimal;
import java.util.List;

import com.orbitcrm.record.dto.RecordView;

/**
 * One column (stage) of the pipeline Kanban board.
 *
 * <p>Purpose: everything needed to render a stage column: header totals and the first cards.
 *
 * @author Abhay
 * @param stage          the stage
 * @param count          number of visible deals in the stage
 * @param totalAmount    sum of their amounts
 * @param weightedAmount sum of their weighted amounts (amount x probability)
 * @param deals          the first deals of the stage, ordered by expected close date
 */
public record BoardColumn(StageView stage, long count, BigDecimal totalAmount, BigDecimal weightedAmount,
                   List<RecordView> deals) {
}
