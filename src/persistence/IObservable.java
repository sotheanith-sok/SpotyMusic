package persistence;

public interface IObservable {
    void addObserver(IObserver observer);

    void removeObserver(IObserver observer);
}
