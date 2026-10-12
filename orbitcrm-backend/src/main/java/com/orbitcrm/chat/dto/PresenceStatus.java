package com.orbitcrm.chat.dto;

/**
 * A person's presence, as shown next to their name (modelled on Microsoft Teams).
 *
 * <p>Purpose: one vocabulary for the backend and the frontend. The effective status is resolved in this order:
 * <ol>
 *   <li>{@link #OFFLINE} when the person has no open app tab, or chose "Appear offline";</li>
 *   <li>the status the person chose: {@link #BUSY}, {@link #DO_NOT_DISTURB}, {@link #BE_RIGHT_BACK} or
 *   {@link #AWAY};</li>
 *   <li>{@link #IN_A_MEETING} while a meeting activity they own or attend is in progress;</li>
 *   <li>{@link #AWAY} when every open tab has been idle for a few minutes;</li>
 *   <li>otherwise {@link #AVAILABLE}.</li>
 * </ol>
 * Choosing {@link #AVAILABLE} clears a chosen status and returns to automatic presence; {@link #IN_A_MEETING} can
 * never be chosen.
 *
 * @author Abhay
 */
public enum PresenceStatus {
    AVAILABLE,
    BUSY,
    IN_A_MEETING,
    DO_NOT_DISTURB,
    BE_RIGHT_BACK,
    AWAY,
    OFFLINE
}
