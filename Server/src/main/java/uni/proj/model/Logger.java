package uni.proj.model;

import uni.proj.events.Event;

import static java.io.IO.*;

public class Logger {

    private boolean headerPrinted = false;

    public Logger() {}

    public void log(Event event) {
        if (!headerPrinted) {
            printHeader();
            headerPrinted = true;
        }
        println(event);
        printFooter();
    }

    public void reset() {
        headerPrinted = false;
    }

    public void clear() {
        print("\033[H\033[2J"); // ANSI escape per pulire il terminale (non su tutti i sistemi)
        System.out.flush();
    }

    private void printHeader() {
        String top =    "+------------+----------------------------------------+----------------------------+";
        String header = "| TYPE       | MESSAGE                                | TIMESTAMP                  |";
        String mid =    "+------------+----------------------------------------+----------------------------+";

        println(top);
        println(header);
        println(mid);
    }

    public void printFooter() {
        String bottom = "+------------+----------------------------------------+----------------------------+";
        println(bottom);
    }
}
