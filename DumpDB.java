import java.sql.*;

public class DumpDB {
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:DB/main.db");
             Statement stmt = conn.createStatement()) {
            
            System.out.println("=== USER_ACTIVITY_LOG ===");
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM user_activity_log")) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                
                for (int i = 1; i <= cols; i++) {
                    System.out.print(meta.getColumnName(i) + "\t");
                }
                System.out.println();
                
                int count = 0;
                while (rs.next()) {
                    for (int i = 1; i <= cols; i++) {
                        System.out.print(rs.getString(i) + "\t");
                    }
                    System.out.println();
                    count++;
                }
                if (count == 0) {
                    System.out.println("(empty)");
                }
            }
            
            // Also check portfolios history if there's one
            System.out.println("\n=== PORTFOLIOS ===");
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM portfolios")) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                
                for (int i = 1; i <= cols; i++) {
                    System.out.print(meta.getColumnName(i) + "\t");
                }
                System.out.println();
                
                while (rs.next()) {
                    for (int i = 1; i <= cols; i++) {
                        System.out.print(rs.getString(i) + "\t");
                    }
                    System.out.println();
                }
            }
        }
    }
}