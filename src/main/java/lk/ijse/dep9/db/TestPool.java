package lk.ijse.dep9.db;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class TestPool implements Serializable {
    private List<Connection> pool = new ArrayList<>();
    private List<Connection> consumerPool = new ArrayList<>();
}
