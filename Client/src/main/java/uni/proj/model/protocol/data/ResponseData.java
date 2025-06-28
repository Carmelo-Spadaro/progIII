package uni.proj.model.protocol.data;

import uni.proj.model.protocol.MessageType;

public record ResponseData(MessageType responseTo, String message) {
}
