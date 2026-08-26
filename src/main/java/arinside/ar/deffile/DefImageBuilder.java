package arinside.ar.deffile;

import com.bmc.arsys.api.Image;
import com.bmc.arsys.api.ImageData;
import com.bmc.arsys.api.ObjectPropertyMap;

import java.nio.charset.Charset;

/**
 * Java port of {@code ImageObjectParseEventHandler}, targeting {@code com.bmc.arsys.api.
 * Image} directly - confirmed via {@code javap} that the client type has no {@code setHelpText}/
 * {@code setDiary} (matching {@code arinside.ar.xmlfile.ImageXmlBuilder}'s identical scope, which
 * likewise never sets those two fields), so {@code HELP}/{@code CHANGE_DIARY} tags are recognized
 * but have nothing to attach to and are dropped, same as several other object types' documented
 * client-API gaps elsewhere in this port.
 */
final class DefImageBuilder {
    private String name, type, owner, lastChangedBy, description, checkSum, imageContent;
    private ObjectPropertyMap properties;
    private long timestamp;

    Image build() {
        if (name == null || name.isEmpty()) return null;
        Image image = new Image();
        image.setName(name);
        image.setType(type);
        image.setOwner(owner);
        image.setLastChangedBy(lastChangedBy);
        image.setDescription(description);
        image.setCheckSum(checkSum);
        if (properties != null) image.setProperties(properties);
        if (imageContent != null) image.setImageData(new ImageData(decodeHexContent(imageContent)));
        arinside.ar.ObjectTimestamp.set(image, timestamp);
        return image;
    }

    void item(DefItemLabel item, String raw, Charset charset) {
        switch (item) {
            case NAME -> name = raw;
            case IMAGE_TYPE -> type = raw;
            case OWNER -> owner = raw;
            case LAST_CHANGED -> lastChangedBy = raw;
            case TIMESTAMP -> timestamp = ParseUtil.longValue(raw);
            case DESCRIPTION -> description = raw;
            case IMAGE_CHECKSUM -> checkSum = raw;
            case OBJECT_PROP -> properties = DefPropertyDecoder.decode(raw, charset, properties != null ? properties : new ObjectPropertyMap());
            case IMAGE_CONTENT -> imageContent = raw;
            default -> { /* IMAGE_SIZE/HELP/CHANGE_DIARY - no client setter or not rendered */ }
        }
    }

    /**
     * Java port of {@code com.bmc.arsys.domain.utils.ConversionUtil.getBytesForBinaryContentInHexFormat}
     * (read directly): a byte is either the {@code '!'} sentinel for a
     * literal 0x00, the {@code '~'} sentinel for a literal 0xFF, a hex-digit-adjacent pass-through
     * literal (everything in the ranges 37-47 / 58-96 / 103-125 - deliberately excludes plain digits
     * 0-9 and lowercase hex letters a-f, which only ever appear as the second half of a hex pair), or
     * else consumed together with the following byte as one 2-hex-digit byte value.
     */
    private static byte[] decodeHexContent(String s) {
        byte[] in = s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] out = new byte[in.length];
        int index = 0;
        for (int i = 0; i < in.length; i++) {
            int b = in[i] & 0xFF;
            if (b == 33) {
                out[index] = 0;
            } else if ((b < 37 || b > 47) && (b < 58 || b > 96) && (b < 103 || b > 125)) {
                if (b == 126) {
                    out[index] = (byte) 255;
                } else if (i + 1 < in.length) {
                    String hex = new String(in, i, 2, java.nio.charset.StandardCharsets.ISO_8859_1);
                    try {
                        out[index] = (byte) Integer.parseInt(hex, 16);
                    } catch (NumberFormatException e) {
                        out[index] = (byte) b;
                    }
                    i++;
                } else {
                    out[index] = (byte) b;
                }
            } else {
                out[index] = (byte) b;
            }
            index++;
        }
        byte[] result = new byte[index];
        System.arraycopy(out, 0, result, 0, index);
        return result;
    }
}
