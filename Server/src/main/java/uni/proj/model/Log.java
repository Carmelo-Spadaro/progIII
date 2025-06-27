package uni.proj.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uni.proj.model.status.LogStatus;

public class Log {
    private final StringProperty type;
    private final StringProperty message;
    private final StringProperty time;

    public Log(LogStatus e) {
        type = new SimpleStringProperty(e.getType());
        message = new SimpleStringProperty(e.getMessage());
        time = new SimpleStringProperty(e.getTimeString());
    }

    public Log(String type, String message, String time) {
        this.type = new SimpleStringProperty(type);
        this.message = new SimpleStringProperty(message);
        this.time = new SimpleStringProperty(time);
    }

    public StringProperty typeProperty() { return type; }

    public StringProperty messageProperty() { return message; }

    public StringProperty timeProperty() { return time; }
}
