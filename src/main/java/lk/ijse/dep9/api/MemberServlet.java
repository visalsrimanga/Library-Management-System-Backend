package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "MemberServlet", value = "/members/*", loadOnStartup = 0)
public class MemberServlet extends HttpServlet2 {

    @Resource(lookup = "java:/comp/env/jdbc/dep9-lms")
    private DataSource pool;

    /* This is the old way */
    /*@Override
    public void init() throws ServletException {
        try {
            InitialContext ctx = new InitialContext();
            pool = (DataSource) ctx.lookup("jdbc/lms");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }*/

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
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Expented valid UUID");
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid UUID");
            }
        }
    }

    private void loadAllMembers(HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT * FROM member");

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }

//            response.addHeader("Access-Control-Allow-Origin", "http://localhost:5500");
//            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Fail to load data from database");
        }
    }

    private void searchMembers(String query, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement
                    ("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            query = "%"+query+"%";

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);

            ResultSet rst = stm.executeQuery();
            ArrayList<MemberDTO> members = new ArrayList<>();

            while(rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }

            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while load data from database");
        }
    }

    private void searchPaginatedMembers(String query, int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            String sql = "SELECT COUNT(id) as count FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?";
            PreparedStatement countStm = connection.prepareStatement(sql);

            PreparedStatement stm = connection.prepareStatement
                    ("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ? LIMIT ? OFFSET ?");

            query="%"+query+"%";

            int length = sql.split("[?]").length;

            for (int i = 1; i <= length; i++) {
                countStm.setString(i, query);
                stm.setString(i, query);
            }

            stm.setInt(length+1, size);
            stm.setInt(length+2, (page-1)*size);
            ResultSet rst = countStm.executeQuery();
            rst.next();
            response.setIntHeader("X-Total-Count", rst.getInt("count"));
            rst = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while(rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }

            /*response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");*/
            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members, response.getWriter());


        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Fail to fetch members");
        }
    }

    private void loadAllPaginatedMembers(int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT COUNT(id) AS count FROM member");
            rst.next();
            int totalMembers = rst.getInt("count");
            response.setIntHeader("X-Total-Count", totalMembers);

            PreparedStatement stm2 = connection.
                    prepareStatement("SELECT * FROM member LIMIT ? OFFSET ?");
            stm2.setInt(1, size);
            stm2.setInt(2, (page -1)*size);
            rst = stm2.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while(rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }

//            response.addHeader("Access-Control-Allow-Origin", "*");
//            response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
//            response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");
            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Fail to execute the query");
        }
    }

    private void getMemberDetails(String memberId, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id=?");
            stm.setString(1, memberId);
            ResultSet rst = stm.executeQuery();

            if (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                response.setContentType("application/json");
//                response.setHeader("Access-Control-Allow-Origin","*");
                JsonbBuilder.create().toJson(new MemberDTO(id, name, address, contact), response.getWriter());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid member id");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to execute the query");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid UUID");
            return;
        }

        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                .matcher(request.getPathInfo());
        if (matcher.matches()){
            deleteMember(matcher.group(1), response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid UUID");
        }
    }

    private void deleteMember(String memberID, HttpServletResponse response){
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("DELETE FROM member WHERE id=?");
            stm.setString(1, memberID);
            int affectedRows = stm.executeUpdate();
            if (affectedRows == 0){
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid member Id");
            } else {
//                response.setHeader("Access-Control-Allow-Origin","*");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        } catch (SQLException|IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            try {
                if (request.getContentType() == null || !request.getContentType().startsWith("application/json")){
                    throw new RuntimeException("Invalid JSON");
                }

                MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);

                if (member.getName() == null || !member.getName().matches("[A-Za-z ]+")){
                    throw new JsonbException("Name is empty or invalid");
                } else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                    throw new JsonbException("Contact is empty or invalid");
                } else if (member.getAddress() == null || !member.getAddress().matches("[A-Za-z0-9 ,:/-]+")) {
                    throw new JsonbException("Address is empty or invalid");
                }

                try(Connection connection = pool.getConnection()){
                    member.setId(UUID.randomUUID().toString());
                    PreparedStatement stm = connection.prepareStatement
                            ("INSERT INTO member (id, name, address, contact) VALUES (?, ?, ?, ?)");
                    stm.setString(1, member.getId());
                    stm.setString(2, member.getName());
                    stm.setString(3, member.getAddress());
                    stm.setString(4, member.getContact());

                    int affectedRows = stm.executeUpdate();

                    if (affectedRows == 1) {
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setContentType("application/json");
//                        response.addHeader("Access-Control-Allow-Origin", "*");
                        JsonbBuilder.create().toJson(member, response.getWriter());
                    } else {
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                }

            } catch (JsonbException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON");
            }

        } else {
            response.sendError(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid UUID");
            return;
        }
        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                .matcher(request.getPathInfo());
        if (matcher.matches()){
            updateMember(matcher.group(1), request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid UUID");
        }
    }

    private void updateMember(String memberId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (request.getContentType() == null || !request.getContentType().startsWith("application/json")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Json");
            }
            MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);

            if(member.getId() == null || !memberId.equalsIgnoreCase(member.getId())){
                throw new JsonbException("Id is empty or invalid");
            }else if (member.getName() == null || !member.getName().matches("[A-Za-z ]+")){
                throw new JsonbException("Name is empty or invalid");
            } else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                throw new JsonbException("Contact is empty or invalid");
            } else if (member.getAddress() == null || !member.getAddress().matches("[A-Za-z0-9 ,:/-]+")) {
                throw new JsonbException("Address is empty or invalid");
            }

            try(Connection connection = pool.getConnection()){
                PreparedStatement stm = connection.prepareStatement
                        ("UPDATE member SET name=?, address=?, contact=? WHERE id=?");
                stm.setString(1, member.getName());
                stm.setString(2, member.getAddress());
                stm.setString(3, member.getContact());
                stm.setString(4, member.getId());

                if (stm.executeUpdate() == 1){
//                    response.setHeader("Access-Control-Allow-Origin","*");
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Member does not exits");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while loading data from DB");
            }
        } catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    /*@Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, HEAD, OPTIONS, PUT");

        String headers = req.getHeader("Access-Control-Request-Headers");
        if (headers != null){
            resp.setHeader("Access-Control-Allow-Headers", headers);
            resp.setHeader("Access-Control-Expose-Headers", headers);
        }
    }*/
}