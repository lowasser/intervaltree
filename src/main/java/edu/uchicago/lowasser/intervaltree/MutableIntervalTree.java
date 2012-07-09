package edu.uchicago.lowasser.intervaltree;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.BoundType;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

import javax.annotation.Nullable;

public final class MutableIntervalTree<C extends Comparable<C>> extends AbstractSet<Range<C>>
    implements IntervalTree<C> {
  @SuppressWarnings("rawtypes")
  private static final Ordering<Range<? extends Comparable>> UPPER_BOUND_ORDERING =
      new Ordering<Range<? extends Comparable>>() {
        @Override
        public int compare(Range<? extends Comparable> left, Range<? extends Comparable> right) {
          if (left.hasUpperBound()) {
            if (right.hasUpperBound()) {
              return ComparisonChain.start()
                  .compare(left.upperEndpoint(), right.upperEndpoint())
                  .compareFalseFirst(
                      left.upperBoundType() == BoundType.CLOSED,
                      right.upperBoundType() == BoundType.CLOSED)
                  .result();
            } else {
              return -1;
            }
          } else {
            if (right.hasUpperBound()) {
              return 1;
            } else {
              return 0;
            }
          }
        }
      };

  private static final Ordering<Range<? extends Comparable>> LOWER_BOUND_ORDERING =
      new Ordering<Range<? extends Comparable>>() {
        @Override
        public int compare(Range<? extends Comparable> left, Range<? extends Comparable> right) {
          if (left.hasLowerBound()) {
            if (right.hasLowerBound()) {
              return ComparisonChain.start()
                  .compare(left.lowerEndpoint(), right.lowerEndpoint())
                  .compareTrueFirst(
                      left.lowerBoundType() == BoundType.CLOSED,
                      right.lowerBoundType() == BoundType.CLOSED)
                  .result();
            } else {
              return 1;
            }
          } else {
            if (right.hasLowerBound()) {
              return -1;
            } else {
              return 0;
            }
          }
        }
      };
      
  @VisibleForTesting
  static final Ordering<Range<? extends Comparable>> RANGE_ORDERING =
      LOWER_BOUND_ORDERING.compound(UPPER_BOUND_ORDERING);

  private static <C extends Comparable<C>> int compareLowerToUpper(Range<C> left, Range<C> right) {
    if (!left.hasLowerBound() || !right.hasUpperBound()) {
      return -1;
    } else {
      return ComparisonChain
          .start()
          .compare(left.lowerEndpoint(), right.upperEndpoint())
          .compareTrueFirst(
              left.lowerBoundType() == BoundType.CLOSED,
              right.upperBoundType() == BoundType.OPEN)
          .result();
    }
  }

  private static final Random RANDOM = new Random(31459265358L);

  private transient final Link<C> header;
  private transient int size;
  private transient int modCount;
  @Nullable
  private transient Node<C> root;

  public static <C extends Comparable<C>> MutableIntervalTree<C> create() {
    return new MutableIntervalTree<C>();
  }

  public static <C extends Comparable<C>> MutableIntervalTree<C> create(
      Iterable<Range<C>> elements) {
    MutableIntervalTree<C> result = create();
    for (Range<C> range : elements) {
      result.add(range);
    }
    return result;
  }

  MutableIntervalTree() {
    this.size = 0;
    this.root = null;
    this.header = new Link<C>();
  }

  @Override
  public Iterator<Range<C>> iterator() {
    return new Iterator<Range<C>>() {
      Link<C> next = header.successor;
      Range<C> toRemove;
      int expectedModCount = modCount;

      private void checkForComodification() {
        if (modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
      }

      @Override
      public boolean hasNext() {
        checkForComodification();
        return next != header;
      }

      @Override
      public Range<C> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        try {
          return toRemove = ((Node<C>) next).getRange();
        } finally {
          next = next.successor;
        }
      }

      @Override
      public void remove() {
        checkForComodification();
        checkState(toRemove != null);
        MutableIntervalTree.this.remove(toRemove);
        expectedModCount = modCount;
        toRemove = null;
      }
    };
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean contains(@Nullable Object o) {
    if (o instanceof Range) {
      try {
        @SuppressWarnings("unchecked")
        Range<C> r = (Range<C>) o;
        return root != null && root.contains(r);
      } catch (ClassCastException e) {
        return false;
      }
    }
    return false;
  }

  @Override
  public boolean add(Range<C> range) {
    boolean[] modified = new boolean[1];
    if (root == null) {
      root = new Node<C>(range, RANDOM.nextInt());
      succeeds(header, root);
      succeeds(root, header);
      modified[0] = true;
    } else {
      root = root.add(range, modified);
    }

    if (modified[0]) {
      size++;
      modCount++;
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean remove(@Nullable Object o) {
    if (root != null && o instanceof Range) {
      try {
        @SuppressWarnings("unchecked")
        Range<C> r = (Range<C>) o;
        boolean[] modified = new boolean[1];
        root = root.remove(r, modified);
        if (modified[0]) {
          size--;
          modCount++;
          return true;
        }
      } catch (ClassCastException e) {
        return false;
      }
    }
    return false;
  }

  @Override
  public void clear() {
    root = null;
    size = 0;
    modCount++;
  }

  private static class Link<C> {
    Link<C> predecessor;
    Link<C> successor;

    Link() {
      this.predecessor = this;
      this.successor = this;
    }
  }

  private static <C> void succeeds(Link<C> pred, Link<C> succ) {
    pred.successor = succ;
    succ.predecessor = pred;
  }

  @Override
  public Iterable<Range<C>> connected(final Range<C> query) {
    return new Iterable<Range<C>>() {
      @Override
      public Iterator<Range<C>> iterator() {
        if (root == null) {
          return Iterators.emptyIterator();
        }
        final Deque<Node<C>> deque = new ArrayDeque<Node<C>>();
        deque.push(root);
        return new AbstractIterator<Range<C>>() {
          @Override
          protected Range<C> computeNext() {
            while (!deque.isEmpty()) {
              Node<C> node = deque.pop();
              if (compareLowerToUpper(query, node.maxUpperCut) <= 0) {
                // query starts before node.maxUpperCut ends
                if (node.left != null) {
                  deque.push(node.left);
                }
                if (node.right != null && compareLowerToUpper(node.range, query) <= 0) {
                  // node.range starts before query ends
                  deque.push(node.right);
                }
                if (node.range.isConnected(query)) {
                  return node.range;
                }
              }
            }
            return endOfData();
          }
        };
      }
    };
  }

  @Override
  public Iterable<Range<C>> enclosedBy(final Range<C> query) {
    return new Iterable<Range<C>>() {

      @Override
      public Iterator<Range<C>> iterator() {
        if (root == null) {
          return Iterators.emptyIterator();
        }
        final Deque<Node<C>> deque = new ArrayDeque<Node<C>>();
        deque.push(root);
        return new AbstractIterator<Range<C>>() {
          @Override
          protected Range<C> computeNext() {
            while (!deque.isEmpty()) {
              Node<C> node = deque.pop();
              if (compareLowerToUpper(query, node.maxUpperCut) <= 0) {
                if (node.left != null && LOWER_BOUND_ORDERING.compare(query, node.range) <= 0) {
                  deque.push(node.left);
                }
                if (node.right != null) {
                  deque.push(node.right);
                }
                if (query.encloses(node.range)) {
                  return node.range;
                }
              }
            }
            return endOfData();
          }
        };
      }
    };
  }

  @Override
  public Iterable<Range<C>> enclosing(final Range<C> query) {
    return new Iterable<Range<C>>() {

      @Override
      public Iterator<Range<C>> iterator() {
        if (root == null) {
          return Iterators.emptyIterator();
        }
        final Deque<Node<C>> deque = new ArrayDeque<Node<C>>();
        deque.push(root);
        return new AbstractIterator<Range<C>>() {
          @Override
          protected Range<C> computeNext() {
            while (!deque.isEmpty()) {
              Node<C> node = deque.pop();
              if (UPPER_BOUND_ORDERING.compare(query, node.maxUpperCut) <= 0) {
                if (node.left != null) {
                  deque.push(node.left);
                }
                if (node.right != null && LOWER_BOUND_ORDERING.compare(node.range, query) <= 0) {
                  deque.push(node.right);
                }
                if (node.range.encloses(query)) {
                  return node.range;
                }
              }
            }
            return endOfData();
          }
        };
      }
    };
  }

  @Override
  public Iterable<Range<C>> containing(C value) {
    return enclosing(Ranges.singleton(value));
  }

  private static final class Node<C extends Comparable<C>> extends Link<C> {
    private final Range<C> range;
    private final int heapKey;

    private Range<C> maxUpperCut;

    @Nullable
    private Node<C> left;
    @Nullable
    private Node<C> right;

    Node(Range<C> range, int heapKey) {
      this.range = range;
      this.heapKey = heapKey;
      this.maxUpperCut = range;
      this.left = null;
      this.right = null;
    }

    Range<C> getRange() {
      return range;
    }

    private void updateUpperCut() {
      Range<C> upper = range;
      if (left != null) {
        upper = UPPER_BOUND_ORDERING.max(upper, left.maxUpperCut);
      }
      if (right != null) {
        upper = UPPER_BOUND_ORDERING.max(upper, right.maxUpperCut);
      }
      this.maxUpperCut = upper;
    }

    public boolean contains(Range<C> query) {
      int cmp = RANGE_ORDERING.compare(query, range);
      if (cmp == 0) {
        return true;
      } else if (cmp < 0) {
        return left != null && left.contains(query);
      } else {
        return right != null && right.contains(query);
      }
    }

    public Node<C> add(Range<C> toAdd, boolean[] modified) {
      int cmp = RANGE_ORDERING.compare(toAdd, range);
      if (cmp == 0) {
        modified[0] = false;
        return this;
      } else if (cmp < 0) {
        Node<C> oldLeft = left;
        if (oldLeft == null) {
          modified[0] = true;
          left = new Node<C>(toAdd, RANDOM.nextInt());
          succeeds(predecessor, left);
          succeeds(left, this);
        } else {
          left = left.add(toAdd, modified);
        }
        this.maxUpperCut = UPPER_BOUND_ORDERING.max(maxUpperCut, left.maxUpperCut);
        if (left.heapKey >= heapKey) {
          return this;
        }
        Node<C> newTop = left;
        this.left = newTop.right;
        newTop.right = this;
        this.updateUpperCut();
        newTop.updateUpperCut();
        return newTop;
      } else {
        Node<C> oldRight = right;
        if (oldRight == null) {
          modified[0] = true;
          right = new Node<C>(toAdd, RANDOM.nextInt());
          succeeds(right, successor);
          succeeds(this, right);
        } else {
          right = right.add(toAdd, modified);
        }
        this.maxUpperCut = UPPER_BOUND_ORDERING.max(maxUpperCut, right.maxUpperCut);
        if (right.heapKey >= heapKey) {
          return this;
        }
        Node<C> newTop = right;
        this.right = newTop.left;
        newTop.left = this;
        this.updateUpperCut();
        newTop.updateUpperCut();
        return newTop;
      }
    }

    private static <C extends Comparable<C>> Node<C> merge(
        @Nullable Node<C> left,
        @Nullable Node<C> right) {
      if (left == null) {
        return right;
      } else if (right == null) {
        return left;
      } else if (left.heapKey <= right.heapKey) {
        left.right = merge(left.right, right);
        return left;
      } else {
        right.left = merge(left, right.left);
        return right;
      }
    }

    public Node<C> remove(Range<C> toRemove, boolean[] modified) {
      int cmp = RANGE_ORDERING.compare(toRemove, range);
      if (cmp == 0) {
        modified[0] = true;
        succeeds(predecessor, successor);
        return merge(left, right);
      }

      if (cmp < 0 && left != null) {
        left = left.remove(toRemove, modified);
      } else if (cmp > 0 && right != null) {
        right = right.remove(toRemove, modified);
      } else {
        modified[0] = false;
      }
      return this;
    }
    
    @Override
    public String toString() {
      return Objects.toStringHelper(this)
          .omitNullValues()
          .add("range", range)
          .add("left", left)
          .add("right", right)
          .toString();
    }
  }

  private static <C extends Comparable<C>> boolean intersects(Range<C> range1, Range<C> range2) {
    return range1.isConnected(range2) && !range1.intersection(range2).isEmpty();
  }
}
