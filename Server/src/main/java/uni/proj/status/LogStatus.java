package uni.proj.status;

import java.time.Instant;

public abstract class LogStatus {

    String message;
    private final Instant time;
    protected final String type;

    public LogStatus(String message) {
        this.message = message;
        this.time = Instant.now();
        this.type = this.getClass().getSimpleName();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTime() {
        return Instant.ofEpochMilli(time.toEpochMilli());
    }

    public String getType() {
        return type;
    }

    public String getTimeString() {
        return time.toString().substring(0, 23);
    }

    @Override
    public String toString() {
        String typeCol = String.format("%-10s", type);
        String msgCol = String.format("%-38s", message);
        String timeCol = getTimeString();
        return String.format("| %-10s | %-38s | %-26s |", typeCol, msgCol, timeCol);
    }
}
