package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.dto.IssueNoteDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@WebServlet(name = "IssueNoteServlet", value = "/issue-notes")
public class IssueNoteServlet extends HttpServlet2 {

    @Resource(lookup = "java:/comp/env/jdbc/dep9-lms")
    private DataSource pool;
    @Override

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if(request.getPathInfo() != null && !request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }

        try {
            if (request.getContentType() == null || !request.getContentType().startsWith("application/json")){
                throw new JsonException("Invalid JSON");
            }

            IssueNoteDTO issueNote = JsonbBuilder.create().fromJson(request.getReader(), IssueNoteDTO.class);
            createIssueNote(issueNote, response);

        } catch (JsonbException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }
    private void createIssueNote(IssueNoteDTO issueNote, HttpServletResponse response) throws IOException {
        if (issueNote.getMemberId() == null ||
                !issueNote.getMemberId().matches("([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})")) {
            throw new JsonbException("Member id is empty or invalid");
        } else if (issueNote.getBooks().isEmpty()) {
            throw new JsonbException("Can't place an issue note without books");
        }else if(issueNote.getBooks().size() > 3){
            throw new JsonbException("Can't issue more than 3 books");
        } else if (!issueNote.getBooks().stream()
                .allMatch(isbn -> isbn == null || !isbn.matches("([0-9][0-9\\\\-]*[0-9])"))) {
            throw new JsonbException("Invalid isbn in the books list");
        }else if(issueNote.getBooks().stream().collect(Collectors.toSet()).size()
                != issueNote.getBooks().size()){
            throw new JsonbException("Duplicate isbns are found");
        }

        try (Connection connection = pool.getConnection()) {
            PreparedStatement stmMemberExist = connection.
                    prepareStatement("SELECT id FROM member WHERE id=?");
            stmMemberExist.setString(1, issueNote.getMemberId());
            if (!stmMemberExist.executeQuery().next()) {
                throw new JsonbException("Member doesn't exist");
            }

            PreparedStatement stm = connection.
                    prepareStatement("SELECT b.title, b.copies, ((b.copies - COUNT(ii.isbn)) > 0) as availability  " +
                            "FROM issue_item ii\n" +
                            "    INNER JOIN `return` r ON NOT (ii.issue_id = r.issue_id and ii.isbn = r.isbn)\n" +
                            "    RIGHT OUTER JOIN book b on ii.isbn = b.isbn WHERE b.isbn = ? GROUP BY b.isbn");

            PreparedStatement stm2 = connection.prepareStatement("SELECT *, b.title FROM issue_item ii\n" +
                    "    INNER JOIN `return` r ON NOT(ii.issue_id = r.issue_id and ii.isbn = r.isbn)\n" +
                    "    INNER JOIN book b on ii.isbn = b.isbn\n" +
                    "    INNER JOIN issue_note `in` on ii.issue_id = `in`.id\n" +
                    "    WHERE `in`.member_id = ? AND b.isbn = ?");

            stm2.setString(1, issueNote.getMemberId());

            for (String isbn : issueNote.getBooks()) {
                stm.setString(1, isbn);
                stm2.setString(2, isbn);
                ResultSet rst = stm.executeQuery();
                ResultSet rst2 = stm2.executeQuery();
                if (!rst.next()) throw new JsonbException("Book doesn't exist");
                if (!rst.getBoolean("availability")) {
                    throw new JsonbException(isbn + " is not available at the moment");
                }
                if (rst2.next()) throw new JsonbException("Book has been already issued to the same member before");
            }

            PreparedStatement stmAvailable = connection.
                    prepareStatement("SELECT m.name, 3 - COUNT(r.issue_id) as available FROM issue_note\n" +
                            "    INNER JOIN issue_item ii on issue_note.id = ii.issue_id\n" +
                            "    INNER JOIN `return` r ON NOT(ii.issue_id = r.issue_id and ii.isbn = r.isbn)\n" +
                            "    RIGHT OUTER JOIN member m on issue_note.member_id = m.id\n" +
                            "    WHERE m.id = ? GROUP BY m.id");
            stmAvailable.setString(1, issueNote.getMemberId());
            ResultSet rst = stmAvailable.executeQuery();
            rst.next();
            int available = rst.getInt("available");
            if (issueNote.getBooks().size() > available) {
                throw new JsonbException("Member can borrow only " + available + " books");
            }

            try {
                connection.setAutoCommit(false);
                PreparedStatement stmIssueNote =
                        connection.
                                prepareStatement("INSERT INTO issue_note (date, member_id) VALUES (?,?)",
                                        Statement.RETURN_GENERATED_KEYS);
                stmIssueNote.setDate(1, Date.valueOf(LocalDate.now()));
                stmIssueNote.setString(2, issueNote.getMemberId());
                if (stmIssueNote.executeUpdate() != 1){
                    throw new SQLException("Failed to insert the issue note");
                }

                ResultSet generatedKeys = stmIssueNote.getGeneratedKeys();
                generatedKeys.next();
                int issueNoteId = generatedKeys.getInt(1);

                PreparedStatement stmIssueItem =
                        connection.prepareStatement("INSERT INTO issue_item (issue_id, isbn) VALUES (?,?)");
                stmIssueItem.setInt(1, issueNoteId);
                for (String isbn : issueNote.getBooks()) {
                    stmIssueItem.setString(2, isbn);
                    if (stmIssueItem.executeUpdate() != 1){
                        throw new SQLException("Failed to insert an issue item");
                    }
                }

                connection.commit();
                issueNote.setDate(LocalDate.now());
                issueNote.setId(issueNoteId);
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.setContentType("application/json");
                JsonbBuilder.create().toJson(issueNote, response.getWriter());

            }catch (Throwable t){
                t.printStackTrace();
                connection.rollback();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to place the issue note");
            }finally{
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to place the issue note");
        }
    }
}

//ac0351e9-fdee-4726-8d66-40f2621bfa9e  Kamala
//    1234-7891                 Clean Code          3 -1
//    4567-4567     returned    SQL Specification   1 -1+1

//d333d919-3ae4-4496-8a81-4915053dec08  Visal Srimanga
//    1234-1234                 Java EE         2 -1
//    4567-7891                 Ecma Script     1 -1
//    7891-1234                 Java Language   3 -1