package com.shouyun.theartofstockings;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Bounded, self-contained PNG data. Safe to validate on a headless dedicated server. */
public final class CustomStockingsTexture {
    public static final int MAX_BYTES = 320 * 1024;
    public static final int MAX_IMPORT_BYTES = 16 * 1024 * 1024;
    public static final int SIZE = 256;
    public static final int MAX_ENCODED_LENGTH = (MAX_BYTES + 2) / 3 * 4;
    // Byte arrays avoid NBT's 65,535-byte UTF string limit. Read old string components too.
    public static final com.mojang.serialization.Codec<String> CODEC = com.mojang.serialization.Codec.either(
            com.mojang.serialization.Codec.string(0,MAX_ENCODED_LENGTH),
            com.mojang.serialization.Codec.BYTE_BUFFER.validate(buffer -> buffer.remaining()<=MAX_BYTES
                    ? com.mojang.serialization.DataResult.success(buffer)
                    : com.mojang.serialization.DataResult.error(()->"Stockings PNG exceeds maximum size")))
            .xmap(either -> either.map(value->value,buffer->{byte[] bytes=new byte[buffer.remaining()];buffer.duplicate().get(bytes);return Base64.getEncoder().encodeToString(bytes);}),
                    value->com.mojang.datafixers.util.Either.right(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(value))));
    public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf,String> STREAM_CODEC =
            net.minecraft.network.codec.ByteBufCodecs.byteArray(MAX_BYTES).map(Base64.getEncoder()::encodeToString,Base64.getDecoder()::decode);
    private CustomStockingsTexture() {}

    public static final class ImportFailure extends java.io.IOException {
        public final String reason;
        public ImportFailure(String reason) { super(reason);this.reason=reason; }
    }
    public static String encode(java.awt.image.BufferedImage image) throws java.io.IOException {
        var output=new java.io.ByteArrayOutputStream();
        if(!ImageIO.write(image,"PNG",output) || output.size()>MAX_BYTES)throw new ImportFailure("too_large");
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }
    public static java.awt.image.BufferedImage decode(String encoded) throws java.io.IOException {
        var image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
        if(image==null)throw new ImportFailure("damaged");
        return image;
    }
    /** Strip editor metadata, convert indexed/16-bit PNG to RGBA8, and fit the standard UV atlas. */
    public static String normalize(byte[] bytes) throws java.io.IOException {
        if(bytes.length>MAX_IMPORT_BYTES)throw new ImportFailure("too_large");
        if(bytes.length<8 || java.nio.ByteBuffer.wrap(bytes).getLong()!=0x89504E470D0A1A0AL)throw new ImportFailure("not_png");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new ImportFailure("damaged");
            var reader=readers.next();
            try {
                reader.setInput(input);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width!=height)throw new ImportFailure("not_square");
                if(width<1 || width>4096)throw new ImportFailure("dimensions");
                var image=reader.read(0);
                var normalized=new java.awt.image.BufferedImage(SIZE,SIZE,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics=normalized.createGraphics();
                try {
                    graphics.setComposite(java.awt.AlphaComposite.Src);
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    graphics.drawImage(image,0,0,SIZE,SIZE,null);
                } finally { graphics.dispose(); }
                return encode(normalized);
            } finally { reader.dispose(); }
        } catch(ImportFailure failure) { throw failure; }
        catch(Exception invalid) { throw new ImportFailure("damaged"); }
    }

    public static boolean valid(String encoded) {
        if (encoded.isEmpty()) return true;
        if (encoded.length() > MAX_ENCODED_LENGTH) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 33 || bytes.length > MAX_BYTES) return false;
            var header = java.nio.ByteBuffer.wrap(bytes);
            if (header.getLong() != 0x89504E470D0A1A0AL || header.getInt() != 13 || header.getInt() != 0x49484452) return false;
            int width = header.getInt(), height = header.getInt();
            if (width != height || (width != 64 && width != 128 && width != 256)) return false;
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return false;
                var reader = readers.next();
                try {
                    if (!reader.getFormatName().equalsIgnoreCase("png")) return false;
                    reader.setInput(input);
                    var decoded = reader.read(0);
                    return decoded != null && decoded.getWidth() == width && decoded.getHeight() == height;
                } finally { reader.dispose(); }
            }
        } catch (Exception invalid) { return false; }
    }
}
