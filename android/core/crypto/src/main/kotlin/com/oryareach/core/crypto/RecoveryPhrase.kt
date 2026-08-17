package com.oryareach.core.crypto

import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult
import org.bouncycastle.crypto.digests.SHA256Digest

/**
 * The workspace key rendered as a 24-word BIP-39 mnemonic.
 *
 * The phrase *is* the key, encoded — not a passphrase that unlocks a stored copy of it.
 * That means there is nothing extra to keep on the server, no KDF parameters that could
 * drift between app versions, and no wrapped blob to lose. A 32-byte key is exactly 256
 * bits of BIP-39 entropy, which is exactly 24 words.
 *
 * The BIP-39 checksum makes a mistyped or misremembered phrase fail loudly instead of
 * silently producing a key that decrypts nothing.
 *
 * This is the only way back into the workspace if every paired device is lost, so the setup
 * flow must make the user actually write it down.
 */
object RecoveryPhrase {

    const val WORD_COUNT = 24

    private const val BITS_PER_WORD = 11
    private const val ENTROPY_BITS = WorkspaceKey.SIZE_BYTES * 8
    private const val CHECKSUM_BITS = ENTROPY_BITS / 32

    private val wordList: List<String> by lazy {
        // Absolute (leading "/") so lookup is classpath-root-relative rather than relative to
        // this class's package — R8 flattens/repackages obfuscated classes in release builds,
        // which breaks a package-relative getResourceAsStream even though the packaged resource
        // itself is untouched and still sits at its original path.
        val stream = requireNotNull(RecoveryPhrase::class.java.getResourceAsStream(WORDLIST_RESOURCE)) {
            "missing BIP-39 word list at $WORDLIST_RESOURCE"
        }
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map(String::trim).filter(String::isNotEmpty).toList()
        }.also {
            check(it.size == 2048) { "BIP-39 word list must hold 2048 words, found ${it.size}" }
        }
    }

    private val wordIndex: Map<String, Int> by lazy {
        wordList.withIndex().associate { (index, word) -> word to index }
    }

    fun encode(key: WorkspaceKey): List<String> {
        val entropy = key.bytes()
        return try {
            val bits = StringBuilder(ENTROPY_BITS + CHECKSUM_BITS)
            entropy.forEach { bits.append(it.toBitString()) }
            bits.append(checksumByte(entropy).toBitString().take(CHECKSUM_BITS))

            (0 until WORD_COUNT).map { i ->
                val chunk = bits.substring(i * BITS_PER_WORD, (i + 1) * BITS_PER_WORD)
                wordList[chunk.toInt(radix = 2)]
            }
        } finally {
            entropy.fill(0)
        }
    }

    fun decode(phrase: String): AppResult<WorkspaceKey> = decode(normalize(phrase))

    fun decode(words: List<String>): AppResult<WorkspaceKey> {
        if (words.size != WORD_COUNT) return AppResult.Failure(AppError.Crypto.KeyUnavailable)

        val bits = StringBuilder(ENTROPY_BITS + CHECKSUM_BITS)
        for (word in words) {
            val index = wordIndex[word] ?: return AppResult.Failure(AppError.Crypto.KeyUnavailable)
            bits.append(index.toString(2).padStart(BITS_PER_WORD, '0'))
        }

        val entropy = ByteArray(WorkspaceKey.SIZE_BYTES) { i ->
            bits.substring(i * 8, (i + 1) * 8).toInt(radix = 2).toByte()
        }

        val expected = checksumByte(entropy).toBitString().take(CHECKSUM_BITS)
        if (bits.substring(ENTROPY_BITS) != expected) {
            entropy.fill(0)
            return AppResult.Failure(AppError.Crypto.KeyUnavailable)
        }

        return try {
            AppResult.Success(WorkspaceKey(entropy))
        } finally {
            entropy.fill(0)
        }
    }

    /** Lowercases and collapses whitespace so copy-paste and line breaks are tolerated. */
    fun normalize(phrase: String): List<String> =
        phrase.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)

    private fun checksumByte(entropy: ByteArray): Byte {
        val digest = SHA256Digest()
        digest.update(entropy, 0, entropy.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out[0]
    }

    private fun Byte.toBitString(): String =
        (toInt() and 0xFF).toString(2).padStart(8, '0')

    private const val WORDLIST_RESOURCE = "/com/oryareach/core/crypto/bip39-english.txt"
}
