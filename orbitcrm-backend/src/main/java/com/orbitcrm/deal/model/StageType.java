package com.orbitcrm.deal.model;

/**
 * The type of a pipeline stage.
 *
 * <p>Purpose: separate the business meaning of a stage from its name. {@code OPEN} stages are in progress,
 * {@code WON} and {@code LOST} stages close the deal. The type determines the deal's status, its close date and how
 * dashboards count won revenue, win rate and the open pipeline.
 *
 * @author Abhay
 */
public enum StageType { OPEN, WON, LOST }
