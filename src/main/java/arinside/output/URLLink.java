package arinside.output;

/** Java port of output/URLLink.{h,cpp}. */
public final class URLLink {

    public enum Target { SELF, PARENT, TOP, BLANK }

    private final String html;

    private URLLink(String html) {
        this.html = html;
    }

    public static URLLink to(String caption, PagePath page, ImageTag image, int rootLevel) {
        return to(caption, page, image, rootLevel, true, Target.SELF);
    }

    public static URLLink to(String caption, PagePath page, ImageTag image, int rootLevel, boolean validate, Target target) {
        StringBuilder sb = new StringBuilder();
        sb.append(image.toHtml());
        sb.append("<a href=\"").append(RootPath.of(rootLevel)).append(page.fullFileName()).append("\"");
        if (target != Target.SELF) {
            sb.append(" target=\"");
            sb.append(switch (target) {
                case PARENT -> "_parent";
                case TOP -> "_top";
                case BLANK -> "_blank";
                case SELF -> "";
            });
            sb.append("\"");
        }
        sb.append(">");
        sb.append(validate ? WebUtil.validate(caption) : caption);
        sb.append("</a>");
        return new URLLink(sb.toString());
    }

    public static URLLink to(String caption, PagePath page, ImageTag.Id imageId, int rootLevel) {
        return to(caption, page, new ImageTag(imageId, rootLevel), rootLevel);
    }

    public static URLLink to(String caption, PagePath page, int rootLevel) {
        return to(caption, page, new ImageTag(ImageTag.Id.NoImage, 0), rootLevel);
    }

    /** Ported from DirectURLLink - hand-built HTML that doesn't go through the naming/PagePath machinery. */
    public static URLLink direct(String html) {
        return new URLLink(html);
    }

    /** Just the href value CWebUtil::GetRelativeURL computes - used by the *ListJson blocks (object_list.js's row array), which need a bare URL string, not a full anchor tag. */
    public static String relativeUrl(int rootLevel, PagePath page) {
        return RootPath.of(rootLevel) + page.fullFileName();
    }

    public static URLLink createTopAnchor() {
        return direct("<a id=\"top\"></a>");
    }

    public static URLLink linkToTop(int rootLevel) {
        return direct(new ImageTag(ImageTag.Id.Up, rootLevel).toHtml() + "<a href=\"#top\">Top</a>");
    }

    public String toHtml() { return html; }

    @Override
    public String toString() { return html; }
}
