import javax.swing.*;
import java.awt.*;

public class UserScreen {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Kullanıcı Ekranı");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);
        frame.setLayout(new BorderLayout());

        // Hoşgeldiniz etiketi
        JLabel welcomeLabel = new JLabel("Hoşgeldiniz, {username}!", SwingConstants.CENTER);
        frame.add(welcomeLabel, BorderLayout.NORTH);

        // Ortada birleşen paneller için ana panel
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0)); // Aralarında boşluk bırakmak için hgap kullanıldı
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Kenarlardan iç boşluk bırakmak için

        // Sol: Oda oluşturma bölümü
        JPanel leftPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Bir oda oluştur"));
        JTextField roomName = new JTextField();
        roomName.setBorder(BorderFactory.createTitledBorder("Oda adı"));
        JPasswordField roomPassword = new JPasswordField();
        roomPassword.setBorder(BorderFactory.createTitledBorder("Oda şifresi"));
        JButton createRoomButton = new JButton("Oda Oluştur");
        leftPanel.add(roomName);
        leftPanel.add(roomPassword);
        leftPanel.add(createRoomButton);

        // Sağ: Odaya katılma bölümü
        JPanel rightPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Bir odaya katıl"));
        JTextField roomId = new JTextField();
        roomId.setBorder(BorderFactory.createTitledBorder("Oda ID"));
        JPasswordField joinRoomPassword = new JPasswordField();
        joinRoomPassword.setBorder(BorderFactory.createTitledBorder("Oda Şifre"));
        JButton joinRoomButton = new JButton("Odaya Katıl");
        rightPanel.add(roomId);
        rightPanel.add(joinRoomPassword);
        rightPanel.add(joinRoomButton);

        // Ortada birleşmesi için panelleri ana panele ekle
        centerPanel.add(leftPanel);
        centerPanel.add(rightPanel);
        frame.add(centerPanel, BorderLayout.CENTER);

        frame.setVisible(true);
    }
}