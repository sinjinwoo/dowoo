package io.dedyn.jwlabs.dowoo.settings.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiKeyCipherTest {

    private ApiKeyCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new ApiKeyCipher("test-encryption-secret-for-unit-tests");
    }

    @Test
    void decrypt_returnsOriginalPlainText() {
        String plainText = "AIzaSyTestKeyValue1234567890";

        String encrypted = cipher.encrypt(plainText);
        String decrypted = cipher.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }

    @Test
    void encrypt_sameInputTwice_producesDifferentCipherText() {
        // 매번 새 IV를 뽑아 붙이므로, 같은 평문이라도 암호문은 매번 달라야 한다(IV 재사용 방지 확인).
        String plainText = "same-plain-text";

        String first = cipher.encrypt(plainText);
        String second = cipher.encrypt(plainText);

        assertNotEquals(first, second);
        assertEquals(plainText, cipher.decrypt(first));
        assertEquals(plainText, cipher.decrypt(second));
    }

    @Test
    void decrypt_withDifferentSecret_fails() {
        String encrypted = cipher.encrypt("some-api-key");
        ApiKeyCipher otherCipher = new ApiKeyCipher("a-completely-different-secret");

        assertThrows(IllegalStateException.class, () -> otherCipher.decrypt(encrypted));
    }
}
