public class UserSession {
    private static UserSession instance;
    private String username;
    private int userId;
    private int roomId;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    
    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    // User session'ı sıfırlayan metot
    public void clearSession() {
        this.username = null;
        this.userId = 0;
        this.roomId = 0;
    }
}