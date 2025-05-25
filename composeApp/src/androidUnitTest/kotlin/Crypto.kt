import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class Crypto {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun hello() {
        val m = KeyGenerator.getInstance("HmacSHA256")


        val t = SecretKeyFactory.getInstance("HmacSHA256")


        val cipher = Cipher.getInstance("AES_256/GCM/NoPadding")
        val k = SecretKeySpec(ByteArray(32, { it.toByte() }), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, ByteArray(12, { (2 * it).toByte() })))
        assertEquals(cipher.blockSize, 16)
        val result = cipher.doFinal(ByteArray(32, { (0).toByte() }))
        System.err.println(result.size)
        System.err.println(result.toHexString().chunked(32).joinToString(" "))

        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, ByteArray(16, { if (it == 15) (2 * it + 1).toByte() else (2 * it).toByte() })))
        val result2 = cipher.doFinal(ByteArray(32, { (0).toByte() }))
        System.err.println(result2.size)
        System.err.println(result2.toHexString().chunked(32).joinToString(";"))

    }
}