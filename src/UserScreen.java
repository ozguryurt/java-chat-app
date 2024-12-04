
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserScreen {

    public UserScreen() {
        System.out.println("UserScreen worked");
        JFrame frame = new JFrame("Kullanıcı Ekranı");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);
        frame.setLayout(new BorderLayout());

        // Kullanıcı bilgisi
        int userId = UserSession.getInstance().getUserId();
        String username = UserSession.getInstance().getUsername();

        JLabel welcomeLabel = new JLabel("Hoşgeldiniz, " + username + "!", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 16));
        frame.add(welcomeLabel, BorderLayout.NORTH);

        // Ortada birleşen paneller için ana panel
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Sol: Oda oluşturma bölümü
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Bir Oda Oluştur"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        leftPanel.add(new JLabel("Oda Adı:"), gbc);
        JTextField roomNameField = new JTextField(15);
        gbc.gridy = 1;
        leftPanel.add(roomNameField, gbc);

        gbc.gridy = 2;
        leftPanel.add(new JLabel("Oda Şifresi:"), gbc);
        JPasswordField roomPasswordField = new JPasswordField(15);
        gbc.gridy = 3;
        leftPanel.add(roomPasswordField, gbc);

        gbc.gridy = 4;
        JButton createRoomButton = new JButton("Oda Oluştur");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        leftPanel.add(createRoomButton, gbc);

        // Sağ: Odaya katılma bölümü
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Bir Odaya Katıl"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        rightPanel.add(new JLabel("Oda ID:"), gbc);
        JTextField roomIdField = new JTextField(15);
        gbc.gridy = 1;
        rightPanel.add(roomIdField, gbc);

        gbc.gridy = 2;
        rightPanel.add(new JLabel("Oda Şifresi:"), gbc);
        JPasswordField joinRoomPasswordField = new JPasswordField(15);
        gbc.gridy = 3;
        rightPanel.add(joinRoomPasswordField, gbc);

        gbc.gridy = 4;
        JButton joinRoomButton = new JButton("Odaya Katıl");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(joinRoomButton, gbc);

        // Panelleri ana panele ekle
        centerPanel.add(leftPanel);
        centerPanel.add(rightPanel);
        frame.add(centerPanel, BorderLayout.CENTER);

        // Oda oluşturma işlemi
        createRoomButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String roomName = roomNameField.getText();
                String roomPassword = new String(roomPasswordField.getPassword());

                if (roomName.isEmpty() || roomPassword.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Lütfen tüm alanları doldurun.", "Hata", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                SwingWorker<Void, Void> createRoomWorker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() {
                        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", "")) {
                            String sql = "INSERT INTO rooms (room_name, room_password, creator_id) VALUES (?, ?, ?)";
                            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                            statement.setString(1, roomName);
                            statement.setString(2, HashingUtil.hashPassword(roomPassword));
                            statement.setString(3, Integer.toString(userId));

                            int rowsInserted = statement.executeUpdate();
                            if (rowsInserted > 0) {
                                ResultSet generatedKeys = statement.getGeneratedKeys();
                                if (generatedKeys.next()) {
                                    int createdRoomId = generatedKeys.getInt(1);
                                    UserSession.getInstance().setRoomId(createdRoomId); // Kullanıcıyı odaya ata
                                    SwingUtilities.invokeLater(() -> {
                                        JOptionPane.showMessageDialog(frame, "Oda başarıyla oluşturuldu.", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
                                        try {
                                            new ChatRoomScreen();  // Sohbet ekranını aç
                                        } catch (SQLException ex) {
                                            Logger.getLogger(UserScreen.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                        frame.dispose();   // Ana ekranı kapat
                                    });
                                }
                            } else {
                                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Oda oluşturulamadı.", "Hata", JOptionPane.ERROR_MESSAGE));
                            }
                        } catch (SQLException ex) {
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Veritabanı hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE));
                            ex.printStackTrace();
                        }
                        return null;
                    }
                };
                createRoomWorker.execute();
            }
        });

        // Odaya katılma işlemi
        joinRoomButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String roomId = roomIdField.getText();
                String roomPassword = new String(joinRoomPasswordField.getPassword());

                if (roomId.isEmpty() || roomPassword.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Lütfen tüm alanları doldurun.", "Hata", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                SwingWorker<Void, Void> joinRoomWorker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", "")) {
                            String sql = "SELECT id, room_password FROM rooms WHERE id = ?";
                            PreparedStatement statement = connection.prepareStatement(sql);
                            statement.setInt(1, Integer.parseInt(roomId));
                            ResultSet resultSet = statement.executeQuery();

                            if (resultSet.next()) {
                                String storedPassword = resultSet.getString("room_password");
                                int joinedRoomId = resultSet.getInt("id");

                                // Eğer şifre doğruysa, kullanıcıyı odaya katılmaya izin ver
                                if (storedPassword.equals(HashingUtil.hashPassword(roomPassword))) {
                                    // UserSession'da oda ID'sini ayarla
                                    UserSession.getInstance().setRoomId(joinedRoomId);

                                    // Sohbet ekranını aç
                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            new ChatRoomScreen();  // Sohbet ekranını aç
                                            frame.dispose();  // Ana ekranı kapat
                                        } catch (SQLException ex) {
                                            Logger.getLogger(UserScreen.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    });
                                } else {
                                    // Şifre yanlışsa hata mesajı göster
                                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Şifre hatalı.", "Hata", JOptionPane.ERROR_MESSAGE));
                                }
                            } else {
                                // Oda bulunamazsa hata mesajı göster
                                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Oda bulunamadı.", "Hata", JOptionPane.ERROR_MESSAGE));
                            }
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Veritabanı hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE));
                        }
                        return null;
                    }
                };
                joinRoomWorker.execute();
            }
        });

        frame.setVisible(true);
    }
}
