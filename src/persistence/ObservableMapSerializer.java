package persistence;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import net.common.JsonSerializer;
import utils.DebouncedRunnable;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ObservableMapSerializer<K, V extends IObservable> implements IObserver {

    private static JsonFactory factory = new JsonFactory();

    private ObservableMap<K, V> map;

    private File dest;

    private JsonSerializer<V> serializer;

    private DebouncedRunnable task;

    public ObservableMapSerializer(ObservableMap<K, V> map, File dest, JsonSerializer<V> serializer, ScheduledExecutorService executor, long debouncePeriod, TimeUnit unit) {
        this.map = map;
        this.dest = dest;
        this.serializer = serializer;

        this.task = new DebouncedRunnable(this::serialize, debouncePeriod, unit, true, executor);
        this.map.addObserver(this);
    }

    private void serialize() {
        try {
            if (!this.dest.exists()) {
                this.dest.createNewFile();
            }

            OutputStream out = new BufferedOutputStream(new FileOutputStream(this.dest));
            JsonGenerator gen = factory.createGenerator(out, JsonEncoding.UTF8);

            gen.writeStartObject();
            for (Map.Entry<K, V> entry : this.map.entrySet()) {
                gen.writeFieldName(entry.getKey().toString());
                this.serializer.serialize(entry.getValue(), gen);
            }
            gen.writeEndObject();
            gen.close();
            out.close();

        } catch (FileNotFoundException e) {
            System.err.println("[ObservableMapSerializer][serialize] There was a problem opening the destination file");
            e.printStackTrace();

        } catch (IOException e) {
            System.err.println("[ObservableMapSerializer][serialize] There was a problem serializing the observable map");
            e.printStackTrace();
        }
    }

    @Override
    public void onObservableChange() {
        this.task.run();
    }
}
