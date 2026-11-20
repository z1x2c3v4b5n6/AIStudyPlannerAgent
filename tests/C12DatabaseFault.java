import java.sql.*;
import java.util.UUID;

class C12DatabaseFault {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("DB_URL");
        if(url==null || !url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Fault injection requires a local disposable _test schema");
        String trigger="c12_"+UUID.fromString(args[1]).toString().replace("-","");
        try(var connection=DriverManager.getConnection(url,
                System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",""));
            var statement=connection.createStatement()) {
            switch(args[0]){
                case "install" -> {
                    String document=UUID.fromString(args[2]).toString();
                    statement.execute("""
                            CREATE TRIGGER %s BEFORE UPDATE ON knowledge_document FOR EACH ROW
                            BEGIN
                              IF NEW.id='%s' AND NOT (OLD.active_version <=> NEW.active_version) THEN
                                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='C12 isolated publication fault';
                              END IF;
                            END
                            """.formatted(trigger,document));
                }
                case "remove" -> statement.execute("DROP TRIGGER IF EXISTS "+trigger);
                case "connections" -> {
                    try(var rows=statement.executeQuery("SHOW STATUS LIKE 'Threads_connected'")) {
                        if(rows.next()) System.out.println(rows.getInt(2));
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported acceptance action");
            }
        }
    }
}
