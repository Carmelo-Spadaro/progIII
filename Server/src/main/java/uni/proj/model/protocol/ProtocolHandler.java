package uni.proj.model.protocol;

import com.google.gson.Gson;

public class ProtocolHandler {
    private final Gson gson = new Gson();

    public String encode(Message message) {
        return gson.toJson(message);
    }

    public Message decode(String json) {
        return gson.fromJson(json, Message.class);
    }
}
