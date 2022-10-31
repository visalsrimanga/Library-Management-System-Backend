package lk.ijse.dep9.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import lk.ijse.dep9.db.ConnectionPool;
import org.apache.commons.dbcp2.BasicDataSource;

//@WebListener
public class ContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
//        ConnectionPool dbPool = new ConnectionPool(10);
//        sce.getServletContext().setAttribute("pool", dbPool);

        /* BY using inbuilt connection pool */
        BasicDataSource dbPool = new BasicDataSource();
        dbPool.setUrl("jdbc:mysql://localhost:3306/dep9_lms");
        dbPool.setUsername("root");
        dbPool.setPassword("Dvs&12345");
        dbPool.setDriverClassName("com.mysql.cj.jdbc.Driver");

        dbPool.setInitialSize(10);
        dbPool.setMaxTotal(20);

        sce.getServletContext().setAttribute("pool", dbPool);
    }
}
