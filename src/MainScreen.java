
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.time.LocalDateTime;

public class MainScreen {

    public MainScreen() {
        System.out.println("MainScreen worked");
        JFrame frame = new JFrame("Ana Ekran");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        // Ortada birleşen paneller için ana panel
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Sol: Kayıt bölümü
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Kayıt Ol"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        leftPanel.add(new JLabel("Kullanıcı Adı:"), gbc);
        JTextField usernameField = new JTextField(15);
        gbc.gridy = 1;
        leftPanel.add(usernameField, gbc);
        gbc.gridy = 2;
        leftPanel.add(new JLabel("Şifre:"), gbc);
        JPasswordField passwordField = new JPasswordField(15);
        gbc.gridy = 3;
        leftPanel.add(passwordField, gbc);
        gbc.gridy = 4;
        leftPanel.add(new JLabel("Şifreyi Tekrarla:"), gbc);
        JPasswordField confirmPasswordField = new JPasswordField(15);
        gbc.gridy = 5;
        leftPanel.add(confirmPasswordField, gbc);
        gbc.gridy = 6;
        JButton registerButton = new JButton("Kayıt Ol");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        leftPanel.add(registerButton, gbc);

        // Kayıt işlemi
        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                String confirmPassword = new String(confirmPasswordField.getPassword());

                if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Lütfen tüm alanları doldurun.", "Hata", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    JOptionPane.showMessageDialog(frame, "Şifreler uyuşmuyor.", "Hata", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // SwingWorker ile arka plan işlemi
                SwingWorker<Boolean, Void> registerWorker = new SwingWorker<>() {
                    @Override
                    protected Boolean doInBackground() {
                        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", "")) {
                            String sql = "INSERT INTO users (username, password, created_at) VALUES (?, ?, ?)";
                            PreparedStatement statement = connection.prepareStatement(sql);
                            String hashedPassword = HashingUtil.hashPassword(password); // Şifreyi hashle
                            statement.setString(1, username);
                            statement.setString(2, hashedPassword); // Hashlenmiş şifreyi veritabanına kaydet
                            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));

                            int rowsInserted = statement.executeUpdate();
                            return rowsInserted > 0;
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Veritabanı hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE));
                            return false;
                        }
                    }

                    @Override
                    protected void done() {
                        try {
                            boolean success = get(); // doInBackground() metodunun dönüş değerini al

                            if (success) {
                                JOptionPane.showMessageDialog(frame, "Kayıt başarılı!", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(frame, "Kayıt başarısız.", "Hata", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(frame, "Bir hata oluştu: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                registerWorker.execute(); // İş parçacığını başlat
            }
        });
        
        // Sağ: Giriş bölümü
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Giriş Yap"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        rightPanel.add(new JLabel("Kullanıcı Adı:"), gbc);
        JTextField loginUsernameField = new JTextField(15);
        gbc.gridy = 1;
        rightPanel.add(loginUsernameField, gbc);
        gbc.gridy = 2;
        rightPanel.add(new JLabel("Şifre:"), gbc);
        JPasswordField loginPasswordField = new JPasswordField(15);
        gbc.gridy = 3;
        rightPanel.add(loginPasswordField, gbc);
        gbc.gridy = 4;
        JButton loginButton = new JButton("Giriş Yap");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(loginButton, gbc);

        // Panelleri ana panele ekle
        centerPanel.add(leftPanel);
        centerPanel.add(rightPanel);
        frame.add(centerPanel, BorderLayout.CENTER);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = loginUsernameField.getText();
                String password = new String(loginPasswordField.getPassword()); // Şifreyi string olarak al

                if (username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Kullanıcı adı ve şifre boş olamaz.", "Hata", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // SwingWorker tanımlama
                SwingWorker<Boolean, Void> loginWorker = new SwingWorker<>() {
                    private int userId;

                    @Override
                    protected Boolean doInBackground() {
                        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp_db", "root", "")) {
                            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
                            PreparedStatement statement = connection.prepareStatement(sql);
                            statement.setString(1, username);
                            statement.setString(2, HashingUtil.hashPassword(password)); // Şifreyi doğrudan karşılaştır

                            ResultSet resultSet = statement.executeQuery();

                            if (resultSet.next()) {
                                userId = resultSet.getInt("id"); // Veritabanından kullanıcı ID'sini al
                                return true;
                            } else {
                                return false;
                            }
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Veritabanı hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE));
                            return false;
                        }
                    }

                    @Override
                    protected void done() {
                        try {
                            boolean success = get(); // doInBackground() metodunun dönüş değerini al

                            if (success) {
                                // Başarılı giriş
                                UserSession.getInstance().setUsername(username); // Kullanıcıyı kaydet
                                UserSession.getInstance().setUserId(userId);     // Kullanıcı ID'sini kaydet
                                new UserScreen();  // Kullanıcı ekranını aç
                                frame.dispose();   // Ana ekranı kapat
                            } else {
                                // Kullanıcı yok
                                JOptionPane.showMessageDialog(frame, "Kullanıcı adı veya şifre yanlış.", "Hata", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(frame, "Bir hata oluştu: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };

                // SwingWorker'ı başlat
                loginWorker.execute();
            }
        });

        frame.setVisible(true);
    }
}
