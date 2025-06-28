package uni.proj.model.protocol.data;

import uni.proj.model.protocol.MessageType;

public record ErrorData(MessageType errorTo, String message) {
}
