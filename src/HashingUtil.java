import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingUtil {
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256"); // SHA-256 algoritmasını temsil eden bir MessageDigest nesnesi oluşturur.
            byte[] hash = md.digest(password.getBytes()); // String tipindeki şifreyi byte dizisine çevirir.
            // md.digest() ise bu byte dizisini hash'leyerek SHA-256 algoritmasına göre bir byte dizisi oluşturur.
            StringBuilder hexString = new StringBuilder(); // SHA-256 algoritmasından gelen byte dizisi, okunamaz, dönen her bir byte, 16'lık taban (hexadecimal) formatına çevrilir:
            for (byte b : hash) {
                // bit seviyesinde bir AND işlemi yapılır, 
                String hex = Integer.toHexString(0xff & b); // 0xff & b: Byte'in negatif olmamasını sağlamak için maskelenir. (integerı hexadecimal stringe çevir)
                if (hex.length() == 1) hexString.append('0'); // Integer.toHexString(...): Sayıyı hexadecimal string'e dönüştürür.
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashleme işlemi başarısız: ", e);
        }
    }
}