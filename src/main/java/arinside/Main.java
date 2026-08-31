package arinside;

import arinside.ar.ArClient;
import arinside.ar.AssociationRepository;
import arinside.ar.BlackList;
import arinside.ar.ContainerRepository;
import arinside.ar.ContainerSource;
import arinside.ar.FileModeContainerRepository;
import arinside.ar.FileModeImageRepository;
import arinside.ar.FileModeSchemaRepository;
import arinside.ar.FileModeWorkflowRepository;
import arinside.ar.IdentityRepository;
import arinside.ar.ImageSource;
import arinside.ar.OverlaySupport;
import arinside.ar.ReadPool;
import arinside.ar.SchemaRepository;
import arinside.ar.SchemaSource;
import arinside.ar.SchemaBulkCache;
import arinside.ar.WorkflowBulkCache;
import arinside.ar.WorkflowRepository;
import arinside.ar.WorkflowSource;
import arinside.ar.XmlFileContainerRepository;
import arinside.ar.XmlFileImageRepository;
import arinside.ar.XmlFileSchemaRepository;
import arinside.ar.XmlFileWorkflowRepository;
import arinside.ar.xmlfile.ArsXmlFileParser;
import arinside.ar.xmlfile.ParsedObjects;
import arinside.cli.CommandLineArgs;
import arinside.config.AppConfig;
import arinside.config.AppConfigReader;
import arinside.doc.*;
import arinside.incremental.RunState;
import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.WebPage;
import arinside.ar.GroupRecord;
import arinside.ar.RoleRecord;
import arinside.ar.UserRecord;
import arinside.scan.AppMembershipIndex;
import arinside.scan.GuideCallIndex;
import arinside.scan.SchemaReferenceIndex;
import arinside.scan.SchemaWorkflowIndex;
import arinside.scan.ContainerReferenceIndex;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.FilterErrorHandlerIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.ImageReferenceIndex;
import arinside.scan.JoinFieldIndex;
import arinside.scan.MenuAttachmentIndex;
import arinside.scan.SchemaTypeIndex;
import arinside.scan.MissingFieldReferenceIndex;
import arinside.scan.PermissionIndex;
import arinside.scan.RoleIndex;
import arinside.scan.ScopeFilter;
import arinside.scan.WorkflowReferenceIndex;
import arinside.util.OutputDirectory;
import arinside.util.ResourceExtractor;
import arinside.util.WritePool;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Java port of Main.cpp/CMain::Run + CARInside::DoWork (ARInside.cpp). Runs against a live server
 * connection, or fully offline against an AR System Administrator .xml export (see
 * appConfig.fileMode / the ArsXmlFileParser branch below - a .def-format file still needs a live
 * connection, see FileModeWorkflowRepository's javadoc). Documents forms, active links, filters,
 * escalations, menus, containers (all 5 ARCON_* subtypes), users, groups, roles, and images, with
 * overlay-aware visibility/naming and a scan/-backed workflow cross-reference index on schema
 * pages - see the project notes for the still-open deferred gaps within each object
 * type.
 */
public final class Main {

    public static void main(String[] args) {
        long pipelineStart = arinside.util.Timing.start();
        System.out.println(Version.PRODUCT_NAME + " Version " + Version.APP_VERSION);
        System.out.println();

        CommandLineArgs cmdLine;
        try {
            cmdLine = CommandLineArgs.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            CommandLineArgs.printUsage();
            System.exit(1);
            return;
        }

        if (cmdLine.isHelpRequested()) {
            CommandLineArgs.printUsage();
            return;
        }

        System.out.println("Load application configuration settings: '" + cmdLine.getIniFilename() + "'");
        AppConfig appConfig = new AppConfig();
        try {
            new AppConfigReader(cmdLine.getIniFilename()).loadTo(appConfig);
            appConfig.validate(cmdLine);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            System.exit(1);
            return;
        }

        if (AppConfig.verboseMode) appConfig.dump();

        arinside.output.WebUtil.webpageFileExtension = appConfig.gzCompression
            ? "htm.gz" // GZip writer itself lands in Phase 6; extension is set now for path fidelity
            : "htm";

        // Incremental runs: read the previous run's state (before anything below can delete it) and,
        // if nothing has changed since, leave the existing output untouched and stop here. Inert in
        // diff mode. See AppConfig.incrementalRuns / arinside.incremental.
        long incrementalProbeTime = java.time.Instant.now().getEpochSecond();
        RunState prevRunState = appConfig.incrementalRuns && !appConfig.diffMode
            ? RunState.readOrNull(appConfig.targetFolder) : null;
        String incrementalFileHash = null;
        if (appConfig.incrementalRuns && !appConfig.diffMode && appConfig.fileMode) {
            incrementalFileHash = RunState.sha256(appConfig.objListXML);
            if (prevRunState != null && "file".equals(prevRunState.mode)
                    && prevRunState.source.equals(appConfig.objListXML)
                    && !incrementalFileHash.isEmpty() && incrementalFileHash.equals(prevRunState.fileHash)) {
                System.out.println("Incremental: '" + appConfig.objListXML + "' is unchanged since the last run ("
                    + prevRunState.generated + "). Output left as-is.");
                return;
            }
        }
        if (appConfig.incrementalRuns && !appConfig.diffMode && !appConfig.fileMode
                && prevRunState != null && "server".equals(prevRunState.mode)
                && prevRunState.source.equals(appConfig.serverName)) {
            try (ArClient probe = ArClient.connect(appConfig)) {
                java.util.Optional<String> change = arinside.incremental.ChangeProbe.firstChange(probe, prevRunState);
                if (change.isEmpty()) {
                    System.out.println("Incremental: no changes on '" + appConfig.serverName + "' since the last run ("
                        + prevRunState.generated + "). Output left as-is.");
                    return;
                }
                System.out.println("Incremental: changes detected (" + change.get() + ") - running in full.");
            } catch (ARException e) {
                System.out.println("Incremental probe failed (" + e.getMessage() + ") - running in full.");
            }
        }

        if (appConfig.deleteExistingFiles) {
            OutputDirectory.deleteExistingFiles(appConfig.targetFolder);
        }

        OutputDirectory dirs = new OutputDirectory(appConfig);
        if (!dirs.createAppDirectory()) {
            System.out.println("Failed to create target directory: " + appConfig.targetFolder);
            System.exit(1);
            return;
        }

        if (OutputDirectory.validateTargetDir(appConfig.targetFolder) != 0) {
            System.out.println("Failed to create target directory: " + appConfig.targetFolder);
            System.exit(1);
            return;
        }

        if (appConfig.gzCompression) {
            writeHtAccess(appConfig.targetFolder);
        }

        System.out.println("Extracting web assets...");
        ResourceExtractor.extractTo(appConfig.targetFolder);

        System.out.println("Writing navigation page...");
        arinside.output.NavigationPage.write(appConfig);
        // Placeholder so every page's <script src="img/search-index.js"> resolves even if the
        // documentation phase below throws; overwritten with real data once it completes.
        arinside.output.SearchIndex.writeEmpty(appConfig.targetFolder);

        if (appConfig.diffMode) {
            try {
                arinside.diff.DiffRunner.run(appConfig);
            } catch (Exception e) {
                System.out.println("EXCEPTION building diff report: " + e);
                if (AppConfig.verboseMode) e.printStackTrace(System.out);
                System.exit(1);
            }
            System.out.println(Version.PRODUCT_NAME + " diff run complete.");
            if (appConfig.openWhenDone) openInBrowser(java.nio.file.Path.of(appConfig.targetFolder, "diff", "index." + arinside.output.WebUtil.webPageSuffix()));
            return;
        }

        {
            // Declared outside the try (not just outside the file-mode/server-mode branch) so
            // they're visible in the finally block below, which closes them regardless of how the
            // try body exits.
            ReadPool reads = null; // null in file mode - see FileModeSchemaRepository's javadoc for why file mode stays fully sequential this round
            WritePool writes = null;
            // Connectionless XML file mode never opens a connection at all - see AppConfig.connectionless's
            // javadoc. try-with-resources tolerates a null resource (its generated close() call is
            // simply skipped), so this stays a single code path rather than duplicating the pipeline.
            try (ArClient client = appConfig.connectionless ? null : ArClient.connect(appConfig)) {
                int serverOverlayMode = client != null
                    ? OverlaySupport.fetchServerOverlayMode(client)
                    : 1; // no live server to ask; matches this port's own default elsewhere for ars764+-era servers
                if (AppConfig.verboseMode) System.out.println("Server overlay mode: " + serverOverlayMode);

                SchemaSource schemas;
                WorkflowSource workflow;
                ContainerSource containers;
                ImageSource images;
                Set<String> knownUserNames = new HashSet<>();
                IdentityRepository identity = null; // only set (and only used later) in server mode - see the users/groups/roles block below
                BlackList blackList = null; // only set in server mode - needed to build fresh per-connection repos for the read pool
                WorkflowBulkCache workflowCache = null; // only set in server mode - see WorkflowBulkCache's javadoc
                SchemaBulkCache schemaCache = null; // only set in server mode - see SchemaBulkCache's javadoc

                if (appConfig.connectionless) {
                    // Genuinely offline: the whole object graph is parsed directly out of the real
                    // AR System export by either ArsXmlFileParser (.xml) or DefFileParser (.def, see
                    // its javadoc) - no ArClient/live session at all either way, dispatched by the
                    // same sniff AppConfig.validate() already used to decide connectionless=true.
                    // Both produce the same ParsedObjects shape, so everything downstream (the
                    // XmlFile*Repository classes - genuinely format-agnostic despite the name, see
                    // XmlFileSchemaRepository's javadoc) is unchanged either way. No BlackList/users/
                    // groups/roles in either format, since neither export format has a user/group/
                    // role object type.
                    boolean isDefFile = arinside.ar.FileFormatSniffer.isDefFormat(appConfig.objListXML);
                    System.out.println("Parsing objects from " + (isDefFile ? ".def" : ".xml") + " file '" + appConfig.objListXML + "' (no server connection)...");
                    ParsedObjects parsed = isDefFile
                        ? arinside.ar.deffile.DefFileParser.parse(appConfig.objListXML)
                        : ArsXmlFileParser.parse(appConfig.objListXML);
                    schemas = new XmlFileSchemaRepository(parsed);
                    workflow = new XmlFileWorkflowRepository(parsed);
                    containers = new XmlFileContainerRepository(parsed);
                    images = new XmlFileImageRepository(parsed);
                    System.out.println(schemas.listFormNames().size() + " forms, "
                        + workflow.listActiveLinkNames().size() + " active links, "
                        + workflow.listFilterNames().size() + " filters, "
                        + workflow.listEscalationNames().size() + " escalations, "
                        + workflow.listMenuNames().size() + " menus, "
                        + (containers.listContainerNames(Constants.ARCON_GUIDE).size()
                            + containers.listContainerNames(Constants.ARCON_APP).size()
                            + containers.listContainerNames(Constants.ARCON_PACK).size()
                            + containers.listContainerNames(Constants.ARCON_FILTER_GUIDE).size()
                            + containers.listContainerNames(Constants.ARCON_WEBSERVICE).size()) + " containers, "
                        + images.listImageNames().size() + " images parsed from file.");

                    // No ReadPool (nothing to fetch live - everything's already in memory), but
                    // rendering+writing is pure local work just like server mode.
                    writes = WritePool.open(appConfig.writeConcurrency);
                } else if (appConfig.fileMode) {
                    // .def-format file mode: still requires a live, logged-in connection despite
                    // reading object data from a local file - see FileModeSchemaRepository's javadoc
                    // for why this deviates from the C++'s fully offline file mode. No
                    // BlackList/users/groups/roles here, matching the C++'s own file-mode scope (see
                    // ARInside.cpp::LoadFromFile - it only ever parses schema/field/VUI/active-link/
                    // filter/escalation/menu/container/image entries out of the XML, nothing
                    // user/group/role-related).
                    System.out.println("Loading objects from file '" + appConfig.objListXML + "'...");
                    schemas = new FileModeSchemaRepository(client, appConfig.objListXML);
                    workflow = new FileModeWorkflowRepository(client, appConfig.objListXML);
                    containers = new FileModeContainerRepository(client, appConfig.objListXML);
                    images = new FileModeImageRepository(client, appConfig.objListXML);
                    System.out.println(schemas.listFormNames().size() + " forms, "
                        + workflow.listActiveLinkNames().size() + " active links, "
                        + workflow.listFilterNames().size() + " filters, "
                        + workflow.listEscalationNames().size() + " escalations, "
                        + workflow.listMenuNames().size() + " menus loaded from file.");

                    // No ReadPool in file mode (nothing left to fetch live - see
                    // FileModeSchemaRepository's javadoc, everything is pre-loaded in memory now),
                    // but rendering+writing is pure local work just like server mode, so the write
                    // pool still applies here.
                    writes = WritePool.open(appConfig.writeConcurrency);
                } else {
                    blackList = appConfig.blackList.isBlank()
                        ? BlackList.empty()
                        : BlackList.loadFromServer(client, appConfig.blackList);

                    // Fetched early (users are otherwise documented near the end of the pipeline) so
                    // ServerObjectHistoryWidget can check "does this owner/last-changed-by name resolve
                    // to a real user" before linking, matching the C++'s LinkToUser existence check -
                    // an unconditional link would dangle for historical/system account names no longer
                    // registered on the server. Cheap: one raw entry-query call, not fetched per-object.
                    identity = new IdentityRepository(client, appConfig, blackList);
                    for (UserRecord u : identity.listUsers()) knownUserNames.add(u.loginName);
                    images = identity;

                    System.out.println("Documenting server info...");
                    new ServerInfoPage(client, appConfig).render();

                    System.out.println("Bulk-loading active links / filters / escalations (fast path, falls back to per-object loading on failure)...");
                    workflowCache = WorkflowBulkCache.load(client);
                    workflow = new WorkflowRepository(client, blackList, workflowCache);
                    System.out.println("Bulk-loading forms (fast path, falls back to per-object loading on failure)...");
                    schemaCache = SchemaBulkCache.load(client);
                    schemas = new SchemaRepository(client, blackList, schemaCache);
                    containers = new ContainerRepository(client, blackList);

                    reads = ReadPool.open(appConfig, appConfig.readConcurrency);
                    writes = WritePool.open(appConfig.writeConcurrency);
                }

                // Mirrors ARInside.cpp's own two-timer split (nDurationLoad/nDurationDocumentation,
                // stopped/started around LoadServerObjects() vs Documentation()) as closely as this
                // port's interleaved scan/doc pipeline allows: everything above this point is the
                // "pull objects from the server" cost (bulk AL/filter/escalation/form fetch, or the
                // file-mode/XML-mode parse); everything below (scan/ indexing + doc/ page rendering,
                // which this port can't cleanly separate the way the C++'s own two-phase
                // BuildReferences()-then-Documentation() structure does) counts as "write documented
                // pages" - see DocSummaryInfo.Counts's javadoc.
                long loadSeconds = java.time.Duration.ofNanos(System.nanoTime() - pipelineStart).toSeconds();

                // Fail fast on a typo'd --scope form name before burning a full scan pass on it.
                if (!appConfig.scope.isEmpty() && !schemas.listFormNames().contains(appConfig.scope)) {
                    throw new IllegalArgumentException("[ERR] --scope '" + appConfig.scope + "' is not a known form name.");
                }

                BlackList blForIndexes = blackList;
                WorkflowBulkCache workflowCacheForIndexes = workflowCache;
                SchemaBulkCache schemaCacheForIndexes = schemaCache;
                System.out.println("Indexing workflow-to-form references (scan/ pass)...");
                WorkflowReferenceIndex workflowIndex = reads != null
                    ? WorkflowReferenceIndex.build(workflow, serverOverlayMode, reads, c -> new WorkflowRepository(c, blForIndexes, workflowCacheForIndexes))
                    : WorkflowReferenceIndex.build(workflow, serverOverlayMode);
                System.out.println("Workflow reference index built.");

                // Moved up from just before AL/filter/escalation are documented (see below) - built
                // here instead of there so GlobalFieldIndex.build() can pass them straight through to
                // its own piggybacked Column/Table-field reference scan (ScanFields.cpp's
                // AR_DATA_TYPE_COLUMN/AR_DATA_TYPE_TABLE cases - see GlobalFieldIndex's javadoc), which
                // needs to run as part of the SAME full-field pass this index already does rather than
                // a separate one. Still complete well before FieldDetailPage renders, same as before.
                FieldReferenceIndex fieldRefs = new FieldReferenceIndex();
                MissingFieldReferenceIndex missingFieldRefs = new MissingFieldReferenceIndex();

                System.out.println("Indexing global fields (scan/ pass)...");
                GlobalFieldIndex globalFields = reads != null
                    ? GlobalFieldIndex.build(schemas, reads, c -> new SchemaRepository(c, blForIndexes, schemaCacheForIndexes), serverOverlayMode, appConfig.overlaySupport, fieldRefs, missingFieldRefs)
                    : GlobalFieldIndex.build(schemas, serverOverlayMode, appConfig.overlaySupport, fieldRefs, missingFieldRefs);
                new GlobalFieldsPage(globalFields, appConfig).render();
                System.out.println(globalFields.byFieldId().size() + " global field IDs found.");

                System.out.println("Indexing join-form field references (scan/ pass)...");
                JoinFieldIndex joinFields = reads != null
                    ? JoinFieldIndex.build(schemas, reads, c -> new SchemaRepository(c, blForIndexes, schemaCacheForIndexes))
                    : JoinFieldIndex.build(schemas);

                System.out.println("Indexing schema audit/archive type overrides (scan/ pass)...");
                SchemaTypeIndex schemaTypes = reads != null
                    ? SchemaTypeIndex.build(schemas, reads, c -> new SchemaRepository(c, blForIndexes, schemaCacheForIndexes))
                    : SchemaTypeIndex.build(schemas);

                // Built here, before AL/filter/menu/container are documented, matching the real
                // C++'s own ContainerList(ARCON_APP)-runs-before-everything-else ordering in
                // ARInside.cpp (confirmed by reading it, not assumed) - AppRefName is a mutation
                // DocApplicationDetails.cpp applies as a side effect of documenting each Application
                // container, so the C++ itself depends on Applications being processed first for the
                // "Application: <link>" header line to show up on any other object type's page. See
                // AppMembershipIndex's javadoc.
                System.out.println("Indexing application membership (scan/ pass)...");
                AppMembershipIndex appIndex = AppMembershipIndex.build(containers, schemas);

                // Fetched early (schema permission rows need it, and schemas render before the
                // roles catalog page later in the pipeline) via a second identity.listRoles() call -
                // RoleOverviewPage makes its own separate call later for the actual catalog page;
                // see RoleIndex's javadoc for why this small duplication was accepted over threading
                // the list all the way through. Empty (not null) in file mode - identity is only set
                // in server mode, and file mode never has role data to filter/link against either,
                // matching the C++'s own file-mode behavior here.
                System.out.println("Indexing roles for permission-display scoping (scan/ pass)...");
                RoleIndex roleIndex = identity != null ? RoleIndex.build(identity.listRoles()) : RoleIndex.build(List.of());

                // DB Table ID/View/SH View rows on the schema General tab - a raw SQL passthrough
                // query (ARGetListSQL-equivalent), server mode only. See SchemaDbInfoIndex's javadoc -
                // this was previously (wrongly) documented as a permanent Java-API-surface gap.
                System.out.println("Indexing schema DB table info (scan/ pass)...");
                arinside.scan.SchemaDbInfoIndex schemaDbInfo = arinside.scan.SchemaDbInfoIndex.build(client);

                // Same reasoning as roleIndex above - CARInside::LinkToGroup only links a positive
                // group ID when CARGroup::Exists() is true (using the group's real name as link
                // text, e.g. "Public" for group 0 - a real bug fixed here, found via user report:
                // this used to just track membership as a Set<Integer> and always render the literal
                // text "Group N" regardless of the group's actual name), falling back to plain
                // numeric text otherwise (ARInside.cpp:1177-1203) - a second, cheap
                // identity.listGroups() call rather than threading Main.java's later groupNamesById
                // map (built near the end of the pipeline, well after schemas render) all the way
                // back up here.
                Map<Integer, GroupRecord> earlyGroupsById = new HashMap<>();
                if (identity != null) for (GroupRecord g : identity.listGroups()) earlyGroupsById.put(g.groupId, g);

                // Also built here, before AL/filter/menu are documented - see ContainerReferenceIndex's
                // javadoc (matches DocAlDetails.cpp/DocFilterDetails.cpp/DocCharMenuDetails.cpp's
                // shared "which containers reference this object" pattern).
                System.out.println("Indexing container references (scan/ pass)...");
                ContainerReferenceIndex containerRefs = reads != null
                    ? ContainerReferenceIndex.build(containers, serverOverlayMode, appConfig.overlaySupport, reads, c -> new ContainerRepository(c, blForIndexes))
                    : ContainerReferenceIndex.build(containers, serverOverlayMode, appConfig.overlaySupport);

                // Built once every dependency (WorkflowReferenceIndex/ContainerReferenceIndex/
                // AppMembershipIndex) is available, well before any per-type write loop starts -
                // see ScopeFilter's javadoc for what "in scope" means. Null (not built at all) for
                // a normal, unscoped full run.
                ScopeFilter scopeFilter = appConfig.scope.isEmpty() ? null
                    : ScopeFilter.build(appConfig.scope, schemas, workflowIndex, containerRefs, appIndex);

                // fieldRefs/missingFieldRefs are now built earlier, right before GlobalFieldIndex (see
                // above) - still populated further as a side effect of rendering AL/filter/escalation
                // "Run If" qualifiers below, and complete by the time FieldDetailPage renders each
                // field's "Referenced By" section, same guarantee as before this reorder.
                // Populated as a side effect of VUI/container rendering, consumed by ImageDetailPage
                // (documented last in this pipeline - see ImageReferenceIndex's javadoc).
                ImageReferenceIndex imageRefs = new ImageReferenceIndex();

                // Built here (moved up from just before forms are documented) since its scan/-phase
                // maps need to already exist before AL/filter/escalation render, AND the SAME
                // instance keeps accumulating a second, doc/-phase set of maps as those pages render
                // (Push Fields/Service/Delete-Entry targets) - see SchemaReferenceIndex's javadoc for
                // why both halves exist. Forms are still documented after AL/filter/escalation, so
                // both halves are complete by the time SchemaDetailPage reads this same instance.
                System.out.println("Indexing schema references (scan/ pass)...");
                SchemaReferenceIndex schemaRefs = reads != null
                    ? SchemaReferenceIndex.build(workflow, schemas, reads, c -> new WorkflowRepository(c, blForIndexes, workflowCacheForIndexes))
                    : SchemaReferenceIndex.build(workflow, schemas);

                System.out.println("Documenting active links...");
                int alCount = new ActiveLinkOverviewPage(workflow, appConfig, serverOverlayMode).render();
                System.out.println(alCount + " active links listed.");
                ActiveLinkDetailPage alPage = new ActiveLinkDetailPage(workflow, appConfig, serverOverlayMode, globalFields, fieldRefs, missingFieldRefs, knownUserNames, appIndex, containerRefs, schemaRefs, roleIndex, earlyGroupsById);
                List<String> alNames = scoped(workflow.listActiveLinkNames(), scopeFilter != null ? scopeFilter::activeLinkInScope : null,
                    writes, name -> ScopeStubPage.render(appConfig, "Active Link", name, Naming.activeLinkDetail(name, false)));
                if (reads != null) {
                    BlackList bl = blackList;
                    documentEachParallel("active link detail", alNames, reads, writes,
                        (c, name) -> new Named<>(name, alPage.fetch(new WorkflowRepository(c, bl, workflowCacheForIndexes), name)),
                        n -> alPage.render(n.name(), n.data()));
                } else {
                    documentEachWriteOnly("active link detail", alNames, writes, alPage::render);
                }
                // Java port bug fix, found via a real full-pipeline compare-output.py regression
                // check (2026-08-18): documentOverlayBaseLayers relies on comparing a default-mode
                // name list against a "-2"-mode name list, and separately re-fetching each base name
                // while "-2" is active - but workflow/schemas are backed by WorkflowBulkCache/
                // SchemaBulkCache (added in a later speed-optimization round than the overlay
                // base-layer feature itself), and listActiveLinkNames()/getActiveLink() etc. all
                // serve straight from that cache regardless of the session's current overlay-group
                // setting. That made BOTH discoverOverlayBaseNames() calls return the identical
                // cached (always default-mode) list - zero diff, so this whole feature was silently
                // finding 0 base-layer objects for every type - and even if a base name HAD been
                // discovered another way, the render step's own getActiveLink()/getForm() call would
                // still have served the stale cached (active-layer) object instead of a real live
                // "-2"-aware fetch. Fixed by using a genuinely cache-less WorkflowRepository (2-arg
                // constructor, no WorkflowBulkCache) - built once, reused for all three AL/Filter/
                // Escalation base-layer passes below - for both the name lister and the page
                // instance's own repo, so every call in this one pass is a real live query.
                WorkflowRepository workflowLive = new WorkflowRepository(client, blackList);
                ActiveLinkDetailPage alLivePage = new ActiveLinkDetailPage(workflowLive, appConfig, serverOverlayMode, globalFields, fieldRefs, missingFieldRefs, knownUserNames, appIndex, containerRefs, schemaRefs, roleIndex, earlyGroupsById);
                documentOverlayBaseLayers(client, "active link", workflowLive::listActiveLinkNames, alLivePage::render,
                    workflowLive::getActiveLink,
                    (name, base) -> alLivePage.render(name, workflowLive.getActiveLink(name), base));
                new ActiveLinkActionPage(workflow, appConfig, serverOverlayMode).render();

                // Built here, before filters are documented, so FilterDetailPage's reverse
                // "Workflow Reference" (which OTHER filters selected this one as their Error
                // Handler) can use it - needs a full scan of every filter first, same timing
                // constraint as AppMembershipIndex/ContainerReferenceIndex above.
                System.out.println("Indexing filter error handlers (scan/ pass)...");
                FilterErrorHandlerIndex filterErrorHandlers = reads != null
                    ? FilterErrorHandlerIndex.build(workflow, reads, c -> new WorkflowRepository(c, blForIndexes, workflowCacheForIndexes))
                    : FilterErrorHandlerIndex.build(workflow);

                System.out.println("Documenting filters...");
                int filterCount = new FilterOverviewPage(workflow, appConfig, serverOverlayMode).render();
                System.out.println(filterCount + " filters listed.");
                FilterDetailPage filterPage = new FilterDetailPage(workflow, appConfig, serverOverlayMode, globalFields, fieldRefs, missingFieldRefs, knownUserNames, appIndex, containerRefs, filterErrorHandlers, schemaRefs);
                List<String> filterNames = scoped(workflow.listFilterNames(), scopeFilter != null ? scopeFilter::filterInScope : null,
                    writes, name -> ScopeStubPage.render(appConfig, "Filter", name, Naming.filterDetail(name, false)));
                if (reads != null) {
                    BlackList bl = blackList;
                    documentEachParallel("filter detail", filterNames, reads, writes,
                        (c, name) -> new Named<>(name, filterPage.fetch(new WorkflowRepository(c, bl, workflowCacheForIndexes), name)),
                        n -> filterPage.render(n.name(), n.data()));
                } else {
                    documentEachWriteOnly("filter detail", filterNames, writes, filterPage::render);
                }
                FilterDetailPage filterLivePage = new FilterDetailPage(workflowLive, appConfig, serverOverlayMode, globalFields, fieldRefs, missingFieldRefs, knownUserNames, appIndex, containerRefs, filterErrorHandlers, schemaRefs);
                documentOverlayBaseLayers(client, "filter", workflowLive::listFilterNames, filterLivePage::render,
                    workflowLive::getFilter,
                    (name, base) -> filterLivePage.render(name, workflowLive.getFilter(name), base));
                new FilterActionPage(workflow, appConfig, serverOverlayMode).render();
                new FilterErrorHandlersPage(workflow, appConfig, serverOverlayMode, filterErrorHandlers).render();

                System.out.println("Documenting escalations...");
                int escalCount = new EscalationOverviewPage(workflow, appConfig, serverOverlayMode).render();
                System.out.println(escalCount + " escalations listed.");
                EscalationDetailPage escalPage = new EscalationDetailPage(workflow, appConfig, serverOverlayMode, globalFields, fieldRefs, missingFieldRefs, knownUserNames, appIndex, containerRefs, schemaRefs);
                List<String> escalNames = scoped(workflow.listEscalationNames(), scopeFilter != null ? scopeFilter::escalationInScope : null,
                    writes, name -> ScopeStubPage.render(appConfig, "Escalation", name, Naming.escalationDetail(name, false)));
                if (reads != null) {
                    BlackList bl = blackList;
                    documentEachParallel("escalation detail", escalNames, reads, writes,
                        (c, name) -> new Named<>(name, escalPage.fetch(new WorkflowRepository(c, bl, workflowCacheForIndexes), name)),
                        n -> escalPage.render(n.name(), n.data()));
                } else {
                    documentEachWriteOnly("escalation detail", escalNames, writes, escalPage::render);
                }
                EscalationDetailPage escalLivePage = new EscalationDetailPage(workflowLive, appConfig, serverOverlayMode, globalFields, fieldRefs, missingFieldRefs, knownUserNames, appIndex, containerRefs, schemaRefs);
                documentOverlayBaseLayers(client, "escalation", workflowLive::listEscalationNames, escalLivePage::render,
                    workflowLive::getEscalation,
                    (name, base) -> escalLivePage.render(name, workflowLive.getEscalation(name), base));
                new EscalationActionPage(workflow, appConfig, serverOverlayMode).render();

                // Associations have no file-mode/XML equivalent - see AssociationSource's javadoc -
                // so this is skipped entirely outside live server mode. Documented here, before
                // forms/fields, for the same reason AL/filter/escalation are: so FieldReferenceIndex/
                // MissingFieldReferenceIndex are already complete by the time FieldDetailPage renders
                // each field's "Referenced By" section and ValidatorPage renders the missing-field-
                // reference check - see FieldReferenceIndex's javadoc.
                int associationCount = 0;
                if (client != null) {
                    System.out.println("Documenting associations...");
                    AssociationRepository associations = new AssociationRepository(client);
                    associationCount = new AssociationOverviewPage(associations, appConfig).render();
                    System.out.println(associationCount + " associations listed.");
                    AssociationDetailPage associationPage = new AssociationDetailPage(associations, appConfig, globalFields, fieldRefs, missingFieldRefs, knownUserNames);
                    if (reads != null) {
                        documentEachParallel("association detail", associations.listAssociationNames(), reads, writes,
                            (c, name) -> new Named<>(name, associationPage.fetch(new AssociationRepository(c), name)),
                            n -> associationPage.render(n.name(), n.data()));
                    } else {
                        documentEachWriteOnly("association detail", associations.listAssociationNames(), writes, associationPage::render);
                    }
                }

                System.out.println("Documenting forms...");
                int formCount = new SchemaOverviewPage(schemas, appConfig, serverOverlayMode, schemaTypes).render();
                System.out.println(formCount + " forms listed.");
                SchemaDetailPage schemaPage = new SchemaDetailPage(schemas, appConfig, workflowIndex, serverOverlayMode, fieldRefs, missingFieldRefs, globalFields, joinFields, schemaTypes, imageRefs, knownUserNames, schemaRefs, containerRefs, appIndex, roleIndex, earlyGroupsById, schemaDbInfo);
                List<String> formNames = scoped(schemas.listFormNames(), scopeFilter != null ? scopeFilter::formInScope : null,
                    writes, name -> ScopeStubPage.render(appConfig, "Form", name, Naming.schemaDetail(name, false)));
                if (reads != null) {
                    BlackList bl = blackList;
                    documentEachParallel("form detail", formNames, reads, writes,
                        (c, name) -> schemaPage.fetch(new SchemaRepository(c, bl, schemaCacheForIndexes), name),
                        schemaPage::render);
                } else {
                    documentEachWriteOnly("form detail", formNames, writes, schemaPage::render);
                }
                // See the AL/Filter/Escalation base-layer fix's comment above for why a cache-less
                // repository is required here too - SchemaBulkCache has the identical problem for
                // listFormNames()/getForm().
                SchemaRepository schemasLive = new SchemaRepository(client, blackList);
                SchemaDetailPage schemaLivePage = new SchemaDetailPage(schemasLive, appConfig, workflowIndex, serverOverlayMode, fieldRefs, missingFieldRefs, globalFields, joinFields, schemaTypes, imageRefs, knownUserNames, schemaRefs, containerRefs, appIndex, roleIndex, earlyGroupsById, schemaDbInfo);
                documentOverlayBaseLayers(client, "form", schemasLive::listFormNames, schemaLivePage::render,
                    name -> schemaLivePage.fetchBase(schemasLive, name),
                    (name, base) -> schemaLivePage.render(schemaLivePage.fetchWithDiff(schemasLive, name, base)));

                System.out.println("Indexing group/role permission cross-references (scan/ pass)...");
                PermissionIndex permIndex = reads != null
                    ? PermissionIndex.build(schemas, workflow, containers, serverOverlayMode, appIndex, globalFields, reads,
                        c -> new SchemaRepository(c, blForIndexes, schemaCacheForIndexes), c -> new WorkflowRepository(c, blForIndexes, workflowCacheForIndexes), c -> new ContainerRepository(c, blForIndexes))
                    : PermissionIndex.build(schemas, workflow, containers, serverOverlayMode, appIndex, globalFields);
                System.out.println("Permission index built.");

                // Built here (before menus are documented) so both MenuOverviewPage's "used in
                // workflow" marker and MenuDetailPage's query/SQL menu qualification rendering can
                // use it - see MenuAttachmentIndex's javadoc.
                System.out.println("Indexing menu attachments (scan/ pass)...");
                MenuAttachmentIndex menuAttachments = reads != null
                    ? MenuAttachmentIndex.build(schemas, workflow, reads, c -> new SchemaRepository(c, blForIndexes, schemaCacheForIndexes), c -> new WorkflowRepository(c, blForIndexes, workflowCacheForIndexes))
                    : MenuAttachmentIndex.build(schemas, workflow);

                System.out.println("Documenting menus...");
                int menuCount = new MenuOverviewPage(workflow, appConfig, serverOverlayMode, menuAttachments).render();
                System.out.println(menuCount + " menus listed.");
                MenuDetailPage menuPage = new MenuDetailPage(workflow, appConfig, serverOverlayMode, knownUserNames, workflowIndex, globalFields, menuAttachments, containers, appIndex, containerRefs, fieldRefs, missingFieldRefs);
                List<String> menuNames = scoped(workflow.listMenuNames(), scopeFilter != null ? scopeFilter::menuInScope : null,
                    writes, name -> ScopeStubPage.render(appConfig, "Menu", name, Naming.menuDetail(name, false)));
                if (reads != null) {
                    BlackList bl = blackList;
                    documentEachParallel("menu detail", menuNames, reads, writes,
                        (c, name) -> menuPage.fetch(new WorkflowRepository(c, bl, workflowCacheForIndexes), name),
                        menuPage::render);
                } else {
                    documentEachWriteOnly("menu detail", menuNames, writes, menuPage::render);
                }
                // Reuses workflowLive (the cache-less repo already built above for AL/Filter/
                // Escalation's own base-layer passes - see that comment) rather than a fresh
                // WorkflowRepository, for the identical staleness reason.
                MenuDetailPage menuLivePage = new MenuDetailPage(workflowLive, appConfig, serverOverlayMode, knownUserNames, workflowIndex, globalFields, menuAttachments, containers, appIndex, containerRefs, fieldRefs, missingFieldRefs);
                documentOverlayBaseLayers(client, "menu", workflowLive::listMenuNames, menuLivePage::render,
                    workflowLive::getMenu,
                    (name, base) -> menuLivePage.render(menuLivePage.fetchWithDiff(workflowLive, name, base)));

                // Built here, just before container documentation - only consumed by ContainerDetailPage's
                // Application Content section, so (unlike AppMembershipIndex/ContainerReferenceIndex) it
                // has no "must exist before X's header renders" ordering constraint on AL/filter/menu docs.
                System.out.println("Indexing schema workflow attachments (scan/ pass)...");
                SchemaWorkflowIndex schemaWorkflow = reads != null
                    ? SchemaWorkflowIndex.build(workflow, containers, reads, c -> new WorkflowRepository(c, blForIndexes, workflowCacheForIndexes), c -> new ContainerRepository(c, blForIndexes))
                    : SchemaWorkflowIndex.build(workflow, containers);

                // Also only consumed by container documentation - see SchemaWorkflowIndex's javadoc for why this has no earlier ordering constraint either.
                System.out.println("Indexing guide callers (scan/ pass)...");
                GuideCallIndex guideCalls = reads != null
                    ? GuideCallIndex.build(workflow, reads, c -> new WorkflowRepository(c, blForIndexes, workflowCacheForIndexes))
                    : GuideCallIndex.build(workflow);

                int alGuideCount = documentContainerType(client, containers, appConfig, Constants.ARCON_GUIDE, "Active Link Guides", ImageTag.Id.ActiveLinkGuide, serverOverlayMode, knownUserNames, workflowIndex, imageRefs, globalFields, appIndex, schemaWorkflow, guideCalls, containerRefs, roleIndex, earlyGroupsById, reads, writes, blackList, scopeFilter);
                int applicationCount = documentContainerType(client, containers, appConfig, Constants.ARCON_APP, "Applications", ImageTag.Id.Application, serverOverlayMode, knownUserNames, workflowIndex, imageRefs, globalFields, appIndex, schemaWorkflow, guideCalls, containerRefs, roleIndex, earlyGroupsById, reads, writes, blackList, scopeFilter);
                int packListCount = documentContainerType(client, containers, appConfig, Constants.ARCON_PACK, "Packing Lists", ImageTag.Id.PackingList, serverOverlayMode, knownUserNames, workflowIndex, imageRefs, globalFields, appIndex, schemaWorkflow, guideCalls, containerRefs, roleIndex, earlyGroupsById, reads, writes, blackList, scopeFilter);
                int filterGuideCount = documentContainerType(client, containers, appConfig, Constants.ARCON_FILTER_GUIDE, "Filter Guides", ImageTag.Id.FilterGuide, serverOverlayMode, knownUserNames, workflowIndex, imageRefs, globalFields, appIndex, schemaWorkflow, guideCalls, containerRefs, roleIndex, earlyGroupsById, reads, writes, blackList, scopeFilter);
                int webServiceCount = documentContainerType(client, containers, appConfig, Constants.ARCON_WEBSERVICE, "Web Services", ImageTag.Id.Webservice, serverOverlayMode, knownUserNames, workflowIndex, imageRefs, globalFields, appIndex, schemaWorkflow, guideCalls, containerRefs, roleIndex, earlyGroupsById, reads, writes, blackList, scopeFilter);

                System.out.println("Documenting validator/analyzer/custom-workflow pages...");
                new ValidatorPage(appConfig, permIndex, missingFieldRefs, globalFields).render();
                new AnalyzerPage(appConfig, permIndex, globalFields).render();
                new CustomWorkflowPage(appConfig, permIndex, workflowIndex, globalFields, knownUserNames).render();
                new MessageListPage(appConfig, workflowIndex).render();
                new NotificationListPage(appConfig, workflowIndex).render();

                // Users/groups/roles come from a raw entry-query against reserved admin forms
                // (User/Group/Roles) via IdentityRepository - not covered by the def-file XML
                // export format (no AR_STRUCT_ITEM_XML_USER/GROUP/ROLE type exists), matching the
                // C++'s own file mode, which also never populates userList/groupList/roleList when
                // loading from a file. Skipped entirely in file mode.
                int userCount = 0, groupCount = 0, roleCount = 0;
                if (!appConfig.fileMode) {
                System.out.println("Documenting users...");
                List<UserRecord> users = new UserOverviewPage(identity, appConfig).render();
                userCount = users.size();
                System.out.println(users.size() + " users listed.");
                UserDetailPage userDetail = new UserDetailPage(appConfig, knownUserNames, roleIndex, earlyGroupsById);
                if (writes != null) {
                    documentWriteOnly("user detail", users, writes, userDetail::render);
                } else {
                    for (UserRecord u : users) userDetail.render(u);
                }
                System.out.println(users.size() + " user detail pages written.");

                Map<Integer, List<UserRecord>> usersByGroup = new HashMap<>();
                for (UserRecord u : users) {
                    for (Integer gid : u.groupIds) {
                        usersByGroup.computeIfAbsent(gid, k -> new ArrayList<>()).add(u);
                    }
                }

                // Groups are documented before roles (despite the C++'s Users->Roles->Groups order)
                // so RoleDetailPage can hyperlink each role's Test/Production Group to the group's
                // real name - matching the C++'s own LinkToGroup(appName, groupId, rootLevel) call,
                // which resolves the group's display name the same way.
                System.out.println("Documenting groups...");
                List<GroupRecord> groups = new GroupOverviewPage(identity, appConfig).render();
                groupCount = groups.size();
                System.out.println(groups.size() + " groups listed.");
                Map<Integer, String> groupNamesById = new HashMap<>();
                for (GroupRecord g : groups) groupNamesById.put(g.groupId, g.name);

                System.out.println("Documenting roles...");
                List<RoleRecord> roles = new RoleOverviewPage(identity, appConfig).render();
                roleCount = roles.size();
                System.out.println(roles.size() + " roles listed.");
                RoleDetailPage roleDetail = new RoleDetailPage(appConfig, globalFields, groupNamesById, knownUserNames);
                if (writes != null) {
                    documentWriteOnly("role detail", roles, writes, r -> roleDetail.render(r, permIndex));
                } else {
                    for (RoleRecord r : roles) roleDetail.render(r, permIndex);
                }
                System.out.println(roles.size() + " role detail pages written.");

                System.out.println("Writing group detail pages...");
                GroupDetailPage groupDetail = new GroupDetailPage(appConfig, globalFields, knownUserNames);
                if (writes != null) {
                    documentWriteOnly("group detail", groups, writes, g -> groupDetail.render(g, permIndex, usersByGroup, roles));
                } else {
                    for (GroupRecord g : groups) groupDetail.render(g, permIndex, usersByGroup, roles);
                }
                System.out.println(groups.size() + " group detail pages written.");
                }

                System.out.println("Documenting images...");
                int imageCount = new ImageOverviewPage(images, appConfig, serverOverlayMode).render();
                System.out.println(imageCount + " images listed.");
                ImageDetailPage imagePage = new ImageDetailPage(images, appConfig, workflowIndex, imageRefs, knownUserNames, serverOverlayMode);
                if (reads != null) {
                    documentEachParallel("image detail", images.listImageNames(), reads, writes,
                        (c, name) -> new Named<>(name, c.raw().getImage(name)),
                        n -> imagePage.render(n.name(), n.data()));
                } else {
                    documentEachWriteOnly("image detail", images.listImageNames(), writes, imagePage::render);
                }
                // ImageSource has no bulk-cache variant - images is already safe to reuse directly
                // for the base-layer/diff pass. No overlaid Image existed on this feature's test
                // server (see ImageDetailPage.diffAgainstBase's javadoc) - wired up the same way as
                // the proven types, but not live-verified.
                documentOverlayBaseLayers(client, "image", images::listImageNames, imagePage::render,
                    images::getImage,
                    (name, base) -> imagePage.render(name, images.getImage(name), base));

                // Innovation Studio pass runs before the index page / search index / JSON export so
                // its definitions feed all three.
                arinside.ar.is.IsRepository isRepo = null;
                if (appConfig.documentInnovationStudio) {
                    try {
                        Set<String> documentedForms = new HashSet<>(schemas.listFormNames());
                        GlobalFieldIndex gfiForIs = globalFields;
                        java.util.function.Function<String, String> formHref = formName ->
                            documentedForms.contains(formName)
                                ? arinside.output.URLLink.relativeUrl(2, Naming.schemaDetail(formName, gfiForIs.isOverlaid(formName)))
                                : null;
                        isRepo = documentInnovationStudio(appConfig, formHref);
                    } catch (RuntimeException e) {
                        System.out.println("EXCEPTION documenting Innovation Studio: " + e.getMessage());
                        if (AppConfig.verboseMode) e.printStackTrace(System.out);
                    }
                }

                System.out.println("Writing index page...");
                long documentationSeconds = java.time.Duration.ofNanos(System.nanoTime() - pipelineStart).toSeconds() - loadSeconds;
                DocSummaryInfo.render(appConfig, new DocSummaryInfo.Counts(
                    alCount, webServiceCount, alGuideCount, filterGuideCount, packListCount, applicationCount,
                    escalCount, filterCount, groupCount, menuCount, roleCount, formCount, userCount, imageCount, associationCount,
                    globalFields.totalFieldCount(), loadSeconds, documentationSeconds, WebPage.filesCreated.get()),
                    isRepo);

                if (appConfig.searchIndex) {
                    arinside.output.SearchIndex.writeTo(appConfig.targetFolder);
                    System.out.println(arinside.output.SearchIndex.size() + " objects in search index.");
                } else {
                    arinside.output.SearchIndex.writeEmpty(appConfig.targetFolder);
                }

                if (appConfig.jsonOutput) {
                    arinside.output.JsonExport.writeTo(appConfig);
                    System.out.println("JSON export written to data/.");
                }

                if (appConfig.incrementalRuns && !appConfig.diffMode) {
                    RunState state = new RunState();
                    state.generated = java.time.Instant.now().toString();
                    state.probeTime = incrementalProbeTime;
                    if (appConfig.fileMode) {
                        state.mode = "file";
                        state.source = appConfig.objListXML;
                        state.fileHash = incrementalFileHash != null ? incrementalFileHash : RunState.sha256(appConfig.objListXML);
                        state.write(appConfig.targetFolder);
                        System.out.println("Incremental: run state written to " + RunState.FILE_NAME + ".");
                    } else {
                        state.mode = "server";
                        state.source = appConfig.serverName;
                        state.fileHash = "-";
                        try {
                            arinside.incremental.ChangeProbe.snapshotInto(client, state);
                            state.write(appConfig.targetFolder);
                            System.out.println("Incremental: run state written to " + RunState.FILE_NAME + ".");
                        } catch (ARException e) {
                            System.out.println("Incremental: could not snapshot object names (" + e.getMessage() + "); state not written.");
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("EXCEPTION connecting/documenting: " + e.getMessage());
            } finally {
                if (reads != null) reads.close();
                if (writes != null) writes.close();
            }
        }

        System.out.println();
        System.out.println(WebPage.filesCreated.get() + " files created.");
        System.out.println(arinside.util.Timing.summary(System.nanoTime() - pipelineStart));
        System.out.println(Version.PRODUCT_NAME + " run complete.");

        if (appConfig.openWhenDone) {
            openInBrowser(java.nio.file.Path.of(appConfig.targetFolder, Naming.mainHome().fullFileName()));
        }
    }

    /**
     * {@code --open}: best-effort "show me the result now". Falls back to just printing the
     * {@code file://} URL when there's no desktop (headless / CI / SSH) - never fails the run.
     */
    private static void openInBrowser(java.nio.file.Path indexPage) {
        java.net.URI uri = indexPage.toUri();
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()
                    && java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception e) {
            if (AppConfig.verboseMode) System.out.println("--open: " + e);
        }
        System.out.println("Open: " + uri);
    }

    /**
     * Innovation Studio documentation pass (see {@code arinside.ar.is}). Additive to a normal run:
     * pulls the IS bundle inventory + the rule/process/web-API/association/event/... definitions
     * over the rx REST API and renders them. Record definitions are skipped (they are the classic
     * AR forms). For now this reports counts; the doc/ pages land in a follow-up increment.
     */
    private static arinside.ar.is.IsRepository documentInnovationStudio(AppConfig appConfig,
            java.util.function.Function<String, String> formHref) {
        System.out.println("Documenting Innovation Studio at " + appConfig.isServerUrl + " ...");
        try (arinside.ar.is.IsClient client = new arinside.ar.is.IsClient(
                appConfig.isServerUrl, appConfig.effectiveIsUsername(), appConfig.effectiveIsPassword())) {
            arinside.ar.is.IsRepository repo = arinside.ar.is.IsRepository.load(client);
            if (repo.isEmpty()) {
                System.out.println("  no Innovation Studio content found - nothing to document.");
                return null;
            }
            arinside.output.NavigationPage.NavItem isNav = arinside.doc.is.IsPages.render(appConfig, repo, formHref);
            // regenerate nav.js with the Innovation Studio section appended
            arinside.output.NavigationPage.write(appConfig, java.util.List.of(isNav));
            System.out.println("  " + repo.bundles().size() + " bundles, "
                + repo.totalDefinitions() + " definitions documented under is/.");
            return repo;
        }
    }

    /** Java port of CARInside::WriteHTAccess - lets an Apache server serve the .htm.gz files with the right Content-Encoding. */
    private static void writeHtAccess(String targetFolder) {
        java.nio.file.Path file = java.nio.file.Path.of(targetFolder, ".htaccess");
        if (java.nio.file.Files.exists(file)) return; // already there, assume correctly configured
        try {
            java.nio.file.Files.writeString(file, "RemoveType .gz\nAddEncoding gzip .gz");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error saving file '" + file + "' to disk. Error: " + e.getMessage(), e);
        }
    }

    private static int documentContainerType(ArClient client, ContainerSource repo, AppConfig appConfig, int type, String title, ImageTag.Id icon, int serverOverlayMode, Set<String> knownUserNames, WorkflowReferenceIndex workflowIndex, ImageReferenceIndex imageRefs, GlobalFieldIndex globalFields, AppMembershipIndex appIndex, SchemaWorkflowIndex schemaWorkflow, GuideCallIndex guideCalls, ContainerReferenceIndex containerRefs, RoleIndex roleIndex, Map<Integer, GroupRecord> groupsById, ReadPool reads, WritePool writes, BlackList blackList, ScopeFilter scopeFilter) throws ARException {
        System.out.println("Documenting " + title + "...");
        int count = new ContainerOverviewPage(repo, appConfig, type, title, icon, serverOverlayMode, guideCalls).render();
        System.out.println(count + " " + title.toLowerCase() + " listed.");
        ContainerDetailPage detail = new ContainerDetailPage(repo, appConfig, type, title, icon, serverOverlayMode, knownUserNames, workflowIndex, imageRefs, globalFields, appIndex, schemaWorkflow, guideCalls, containerRefs, roleIndex, groupsById);
        List<String> containerNames = scoped(repo.listContainerNames(type), scopeFilter != null ? name -> scopeFilter.containerInScope(type, name) : null,
            writes, name -> ScopeStubPage.render(appConfig, title, name, Naming.containerDetail(type, name, false)));
        if (reads != null) {
            documentEachParallel(title.toLowerCase() + " detail", containerNames, reads, writes,
                (c, name) -> new Named<>(name, detail.fetch(new ContainerRepository(c, blackList), name)),
                n -> detail.render(n.name(), n.data()));
        } else {
            documentEachWriteOnly(title.toLowerCase() + " detail", containerNames, writes, detail::render);
        }
        // ContainerRepository has no bulk-cache variant (unlike Schema/Workflow) - repo is already
        // safe to reuse directly for the base-layer/diff pass, no separate cache-less instance needed.
        documentOverlayBaseLayers(client, title.toLowerCase(), () -> repo.listContainerNames(type), detail::render,
            repo::getContainer,
            (name, base) -> detail.render(name, repo.getContainer(name), base));
        return count;
    }

    @FunctionalInterface
    private interface DetailRenderer {
        void render(String name) throws ARException;
    }

    /**
     * Splits {@code allNames} into in-scope/out-of-scope per {@code inScopePredicate} (null means
     * no --scope was requested, so every name is in scope and this is a no-op), writing a
     * {@link ScopeStubPage} for every out-of-scope name so no link into it ever 404s, and
     * returning only the in-scope subset for the caller's normal fetch+render loop. See
     * {@link arinside.scan.ScopeFilter}'s javadoc for what "in scope" means.
     */
    private static List<String> scoped(List<String> allNames, java.util.function.Predicate<String> inScopePredicate,
                                        WritePool writes, DetailRenderer stubRenderer) {
        if (inScopePredicate == null) return allNames;
        List<String> inScope = new ArrayList<>();
        List<String> outOfScope = new ArrayList<>();
        for (String name : allNames) {
            (inScopePredicate.test(name) ? inScope : outOfScope).add(name);
        }
        documentEachWriteOnly("scope stub", outOfScope, writes, stubRenderer);
        return inScope;
    }

    private static void documentEach(String label, List<String> names, DetailRenderer renderer) {
        int done = 0;
        for (String name : names) {
            try {
                renderer.render(name);
            } catch (Exception e) {
                System.out.println("EXCEPTION " + label + " documentation of '" + name + "': " + e.getMessage());
            }
            done++;
            if (done % 500 == 0) System.out.println("  ... " + done + "/" + names.size() + " " + label + " pages written");
        }
        System.out.println(done + " " + label + " pages written.");
    }

    /** Pairs a fetched object back up with the name it was fetched by, for the Doc*DetailPage classes whose render() takes (name, data) as two separate arguments rather than bundling the name into their own data record. */
    private record Named<T>(String name, T data) {}

    @FunctionalInterface
    private interface ParallelFetch<D> {
        D fetch(ArClient client, String name) throws Exception;
    }

    @FunctionalInterface
    private interface ParallelRender<D> {
        void render(D data) throws Exception;
    }

    /**
     * Parallel replacement for documentEach: each name's fetch runs on a pooled AR System
     * connection (ReadPool), and as soon as that fetch completes, its render+write is submitted
     * to the write pool (WritePool) - independently sized/concurrent from the read side. Per-item
     * failures (fetch OR render) are caught and logged in the same "EXCEPTION label ... 'name':
     * message" shape documentEach already used, not allowed to abort the batch or the run.
     *
     * <p>Joins directly on each item's full fetch-then-render CompletableFuture chain (via
     * thenCompose, whose completion semantics are unambiguous) rather than having ReadPool/
     * WritePool track "pending work" themselves - simpler and avoids a subtle ordering question
     * about exactly when a chained thenAccept-style callback is guaranteed to have run relative to
     * another thread's join() on the upstream future.
     */
    private static <D> void documentEachParallel(String label, List<String> names, ReadPool reads, WritePool writes,
            ParallelFetch<D> fetch, ParallelRender<D> render) {
        List<CompletableFuture<Void>> chains = new ArrayList<>(names.size());
        for (String name : names) {
            CompletableFuture<D> fetched = reads.<D>submit(c -> fetch.fetch(c, name))
                .exceptionally(ex -> {
                    System.out.println("EXCEPTION " + label + " fetch of '" + name + "': " + rootMessage(ex));
                    return null;
                });
            CompletableFuture<Void> chain = fetched.thenCompose(data -> {
                if (data == null) return CompletableFuture.completedFuture(null);
                return writes.submit(() -> render.render(data))
                    .exceptionally(ex -> {
                        System.out.println("EXCEPTION " + label + " write of '" + name + "': " + rootMessage(ex));
                        if (AppConfig.verboseMode) ((Throwable) ex).printStackTrace(System.out);
                        return null;
                    });
            });
            chains.add(chain);
        }
        int done = 0;
        for (CompletableFuture<Void> c : chains) {
            c.join();
            done++;
            if (done % 500 == 0) System.out.println("  ... " + done + "/" + names.size() + " " + label + " pages written");
        }
        System.out.println(done + " " + label + " pages written.");
    }

    /** Write-only parallel variant for object types with no per-item AR System fetch (users/groups/roles - already bulk-loaded by IdentityRepository up front). */
    /**
     * File-mode counterpart to documentEachParallel - no ReadPool involved since file mode's
     * repos are now fully pre-loaded in memory (see FileModeSchemaRepository's javadoc), so the
     * existing fused render(name) method (fetch+render together) is fast/local enough to run
     * directly on the write pool, one call per name.
     */
    private static void documentEachWriteOnly(String label, List<String> names, WritePool writes, DetailRenderer renderer) {
        List<CompletableFuture<Void>> chains = new ArrayList<>(names.size());
        for (String name : names) {
            chains.add(writes.submit(() -> renderer.render(name))
                .exceptionally(ex -> {
                    System.out.println("EXCEPTION " + label + " documentation of '" + name + "': " + rootMessage(ex));
                    return null;
                }));
        }
        int done = 0;
        for (CompletableFuture<Void> c : chains) {
            c.join();
            done++;
            if (done % 500 == 0) System.out.println("  ... " + done + "/" + names.size() + " " + label + " pages written");
        }
        System.out.println(done + " " + label + " pages written.");
    }

    private static <T> void documentWriteOnly(String label, List<T> items, WritePool writes, ParallelRender<T> render) {
        List<CompletableFuture<Void>> chains = new ArrayList<>(items.size());
        for (T item : items) {
            chains.add(writes.submit(() -> render.render(item))
                .exceptionally(ex -> {
                    System.out.println("EXCEPTION " + label + " write: " + rootMessage(ex));
                    return null;
                }));
        }
        for (CompletableFuture<Void> c : chains) c.join();
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    /**
     * Second pass for objects with a hidden overlay base layer - see OverlaySupport's javadoc for
     * the AR_OVERLAY_CLIENT_MODE_FULL mechanism this relies on. Re-renders the SAME detail-page
     * renderer already used for the normal pass, just for the small set of names discovered to
     * have a base layer, with the session's overlay group temporarily set to "-2" so the existing
     * renderer's own re-fetch-and-check-overlayType logic picks up the base layer instead of the
     * active one - no changes needed in any Doc*DetailPage class for this to work.
     */
    private static void documentOverlayBaseLayers(ArClient client, String label, OverlaySupport.NameLister lister, DetailRenderer renderer) throws ARException {
        documentOverlayBaseLayers(client, label, lister, renderer, null, null);
    }

    @FunctionalInterface
    private interface BaseFetcher<T> {
        T fetch(String name) throws ARException;
    }

    @FunctionalInterface
    private interface DiffRenderer<T> {
        void render(String name, T base) throws ARException;
    }

    /**
     * Same base-layer discovery/toggle as the 4-arg overload, plus (when {@code baseFetcher}/
     * {@code diffRenderer} are non-null) a third pass: fetch each base-layer object's data (still
     * under "-2", right after the base-layer page render so both happen in the same toggle window),
     * then - back in default/overlay mode - re-render the PLAIN-name page a third time with that
     * base data attached, overwriting the normal first pass's output with the diff-annotated
     * version. See {@link arinside.doc.OverlayDiff}'s class javadoc for why this is a real
     * base-vs-overlay comparison rather than trusting AR System's own granular-overlay bookkeeping.
     */
    private static <T> void documentOverlayBaseLayers(ArClient client, String label, OverlaySupport.NameLister lister,
                                                        DetailRenderer renderer, BaseFetcher<T> baseFetcher, DiffRenderer<T> diffRenderer) throws ARException {
        if (client == null) return; // connectionless XML file mode - nothing to toggle overlay-group discovery against, see AppConfig.connectionless's javadoc
        List<String> baseNames = OverlaySupport.discoverOverlayBaseNames(client, lister);
        if (baseNames.isEmpty()) return;
        Map<String, T> baseObjects = baseFetcher != null ? new HashMap<>() : null;
        client.raw().setOverlayGroup("-2");
        try {
            documentEach(label + " overlay base layer", baseNames, renderer);
            if (baseFetcher != null) {
                for (String name : baseNames) {
                    try {
                        baseObjects.put(name, baseFetcher.fetch(name));
                    } catch (Exception e) {
                        System.out.println("EXCEPTION " + label + " overlay base fetch of '" + name + "': " + rootMessage(e));
                    }
                }
            }
        } finally {
            client.raw().setOverlayGroup(null);
        }
        if (diffRenderer != null) {
            documentEach(label + " overlay diff", baseNames, name -> {
                T base = baseObjects.get(name);
                if (base != null) diffRenderer.render(name, base);
            });
        }
    }
}
