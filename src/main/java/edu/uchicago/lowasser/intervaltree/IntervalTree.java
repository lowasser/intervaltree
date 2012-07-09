package edu.uchicago.lowasser.intervaltree;

import com.google.common.collect.Range;

import java.util.Set;

public interface IntervalTree<C extends Comparable<C>> extends Set<Range<C>> {
  Iterable<Range<C>> connected(Range<C> range);
  
  Iterable<Range<C>> enclosedBy(Range<C> range);
  
  Iterable<Range<C>> enclosing(Range<C> range);
  
  Iterable<Range<C>> containing(C value);
}
