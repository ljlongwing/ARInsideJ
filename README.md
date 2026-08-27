# ARInsideJ

ARInsideJ is a Java port of [ARInside](https://github.com/gabeluci/ARInside), using the BMC AR
System **Java** API (`com.bmc.arsys.api`) instead of the original C++ tool's C API. It generates
the same kind of static HTML documentation site (forms/fields, active links, filters, escalations,
menus, containers, users/groups/roles, images, plus the cross-reference graph between all of them)
from either a live AR System server connection or an offline AR System Administrator `.xml`/`.def`
export.

This port exists because the C API ARInside's C++ build depends on hasn't been updated by BMC past
9.1.0, and several bulk calls fail against modern (21+) AR servers, requiring workarounds. The Java
API is still actively maintained and doesn't have that problem.

## Download

Don't want to build it yourself? Grab a build from the
[latest release](https://github.com/ljlongwing/ARInsideJ/releases/latest) - the proprietary
`arapi`/`arlogger` jars (see "Requirements" below) are already bundled in, so it runs standalone
with no extra setup. Two assets:

* **`arinsidej-<version>.zip`** - the fat jar plus the `run-arinsidej.bat`/`run-arinsidej.sh`
  launcher scripts and a sample `settings.ini` to edit. Unzip and run the launcher for your OS.
* **`arinsidej.jar`** - just the fat jar, for slotting into an existing setup.

```
java -jar arinsidej.jar -i settings.ini -l Demo -p pass -s myserver
```

The "Requirements" and "Building" sections below are only needed if you want to build from source
instead.

## Requirements

* **JDK 17+** to build. Verified against JDK 25.
* **Maven** to build (`mvn`).
* **The AR System Java API jar** (`arapi*.jar`) - proprietary, not on any public Maven repository,
  same situation as the C++ tool's `arapi` C SDK. You need your own copy (from a BMC AR System
  install or a BMC download) and must install it into your local Maven repo before building:

  ```
  mvn install:install-file -Dfile="<path to arapi*.jar>" ^
    -DgroupId=com.bmc.arsys -DartifactId=arapi -Dversion=23.3.002 -Dpackaging=jar
  ```

* **`com.bmc.arsys.logger.ARLogger`** - a runtime dependency of `ARServerUser`'s static
  initializer that is *not* bundled inside `arapi*.jar`. Source it from a local AR System install,
  e.g. `<ARSystem install>\diserver\data-integration\lib\arlogger*.jar`, and install it the same way:

  ```
  mvn install:install-file -Dfile="<path to arlogger*.jar>" ^
    -DgroupId=com.bmc.arsys -DartifactId=arlogger -Dversion=23.3.000 -Dpackaging=jar
  ```

  Adjust the `-Dversion` values in both commands (and `pom.xml`'s `<dependency>` versions, if they
  differ) to match the jars you actually have.

* An account with administrator rights on the target AR System server - otherwise the
  documentation will be incomplete, same requirement as the C++ tool.

## Building

```
mvn -o package
```

This produces two jars under `target/`:

* **`arinsidej.jar`** - a self-contained "fat" jar with every dependency (including `arapi`/
  `arlogger`) merged in. This is the one you actually run - no classpath setup needed.
* `original-arinsidej.jar` - the plain, unshaded jar (an artifact of the build, not meant to be run
  directly).

It also packs `target/arinsidej-<version>.zip`, the release bundle (fat jar + launcher scripts +
sample `settings.ini` + README/LICENSE) - see "Download" above for what's in it.

`mvn -o compile` (without `package`) is enough if you're just iterating on source and running via
an IDE or a manually-assembled classpath, but `package` is what you want for a distributable build.

## Getting Started

```
java -jar arinsidej.jar -i settings.ini -l Demo -p pass -s localhost
```

This connects to `localhost` as `Demo`, and documents every form/field/workflow object it finds.
`-i` points at your configuration file (see below); `-l`/`-p`/`-s` are the login, password, and
server, and can also be set inside the ini file itself instead of on the command line. Depending on
server size this can take anywhere from a couple of minutes to the better part of an hour; the
`ReadConcurrency`/`WriteConcurrency` ini settings (see below) are the main lever for run time on a
large server. Once it finishes, open `index.htm` in the `TargetFolder` you configured.

## Command Line Arguments

| Argument | Type | Description |
|----------|------|-------------|
| -i, --ini \<string\> | required | Configuration file name (default `settings.ini`) |
| -s, --server \<string\> | optional | AR System server name to connect to |
| -l, --login \<string\> | optional | Login name |
| -p, --pwd \<string\> | optional | Password |
| -t, --tcp \<int\> | optional | TCP port. `0`/unset uses the portmapper. |
| -r, --rpc \<int\> | optional | RPC program number - a private-queue port, or `0` (default) for the fast/list server queue. |
| -o, --output \<string\> | optional | Output directory, overrides `TargetFolder` from the ini file. |
| --slow | optional | Disables fast/bulk object loading (one-at-a-time `getX(name)` calls instead of `getListXObjects()`). Needed for some server versions - see [Known Limitations](#known-limitations). |
| --scope \<string\> | optional | Only document this one form plus its directly-related workflow tree, instead of the whole server - see [Scoped Export](#scoped-export---scope) below. |
| -v, --verbose | optional | Verbose output. |
| -h, --help | optional | Show command line usage. |

Argument order doesn't matter. Quote values containing spaces (`-i "my settings.ini"`).

## Configuration File (`settings.ini`)

A flat `key = value` text file (`#` starts a comment), read via `java.util.Properties`. Any setting
can also be set from the command line where a corresponding flag exists (see the table above);
command-line values take precedence over the ini file.

#### Server

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| ServerName | String | *(empty)* | AR System server to connect to. |
| Username | String | *(empty)* | Login name. |
| Password | String | *(empty)* | Password. |
| TCPPort | Integer | `0` | See `-t` above. |
| RPCPort | Integer | `0` | See `-r` above. |

#### Application

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| TargetFolder | String | *(empty)* | Output directory. Overridable with `-o`. |
| FileMode | Boolean | `FALSE` | `TRUE` to load object data from `ObjListXML` instead of connecting live - see [File Mode](#file-mode--offline-xml-export) below. |
| ObjListXML | String | *(empty)* | Path to an AR System Administrator export, used when `FileMode=TRUE`. |
| BlackList | String | *(empty)* | Name of an AR System Packing List whose referenced objects are excluded from the documentation. |
| Scope | String | *(empty)* | Same as `--scope` above - ini-file equivalent. |
| DeleteExistingFiles | Boolean | `FALSE` | Delete everything already in `TargetFolder` before writing. |
| GZCompression | Boolean | `FALSE` | Write gzip-compressed `.htm.gz` pages plus a generated `.htaccess`, for serving directly from Apache. |
| OverlayMode | Boolean | `TRUE` | Document overlay-feature details (server 7.6.04+). |
| ReadConcurrency | Integer | `8` | Max concurrent AR System connections used to fetch objects. `1` reproduces old fully-sequential behavior. |
| WriteConcurrency | Integer | `16` | Max concurrent worker threads rendering/writing local HTML pages. |

#### Data Retrieval

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| UserForm / GroupForm / RoleForm | String | `User` / `Group` / `Roles` | Names of the reserved admin forms queried for user/group/role data. |
| UserQuery / GroupQuery / RoleQuery | String | `1=1` | Qualification restricting which rows are returned. |
| MaxRetrieve | Integer | `0` | Cap on rows retrieved per query; `0` = no cap. |

#### Layout

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| CompanyName | String | *(empty)* | Displayed in the generated documentation. |
| CompanyUrl | String | *(empty)* | Link target for `CompanyName`. |
| RunNotes | String | *(empty)* | Free-text note shown on the index page. |

#### Settings accepted but not currently wired to behavior

For compatibility with existing C++ `settings.ini` files, these keys are parsed without error but
have **no effect** in this port yet: `LoadServerInfoList`, `LoadUserList`/`LoadGroupList`/
`LoadRoleList` (identity data always loads), `Utf-8`, `CompactFolder`, `OldNaming` (this port only
ever uses the newer object-name-based file naming), `APITimeout`. If you're carrying over a C++
`settings.ini`, leaving these in place is harmless - just don't expect them to change anything yet.

### Example

```ini
ServerName = myserver
Username = Demo
Password = pass
TargetFolder = D:\arinside-doc\myserver
ReadConcurrency = 8
WriteConcurrency = 16
CompanyName = My Company
CompanyUrl = http://example.com
```

## Scoped Export (`--scope`)

For troubleshooting one form's workflow, or fast iteration during development, `--scope "<form
name>"` (or `Scope=` in the ini file) limits the documentation to that one form plus everything
directly related to it:

* the form itself (fields, VUIs, all its normal tabs),
* every Active Link / Filter / Escalation that executes directly on it,
* every guide / application / packing list / filter guide / webservice container those (or the
  form itself) belong to,
* every menu the form's own fields reference.

This is a "hop-1" tree, not a transitive one - workflow that pushes fields into some *other* form
isn't followed into that other form. Everything outside the tree still gets a page (so no link
inside the scoped output ever 404s), but it's a small placeholder ("excluded from this scoped
export") instead of full documentation. All the usual scan/indexing still runs a full pass over the
whole server first - the scope only changes which per-object pages get fully rendered vs. stubbed,
so it doesn't speed up server-side data collection, just the local rendering/writing phase.

```
java -jar arinsidej.jar -i settings.ini -s myserver -l Demo -p pass --scope "HPD:Help Desk"
```

## File Mode / Offline `.xml` and `.def` Export

Two AR System export formats can be pointed at via `FileMode=TRUE` + `ObjListXML=<path>`, both
parsed entirely offline - no server connection is opened at all, and no `Server`/`Username`/
`Password` settings are needed, since the object data comes from the file. The format is sniffed
automatically from the file's contents:

* A genuine, self-describing **`.xml`** export (AR System Administrator's XML export format).
* A packed **`.def`** export (AR System Administrator's `.def` export format).

## Known Limitations

* **Overlay base-layer pages**: for the small number of objects with an active overlay, the AR
  System Java API has no way (found so far) to reach the hidden base layer by name - only the
  active overlay layer gets documented for those objects. Typically affects well under 0.1% of
  objects on a real server.
* **`--slow`** disables the bulk `getListXObjects()`-style calls in favor of one-at-a-time fetches.
  Some servers (very old, or 21+) don't reliably support the bulk calls; this port also falls back
  to slow loading automatically in those cases and silently ignores "can't decode result" errors
  from Innovation Studio-format objects on 21+ servers, matching the C++ tool's documented
  workaround for the same problem.
* A handful of validator pages compare content by sanitized *name* rather than the C++'s internal
  numeric object ID (which this port's architecture doesn't have an equivalent of), so a couple of
  filenames differ from the C++ tool's output even though the content is equivalent.

## Troubleshooting

Run with `-v` and redirect output to a file to capture detailed logs:

```
java -jar arinsidej.jar -i settings.ini ... -v > arinside.log 2>&1
```
