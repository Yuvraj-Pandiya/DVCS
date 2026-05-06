package com.dvcs.git.object;

/**
 * Represents the type of a Git object in the content-addressable object store.
 *
 * <ul>
 *   <li>{@link #BLOB}   – raw file content</li>
 *   <li>{@link #TREE}   – directory listing (name + mode + sha entries)</li>
 *   <li>{@link #COMMIT} – snapshot with tree pointer, parent(s), and metadata</li>
 * </ul>
 */
public enum ObjectType {

    /** Raw file content. */
    BLOB,

    /** Directory listing composed of named entries pointing to blobs or other trees. */
    TREE,

    /** A snapshot commit referencing a root tree, zero or more parent commits, and author metadata. */
    COMMIT
}
