
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DemoServer {

    // // Odaların ve o odadaki istemcilerin listesi
    private static final Map<Integer, List<ClientHandler>> rooms = new ConcurrentHashMap<>();

    // Sabit boyutlu iş parçacığı havuzu ve queue yapısı
    private static final int THREAD_POOL_SIZE = 10;
    private static final BlockingQueue<Runnable> messageQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService threadPool = new ThreadPoolExecutor(
            THREAD_POOL_SIZE,
            THREAD_POOL_SIZE,
            0L,
            TimeUnit.MILLISECONDS,
            messageQueue
    );
    /*
    Eğer bir kuyruk kullanılmasaydı:
    İş parçacığı havuzunun boyutu dolduğunda, yeni görevler kabul edilemezdi.
    Sunucu gelen görevleri doğrudan reddedebilir veya hata fırlatabilirdi.
    Bu, kullanıcıların mesaj göndermesi sırasında başarısızlığa neden olabilirdi.
     */

    // Lock yapısı
    private static final Lock lock = new ReentrantLock();

    // Veri tabanından odaları çek ve rooms değişkenine ekle
    public static void loadRoomsFromDatabase() {
        String query = "SELECT id FROM rooms";
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", ""); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {

            while (resultSet.next()) {
                int roomId = resultSet.getInt("id");
                rooms.put(roomId, new ArrayList<>());
            }
            System.out.println("Oda bilgileri yüklendi.");
        } catch (SQLException e) {
            System.err.println("Veritabanından oda bilgileri yüklenirken bir hata oluştu.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        final int port = 9091;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server " + port + " üzerinden başlatıldı.");

            loadRoomsFromDatabase();

            while (true) {
                // Her gelen client için yeni bir socket oluştur
                Socket clientSocket = serverSocket.accept();
                // Her gelen client için bir thread oluştur
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Port dinlenirken bir hata oluştu: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket clientSocket;
        private PrintWriter out; // giden mesajlar
        private BufferedReader in; // gelen mesajlar
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

            // İstemciden kullanıcı bilgilerini al
            userId = Integer.parseInt(in.readLine());
            username = in.readLine();
            roomId = Integer.parseInt(in.readLine());
        }

        private void joinRoom() throws SQLException {
            // rooms değişkenini kilitleyen yapı
            synchronized (rooms) {
                // roomId yoksa ekle, varsa değiştirme
                rooms.putIfAbsent(roomId, new ArrayList<>());
                rooms.get(roomId).add(this);
            }

            // Odadaki geçmiş mesajları kullanıcıya gönder
            sendRoomMessagesToClient();
            notifyRoomMembers("[+] " + username + " odaya katıldı.");
            updateParticipantsList();
        }

        private void handleClientMessages() throws IOException, SQLException {
            String message;

            while ((message = in.readLine()) != null) {
                final String currentMessage = message;

                // Mesaj işleme işlerini kuyrukta sıraya al (lambda fonksiyonu)
                // verilen görevi (Runnable olarak) iş parçacığı havuzuna ekler.
                // İş parçacığı havuzu, bir işi yürütmeden önce bu işi bir kuyrukta sıraya koyar ve işleme alınacak görevleri bu kuyruktan çeker.
                threadPool.submit(() -> {
                    try {
                        if ("LEAVE_ROOM".equals(currentMessage)) {
                            leaveRoom();
                        } else if ("CLOSE_ROOM".equals(currentMessage) && userId == getRoomOwnerId(roomId)) {
                            closeRoom();
                        } else {
                            saveMessageToDatabase(currentMessage);
                            broadcastMessage(currentMessage);
                        }
                    } catch (Exception e) {
                        System.err.println("Mesaj işlenirken hata: " + e.getMessage());
                    }
                });
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
            return -1;
        }

        private void closeRoom() {
            synchronized (rooms) {
                notifyRoomMembers("[+] Oda kapatıldı.");
                deleteRoomFromDatabase();
                List<ClientHandler> clients = rooms.get(roomId);
                if (clients != null) {
                    for (ClientHandler client : clients) {
                        client.out.println("ROOM_CLOSED");
                        client.closeConnection();
                    }
                    rooms.remove(roomId);
                }
            }
        }

        private void deleteRoomFromDatabase() {
            String deleteMessagesQuery = "DELETE FROM messages WHERE room_id = ?";
            String deleteRoomQuery = "DELETE FROM rooms WHERE id = ?";

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement deleteMessagesStmt = conn.prepareStatement(deleteMessagesQuery); PreparedStatement deleteRoomStmt = conn.prepareStatement(deleteRoomQuery)) {

                    deleteMessagesStmt.setInt(1, roomId);
                    deleteMessagesStmt.executeUpdate();

                    deleteRoomStmt.setInt(1, roomId);
                    int rowsAffected = deleteRoomStmt.executeUpdate();

                    if (rowsAffected > 0) {
                        conn.commit();
                        System.out.println("Oda ve ilgili mesajlar başarıyla silindi: ID = " + roomId);
                    } else {
                        conn.rollback();
                        System.err.println("Oda silinirken bir hata oluştu veya oda zaten yoktu.");
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void broadcastMessage(String message) {
            lock.lock();
            try {
                List<ClientHandler> clients = rooms.get(roomId); // Odadaki kullanıcıları al
                if (clients != null) {
                    for (ClientHandler client : clients) {
                        if (client != this) {
                            client.out.println(username + ": " + message);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private void leaveRoom() {
            synchronized (rooms) {
                List<ClientHandler> clients = rooms.get(roomId);
                if (clients != null) {
                    clients.remove(this);
                    if (clients.isEmpty()) {
                        rooms.remove(roomId);
                    } else {
                        notifyRoomMembers("[+] " + username + " odadan ayrıldı.");
                        updateParticipantsList();
                    }
                }
            }
            closeConnection();
        }

        private void notifyRoomMembers(String notification) {
            synchronized (rooms) {
                List<ClientHandler> clients = rooms.get(roomId);
                if (clients != null) {
                    for (ClientHandler client : clients) {
                        client.out.println(notification);
                    }
                }
            }
        }

        private void updateParticipantsList() {
            synchronized (rooms) {
                List<ClientHandler> clients = rooms.get(roomId);
                if (clients != null) {
                    StringBuilder participants = new StringBuilder();
                    for (ClientHandler client : clients) {
                        participants.append(client.username).append(",");
                    }

                    // Son virgülü kaldır
                    if (participants.length() > 0) {
                        participants.setLength(participants.length() - 1);
                    }

                    // Katılımcı listesini tüm odadaki kullanıcılara gönder
                    for (ClientHandler client : clients) {
                        client.out.println("UPDATE_PARTICIPANTS_LIST " + participants);
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
