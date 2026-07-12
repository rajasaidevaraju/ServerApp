package helpers

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Fast animated GIF encoder built for speed:
 * - Uses a fixed 6x7x6 RGB palette with ordered dithering, so quantization is
 *   constant-time per pixel (no NeuQuant training pass over every frame).
 * - Each frame is quantized and LZW-compressed independently via [encodeFrame],
 *   so frames can be encoded on parallel threads; [assemble] stitches the
 *   pre-compressed frame blocks into the final GIF byte stream in order.
 */
object GifEncoder {

    private const val R_LEVELS = 6
    private const val G_LEVELS = 7
    private const val B_LEVELS = 6

    // step size between palette levels per channel, used to scale dither offsets
    private const val R_STEP = 255 / (R_LEVELS - 1)
    private const val G_STEP = 255 / (G_LEVELS - 1)
    private const val B_STEP = 255 / (B_LEVELS - 1)

    private val PALETTE = buildPalette()

    // 4x4 Bayer matrix for ordered dithering
    private val BAYER = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    )

    private fun buildPalette(): ByteArray {
        // 252 used entries, padded to a 256-entry global color table
        val table = ByteArray(256 * 3)
        var i = 0
        for (r in 0 until R_LEVELS) {
            for (g in 0 until G_LEVELS) {
                for (b in 0 until B_LEVELS) {
                    table[i * 3] = (r * R_STEP).toByte()
                    table[i * 3 + 1] = (g * G_STEP).toByte()
                    table[i * 3 + 2] = (b * B_STEP).toByte()
                    i++
                }
            }
        }
        return table
    }

    /**
     * Quantizes and LZW-compresses a single frame. Safe to call concurrently
     * from multiple threads. Returns the raw image data block (LZW minimum
     * code size + sub-blocks + terminator) ready for [assemble].
     */
    fun encodeFrame(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val indexed = ByteArray(argb.size)
        var p = 0
        for (y in 0 until height) {
            val bayerRow = (y and 3) shl 2
            for (x in 0 until width) {
                val pixel = argb[p]
                val dither = BAYER[bayerRow or (x and 3)] - 8
                val r = clamp255(((pixel shr 16) and 0xFF) + (dither * R_STEP) / 16)
                val g = clamp255(((pixel shr 8) and 0xFF) + (dither * G_STEP) / 16)
                val b = clamp255((pixel and 0xFF) + (dither * B_STEP) / 16)
                val rIdx = (r * R_LEVELS) shr 8
                val gIdx = (g * G_LEVELS) shr 8
                val bIdx = (b * B_LEVELS) shr 8
                indexed[p] = ((rIdx * G_LEVELS + gIdx) * B_LEVELS + bIdx).toByte()
                p++
            }
        }

        val out = ByteArrayOutputStream(argb.size / 4)
        out.write(8) // LZW minimum code size for a 256-color palette
        LzwEncoder(indexed).compress(9, out)
        out.write(0) // block terminator
        return out.toByteArray()
    }

    /** Stitches pre-compressed frame blocks into a complete looping GIF. */
    fun assemble(width: Int, height: Int, delayMs: Int, frames: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream(frames.sumOf { it.size } + 1024)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // logical screen descriptor with a 256-entry global color table
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF7)
        out.write(0) // background color index
        out.write(0) // pixel aspect ratio
        out.write(PALETTE)

        // Netscape application extension: loop forever
        out.write(0x21)
        out.write(0xFF)
        out.write(0x0B)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShort(out, 0)
        out.write(0)

        val delayCs = (delayMs / 10).coerceIn(2, 0xFFFF)
        for (frame in frames) {
            // graphic control extension: keep previous frame, no transparency
            out.write(0x21)
            out.write(0xF9)
            out.write(4)
            out.write(0x04)
            writeShort(out, delayCs)
            out.write(0) // transparent color index (unused)
            out.write(0)

            // image descriptor, full logical screen, no local color table
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, width)
            writeShort(out, height)
            out.write(0)

            out.write(frame)
        }

        out.write(0x3B) // trailer
        return out.toByteArray()
    }

    private fun clamp255(value: Int): Int = when {
        value < 0 -> 0
        value > 255 -> 255
        else -> value
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    /** Standard GIF-LZW compressor (port of the classic ppmtogif algorithm). */
    private class LzwEncoder(private val pixels: ByteArray) {

        private val hsize = 5003
        private val maxbits = 12
        private val maxmaxcode = 1 shl maxbits
        private val htab = IntArray(hsize)
        private val codetab = IntArray(hsize)
        private val accum = ByteArray(256)
        private var aCount = 0
        private var curAccum = 0
        private var curBits = 0
        private var nBits = 0
        private var maxcode = 0
        private var freeEnt = 0
        private var clearFlg = false
        private var initBits = 0
        private var clearCode = 0
        private var eofCode = 0
        private var curPixel = 0

        private val masks = intArrayOf(
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F,
            0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
        )

        fun compress(initBits: Int, outs: OutputStream) {
            this.initBits = initBits
            clearFlg = false
            nBits = initBits
            maxcode = maxCode(nBits)
            clearCode = 1 shl (initBits - 1)
            eofCode = clearCode + 1
            freeEnt = clearCode + 2
            aCount = 0
            curPixel = 0

            var ent = nextPixel()

            var hshift = 0
            var fcode = hsize
            while (fcode < 65536) {
                hshift++
                fcode *= 2
            }
            hshift = 8 - hshift

            resetHash()
            output(clearCode, outs)

            var c = nextPixel()
            while (c != -1) {
                fcode = (c shl maxbits) + ent
                var i = (c shl hshift) xor ent
                var found = false
                if (htab[i] == fcode) {
                    ent = codetab[i]
                    found = true
                } else if (htab[i] >= 0) {
                    var disp = hsize - i
                    if (i == 0) disp = 1
                    do {
                        i -= disp
                        if (i < 0) i += hsize
                        if (htab[i] == fcode) {
                            ent = codetab[i]
                            found = true
                            break
                        }
                    } while (htab[i] >= 0)
                }
                if (!found) {
                    output(ent, outs)
                    ent = c
                    if (freeEnt < maxmaxcode) {
                        codetab[i] = freeEnt++
                        htab[i] = fcode
                    } else {
                        resetHash()
                        freeEnt = clearCode + 2
                        clearFlg = true
                        output(clearCode, outs)
                    }
                }
                c = nextPixel()
            }
            output(ent, outs)
            output(eofCode, outs)
        }

        private fun nextPixel(): Int {
            if (curPixel >= pixels.size) return -1
            return pixels[curPixel++].toInt() and 0xFF
        }

        private fun maxCode(bits: Int): Int = (1 shl bits) - 1

        private fun resetHash() {
            htab.fill(-1)
        }

        private fun output(code: Int, outs: OutputStream) {
            curAccum = curAccum and masks[curBits]
            curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
            curBits += nBits

            while (curBits >= 8) {
                charOut((curAccum and 0xFF).toByte(), outs)
                curAccum = curAccum ushr 8
                curBits -= 8
            }

            if (freeEnt > maxcode || clearFlg) {
                if (clearFlg) {
                    nBits = initBits
                    maxcode = maxCode(nBits)
                    clearFlg = false
                } else {
                    nBits++
                    maxcode = if (nBits == maxbits) maxmaxcode else maxCode(nBits)
                }
            }

            if (code == eofCode) {
                while (curBits > 0) {
                    charOut((curAccum and 0xFF).toByte(), outs)
                    curAccum = curAccum ushr 8
                    curBits -= 8
                }
                flush(outs)
            }
        }

        private fun charOut(c: Byte, outs: OutputStream) {
            accum[aCount++] = c
            if (aCount >= 254) flush(outs)
        }

        private fun flush(outs: OutputStream) {
            if (aCount > 0) {
                outs.write(aCount)
                outs.write(accum, 0, aCount)
                aCount = 0
            }
        }
    }
}
