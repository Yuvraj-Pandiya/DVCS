package com.dvcs.diff.model;

/**
 * Classifies a single line in a unified diff.
 *
 * <ul>
 *   <li>{@link #ADD}     — line present only in the head (new) file</li>
 *   <li>{@link #REMOVE}  — line present only in the base (old) file</li>
 *   <li>{@link #CONTEXT} — line present in both files (unchanged)</li>
 * </ul>
 *
 * <p>Requirement 9: Diff Engine — Myers diff line classification.
 */
public enum LineType {
    ADD,
    REMOVE,
    CONTEXT
}
