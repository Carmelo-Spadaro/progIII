package uni.proj.model.protocol.data;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record SendMailData(String senderEmail, String title, String body, String[] receiversEmail) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendMailData that)) return false;
        return senderEmail.equals(that.senderEmail)
                && title.equals(that.title)
                && body.equals(that.body)
                && emailsSameWithCounts(receiversEmail, that.receiversEmail);
    }

    private boolean emailsSameWithCounts(String[] a, String[] b) {
        if (a.length != b.length) return false;
        Map<String, Integer> countA = new HashMap<>();
        for (String s : a) countA.merge(s, 1, Integer::sum);
        Map<String, Integer> countB = new HashMap<>();
        for (String s : b) countB.merge(s, 1, Integer::sum);
        return countA.equals(countB);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(senderEmail, title, body);
        Map<String, Integer> count = new HashMap<>();
        for (String s : receiversEmail) count.merge(s, 1, Integer::sum);
        result = 31 * result + count.hashCode();
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return senderEmail+": *" + title + "*\n____________________\n" + body + "\n____________________\n" + Arrays.toString(receiversEmail);
    }
}

