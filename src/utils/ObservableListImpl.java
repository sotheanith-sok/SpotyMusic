package utils;

import javafx.collections.ModifiableObservableListBase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A simple implementation of {@link javafx.collections.ObservableList}.
 *
 * @param <E> the type that the list will store
 */
public class ObservableListImpl<E> extends ModifiableObservableListBase<E> {

    private final List<E> delegate;

    /**
     * Creates a new, empty ObservableListImpl.
     */
    public ObservableListImpl() {
        this.delegate = new ArrayList<>();
    }

    /**
     * Creates a new ObservableListImpl, with the given {@link List} as a base.
     *
     * @param contents initial contents of the list
     */
    public ObservableListImpl(List<E> contents) {
        this.delegate = contents;
    }

    /**
     * Creates a new ObservableListImpl with the contents of the given {@link Collection}.
     *
     * @param contents initial contents of the list
     */
    public ObservableListImpl(Collection<? extends E> contents) {
        this.delegate = new ArrayList<>(contents);
    }

    @Override
    public E get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    protected void doAdd(int index, E element) {
        delegate.add(index, element);
    }

    @Override
    protected E doSet(int index, E element) {
        return delegate.set(index, element);
    }

    @Override
    protected E doRemove(int index) {
        return delegate.remove(index);
    }
}
