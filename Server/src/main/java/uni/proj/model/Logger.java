package uni.proj.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uni.proj.status.LogStatus;

import static java.io.IO.*;


@SuppressWarnings("preview")
public class Logger {

    private boolean headerPrinted = false;
    private final ObservableList<Log> logs;

    public Logger() {
        logs = FXCollections.observableArrayList();
    }


    public synchronized void log(LogStatus logStatus) {
        if (!headerPrinted) {
            printHeader();
            headerPrinted = true;
        }
        println(logStatus);
        logs.add(new Log(logStatus));
        printFooter();
    }

    public void clear() {
        print("\033[H\033[2J"); // ANSI escape per pulire il terminale (non su tutti i sistemi)
        logs.clear();
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

    public ObservableList<Log> getLogs() {
        return logs;
    }
}
