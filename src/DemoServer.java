package demoserver;

import java.io.*;
import java.net.*;
import java.util.*;

public class DemoServer {
    
    // Oda numaralarına göre istemcilerin tutulduğu Map
    private static Map<Integer, List<ClientHandler>> rooms = new HashMap<>();

    public static void main(String[] args) {
        final int port = 12345;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server başlatıldı. Port " + port + " üzerinde dinleniyor...");

            while (true) {
                Socket clientSocket = serverSocket.accept(); // Yeni bağlantı 
                new Thread(new ClientHandler(clientSocket)).start(); // Kullanıcıya hizmet veren thread
            }
        } catch (IOException e) {
            System.err.println("Port dinlenirken bir hata oluştu: " + e.getMessage());
        }
    }

    // Her kullanıcı için thread
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private int roomId;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // Oda numarası seçim
                out.println("Bir odaya bağlanmak için oda numarası girin:");
                roomId = Integer.parseInt(in.readLine());

                // Odaya kullanıcı ekle
                synchronized (rooms) {
                    rooms.putIfAbsent(roomId, new ArrayList<>());
                    rooms.get(roomId).add(this);
                }

                // Bağlanan kullanıcıya mesaj gönder
                out.println("Oda " + roomId + " numarasına bağlandınız.");

                // Mesajları okuma ve broadcast
                String message;
                while ((message = in.readLine()) != null) {
                    broadcastMessage("Oda " + roomId + ": " + message, this);
                }

            } catch (IOException e) {
                System.err.println("İstemci ile bağlantı hatası: " + e.getMessage());
            } finally {
                // Kullanıcının bağlantısı kesildiğinde odadan çıksın
                leaveRoom();
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Soket kapatma hatası: " + e.getMessage());
                }
            }
        }

        // Oda içindeki tüm kullanıcılara mesaj gönderme (gönderen kullanıcı hariç)
        private void broadcastMessage(String message, ClientHandler sender) {
            synchronized (rooms) {
                for (ClientHandler client : rooms.get(roomId)) {
                    if (client != sender) { // Gönderen hariç!!!
                        client.out.println(message);
                    }
                }
            }
        }

        // Kullanıcı ayrılma durumu
        private void leaveRoom() {
            synchronized (rooms) {
                rooms.get(roomId).remove(this);
                if (rooms.get(roomId).isEmpty()) {
                    rooms.remove(roomId);
                }
            }
        }
    }
    
}