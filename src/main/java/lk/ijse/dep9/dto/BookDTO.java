package lk.ijse.dep9.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Data   /* toString(), Getters, Setters, equals(), hashcode() all are override */

public class BookDTO implements Serializable {
    private String isbn;
    private String title;
    private String author;
    private Integer copies;

    @Override
    public String toString() {
        return "BookDTO{" +
                "isbn='" + isbn + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", copies=" + copies +
                '}';
    }
}
