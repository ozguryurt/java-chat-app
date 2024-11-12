import javax.swing.*;
import java.awt.*;

public class ChatRoomScreen {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Sohbet Odası");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);
        frame.setLayout(new BorderLayout());

        // Sağ: Katılımcı listesi
        JPanel participantsPanel = new JPanel(new BorderLayout());
        participantsPanel.setBorder(BorderFactory.createTitledBorder("Katılımcılar"));
        JLabel roomInfoLabel = new JLabel("Oda numarası: 13");
        JButton leaveRoomButton = new JButton("Odadan Ayrıl");
        JList<String> participantsList = new JList<>(new String[]{"ozgur", "bayhan"});
        participantsPanel.add(roomInfoLabel, BorderLayout.NORTH);
        participantsPanel.add(new JScrollPane(participantsList), BorderLayout.CENTER);
        participantsPanel.add(leaveRoomButton, BorderLayout.SOUTH);
        frame.add(participantsPanel, BorderLayout.EAST);

        // Sol: Sohbet alanı
        JPanel chatPanel = new JPanel(new BorderLayout());
        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField messageInput = new JTextField();
        JButton sendButton = new JButton("Gönder");
        inputPanel.add(messageInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        frame.add(chatPanel, BorderLayout.CENTER);

        frame.setVisible(true);
    }
}