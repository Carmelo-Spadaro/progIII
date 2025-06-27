package uni.proj.model.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import uni.proj.model.protocol.data.*;

import java.lang.reflect.Type;
import java.util.Set;

public class ProtocolHandler {
    private final Gson gson = new Gson();

    private static final Set<Class<?>> allowedDataTypes = Set.of(
            LoginData.class,
            ChatData.class,
            ErrorData.class,
            RegisterData.class,
            LogoutData.class,
            SendMailData.class,
            ResponseData.class
    );

    public String encode(ProtocolMessage<?> message, Class<?> dataClass) {
        if (!allowedDataTypes.contains(dataClass)) {
            throw new IllegalArgumentException("Tipo non supportato per encode: " + dataClass.getName());
        }

        Type type = TypeToken.getParameterized(ProtocolMessage.class, dataClass).getType();
        return gson.toJson(message, type);
    }

    public ProtocolMessage<?> decode(String json) throws JsonSyntaxException  {
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        MessageType type = gson.fromJson(jsonObject.get("type"), MessageType.class);
        if(type == null)
            throw new JsonSyntaxException("Errore durante la verifica del tipo");

        Class<?> dataClass = getDataClassForType(type);

        if(jsonObject.get("data") == null)
            throw new JsonSyntaxException("Errore, l'oggetto non contiene il campo data");

        Object data = gson.fromJson(jsonObject.get("data"), dataClass);

        if(data == null)
            throw new JsonSyntaxException("Errore, l'oggetto data non può contenere null");


        return new ProtocolMessage<>(type, data);
    }

    public Class<?> getDataClassForType(MessageType type) {
        return switch (type) {
            case LOGIN -> LoginData.class;
            case CHAT  -> ChatData.class;
            case ERROR  -> ErrorData.class;
            case REGISTER -> RegisterData.class;
            case LOGOUT -> LogoutData.class;
            case SEND_MAIL -> SendMailData.class;
            case RESPONSE -> ResponseData.class;
        };
    }

}
