package uni.proj.model.protocol.data;

public record SendMailData(String senderEmail, String title, String body, String[] receiversEmail) {
}
