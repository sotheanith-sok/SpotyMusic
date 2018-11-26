package utils;

import net.Constants;

import java.io.PrintStream;

public class Logger {

    private String name;

    private PrintStream out;

    private PrintStream err;

    private int filterLevel;

    private int threshold;

    public Logger(String name) {
        this(name, System.out, System.err, Constants.LOG);
    }

    public Logger(String name, int filter) {
        this(name, System.out, System.err, filter);
    }

    public Logger(String name, PrintStream out, PrintStream err) {
        this(name, out, err, Constants.LOG);
    }

    public Logger(String name, PrintStream out, PrintStream err, int filter) {
        this.name = name;
        this.out = out;
        this.err = err;
        this.filterLevel = filter;
        this.threshold = Constants.WARN;
    }

    public void println(int level, String message) {
        if (this.filterLevel > level) return;
        String levelLabel = Integer.toString(level);
        switch (level) {
            case Constants.SEVERE : levelLabel =    "SEVERE"; break;
            case Constants.ERROR : levelLabel =     " ERROR"; break;
            case Constants.WARN : levelLabel =      " WARN "; break;
            case Constants.INFO : levelLabel =      " INFO "; break;
            case Constants.LOG : levelLabel =       " LOG  "; break;
            case Constants.FINE : levelLabel =      " FINE "; break;
            case Constants.FINER : levelLabel =     "FINER "; break;
            case Constants.FINEST : levelLabel =    "FINEST"; break;
            case Constants.DEBUG : levelLabel =     " DEBUG"; break;
        }

        if (level >= threshold) {
            err.format("[%s][%s]%s", levelLabel, this.name, message);

        } else {
            out.format("[%s][%s]%s", levelLabel, this.name, message);
        }
    }

    public void format(int level, String fmt, Object... args) {
        this.println(level, String.format(fmt, args));
    }

    public void severe(String message) {
        this.println(Constants.SEVERE, message);
    }

    public void error(String message) {
        this.println(Constants.ERROR, message);
    }

    public void warn(String message) {
        this.println(Constants.WARN, message);
    }

    public void info(String message) {
        this.println(Constants.INFO, message);
    }

    public void log(String message) {
        this.println(Constants.LOG, message);
    }

    public void fine(String message) {
        this.println(Constants.FINE, message);
    }

    public void finer(String message) {
        this.println(Constants.FINER, message);
    }

    public void finest(String message) {
        this.println(Constants.FINEST, message);
    }

    public void debug(String message) {
        this.println(Constants.DEBUG, message);
    }
}
