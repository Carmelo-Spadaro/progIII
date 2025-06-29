package uni.proj.model.protocol.data;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public record SendMailData(String senderEmail, String title, String body, String[] receiversEmail) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendMailData that)) return false;
        return senderEmail.equals(that.senderEmail)
                && title.equals(that.title)
                && body.equals(that.body)
                && Arrays.equals(receiversEmail, that.receiversEmail);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(senderEmail, title, body);
        result = 31 * result + Arrays.hashCode(receiversEmail);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return senderEmail+": *" + title + "*\n____________________\n" + body + "\n____________________\n" + Arrays.toString(receiversEmail);
    }
}

