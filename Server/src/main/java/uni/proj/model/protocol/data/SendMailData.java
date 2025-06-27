package uni.proj.model.protocol.data;

public record SendMailData(String receiverEmail, String senderEmail, String subject, String body) {
}
