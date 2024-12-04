
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class DemoServer {

    private static final Map<Integer, List<ClientHandler>> rooms = new HashMap<>();

    public static void loadRoomsFromDatabase() {
        String query = "SELECT id FROM rooms"; // Oda ID'lerini çeken sorgu
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", ""); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {

            while (resultSet.next()) {
                int roomId = resultSet.getInt("id");
                rooms.put(roomId, new ArrayList<>()); // Her oda için boş bir kullanıcı listesi eklenir
            }

            System.out.println("Oda bilgileri başarıyla yüklendi.");
        } catch (SQLException e) {
            System.err.println("Veritabanından oda bilgileri yüklenirken bir hata oluştu.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        final int port = 9091;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server " + port + " üzerinden başlatıldı.");

            // Odaları yükle
            loadRoomsFromDatabase();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Port dinlenirken bir hata oluştu: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private int userId;
        private String username;
        private int roomId;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                initializeConnection();
                joinRoom();
                handleClientMessages();
            } catch (IOException | SQLException e) {
                System.err.println("Bağlantı hatası: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void initializeConnection() throws IOException {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Socket sunucusuna bağlanan kullanıcı bilgileri
            userId = Integer.parseInt(in.readLine());
            username = in.readLine();
            roomId = Integer.parseInt(in.readLine());
        }

        private void joinRoom() throws SQLException {
            synchronized (rooms) {
                rooms.putIfAbsent(roomId, new ArrayList<>());
                rooms.get(roomId).add(this);
            }

            sendRoomMessagesToClient();
            notifyRoomMembers("[+] " + username + " odaya katıldı.");
        }

        private void handleClientMessages() throws IOException, SQLException {
            String message;

            while ((message = in.readLine()) != null) {
                if ("LEAVE_ROOM".equals(message)) {
                    leaveRoom();
                    break;
                }

                if ("CLOSE_ROOM".equals(message) && userId == getRoomOwnerId(roomId)) {
                    closeRoom();
                    break;
                }

                saveMessageToDatabase(message);
                broadcastMessage(message);
            }
        }

        private int getRoomOwnerId(int roomId) throws SQLException {
            String query = "SELECT creator_id FROM rooms WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, roomId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("creator_id");
                }
            }
            return -1; // Oda bulunamazsa -1 döner
        }

        private void closeRoom() {
            synchronized (rooms) {
                notifyRoomMembers("[+] Oda kapatıldı.");
                deleteRoomFromDatabase();
                List<ClientHandler> clients = rooms.get(roomId);
                if (clients != null) {
                    for (ClientHandler client : clients) {
                        client.out.println("ROOM_CLOSED");
                        client.closeConnection(); // Kullanıcı bağlantısını kapat
                    }
                    rooms.remove(roomId);
                }
            }
        }

        private void deleteRoomFromDatabase() {
            String deleteMessagesQuery = "DELETE FROM messages WHERE room_id = ?";
            String deleteRoomQuery = "DELETE FROM rooms WHERE id = ?";

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false); // İşlemleri bir işlem bloğu olarak başlat

                try (PreparedStatement deleteMessagesStmt = conn.prepareStatement(deleteMessagesQuery); PreparedStatement deleteRoomStmt = conn.prepareStatement(deleteRoomQuery)) {

                    // `messages` tablosundaki ilgili kayıtları sil
                    deleteMessagesStmt.setInt(1, roomId);
                    deleteMessagesStmt.executeUpdate();

                    // `rooms` tablosundaki oda kaydını sil
                    deleteRoomStmt.setInt(1, roomId);
                    int rowsAffected = deleteRoomStmt.executeUpdate();

                    if (rowsAffected > 0) {
                        conn.commit(); // Tüm işlemleri başarılı bir şekilde tamamla
                        System.out.println("Oda ve ilgili mesajlar başarıyla silindi: ID = " + roomId);
                    } else {
                        conn.rollback(); // Hata durumunda değişiklikleri geri al
                        System.err.println("Oda silinirken bir hata oluştu veya oda zaten yoktu.");
                    }
                } catch (SQLException e) {
                    conn.rollback(); // İşlem sırasında hata oluşursa geri al
                    throw e;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void broadcastMessage(String message) {
            synchronized (rooms) {
                for (ClientHandler client : rooms.get(roomId)) {
                    if (client != this) {
                        client.out.println(username + ": " + message);
                    }
                }
            }
        }

        private void leaveRoom() {
            synchronized (rooms) {
                List<ClientHandler> clients = rooms.get(roomId);
                if (clients != null) {
                    clients.remove(this);
                    if (clients.isEmpty()) {
                        rooms.remove(roomId); // Eğer odada kimse kalmazsa oda listesinden kaldır
                    } else {
                        notifyRoomMembers("[+] " + username + " odadan ayrıldı.");
                    }
                }
            }
            closeConnection(); // Kullanıcının bağlantısını kapat
        }

        private void notifyRoomMembers(String notification) {
            synchronized (rooms) {
                for (ClientHandler client : rooms.get(roomId)) {
                    if (client != this) {
                        client.out.println(notification);
                    }
                }
            }
        }

        private void saveMessageToDatabase(String message) throws SQLException {
            String query = "INSERT INTO messages (room_id, sender_id, message, date_time) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, roomId);
                stmt.setInt(2, userId);
                stmt.setString(3, message);
                stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }
        }

        private void sendRoomMessagesToClient() throws SQLException {
            String query = "SELECT sender_id, message, date_time FROM messages WHERE room_id = ? ORDER BY date_time";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, roomId);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int senderId = rs.getInt("sender_id");
                    String message = rs.getString("message");
                    String senderName = (senderId == userId) ? "Ben" : getUsernameById(senderId);

                    out.println(senderName + ": " + message);
                }
            }
        }

        private String getUsernameById(int senderId) throws SQLException {
            String query = "SELECT username FROM users WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, senderId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getString("username");
                }
            }
            return "Bilinmeyen Kullanıcı";
        }

        private Connection getConnection() throws SQLException {
            return DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", "");
        }

        private void closeConnection() {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Soket kapatma hatası: " + e.getMessage());
            }
        }
    }
}
