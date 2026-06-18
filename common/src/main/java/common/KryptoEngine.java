package common;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class KryptoEngine {
    private static final String ALGORITHM = "AES";
    private static final byte[] KEY_BYTES = "MeinSicheresPasswort123!".getBytes(); // 24 Bytes für AES-192

    public static Cipher getCipher(int mode) throws Exception {
        // Nutzt AES mit einem festen Pre-Shared Key für die Ende-zu-Ende-Schicht
        SecretKeySpec keySpec = new SecretKeySpec(KEY_BYTES, 0, 16, ALGORITHM); // 16 Bytes = AES-128
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(mode, keySpec);
        return cipher;
    }
}