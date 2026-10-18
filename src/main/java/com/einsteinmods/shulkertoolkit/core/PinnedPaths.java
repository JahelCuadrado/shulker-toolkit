package com.einsteinmods.shulkertoolkit.core;

/**
 * Where a pinned window's container item is: a path of slot indexes, the first in the open menu,
 * each next one inside the item before it. Up to {@link #MAX_DEPTH} levels, as many as windows can
 * be pinned. Pure; read on pins and clicks, and by the client once per window per frame (a few int
 * comparisons).
 */
public final class PinnedPaths {
  /** Windows pinned at once at most (the player chooses fewer), and so levels of one path. */
  public static final int MAX_DEPTH = 6;

  private PinnedPaths() {}

  public static boolean isValidDepth(int depth) {
    return depth >= 1 && depth <= MAX_DEPTH;
  }

  /** Whether {@code index} addresses one of {@code size} slots. */
  public static boolean isValidIndex(int index, int size) {
    return index >= 0 && index < size;
  }

  /** Whether the first {@code depth} levels of both paths are equal. */
  public static boolean samePath(int[] first, int firstDepth, int[] second, int secondDepth) {
    return firstDepth == secondDepth && sharesPrefix(first, second, firstDepth);
  }

  /** Whether {@code child} is one level below {@code parent}: the slot it holds. */
  public static boolean isChild(int[] parent, int parentDepth, int[] child, int childDepth) {
    return childDepth == parentDepth + 1 && sharesPrefix(parent, child, parentDepth);
  }

  private static boolean sharesPrefix(int[] first, int[] second, int depth) {
    if (depth > first.length || depth > second.length) {
      return false;
    }
    for (int level = 0; level < depth; level++) {
      if (first[level] != second[level]) {
        return false;
      }
    }
    return true;
  }
}
