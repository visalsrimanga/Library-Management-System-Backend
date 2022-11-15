package lk.ijse.dep9.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class IssueNoteDTO {
    private Integer id;
    private LocalDate date;
    private String memberId;
    private ArrayList<String> books = new ArrayList<>();
}
