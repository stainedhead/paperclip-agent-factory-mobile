package com.stainedhead.paperclipmobile.infrastructure.securestore

private val MAGIC = byteArrayOf('P'.code.toByte(), 'F'.code.toByte(), 'S'.code.toByte(), 1)
private const val PROTECTED_FLAG: Byte = 1
private const val UNPROTECTED_FLAG: Byte = 0
private const val HEADER_SIZE = 6
private const val MAX_IV = 32

/** One stored secret: the key mode it was sealed under, the IV and the ciphertext with its tag. No plaintext. */
internal class SecretRecord(
    val protection: Boolean,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    fun encode(): ByteArray =
        MAGIC + byteArrayOf(if (protection) PROTECTED_FLAG else UNPROTECTED_FLAG, iv.size.toByte()) + iv + ciphertext

    companion object {
        /** Null for anything that is not a whole record: wrong magic, short, an impossible IV length. */
        fun decode(bytes: ByteArray): SecretRecord? {
            if (bytes.size < HEADER_SIZE || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
            val flag = bytes[MAGIC.size]
            val ivSize = bytes[MAGIC.size + 1].toInt()
            val end = HEADER_SIZE + ivSize
            val wellFormed =
                ivSize in 1..MAX_IV && bytes.size > end && (flag == PROTECTED_FLAG || flag == UNPROTECTED_FLAG)
            return if (wellFormed) {
                SecretRecord(
                    flag == PROTECTED_FLAG,
                    bytes.copyOfRange(HEADER_SIZE, end),
                    bytes.copyOfRange(end, bytes.size),
                )
            } else {
                null
            }
        }
    }
}
