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
                case "install-submission" -> {
                    String session=UUID.fromString(args[2]).toString();
                    statement.execute("""
                            CREATE TRIGGER %s BEFORE UPDATE ON assessment_session FOR EACH ROW
                            BEGIN
                              IF NEW.id='%s' AND NEW.state='SUBMITTED' THEN
                                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='C13 isolated submission fault';
                              END IF;
                            END
                            """.formatted(trigger,session));
                }
                case "grade-count" -> {
                    String session=UUID.fromString(args[2]).toString();
                    try(var query=connection.prepareStatement("SELECT COUNT(*) FROM assessment_session_item WHERE session_id=? AND grade IS NOT NULL")) {
                        query.setString(1,session);
                        try(var rows=query.executeQuery()) { if(rows.next()) System.out.println(rows.getInt(1)); }
                    }
                }
                case "expire-session" -> {
                    String session=UUID.fromString(args[2]).toString();
                    try(var expire=connection.prepareStatement("UPDATE assessment_session SET expires_at='2000-01-01' WHERE id=? AND state='IN_PROGRESS'")) {
                        expire.setString(1,session);
                        if(expire.executeUpdate()!=1) throw new IllegalStateException("Expected one isolated session");
                    }
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
