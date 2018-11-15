package persistence;

import java.util.HashMap;
import java.util.LinkedList;

public class ObservableMap<K, V extends IObservable> extends HashMap<K, V> implements IObservable, IObserver {

    private LinkedList<IObserver> observers;

    public ObservableMap() {
        super();
        this.observers = new LinkedList<>();
    }

    public ObservableMap(int initCap) {
        super(initCap);
        this.observers = new LinkedList<>();
    }

    public ObservableMap(int initCal, float loadFact) {
        super(initCal, loadFact);
        this.observers = new LinkedList<>();
    }

    @Override
    public V put(K key, V value) {
        V prev = super.put(key, value);
        if (prev != null) {
            prev.removeObserver(this);
        }
        value.addObserver(this);
        this.onObservableChange();
        return prev;
    }

    @Override
    public V replace(K key, V value) {
        if (this.containsKey(key)) {
            V ret = this.put(key, value);
            if (ret != null) this.onObservableChange();
            return ret;
        }
        return null;
    }

    @Override
    public V remove(Object key) {
        V removed = super.remove(key);
        if (removed != null) {
            removed.removeObserver(this);
            this.onObservableChange();
        }
        return removed;
    }

    @Override
    public void addObserver(IObserver observer) {
        this.observers.add(observer);
    }

    @Override
    public void removeObserver(IObserver observer) {
        this.observers.remove(observer);
    }

    @Override
    public void onObservableChange() {
        for (IObserver observer : this.observers) {
            observer.onObservableChange();
        }
    }
}
