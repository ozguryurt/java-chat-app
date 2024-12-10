import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChatRoomScreen {

    private Socket socket; // Sunucuya bağlantıyı temsil eder.
    private PrintWriter out; // Sunucuya mesaj göndermek için kullanılan PrintWriter nesnesi.
    private JTextArea chatArea; // Kullanıcıların mesajlarını ve diğer bilgileri görüntülediği bir metin alanı.
    private JFrame frame; // Sohbet odası arayüzünü temsil eden ana pencere.
    private UserSession userData; // Kullanıcı oturum bilgilerini tutan sınıf.
    private DefaultListModel<String> participantsListModel; // Katılımcı listesinin modelini tutar (dinamik olarak güncellenebilir)
    private JList<String> participantsList; // Katılımcıların görüntülendiği JList arayüz şeyi

    public ChatRoomScreen() throws SQLException {
        chatArea = new JTextArea();
        chatArea.setEditable(false);

        frame = new JFrame("Sohbet Odası"); // Sınıf değişkenine atama
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);
        frame.setLayout(new BorderLayout());

        try {
            socket = new Socket("localhost", 9091);
            out = new PrintWriter(socket.getOutputStream(), true);

            // Kullanıcı bilgilerini sunucuya gönder
            out.println(UserSession.getInstance().getUserId());
            out.println(UserSession.getInstance().getUsername());
            out.println(UserSession.getInstance().getRoomId());

            // Sunucudan gelen mesajları dinlemek için ayrı bir thread
            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if ("ROOM_CLOSED".equals(line)) {
                            int response = JOptionPane.showConfirmDialog(
                                    null,
                                    "Oda kapatıldı.",
                                    "Bilgilendirme",
                                    JOptionPane.DEFAULT_OPTION,
                                    JOptionPane.WARNING_MESSAGE
                            );

                            if (response == JOptionPane.OK_OPTION || response == JOptionPane.CLOSED_OPTION) {
                                new UserScreen(); // Kullanıcı ekranına dön
                                frame.dispose();  // Mevcut pencereyi kapat
                            }
                            break; // Döngüden çık
                        } else if (line.startsWith("UPDATE_PARTICIPANTS_LIST ")) {
                            // Katılımcı listesini güncelle
                            String[] users = line.substring(25).split(",");
                            updateParticipantsList(users);
                        } else {
                            chatArea.append(line + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Sunucuya bağlanılamadı. Lütfen sunucunun açık olduğundan emin olun.", "Bağlantı Hatası", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }

        // Katılımcı listesi için DefaultListModel kullanılıyor
        participantsListModel = new DefaultListModel<>(); // Katılımcı listesinin modelini tutar (dinamik olarak güncellenebilir).
        participantsList = new JList<>(participantsListModel);
        JPanel participantsPanel = new JPanel(new BorderLayout());
        participantsPanel.setBorder(BorderFactory.createTitledBorder("Katılımcılar"));
        JLabel roomInfoLabel = new JLabel("Oda numarası: " + UserSession.getInstance().getRoomId());
        JButton leaveRoomButton = new JButton("Odadan Ayrıl");
        participantsPanel.add(roomInfoLabel, BorderLayout.NORTH);
        participantsPanel.add(new JScrollPane(participantsList), BorderLayout.CENTER);
        participantsPanel.add(leaveRoomButton, BorderLayout.SOUTH);

        // Eğer kullanıcı oda sahibi ise "Odayı Kapat" butonu gözüksün
        if (UserSession.getInstance().getUserId() == getRoomOwnerId(UserSession.getInstance().getRoomId())) {
            // Kullanıcı oda sahibi ise "Odayı Kapat" butonu eklenir
            JButton closeRoomButton = new JButton("Odayı Kapat");
            closeRoomButton.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(frame, "Odayı kapatmak istediğinizden emin misiniz?", "Odayı Kapat", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    out.println("CLOSE_ROOM");
                    /*try {
                        socket.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }*/
                }
            });

            // "Odadan Ayrıl" butonunu aynı panelde tut
            JPanel ownerButtonPanel = new JPanel();
            ownerButtonPanel.setLayout(new GridLayout(2, 1)); // İki buton için 2 satırlı düzen
            ownerButtonPanel.add(leaveRoomButton);
            ownerButtonPanel.add(closeRoomButton);

            participantsPanel.add(ownerButtonPanel, BorderLayout.SOUTH);
        } else {
            // Kullanıcı oda sahibi değilse sadece "Odadan Ayrıl" butonunu göster
            participantsPanel.add(leaveRoomButton, BorderLayout.SOUTH);
        }

        frame.add(participantsPanel, BorderLayout.EAST);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField messageInput = new JTextField();
        JButton sendButton = new JButton("Gönder");
        sendButton.addActionListener(e -> {
            String message = messageInput.getText();
            if (!message.isEmpty()) {
                out.println(message);
                messageInput.setText("");
                chatArea.append("Ben: " + message + "\n");
            }
        });
        inputPanel.add(messageInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        frame.add(chatPanel, BorderLayout.CENTER);

        leaveRoomButton.addActionListener(e -> {
            out.println("LEAVE_ROOM");
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            new UserScreen();
            frame.dispose();
        });

        frame.setVisible(true);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", "");
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

    // Katılımcı listesinde güncelleme yapacak metod
    private void updateParticipantsList(String[] users) {
        // Listede bulunan tüm elemanları sil
        participantsListModel.clear();
        // Yeni kullanıcıları ekle
        for (String user : users) {
            participantsListModel.addElement(user);
        }
    }
}
