package democlient1;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class DemoClient1 {

    private static JTextArea chatArea;
    private static JTextField messageField;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String lastSentMessage = ""; // En son gönderilen mesaj

    public static void main(String[] args) {
        // Sunucu bağlantı bilgileri
        String serverAddress = "127.0.0.1"; // localhost ip'si
        int serverPort = 12345;

        // GUI bileşenlerini
        JFrame frame = new JFrame("Chat Client (Oda 3)");
        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        messageField = new JTextField(40);
        JButton sendButton = new JButton("Gönder");

        // Mesaj gönderme butonu
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Enter tuşuna basıldığında mesaj gönderme
        messageField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Pencere düzeni
        JPanel panel = new JPanel();
        panel.add(new JScrollPane(chatArea));
        panel.add(messageField);
        panel.add(sendButton);

        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Sunucuya bağlanma ve mesajları okuma
        try {
            Socket socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Test amaçlı oda numarası 3 olsun
            out.println("3");

            // Sunucudan gelen mesajları dinleyen thread
            Thread readerThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        // Eğer sunucudan gelen mesaj son gönderilen mesaj değilse, sadece diğerlerinden gelmiş gibi göster
                        if (!serverMessage.equals("Oda 3: " + lastSentMessage)) {
                            chatArea.append(serverMessage + "\n");
                        }
                    }
                } catch (IOException e) {
                    chatArea.append("Sunucu ile bağlantı kesildi.\n");
                }
            });
            readerThread.start();
        } catch (IOException e) {
            chatArea.append("Sunucuya bağlanılamadı.\n");
        }
    }

    // Mesajı sunucuya gönderme
    private static void sendMessage() {
        String message = messageField.getText();
        if (!message.trim().isEmpty()) {
            lastSentMessage = message; // Son gönderilen mesajı sakla
            out.println(message);  // Sunucuya mesaj gönder
            chatArea.append("Sen: " + message + "\n");  // Kullanıcının kendi gönderdiği mesajı "Sen" olarak göster
            messageField.setText("");  // Mesaj alanını temizle
        }
    }
    
}