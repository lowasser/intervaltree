package edu.uchicago.lowasser.intervaltree;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;
import com.google.common.collect.testing.SampleElements;
import com.google.common.collect.testing.SetTestSuiteBuilder;
import com.google.common.collect.testing.TestSetGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class MutableIntervalTreeTest extends TestCase {
  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTestSuite(MutableIntervalTreeTest.class);
    suite.addTest(SetTestSuiteBuilder.using(new TestSetGenerator<Range<Integer>>() {
        @SuppressWarnings("unchecked")
        @Override
        public Range<Integer>[] createArray(int n) {
          return new Range[n];
        }
  
        @Override
        public Iterable<Range<Integer>> order(List<Range<Integer>> insertion) {
          return MutableIntervalTree.RANGE_ORDERING.sortedCopy(insertion);
        }
  
        @Override
        public SampleElements<Range<Integer>> samples() {
          return new SampleElements<Range<Integer>>(
              Ranges.atLeast(0),
              Ranges.singleton(0),
              Ranges.atMost(0),
              Ranges.lessThan(0),
              Ranges.greaterThan(0));
        }
  
        @Override
        public Set<Range<Integer>> create(Object... elems) {
          MutableIntervalTree<Integer> set = MutableIntervalTree.create();
          for (Object o : elems) {
            @SuppressWarnings("unchecked")
            Range<Integer> range = (Range<Integer>) o;
            set.add(range);
          }
          return set;
        }
      })
        .named("MutableIntervalTree")
        .withFeatures(CollectionSize.ANY, CollectionFeature.ALLOWS_NULL_QUERIES,
            CollectionFeature.KNOWN_ORDER, CollectionFeature.GENERAL_PURPOSE)
        .createTestSuite());
    return suite;
  }

  private static final ImmutableList<Range<Integer>> RANGES;

  static {
    ImmutableList.Builder<Range<Integer>> builder = ImmutableList.builder();
    builder.add(Ranges.<Integer>all());
    for (int i = 0; i <= 5; i++) {
      builder
          .add(Ranges.lessThan(i))
          .add(Ranges.atMost(i))
          .add(Ranges.atLeast(i))
          .add(Ranges.greaterThan(i));
    }

    for (int i = 0; i <= 5; i++) {
      for (int j = i; j <= 5; j++) {
        builder
            .add(Ranges.openClosed(i, j))
            .add(Ranges.closedOpen(i, j))
            .add(Ranges.closed(i, j));
      }
      for (int j = i + 1; j <= 5; j++) {
        builder.add(Ranges.open(i, j));
      }
    }

    RANGES = builder.build();
  }

  private static Iterable<Range<Integer>> getRandomSetOfRanges(int nRanges) {
    List<Range<Integer>> allRanges = Lists.newArrayList(RANGES);
    Collections.shuffle(allRanges);
    return allRanges.subList(0, nRanges);
  }

  public void testConnected() {
    for (int nRanges = 0; nRanges <= RANGES.size(); nRanges++) {
      Set<Range<Integer>> currentRanges = ImmutableSet.copyOf(getRandomSetOfRanges(nRanges));
      IntervalTree<Integer> intervalTree = MutableIntervalTree.create(currentRanges);

      for (final Range<Integer> query : RANGES) {
        Set<Range<Integer>> expected = ImmutableSet.copyOf(
            Iterables.filter(currentRanges, new Predicate<Range<Integer>>() {
              @Override
              public boolean apply(@Nullable Range<Integer> input) {
                return query.isConnected(input);
              }
            }));

        Set<Range<Integer>> actual = ImmutableSet.copyOf(intervalTree.connected(query));
        assertEquals(expected, actual);
      }

    }
  }

  public void testEnclosedBy() {
    for (int nRanges = 0; nRanges <= RANGES.size(); nRanges++) {
      Set<Range<Integer>> currentRanges = ImmutableSet.copyOf(getRandomSetOfRanges(nRanges));
      IntervalTree<Integer> intervalTree = MutableIntervalTree.create(currentRanges);

      for (final Range<Integer> query : RANGES) {
        Set<Range<Integer>> expected = ImmutableSet.copyOf(
            Iterables.filter(currentRanges, new Predicate<Range<Integer>>() {
              @Override
              public boolean apply(@Nullable Range<Integer> input) {
                return query.encloses(input);
              }
            }));

        Set<Range<Integer>> actual = ImmutableSet.copyOf(intervalTree.enclosedBy(query));
        assertEquals(expected, actual);

      }
    }
  }

  public void testEnclosing() {
    for (int nRanges = 0; nRanges <= RANGES.size(); nRanges++) {
      Set<Range<Integer>> currentRanges = ImmutableSet.copyOf(getRandomSetOfRanges(nRanges));
      IntervalTree<Integer> intervalTree = MutableIntervalTree.create(currentRanges);

      for (final Range<Integer> query : RANGES) {
        Set<Range<Integer>> expected = ImmutableSet.copyOf(
            Iterables.filter(currentRanges, new Predicate<Range<Integer>>() {
              @Override
              public boolean apply(@Nullable Range<Integer> input) {
                return input.encloses(query);
              }
            }));

        Set<Range<Integer>> actual = ImmutableSet.copyOf(intervalTree.enclosing(query));
        assertEquals(expected, actual);
      }
    }
  }

  public void testContaining() {
    for (int nRanges = 0; nRanges <= RANGES.size(); nRanges++) {
      Set<Range<Integer>> currentRanges = ImmutableSet.copyOf(getRandomSetOfRanges(nRanges));
      IntervalTree<Integer> intervalTree = MutableIntervalTree.create(currentRanges);

      for (int q = -1; q <= 6; q++) {
        final Integer query = q;
        Set<Range<Integer>> expected = ImmutableSet.copyOf(
            Iterables.filter(currentRanges, new Predicate<Range<Integer>>() {
              @Override
              public boolean apply(@Nullable Range<Integer> input) {
                return input.contains(query);
              }
            }));

        Set<Range<Integer>> actual = ImmutableSet.copyOf(intervalTree.containing(query));
        assertEquals(expected, actual);
      }
    }
  }
}
