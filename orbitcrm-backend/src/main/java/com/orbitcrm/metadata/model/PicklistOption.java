package com.orbitcrm.metadata.model;

/**
 * One selectable value in code-defined metadata: a picklist default or a fixed option of a field.
 *
 * <p>Purpose: describe values before they are copied into a tenant (picklist defaults) or values that tenants cannot
 * change (fixed options, such as the modules an activity can relate to). {@code key} is what records store;
 * {@code label} is what users see and may be renamed freely without touching data.
 *
 * @author Abhay
 * @param key       stored value
 * @param label     display label
 * @param color     optional display color
 * @param isDefault whether the value is preselected for new records
 * @param system    system values carry meaning for the platform (e.g. lead status "converted") and cannot be deactivated
 */
public record PicklistOption(String key, String label, String color, boolean isDefault, boolean system) {

    public static PicklistOption of(String key, String label) {
        return new PicklistOption(key, label, null, false, false);
    }

    public static PicklistOption defaultOption(String key, String label) {
        return new PicklistOption(key, label, null, true, false);
    }

    public static PicklistOption system(String key, String label) {
        return new PicklistOption(key, label, null, false, true);
    }

    public PicklistOption withColor(String newColor) {
        return new PicklistOption(key, label, newColor, isDefault, system);
    }
}
