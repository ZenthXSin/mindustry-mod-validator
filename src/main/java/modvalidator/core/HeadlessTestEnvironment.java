package modvalidator.core;

import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustry.net.*;
import mindustry.ui.*;
import mindustry.world.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static mindustry.Vars.*;

/**
 * Manages a headless Mindustry environment for dynamic mod testing.
 * Mirrors ServerLauncher.init() setup sequence exactly.
 */
public class HeadlessTestEnvironment {

    private final Fi testDataDir;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<Throwable> initError = new AtomicReference<>();
    private final CopyOnWriteArrayList<String> errorLogs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> warnLogs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> allLogs = new CopyOnWriteArrayList<>();
    private LoadedMod importedMod = null;

    public HeadlessTestEnvironment(Fi testDataDir){
        this.testDataDir = testDataDir;
    }

    public HeadlessTestEnvironment(String testDataPath){
        this(new Fi(testDataPath));
    }

    /**
     * Initialize the headless Mindustry environment.
     * Mirrors ServerLauncher.init() sequence.
     *
     * @param modPath optional path to a mod file/directory to import before content creation
     */
    public synchronized void initialize(String modPath) throws Exception {
        if(initialized.get()) return;

        testDataDir.deleteDirectory();
        Log.useColors = false;

        Log.logger = new Log.LogHandler() {
            @Override
            public void log(Log.LogLevel level, String text){
                allLogs.add("[" + level + "] " + text);
                if(level == Log.LogLevel.err){
                    errorLogs.add(text);
                }else if(level == Log.LogLevel.warn){
                    warnLogs.add(text);
                }
            }
        };

        try{
            boolean[] begins = {false};

            ApplicationCore core = new ApplicationCore(){
                @Override
                public void setup(){
                    // Phase 1: Core systems (matches ServerLauncher.init())
                    Core.settings.setDataDirectory(testDataDir);
                    loadLocales = false;
                    headless = true;

                    Vars.loadSettings();
                    Vars.init();

                    UI.loadColors();
                    Fonts.loadContentIconsHeadless();

                    // Phase 2: Base content (skip campaign/sector presets - not needed for mod validation)
                    mindustry.ai.UnitCommand.loadAll();
                    mindustry.content.TeamEntries.load();
                    mindustry.content.Items.load();
                    mindustry.ai.UnitStance.loadAll();
                    mindustry.content.StatusEffects.load();
                    mindustry.content.Liquids.load();
                    mindustry.content.Bullets.load();
                    mindustry.content.UnitTypes.load();
                    mindustry.content.Blocks.load();
                    mindustry.content.Loadouts.load();
                    mindustry.content.Weathers.load();
                    // Skip: Planets, SectorPresets, SerpuloTechTree, ErekirTechTree (require map files)
                    mods.loadScripts();

                    // Phase 3: Import mod BEFORE createModContent
                    if(modPath != null){
                        try{
                            Fi modFile = new Fi(modPath);
                            if(modFile.exists()){
                                Fi zipFile;
                                if(modFile.isDirectory()){
                                    zipFile = new Fi(modFile.path() + ".zip");
                                    zipDirectory(modFile, zipFile);
                                }else{
                                    zipFile = modFile;
                                }
                                importedMod = Vars.mods.importMod(zipFile);
                                if(importedMod == null){
                                    initError.compareAndSet(null, new RuntimeException("importMod returned null for: " + modPath));
                                }
                            }else{
                                initError.compareAndSet(null, new RuntimeException("Mod not found: " + modPath));
                            }
                        }catch(Throwable t){
                            initError.compareAndSet(null, new RuntimeException("Failed to import mod: " + t.getMessage(), t));
                        }
                    }

                    // Phase 4: Create mod content (scans imported mods)
                    content.createModContent();

                    // Phase 5: Initialize content
                    content.init();

                    // Phase 6: Check for content errors
                    if(Vars.mods.hasContentErrors()){
                        StringBuilder sb = new StringBuilder();
                        for(LoadedMod mod : Vars.mods.list()){
                            if(mod.hasContentErrors()){
                                for(Content cont : mod.erroredContent){
                                    sb.append("Error in ").append(cont.minfo.sourceFile.path())
                                      .append(": ").append(cont.minfo.baseError != null ? cont.minfo.baseError.getMessage() : "unknown").append("\n");
                                }
                            }
                        }
                        // Don't set initError - we want to continue so we can report errors
                        errorLogs.add("Content errors detected:\n" + sb);
                    }

                    // Phase 7: World + net + logic
                    world = new World(){
                        @Override
                        public float getDarkness(int x, int y){
                            return 0;
                        }
                    };
                    net = new Net(null);

                    Core.app.addListener(logic = new Logic());
                    Core.app.addListener(netServer = new NetServer());

                    // Phase 8: Base templates
                    bases.load();

                    // Phase 9: Mod init classes
                    mods.eachClass(Mod::init);
                }

                @Override
                public void init(){
                    super.init();
                    begins[0] = true;
                    Core.app.exit();
                }
            };

            new HeadlessApplication(core, throwable -> {
                initError.compareAndSet(null, throwable);
            });

            long start = System.currentTimeMillis();
            while(!begins[0] && initError.get() == null && System.currentTimeMillis() - start < 120000){
                Thread.sleep(50);
            }

            if(initError.get() != null){
                throw initError.get();
            }

            if(!begins[0]){
                throw new TimeoutException("Headless environment initialization timed out after 120s");
            }

            initialized.set(true);

        }catch(Throwable t){
            throw new RuntimeException("Failed to initialize headless environment", t);
        }
    }

    private void zipDirectory(Fi sourceDir, Fi destZip) throws Exception {
        try(java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(destZip.file()))){
            Seq<Fi> files = new Seq<>();
            sourceDir.walk(f -> files.add(f));
            for(Fi f : files){
                String relative = f.path().substring(sourceDir.path().length() + 1);
                if(f.isDirectory()){
                    if(!relative.endsWith("/")) relative += "/";
                    zos.putNextEntry(new java.util.zip.ZipEntry(relative));
                    zos.closeEntry();
                }else{
                    zos.putNextEntry(new java.util.zip.ZipEntry(relative));
                    zos.write(f.readBytes());
                    zos.closeEntry();
                }
            }
        }
    }

    public LoadedMod getImportedMod(){ return importedMod; }
    public Seq<LoadedMod> getLoadedMods(){ return Vars.mods.list(); }
    public LoadedMod getMod(String name){ return Vars.mods.getMod(name); }
    public boolean hasContentErrors(){ return Vars.mods.hasContentErrors(); }

    public Map<String, List<String>> getContentErrors(){
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for(LoadedMod mod : Vars.mods.list()){
            if(mod.hasContentErrors()){
                List<String> modErrors = new ArrayList<>();
                for(Content cont : mod.erroredContent){
                    modErrors.add("[" + cont.minfo.sourceFile.path() + "] " +
                        (cont.minfo.baseError != null ? cont.minfo.baseError.getMessage() : "unknown error"));
                }
                errors.put(mod.name, modErrors);
            }
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    public <T extends Content> Seq<T> getContent(ContentType type){
        return content.getBy(type);
    }

    public List<String> getErrorLogs(){ return Collections.unmodifiableList(errorLogs); }
    public List<String> getWarnLogs(){ return Collections.unmodifiableList(warnLogs); }
    public List<String> getAllLogs(){ return Collections.unmodifiableList(allLogs); }

    public World world(){ return Vars.world; }
    public ContentLoader content(){ return Vars.content; }
    public Mods mods(){ return Vars.mods; }

    public void dispose(){
        if(initialized.get()){
            try{ Core.app.exit(); }catch(Exception ignored){}
            initialized.set(false);
        }
    }
}
