package arinside.output;

/**
 * Java equivalent of IFileStructure's resolved result. In the C++, CPageParams + a
 * IFileNamingFactory strategy (Default vs ObjectName) dynamically resolve a (PAGE_*, params)
 * tuple to one of ~150 tiny IFileStructure subclasses via a giant switch statement.
 *
 * We only ever ship the ObjectName naming strategy (OldNaming=TRUE is a legacy pre-3.0.1
 * compatibility flag, off by default, not ported), so instead of replicating that dispatch
 * machinery we just have {@link Naming} return one of these directly per page type, with full
 * compile-time type checking on call sites instead of an untyped int page id.
 */
public record PagePath(String path, String fileName, int rootLevel) {

    /** Path + filename + extension, e.g. "schema/My_Form/index.htm" (matches IFileStructure::GetFullFileName). */
    public String fullFileName() {
        String doc = WebUtil.docName(fileName);
        return path.isEmpty() ? doc : path + "/" + doc;
    }
}
