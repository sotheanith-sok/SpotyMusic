package utils;

import java.util.Comparator;

/**
 * A ComposedComparator is a {@link Comparator} that is created by composing other comparators.
 * The Comparators provided to the ComposedComparator constructor are evaluated in the order
 * that they are provided. If one comparator determines that the two objects being compared
 * are equal, then the next comparator is evaluated.
 *
 * @param <T> the type to compare
 */
public class ComposedComparator<T> implements Comparator<T> {

    private Comparator<T>[] comparators;

    /**
     * Creates a new ComposedComparator, that composes the given comparators.
     *
     * @param comparators the comparators to compose
     */
    public ComposedComparator(Comparator<T>... comparators) {
        this.comparators = comparators;
    }

    @Override
    public int compare(T o1, T o2) {
        int c = this.comparators[0].compare(o1, o2);
        for (int i = 0; i < this.comparators.length && c == 0; i++, c = this.comparators[i].compare(o1, o2));
        return c;
    }
}
