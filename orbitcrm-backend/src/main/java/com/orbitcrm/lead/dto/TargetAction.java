package com.orbitcrm.lead.dto;

/**
 * What a lead conversion does with one target.
 *
 * <p>Purpose: the choices of the conversion dialog: {@code CREATE} a new record from the lead's data,
 * link an {@code EXISTING} record, or {@code NONE} (skip).
 *
 * @author Abhay
 */
public enum TargetAction { CREATE, EXISTING, NONE }
