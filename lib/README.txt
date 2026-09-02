ARInsideJ - BMC AR System Java API jars (required, NOT included)
==============================================================

ARInsideJ is built against BMC's AR System Java API but does not ship it.
Those jars are BMC proprietary and cannot be redistributed, so you must
supply your own copy from a BMC product you are licensed to use.

Put the jars in THIS folder (the lib/ folder next to arinsidej.jar).

  * run-arinsidej.bat / run-arinsidej.sh add every *.jar in here to the
    classpath automatically - any filename works (arapi233_build002.jar,
    arapi.jar, ...).
  * A bare `java -jar arinsidej.jar` also works if you name them exactly
    lib/arapi.jar and lib/arlogger.jar (that path is on the jar's manifest
    Class-Path).


What you need
-------------

  1. arapi<version>.jar
       The AR System Java API itself (com.bmc.arsys.api.*).
       Required in EVERY mode, including the offline .xml / .def file mode.

  2. arlogger<version>.jar
       com.bmc.arsys.logger.ARLogger - a separate runtime dependency of the
       API's ARServerUser class, NOT bundled inside arapi.
       Required for live-server mode and .def file mode. Not needed for a
       pure .xml file-mode run, but harmless to include.

Any reasonably recent AR System version works (20.x or newer recommended).
The jars do NOT have to match your server's version exactly - newer is
generally better. Use both jars from the SAME install so their versions
agree.


Where to find them
------------------

Any ONE of the following BMC installations contains both jars. Copy them
from whichever you have access to. If a path below doesn't match your
version's layout, just search the install tree for  arapi*.jar  and
arlogger*.jar .

* AR System server
    <ARSystemInstallDir>/arserver/api/lib/
  Windows default <ARSystemInstallDir>:
    C:\Program Files\BMC Software\ARSystem

* AR System Mid Tier (web tier)
    <MidTier>/WEB-INF/lib/
  e.g.  .../Apache Tomcat/webapps/arsys/WEB-INF/lib/
    or  C:\Program Files\BMC Software\ARSystem\midtier\WEB-INF\lib\

* AR System Developer Studio
    <DevStudioInstallDir>/plugins/
  Files are OSGi bundles here - e.g. com.bmc.arsys.api_<ver>.jar and
  com.bmc.arsys.logger_<ver>.jar - but they are ordinary jars you can copy
  (and rename). Windows default:
    C:\Program Files\BMC Software\ARSystem\DeveloperStudio\plugins

* AR System Integration / DISERVER (Pentaho data-integration)
    <ARSystemInstallDir>/diserver/data-integration/lib/

* AR System Email Engine, Atrium Integrator, Atrium CMDB, or any other BMC
  product that ships the AR Java API - look for arapi*.jar / arlogger*.jar
  under its lib/ folder.

* BMC Electronic Product Distribution (EPD):  https://webapps.bmc.com/epd
  Download "BMC Helix ITSM: AR System" (or "AR System") and take the
  "AR System C/Java API" package - arapi*.jar and arlogger*.jar are inside.

* Already installed in a local Maven repo?  Copy them straight from
    ~/.m2/repository/com/bmc/arsys/arapi/<version>/arapi-<version>.jar
    ~/.m2/repository/com/bmc/arsys/arlogger/<version>/arlogger-<version>.jar
