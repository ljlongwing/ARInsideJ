package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

/** Maps a &lt;definition xsi:type="..."&gt; value to the concrete {@link Field} subtype the AR Java API expects - vocabulary confirmed against a real form export sample, see ArsXmlFileParser's javadoc. */
final class FieldXsiType {
    private FieldXsiType() {}

    static Field newInstance(String xsiType) {
        if (xsiType == null) return new CharacterField();
        return switch (xsiType) {
            case "character" -> new CharacterField();
            case "integer" -> new IntegerField();
            case "real" -> new RealField();
            case "decimal" -> new DecimalField();
            case "currency" -> new CurrencyField();
            case "date" -> new DateOnlyField();
            case "dateTime" -> new DateTimeField();
            case "diary" -> new DiaryField();
            case "enumeration" -> new SelectionField();
            case "attachment" -> new AttachmentField();
            case "attachmentPool" -> new AttachmentPoolField();
            case "timeOfDay", "timeOfDayValue" -> new TimeOnlyField();
            case "column" -> new ColumnField();
            case "control" -> new ControlField();
            case "displayField" -> new DisplayField();
            case "page" -> new PageField();
            case "pageHolder" -> new PageHolderField();
            case "table" -> new TableField();
            case "trim" -> new TrimField();
            case "viewField" -> new ViewField();
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized field xsi:type '" + xsiType + "', defaulting to character");
                yield new CharacterField();
            }
        };
    }
}
