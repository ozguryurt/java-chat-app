
public class DemoClient1 {

    public static void main(String[] args) {
        // MainScreen'i başlatıyoruz
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    // MainScreen ekranını açıyoruz
                    new MainScreen();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}