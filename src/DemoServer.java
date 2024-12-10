/*
Synchronized, bir blok veya metodun aynı anda yalnızca bir iş parçacığı tarafından erişilmesini sağlamak için kullanılır.
Eşzamanlılık sorunlarını önlemek için kullanılan bir mekanizmadır.
Synchronized ile korunan bir blok veya metod bir lock alır ve bu lock başka bir iş parçacığı tarafından serbest bırakılana kadar diğer iş parçacıkları bu bloğa/methoda erişemez.
*/

import java.io.*; // PrintWriter , BufferedReader, InputStreamReader, IOException kullanımı için. İstemciyle metin tabanlı iletişim.
import java.net.*; // Java network işlemleri için. ServerSocket, Socket
import java.sql.*; // JDBC (Java Database Connectivity) API'nin bir parçası. PreparedStatement (SQL injectiona karşı)
import java.util.*; // Map, List, ArrayList, ConcurrentHashMap (aynı anda birden fazla thread tarafından kullanılabilir) veri yapıları için.
import java.util.concurrent.*; // Multithreading sağlar. ExecutorService, ThreadPoolExecutor, BlockingQueue, LinkedBlockingQueue, TimeUnit

// ReentrantLock (iş parçacığı senkronizasyonu, erişim denetimi sağlar)
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DemoServer {

    // Birden fazla iş parçacığı tarafından eş zamanlı erişime izin veren bir yapı. deadlock durumunu önler.
    // Örneğin, bir istemci mesaj gönderirken başka bir istemci aynı anda odadan çıkabilir.
    // Map ve List kombinasyonu, odalara hızlı erişim sağlar. O(n) karmaşıklığındaki List işlemleri, yalnızca bir oda içindeki istemci sayısına bağlıdır.
    // Haritadan oda bulma işlemi O(1) zaman alır.
    // List: Oda ID'sine göre listeyi sıralamak veya arama yapmak gerekir (O(n)).
    // TreeMap: Oda kimliklerinin sıralı olmasına ihtiyaç yoktur. Sıralama, gereksiz performans maliyeti ekler.
    // HashMap: Aynı anda birden fazla iş parçacığı haritaya erişirse veri tutarsızlıkları oluşabilir.
    // ConcurrentSkipListMap: Sıralama özelliği gereksizdir ve performans kaybına yol açar.
    // Set ve Queue: İstemcilerin bir sırasını veya benzersizliğini sağlamada kullanılabilir ama oda ve istemcilerin ilişkisi için uygun değildir.
    // ConcurrentHashMap kendiliğinden thread-safe olduğu için ayrıca kilit mekanizmasına gerek kalmayabilir.
    private static final Map<Integer, List<ClientHandler>> rooms = new ConcurrentHashMap<>();

    // Havuzda aynı anda çalışabilecek maksimum iş parçacığı sayısını belirtir.
    private static final int THREAD_POOL_SIZE = 10;
    // Kuyruk, iş parçacığı havuzu tarafından işlenmesi gereken görevleri düzenli bir şekilde sıraya koyar.
    // Sonsuz Kuyruk: Kuyruğun boyutu sınırlı olmadığı için görevler dolmaz ve kaybolmaz. Bu, görevlerin güvenle işlenmesini sağlar.
    // ArrayBlockingQueue: Sabit boyutlu olduğu için uygun değil.
    // PriorityBlockingQueue: Öncelik gerektiren bir işleme ihtiyacımız yok.
    private static final BlockingQueue<Runnable> messageQueue = new LinkedBlockingQueue<>();
    // Core ve Maximum iş parçacığı sayısı 10.
    // Core iş parçacıkları hiçbir zaman sonlandırılmaz. (0L)
    // Keep-Alive süresinin birimi milisaniye olarak ayarlanır.
    // messageQueue görevlerin ekleneceği kuyruk yapısı.
    // Yeni Bir İş Parçacığı Her Görev İçin Oluşturulsaydı: Yoğun yük altında sistem kaynaklarının tükenmesine yol açar.
    // newFixedThreadPool(int n) olsaydı: kuyruk türü ve boyutu ayarlanamazdı
    // ForkJoinPool olsaydı: divide-and-conquer) görevler için uygundur.
    private static final ExecutorService threadPool = new ThreadPoolExecutor(
            THREAD_POOL_SIZE,
            THREAD_POOL_SIZE,
            0L,
            TimeUnit.MILLISECONDS,
            messageQueue
    );

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
                // Bu iş parçacığı, istemciden gelen mesajı okur ve hangi işlemin yapılması gerektiğini belirler.
                // Ancak bu iş parçacıkları doğrudan ağır işlemleri (örneğin mesajların veritabanına kaydedilmesi, tüm kullanıcılara iletilmesi) yapmaz.
                // İstemci bağlantısını yöneten ClientHandler iş parçacıkları, ağır iş yüklerini doğrudan yapmaz. Bunun yerine, bu işleri ThreadPoolExecutor'a devreder.
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
                // Her bir mesaj için bir Runnable nesnesi oluşturur ve bu Runnable nesnesini iş parçacığı havuzuna ekler. Bu işlem, mesajın işlenmesini kuyrukta sıraya koyar.
                
                // Neden İşlemleri Kuyrukta Sıraya Alıyoruz?
                // İş parçacığı havuzunun sınırlı boyutunu yönetir ve iş parçacıklarının aşırı yüklenmesini önler.
                // Aynı anda birden fazla işlemi yürütmek için doğru sıralamayı korur
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

        // ReentrantLock niye kullanıldı?
        // Object-level locks, yalnızca belirli bir nesne üzerinde kilitlenebilir ve bu kilit, sadece o nesnenin üyeleri üzerinde etkilidir. rooms haritası üzerinde eş zamanlı erişim gerekli.
        // Bir sınıfın tüm instance'ları üzerinde kilitler. Bir sınıf içindeki tüm nesneler aynı anda kilitli olacağından, bu tür kilitler genellikle verimsizdir.
        // ReadWriteLock uygun değildir çünkü burada yazma operasyonları dışında başka bir işlem yapılmaz.
        // StampedLock kullanmak, ReentrantLock'a göre daha karmaşık ve spesifik bir çözüm gerektirir. Ayrıca, bir mesaj yayınlama işlemi için basit bir kilit yeterlidir.
        // Burada rooms ve clientHandler yönetiminde bir kaynak sınırlaması yoktur. Semaphore daha ziyade bir sınırlı kaynak havuzunu izlemek için kullanılırken daha doğrudur.
        // Countdown Latch: Belirli bir olayın gerçekleşmesi için bir dizi iş parçacığının tamamlanmasını bekler. İş parçacıkları bu latch'i beklerken işlem yapamaz.
        // Mutex: Özel bir kilitleme ve eş zamanlı erişim kontrolü sağlar. Mutex, genellikle sistem seviyesinde daha alt düzey işlemler için kullanılır.
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
