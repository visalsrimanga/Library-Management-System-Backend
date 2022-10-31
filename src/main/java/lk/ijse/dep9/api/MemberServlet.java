package lk.ijse.dep9.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.db.ConnectionPool;
import lk.ijse.dep9.dto.MemberDTO;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "MemberServlet", value = "/members/*", loadOnStartup = 0)
public class MemberServlet extends HttpServlet2 {

    private DataSource pool;

    @Override
    public void init() throws ServletException {
        try {
            InitialContext ctx = new InitialContext();
            pool = (DataSource) ctx.lookup("jdbc/lms");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        response.getWriter().println("MemberServlet: doGet()");
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");

            if (query != null && size != null && page != null){
                if (size.matches("\\d+") && page.matches("\\d+")){
                    searchPaginatedMembers(query, Integer.parseInt(size),Integer.parseInt(page), response);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page or size");
                }
            } else if (query != null) {
                searchMembers(query, response);
            } else if (page != null && size != null) {
                if (size.matches("\\d+") && page.matches("\\d+")){
                    loadAllPaginatedMembers(Integer.parseInt(size), Integer.parseInt(page), response);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page or size");
                }
            } else {
                loadAllMembers(response);
            }
        } else {
            Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                    .matcher(request.getPathInfo());
            if (matcher.matches()){
                getMemberDetails(matcher.group(1), response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid UUID");
            }
        }

    }

    private void loadAllMembers(HttpServletResponse response) throws IOException {
//        response.getWriter().printf("<h1>WS: loadAllMembers()</h1>");
        try {
//            ConnectionPool pool = (ConnectionPool) getServletContext().getAttribute("pool");
//            BasicDataSource pool = (BasicDataSource) getServletContext().getAttribute("pool");
            Connection connection = pool.getConnection();

            Statement stm =connection.createStatement();
                ResultSet rst = stm.executeQuery("SELECT * FROM member");

                ArrayList<MemberDTO> members = new ArrayList<>();

                while(rst.next()){
                    String id = rst.getString("id");
                    String name = rst.getString("name");
                    String address = rst.getString("address");
                    String contact = rst.getString("contact");
                    MemberDTO dto = new MemberDTO(id, name, address, contact);
                    members.add(dto);
                }
            /* How to release the connection in connection pool which is prepared by us */
//                pool.releaseConnection(connection);

            /* This is not going to close the connection, instead it release the connection */
            connection.close();

                Jsonb jsonb = JsonbBuilder.create();
                response.setContentType("application/json");
                jsonb.toJson(members, response.getWriter());


//                StringBuilder sb = new StringBuilder();
//                sb.append("[");
//                while(rst.next()){
//                    String id = rst.getString("id");
////                    String name = rst.getString("name");
////                    String address = rst.getString("address");
////                    String contact = rst.getString("contact");
//                    String jsonObj = "{\n" +
//                            "  \"id\": \""+id+"\",\n" +
//                            "  \"name\": \""+name+"\",\n" +
//                            "  \"address\": \""+address+"\",\n" +
//                            "  \"contact\": \""+contact+"\"\n" +
//                            "}";
//                    sb.append(jsonObj).append(",");
//                }
//                sb.deleteCharAt(sb.length()-1);
//                sb.append("]");
//                response.setContentType("application/json");
//                response.getWriter().println(sb);

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Fail to load members");
        }
    }

    private void searchPaginatedMembers(String query, int size, int page, HttpServletResponse response) throws IOException {
        response.getWriter().printf("<h1>WS: searchMembersByPage(), size = %s, page = %s</h1>",size, page);
    }

    private void searchMembers(String query, HttpServletResponse response) throws IOException {
        response.getWriter().printf("<h1>WS: searchMembers(), query = %s </h1>",query);
    }

    private void loadAllPaginatedMembers(int size, int page, HttpServletResponse response) throws IOException {
        response.getWriter().printf("<h1>WS: loadAllMembersByPage(), size = %s, page = %s</h1>",size, page);
    }

    private void getMemberDetails(String memberId, HttpServletResponse response) throws IOException {
        response.getWriter().printf("<h1>WS: getMemberDetails(), memberId = %s </h1>",memberId);
    }




    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.getWriter().println("MemberServlet: doPost()");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().println("MemberServlet: doDelete()");
    }

    @Override
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().println("MemberServlet: doPatch()");
    }
}
