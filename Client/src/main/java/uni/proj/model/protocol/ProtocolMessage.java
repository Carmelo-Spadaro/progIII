package uni.proj.model.protocol;

public record ProtocolMessage<T>(MessageType type, T data) {
}