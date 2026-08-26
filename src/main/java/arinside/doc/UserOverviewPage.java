package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.IdentityRepository;
import arinside.ar.UserRecord;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of CDocMain::UserList + output/UserTable.{h,cpp}, plus the letter-filtered
 * overview/users_<letter>.htm pages (CDocMain::UserList's searchChar loop, driven from
 * ARInside.cpp's DoWork). Users are the one object type that splits into separate per-letter
 * overview pages (overview/users_a.htm, _b.htm, ... - only for letters/digits that occur among
 * real user names, plus overview/users_other.htm for anything outside a-z0-9). The
 * `user/index.htm` full listing this port already builds stays a single page (matches
 * DefaultFileNamingStrategy's behavior, which this port otherwise follows); only the overview/
 * mirror gets split, matching ObjectNameFileNamingStrategy's behavior.
 */
public final class UserOverviewPage {
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final IdentityRepository repo;
    private final AppConfig appConfig;

    public UserOverviewPage(IdentityRepository repo, AppConfig appConfig) {
        this.repo = repo;
        this.appConfig = appConfig;
    }

    public List<UserRecord> render() throws ARException {
        List<UserRecord> users = repo.listUsers();

        PagePath page = Naming.userOverview();
        WebPage webPage = new WebPage(page.fileName(), "User List", page.rootLevel(), appConfig);
        webPage.addContent(users.size() + " Users\n");
        webPage.addContent(renderTable(users, page.rootLevel()));
        webPage.saveInFolder(page.path());

        renderLetterOverview(users);

        return users;
    }

    private String renderTable(List<UserRecord> users, int rootLevel) {
        Table tbl = new Table("userList", "TblObjectList");
        tbl.addColumn(25, "Login Name");
        tbl.addColumn(25, "Full Name");
        tbl.addColumn(20, "Email");
        tbl.addColumn(15, "License Type");
        tbl.addColumn(15, "Modified");

        for (UserRecord u : users) {
            TableRow row = new TableRow();
            row.addCell(URLLink.to(u.loginName, Naming.userDetail(u.loginName), ImageTag.Id.User, rootLevel).toHtml());
            row.addCell(u.fullName == null ? "" : WebUtil.validate(u.fullName));
            row.addCell(u.email == null ? "" : u.email);
            row.addCell(AREnumLabels.licenseType(u.licenseType));
            row.addCell(u.modified == null ? "" : DateTimeFormat.toHtmlString(u.modified.getValue()));
            tbl.addRow(row);
        }
        if (!users.isEmpty()) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    /** Buckets users by lowercased first non-space char of their login name; non a-z0-9 (or empty) falls into "other". Matches CARObject::GetNameFirstChar/GetFirstCharIndex. */
    private void renderLetterOverview(List<UserRecord> allUsers) {
        Map<Character, List<UserRecord>> byLetter = new LinkedHashMap<>();
        for (char c : LETTERS.toCharArray()) byLetter.put(c, new ArrayList<>());
        List<UserRecord> other = new ArrayList<>();

        for (UserRecord u : allUsers) {
            Character letter = firstStandardChar(u.loginName);
            if (letter != null) byLetter.get(letter).add(u);
            else other.add(u);
        }

        PagePath overviewAll = Naming.overviewUsers();
        String navBar = navBar(byLetter, !other.isEmpty(), null);
        WebPage allPage = new WebPage(overviewAll.fileName(), "User List", overviewAll.rootLevel(), appConfig);
        allPage.addContent(allUsers.size() + " Users\n");
        allPage.addContent(navBar);
        allPage.addContent(renderTable(allUsers, overviewAll.rootLevel()));
        allPage.saveInFolder(overviewAll.path());

        for (Map.Entry<Character, List<UserRecord>> entry : byLetter.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            PagePath letterPage = Naming.overviewUsersLetter(entry.getKey());
            WebPage webPage = new WebPage(letterPage.fileName(), "User List", letterPage.rootLevel(), appConfig);
            webPage.addContent(entry.getValue().size() + " Users\n");
            webPage.addContent(navBar(byLetter, !other.isEmpty(), entry.getKey()));
            webPage.addContent(renderTable(entry.getValue(), letterPage.rootLevel()));
            webPage.saveInFolder(letterPage.path());
        }

        if (!other.isEmpty()) {
            PagePath otherPage = Naming.overviewUsersOther();
            WebPage webPage = new WebPage(otherPage.fileName(), "User List", otherPage.rootLevel(), appConfig);
            webPage.addContent(other.size() + " Users\n");
            webPage.addContent(navBar(byLetter, true, '#'));
            webPage.addContent(renderTable(other, otherPage.rootLevel()));
            webPage.saveInFolder(otherPage.path());
        }
    }

    /** Ported from CDocMain::ShortMenu - a row of a-z0-9# links, disabled (plain text) when that bucket is empty, plain text (no link) for the current page. */
    private String navBar(Map<Character, List<UserRecord>> byLetter, boolean hasOther, Character current) {
        int rootLevel = Naming.overviewUsers().rootLevel();
        StringBuilder sb = new StringBuilder();
        sb.append("<table id=\"formLetterFilter\"><tr>\n");
        for (char c : LETTERS.toCharArray()) {
            sb.append(letterCell(c, byLetter.get(c).size() > 0, current != null && current == c, rootLevel));
        }
        sb.append(letterCell('#', hasOther, current != null && current == '#', rootLevel));
        sb.append("</tr></table>\n");
        return sb.toString();
    }

    private String letterCell(char c, boolean enabled, boolean isCurrent, int rootLevel) {
        if (isCurrent) {
            return "<td>" + c + "</td>\n";
        }
        PagePath target = c == '#' ? Naming.overviewUsersOther() : Naming.overviewUsersLetter(c);
        if (enabled) {
            return "<td>" + URLLink.to(String.valueOf(c), target, rootLevel).toHtml() + "</td>\n";
        }
        return "<td class=\"disabledLetter\">" + c + "</td>\n";
    }

    private Character firstStandardChar(String name) {
        if (name == null) return null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == ' ') continue;
            char lower = Character.toLowerCase(c);
            if ((lower >= 'a' && lower <= 'z') || (lower >= '0' && lower <= '9')) return lower;
            return null;
        }
        return null;
    }
}
