
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/*

synchronized kullanılmışsa: Belirttiğiniz kod bloğu veya metot, otomatik olarak bir kilit mekanizmasına sahip olur.

 */
public class DemoServer {

    // Odaları ve her odadaki kullanıcıları tutan bir Map nesnesi. (kay value ilişkisi, key = oda ID'si, value = ClientHandler listesi)
    //private static final Map<Integer, List<ClientHandler>> rooms = new HashMap<>();
    // ConcurrentHashMap kullanılabilir. Bu, eş zamanlı okuma/yazma işlemlerini optimize eder.
    private static final Map<Integer, List<ClientHandler>> rooms = new ConcurrentHashMap<>();

    // 10 iş parçacığına kadar eşzamanlı işlemleri yöneten bir iş parçacığı havuzu.
    //private static final ExecutorService threadPool = Executors.newFixedThreadPool(10); // 10 iş parçacığı
    // Gerektiğinde iş parçacığı sayısını artırır ve kullanılmayanları zamanla serbest bırakır. (newCachedThreadPool)
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    private static final ReentrantLock roomLock = new ReentrantLock();

    // Sunucu başladığında, veritabanında mevcut olan odaların yüklenmesi.
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
                Socket clientSocket = serverSocket.accept(); // Yeni bir istemci bağlandığında bir Socket nesnesi döndürür.
                new Thread(new ClientHandler(clientSocket)).start(); // Her istemci için yeni bir iş parçacığı oluşturulur ve ClientHandler sınıfı çalıştırılır.
            }
        } catch (IOException e) {
            System.err.println("Port dinlenirken bir hata oluştu: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket clientSocket; // İstemci ile iletişimi sağlayan soket nesnesi.
        private PrintWriter out; // İstemciye veri göndermek ve istemciden gelen veriyi okumak için kullanılır.
        private BufferedReader in; // İstemciye veri göndermek ve istemciden gelen veriyi okumak için kullanılır.
        private int userId; // İstemciye ait bilgiler.
        private String username; // İstemciye ait bilgiler.
        private int roomId; // İstemciye ait bilgiler.

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                initializeConnection(); // İstemciden kullanıcı bilgilerini alır.
                joinRoom(); // Kullanıcıyı belirtilen odaya ekler.
                handleClientMessages(); // Kullanıcıdan gelen mesajları işler.
            } catch (IOException | SQLException e) {
                System.err.println("Bağlantı hatası: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void initializeConnection() throws IOException {
            // PrintWriter ve BufferedReader: Soket üzerinden veri gönderme/alma işlemleri.
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Socket sunucusuna bağlanan kullanıcı bilgileri
            // İstemci, bağlanırken sırasıyla userId, username, ve roomId bilgilerini gönderir.
            userId = Integer.parseInt(in.readLine());
            username = in.readLine();
            roomId = Integer.parseInt(in.readLine());
        }

        private void joinRoom() throws SQLException {
            synchronized (rooms) {
                // Eğer oda yoksa liste oluşturulur.
                rooms.putIfAbsent(roomId, new ArrayList<>());
                rooms.get(roomId).add(this);
            }

            sendRoomMessagesToClient(); // Odadaki eski mesajları istemciye gönderir.
            notifyRoomMembers("[+] " + username + " odaya katıldı."); // Diğer kullanıcılara bildirim mesajı gönderir.
        }

        // Kullanıcıdan gelen mesajlar işlenir.
        // Bir dosya bulunamazsa, bir ağ bağlantısı kesilirse veya bir socket kapatılırken hata oluşursa IOException meydana gelir.
        // BufferedReader kullanılarak istemciden veri okunurken veya PrintWriter ile istemciye veri gönderilirken giriş/çıkış işlemleri yapılır.
        private void handleClientMessages() throws IOException, SQLException {
            String message;

            while ((message = in.readLine()) != null) {
                final String currentMessage = message; // Lambda için gerekli

                // threadPool.submit: Her mesaj işlemi ayrı bir iş parçacığında çalışır.
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
                        e.printStackTrace();
                        // printStackTrace(), Java'da bir exception meydana geldiğinde, bu istisnanın ayrıntılı bilgiyle birlikte stack trace konsola yazdırmak için kullanılan bir yöntemdir.
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
            roomLock.lock();
            try {
                for (ClientHandler client : rooms.get(roomId)) {
                    if (client != this) {
                        client.out.println(username + ": " + message);
                    }
                }
            } finally {
                roomLock.unlock();
            }
        }

        /*
        // broadcastMessage için synchronized yapısı da kullanılabilir
        private void broadcastMessage(String message) {
            synchronized (rooms) {
                for (ClientHandler client : rooms.get(roomId)) {
                    if (client != this) {
                        client.out.println(username + ": " + message);
                    }
                }
            }
        }
        */
        
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
                List<ClientHandler> clients = rooms.get(roomId);
                List<String> participants = new ArrayList<>();

                // Oda üyelerinin listesini oluştur
                for (ClientHandler client : clients) {
                    participants.add(client.username);
                }

                // Katılımcı listesini tüm kullanıcılara gönder
                for (ClientHandler client : clients) {
                    if (client != this) {
                        // Diğer bildirim mesajlarını gönder
                        client.out.println(notification);
                    }
                    // Katılımcı listesini gönder
                    client.out.println("UPDATE_PARTICIPANTS_LIST " + String.join(",", participants));
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
