/**
 * DVCS Diff Engine module.
 *
 * <p>Provides the Myers diff algorithm, three-way merge, binary detection,
 * patch application, and the diff REST API.
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code algorithm} — core algorithms: {@code MyersDiff}, {@code PatchApplier},
 *       {@code BinaryDetector}, {@code ThreeWayMerge}, {@code MergeResult}</li>
 *   <li>{@code model}     — data model: {@code DiffLine}, {@code DiffHunk}, {@code LineType}</li>
 *   <li>{@code service}   — {@code DiffService}, {@code BinaryDiffResult}</li>
 *   <li>{@code controller} — {@code DiffController}</li>
 * </ul>
 */
package com.dvcs.diff;
