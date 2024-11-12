import javax.swing.*;
import java.awt.*;

public class MainScreen {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Ana Ekran");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        // Ortada birleşen paneller için ana panel
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0)); // Panellerin arasında boşluk bırakıldı
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
        gbc.gridy = 1;
        leftPanel.add(new JTextField(15), gbc);
        gbc.gridy = 2;
        leftPanel.add(new JLabel("Şifre:"), gbc);
        gbc.gridy = 3;
        leftPanel.add(new JPasswordField(15), gbc);
        gbc.gridy = 4;
        leftPanel.add(new JLabel("Şifreyi Tekrarla:"), gbc);
        gbc.gridy = 5;
        leftPanel.add(new JPasswordField(15), gbc);
        gbc.gridy = 6;
        JButton registerButton = new JButton("Kayıt Ol");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        leftPanel.add(registerButton, gbc);

        // Sağ: Giriş bölümü
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Giriş Yap"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        rightPanel.add(new JLabel("Kullanıcı Adı:"), gbc);
        gbc.gridy = 1;
        rightPanel.add(new JTextField(15), gbc);
        gbc.gridy = 2;
        rightPanel.add(new JLabel("Şifre:"), gbc);
        gbc.gridy = 3;
        rightPanel.add(new JPasswordField(15), gbc);
        gbc.gridy = 4;
        JButton loginButton = new JButton("Giriş Yap");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(loginButton, gbc);

        // Panelleri ana panele ekle
        centerPanel.add(leftPanel);
        centerPanel.add(rightPanel);
        frame.add(centerPanel, BorderLayout.CENTER);

        frame.setVisible(true);
    }
}