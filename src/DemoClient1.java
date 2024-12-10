public class DemoClient1 {
    public static void main(String[] args) {
        // invokeLater metodu, bir Runnable nesnesindeki kodu Swing'in Event Dispatch Thread (EDT) üzerinde çalıştırır.
        // Swing GUI öğelerinin güvenli bir şekilde işlenmesi için kodun EDT üzerinde çalıştırılması gerekir. EDT, kullanıcı arayüzü olaylarını işleyen özel bir iş parçacığıdır.
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

/*
Event Dispatch Thread (EDT), Java'daki Swing uygulamaları için kullanıcı arayüzü olaylarını işleyen ana iş parçacığıdır.
invokeLater, verilen işlemi EDT'ye ekler ve böylece GUI ile ilgili işlemlerin güvenli bir şekilde bu iş parçacığı üzerinde çalışmasını sağlar.
Bu yaklaşım, çoklu iş parçacığı ortamında GUI'nin kararlı ve tutarlı bir şekilde çalışmasını temin eder.

invokeLater, Swing GUI bileşenlerinin oluşturulması ve manipülasyonu yalnızca EDT üzerinde güvenli bir şekilde yapılabilir.
invokeLater, GUI işlemlerinin diğer arka plan görevlerinden ayrılmasını sağlar. Böylece ana iş parçacığı bloke olmaz ve program daha yanıt verir olur.

Kullanıcının etkileşimlerinden (örneğin tıklamalar, yazmalar) ve programın içindeki GUI işlemlerinden gelen tüm olaylar EventQueue tarafından sıraya alınır.
EventQueue, sıraya alınmış olayları birer birer işler ve bu olayları Event Dispatch Thread (EDT) üzerinde çalıştırır.

SwingUtilities.invokeLater: Kullanıcı arayüzü işlemlerini EDT'de (ana iş parçacığında) çalıştırır.
SwingWorker, uzun süren veya arka planda çalıştırılması gereken işlemleri Swing uygulamasının ana iş parçacığını (Event Dispatch Thread - EDT) engellemeden çalıştırmak için kullanılır.
*/
