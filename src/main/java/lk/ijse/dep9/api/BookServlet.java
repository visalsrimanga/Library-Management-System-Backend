package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.dto.BookDTO;
import lk.ijse.dep9.dto.MemberDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "BooksServlet", value = "/books/*", loadOnStartup = 1)
public class BookServlet extends HttpServlet2 {
    @Resource(lookup = "java:/comp/env/jdbc/dep9-lms")
    private DataSource pool;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");

            if (query != null && size != null && page != null){
                if (size.matches("\\d+") && page.matches("\\d+")){
                    searchPaginatedBooks(query, Integer.parseInt(size), Integer.parseInt(page), response);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page or size");
                }
            } else if (query != null) {
                searchBooks(query, response);
            } else if (page != null && size != null) {
                if (size.matches("\\d+") && page.matches("\\d+")) {
                    loadAllPaginatedBooks(Integer.parseInt(size), Integer.parseInt(page), response);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page or size");
                }
            } else {
                loadAllBooks(response);
            }
        } else {
            Matcher matcher = Pattern.compile("^/([\\d]{3}-[\\d]{1}-[\\d]{2}-[\\d]{6}-[\\d]{1})/?$")
                    .matcher(request.getPathInfo());
            if (matcher.matches()){
                getBookDetails(matcher.group(1), response);
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Expented valid UUID");
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid ISBN");
            }
        }
    }

    private void loadAllBooks(HttpServletResponse response) throws IOException {
        try (Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT * FROM book");

            ArrayList<BookDTO> books = new ArrayList<>();

            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");
                books.add(new BookDTO(isbn, title, author, copies));
            }

            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(books, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "There is an issue while loading the database");
        }
    }
    private void loadAllPaginatedBooks(int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stm1 = connection.createStatement();
            ResultSet rst = stm1.executeQuery("SELECT COUNT(isbn) AS count FROM book");
            rst.next();
            int totalBooks = rst.getInt("count");
            response.setIntHeader("X-Total-Count", totalBooks);

            PreparedStatement stm2 = connection.prepareStatement
                    ("SELECT * FROM book LIMIT ? OFFSET ?");
            stm2.setInt(1, size);
            stm2.setInt(2, (page -1)*size);
            rst = stm2.executeQuery();

            ArrayList<BookDTO> books = new ArrayList<>();

            while(rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");
                books.add(new BookDTO(isbn, title, author, copies));
            }

            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(books, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while loading the data");
        }
    }

    private void searchBooks(String query, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement
                    ("SELECT * FROM book WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ? OR copies LIKE ?");
            query = "%" + query +"%";

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);

            ResultSet rst = stm.executeQuery();
            ArrayList<BookDTO> books = new ArrayList<>();

            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");
                books.add(new BookDTO(isbn, title, author, copies));
            }

            response.setContentType("application/json");
            JsonbBuilder.create().toJson(books, response.getWriter());


        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while getting connection");
        }
    }
    private void searchPaginatedBooks(String query, int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm1 = connection.prepareStatement
                    ("SELECT COUNT(isbn) AS count FROM book WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ? OR copies LIKE ?");
            query = "%" + query +"%";

            stm1.setString(1, query);
            stm1.setString(2, query);
            stm1.setString(3, query);
            stm1.setString(4, query);

            ResultSet rst = stm1.executeQuery();
            rst.next();
            int totalBooks = rst.getInt("count");
            response.setIntHeader("X-Total-Count", totalBooks);

            PreparedStatement stm2 = connection.prepareStatement
                    ("SELECT * FROM book WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ? OR copies LIKE ? LIMIT ? OFFSET ?");

            stm2.setString(1, query);
            stm2.setString(2, query);
            stm2.setString(3, query);
            stm2.setString(4, query);
            stm2.setInt(5, size);
            stm2.setInt(6, (page - 1)*size);
            rst = stm2.executeQuery();

            ArrayList<BookDTO> books = new ArrayList<>();

            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");
                books.add(new BookDTO(isbn, title, author, copies));
            }
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(books, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while loading the data");
        }
    }

    private void getBookDetails(String isbn, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM book WHERE isbn = ?");
            stm.setString(1, isbn);
            ResultSet rst = stm.executeQuery();

            if (rst.next()){
                String isbn1 = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin","*");
                JsonbBuilder.create().toJson(new BookDTO(isbn1, title, author, copies), response.getWriter());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid ISBN");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while loading the data");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            try {
                if(request.getContentType() == null || !request.getContentType().startsWith("application/json")){
                    throw new RuntimeException("Invalid JSON");
                }

                BookDTO bookObj = JsonbBuilder.create().fromJson(request.getReader(), BookDTO.class);

                if (bookObj.getIsbn() == null || !bookObj.getIsbn().matches("^/([\\d]{3}-[\\d]{1}-[\\d]{2}-[\\d]{6}-[\\d]{1})/?$")){
                    System.out.println(bookObj.getIsbn());
                    throw new JsonbException("ISBN is empty or invalid");
                } else if (bookObj.getTitle() == null || !bookObj.getTitle().matches("[A-Za-z0-9 -]+")) {
                    throw new JsonbException("Contact is empty or invalid");
                } else if (bookObj.getAuthor() == null || !bookObj.getAuthor().matches("[A-Za-z ]+")) {
                    throw new JsonbException("Author name is empty or invalid");
                } else if (bookObj.getCopies() < 0) {
                    throw new JsonbException("Invalid number of copies");
                }

                try(Connection connection = pool.getConnection()){
                    PreparedStatement stm = connection.prepareStatement
                            ("INSERT INTO book (isbn, title, author, copies) VALUES (?, ?, ?, ?)");
                    stm.setString(1, bookObj.getIsbn());
                    stm.setString(2, bookObj.getTitle());
                    stm.setString(3, bookObj.getAuthor());
                    stm.setInt(4, bookObj.getCopies());

                    int affectedRows = stm.executeUpdate();

                    if (affectedRows == 1){
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setContentType("application/json");
                        response.addHeader("Access-Control-Allow-Origin", "*");
                        JsonbBuilder.create().toJson(bookObj, response.getWriter());
                    } else {
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while getting connection with database");
                }

            } catch (RuntimeException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }

        } else {
            response.sendError(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid isbn with the request");
            return;
        }
        Matcher matcher = Pattern.compile("^/([0-9]{3}-[0-9]{1}-[0-9]{2}-[0-9]{6}-[0-9]{1})/?$")
                .matcher(request.getPathInfo());
        if (matcher.matches()){
            updateBook(matcher.group(1), request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid isbn");
        }
    }

    private void updateBook(String isbn, HttpServletRequest request, HttpServletResponse response) {
        try {
            if (request.getContentType() == null || !request.getContentType().startsWith("application/json")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Json");
            }

            BookDTO bookObj = JsonbBuilder.create().fromJson(request.getReader(), BookDTO.class);

            if(bookObj.getIsbn() == null || !isbn.equalsIgnoreCase(bookObj.getIsbn())){
                throw new JsonbException("ISBN is empty or invalid");
            }else if (bookObj.getTitle() == null || !bookObj.getTitle().matches("[A-Za-z0-9 -]+")){
                throw new JsonbException("Title is empty or invalid");
            } else if (bookObj.getAuthor() == null || !bookObj.getAuthor().matches("[A-Za-z ]+")) {
                throw new JsonbException("Contact is empty or invalid");
            } else if (bookObj.getCopies() < 0) {
                throw new JsonbException("Address is empty or invalid");
            }

            try(Connection connection = pool.getConnection()){
                PreparedStatement stm =
                        connection.prepareStatement("UPDATE book SET title=?, author=?, copies=? WHERE isbn=?");

                stm.setString(1, bookObj.getTitle());
                stm.setString(2, bookObj.getAuthor());
                stm.setInt(3, bookObj.getCopies());
                stm.setString(4, bookObj.getIsbn());

                int affectedRows = stm.executeUpdate();

                if (affectedRows == 1){
                    response.setHeader("Access-Control-Allow-Origin","*");
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Book does not exits");
                }

            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, HEAD, OPTIONS, PUT");

        String headers = req.getHeader("Access-Control-Request-Headers");
        if (headers != null){
            resp.setHeader("Access-Control-Allow-Headers", headers);
            resp.setHeader("Access-Control-Expose-Headers", headers);
        }
    }
}
